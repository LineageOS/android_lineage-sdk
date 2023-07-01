package org.lineageos.platform.internal.health;

import static org.lineageos.platform.internal.health.Utils.getLocalTimeFromEpochMilli;

import static java.time.format.FormatStyle.SHORT;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import org.lineageos.platform.internal.R;
import org.lineageos.platform.internal.health.provider.ChargingControlProvider.ChargeControlInfo;

import java.time.format.DateTimeFormatter;

public class ChargingControlNotification {
    private final NotificationManager mNotificationManager;
    private final Context mContext;

    private static final int CHARGING_CONTROL_NOTIFICATION_ID = 1000;
    private static final String ACTION_CHARGING_CONTROL_CANCEL_ONCE =
            "lineageos.platform.intent.action.CHARGING_CONTROL_CANCEL_ONCE";
    private static final String CHARGING_CONTROL_CHANNEL_ID = "LineageHealthChargingControl";

    private boolean mIsDoneNotification = false;
    private boolean mIsNotificationPosted = false;
    private final boolean mShouldAlwaysMonitorBattery;
    private final ChargingControlController mChargingControlController;

    private static final DateTimeFormatter mFormatter = DateTimeFormatter.ofLocalizedTime(SHORT);
    private static final String INTENT_PARTS =
            "org.lineageos.lineageparts.CHARGING_CONTROL_SETTINGS";


    protected final static String TAG = "ChargingControlNotification";

    ChargingControlNotification(Context context, boolean shouldAlwaysMonitorBattery, ChargingControlController chargingControlController) {
        mContext = context;
        mShouldAlwaysMonitorBattery = shouldAlwaysMonitorBattery;
        mChargingControlController = chargingControlController;

        // Get notification manager
        mNotificationManager = mContext.getSystemService(NotificationManager.class);

        // Register notification monitor
        IntentFilter notificationFilter = new IntentFilter(ACTION_CHARGING_CONTROL_CANCEL_ONCE);
        mContext.registerReceiver(new LineageHealthNotificationBroadcastReceiver(),
                notificationFilter);
    }

    public void post(Long targetTime, ChargeControlInfo chargeControlInfo) {
        if (chargeControlInfo.mShouldPostNotification) {
            post(targetTime, chargeControlInfo.mIsDoneNotification);
        } else {
            cancel();
        }
    }

    public void post(Long targetTime, boolean done) {
        if (mIsNotificationPosted && mIsDoneNotification == done) {
            return;
        }

        if (mIsNotificationPosted) {
            cancel();
        }

        if (done) {
            postChargingDoneNotification(targetTime);
        } else {
            postChargingControlNotification(targetTime);
        }

        mIsNotificationPosted = true;
        mIsDoneNotification = done;
    }

    public void cancel() {
        cancelChargingControlNotification();
        mIsNotificationPosted = false;
    }

    public boolean isPosted() {
        return mIsNotificationPosted;
    }

    public boolean isDoneNotification() {
        return mIsDoneNotification;
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent.getAction().equals(ACTION_CHARGING_CONTROL_CANCEL_ONCE)) {
            mChargingControlController.setControlCancelledOnce();

            if (mShouldAlwaysMonitorBattery) {
                IntentFilter disconnectFilter = new IntentFilter(
                        Intent.ACTION_POWER_DISCONNECTED);

                // Register a one-time receiver that resets internal state on power
                // disconnection
                mContext.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Log.i(TAG, "Power disconnected, reset internal states");
                        mChargingControlController.resetInternalState();
                        mContext.unregisterReceiver(this);
                    }
                }, disconnectFilter);
            }

            mChargingControlController.updateChargeControl();
            cancelChargingControlNotification();
        }
    }

    private void postChargingControlNotification(Long targetTime) {
        String title = mContext.getString(R.string.charging_control_notification_title);
        String message;
        if (targetTime != null) {
            message = String.format(
                    mContext.getString(R.string.charging_control_notification_content_target),
                    getLocalTimeFromEpochMilli(targetTime).format(mFormatter));
        } else {
            message = String.format(
                    mContext.getString(R.string.charging_control_notification_content_limit),
                    mChargingControlController.getLimit());
        }

        Intent mainIntent = new Intent(INTENT_PARTS);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(mContext, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent cancelOnceIntent = new Intent(ACTION_CHARGING_CONTROL_CANCEL_ONCE);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(mContext, 0,
                cancelOnceIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder notification =
                new Notification.Builder(mContext, CHARGING_CONTROL_CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setContentIntent(mainPendingIntent)
                        .setSmallIcon(R.drawable.ic_charging_control)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_charging_control,
                                mContext.getString(
                                        R.string.charging_control_notification_cancel_once),
                                cancelPendingIntent);

        createNotificationChannelIfNeeded();
        mNotificationManager.notify(CHARGING_CONTROL_NOTIFICATION_ID, notification.build());
    }

    private void postChargingDoneNotification(Long targetTime) {
        cancelChargingControlNotification();

        String title = mContext.getString(R.string.charging_control_notification_title);
        String message;
        if (targetTime != null) {
            message = mContext.getString(
                    R.string.charging_control_notification_content_target_reached);
        } else {
            message = String.format(mContext.getString(
                            R.string.charging_control_notification_content_limit_reached),
                    mChargingControlController.getLimit());
        }

        Intent mainIntent = new Intent(INTENT_PARTS);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(mContext, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder notification = new Notification.Builder(mContext,
                CHARGING_CONTROL_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(mainPendingIntent)
                .setSmallIcon(R.drawable.ic_charging_control)
                .setOngoing(false);

        if (targetTime == null) {
            Intent cancelOnceIntent = new Intent(ACTION_CHARGING_CONTROL_CANCEL_ONCE);
            PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(mContext, 0,
                    cancelOnceIntent, PendingIntent.FLAG_IMMUTABLE);
            notification.addAction(R.drawable.ic_charging_control,
                    mContext.getString(R.string.charging_control_notification_cancel_once),
                    cancelPendingIntent);
        }

        createNotificationChannelIfNeeded();
        mNotificationManager.notify(CHARGING_CONTROL_NOTIFICATION_ID, notification.build());
    }

    private void createNotificationChannelIfNeeded() {
        String id = CHARGING_CONTROL_CHANNEL_ID;
        NotificationChannel channel = mNotificationManager.getNotificationChannel(id);
        if (channel != null) {
            return;
        }

        String name = mContext.getString(R.string.charging_control_notification_channel);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel batteryHealthChannel = new NotificationChannel(id, name,
                importance);
        batteryHealthChannel.setBlockable(true);
        mNotificationManager.createNotificationChannel(batteryHealthChannel);
    }

    private void cancelChargingControlNotification() {
        mNotificationManager.cancel(CHARGING_CONTROL_NOTIFICATION_ID);
    }

    /* Notification Broadcast Receiver */
    private class LineageHealthNotificationBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleNotificationIntent(intent);
        }
    }
}