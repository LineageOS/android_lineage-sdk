/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.platform.internal.health;

import static java.time.format.FormatStyle.SHORT;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.format.DateUtils;
import android.util.Log;

import org.lineageos.platform.internal.R;
import org.lineageos.platform.internal.health.provider.ChargingControlProvider;
import org.lineageos.platform.internal.health.provider.LimitProviderToggle;
import org.lineageos.platform.internal.health.provider.LimitProviderToggleNB;
import org.lineageos.platform.internal.health.provider.TimeProviderDeadline;
import org.lineageos.platform.internal.health.provider.TimeProviderToggle;
import org.lineageos.platform.internal.health.provider.TimeProviderToggleNB;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

import lineageos.providers.LineageSettings;

import vendor.lineage.health.ChargingControlSupportedMode;
import vendor.lineage.health.IChargingControl;

import static lineageos.health.HealthInterface.MODE_NONE;
import static lineageos.health.HealthInterface.MODE_AUTO;
import static lineageos.health.HealthInterface.MODE_MANUAL;
import static lineageos.health.HealthInterface.MODE_LIMIT;

public class ChargingControlController extends LineageHealthFeature {
    private final IChargingControl mChargingControl;
    private final ContentResolver mContentResolver;
    private final ChargingControlNotification mChargingNotification;
    private LineageHealthBatteryBroadcastReceiver mBattReceiver;

    // Defaults
    private final boolean mDefaultEnabled;
    private final int mDefaultMode;
    private final int mDefaultLimit;
    private final int mDefaultStartTime;
    private final int mDefaultTargetTime;

    // User configs
    private boolean mConfigEnabled = false;
    private int mConfigMode = MODE_NONE;
    private int mConfigLimit = 100;
    private int mConfigStartTime = 0;
    private int mConfigTargetTime = 0;

    // Charging control providers
    ChargingControlProvider toggleLimitProvider;
    ChargingControlProvider toggleTimeProvider;
    ChargingControlProvider toggleLimitProviderNB;
    ChargingControlProvider toggleTimeProviderNB;
    ChargingControlProvider deadlineTimeProvider;

    ChargingControlProvider mPreferredLimitProvider;
    ChargingControlProvider mPreferredTimeProvider;
    ChargingControlProvider mCurrentProvider;

    // Settings uris
    private final Uri MODE_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.CHARGING_CONTROL_MODE);
    private final Uri LIMIT_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.CHARGING_CONTROL_LIMIT);
    private final Uri ENABLED_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.CHARGING_CONTROL_ENABLED);
    private final Uri START_TIME_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.CHARGING_CONTROL_START_TIME);
    private final Uri TARGET_TIME_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.CHARGING_CONTROL_TARGET_TIME);

    // Internal state
    private float mBatteryPct = 0;
    private boolean mIsPowerConnected = false;
    private boolean mIsControlCancelledOnce = false;
    private boolean mShouldAlwaysMonitorBattery = false;

    private static final DateTimeFormatter mFormatter = DateTimeFormatter.ofLocalizedTime(SHORT);
    private static final SimpleDateFormat mDateFormatter = new SimpleDateFormat("hh:mm:ss a");

    private static final String INTENT_PARTS =
            "org.lineageos.lineageparts.CHARGING_CONTROL_SETTINGS";

    public ChargingControlController(Context context, Handler handler) {
        super(context, handler);

        mContentResolver = mContext.getContentResolver();
        mChargingControl = IChargingControl.Stub.asInterface(
                ServiceManager.getService(IChargingControl.DESCRIPTOR + "/default"));

        if (mChargingControl == null) {
            Log.i(TAG, "Lineage Health HAL not found");
        }

        mChargingNotification = new ChargingControlNotification(context);

        mDefaultEnabled = mContext.getResources().getBoolean(
                R.bool.config_chargingControlEnabled);
        mDefaultMode = mContext.getResources().getInteger(
                R.integer.config_defaultChargingControlMode);
        mDefaultStartTime = mContext.getResources().getInteger(
                R.integer.config_defaultChargingControlStartTime);
        mDefaultTargetTime = mContext.getResources().getInteger(
                R.integer.config_defaultChargingControlTargetTime);
        mDefaultLimit = mContext.getResources().getInteger(
                R.integer.config_defaultChargingControlLimit);

        // Initialize providers
        toggleLimitProvider = new LimitProviderToggle(mChargingControl, context);
        toggleTimeProvider = new TimeProviderToggle(mChargingControl, context);
        toggleLimitProviderNB = new LimitProviderToggleNB(mChargingControl, context);
        toggleTimeProviderNB = new TimeProviderToggleNB(mChargingControl, context);
        deadlineTimeProvider = new TimeProviderDeadline(mChargingControl, context);

        if (toggleTimeProvider.isSupported()) {
            mPreferredTimeProvider = toggleTimeProvider;
        } else if (deadlineTimeProvider.isSupported()) {
            mPreferredTimeProvider = deadlineTimeProvider;
        } else if (toggleTimeProviderNB.isSupported()) {
            mPreferredTimeProvider = toggleTimeProviderNB;
        } else {
            Log.e(TAG, "No available charging control provider for time!");
            mPreferredTimeProvider = null;
        }

        if (toggleLimitProvider.isSupported()) {
            mPreferredLimitProvider = toggleLimitProvider;
        } else if (toggleLimitProviderNB.isSupported()) {
            mPreferredLimitProvider = toggleLimitProviderNB;
        } else {
            Log.e(TAG, "No available charging control provider for limit!");
            mPreferredTimeProvider = null;
        }

        if (mPreferredLimitProvider == null && mPreferredTimeProvider == null) {
            Log.wtf(TAG, "No available charging control providers!");
        }

        if (mPreferredTimeProvider != null) {
            mShouldAlwaysMonitorBattery |= mPreferredTimeProvider.shouldAlwaysMonitorBattery();
        }

        if (mPreferredLimitProvider != null) {
            mShouldAlwaysMonitorBattery |= mPreferredLimitProvider.shouldAlwaysMonitorBattery();
        }
    }

    @Override
    public boolean isSupported() {
        return mChargingControl != null;
    }

    public boolean isEnabled() {
        return mConfigEnabled;
    }

    public boolean setEnabled(boolean enabled) {
        putBoolean(LineageSettings.System.CHARGING_CONTROL_ENABLED, enabled);
        return true;
    }

    public int getMode() {
        return mConfigMode;
    }

    public boolean setMode(int mode) {
        if (mode < MODE_NONE || mode > MODE_LIMIT) {
            return false;
        }

        putInt(LineageSettings.System.CHARGING_CONTROL_MODE, mode);
        return true;
    }

    public int getStartTime() {
        return mConfigStartTime;
    }

    public boolean setStartTime(int time) {
        if (time < 0 || time > 24 * 60 * 60) {
            return false;
        }

        putInt(LineageSettings.System.CHARGING_CONTROL_START_TIME, time);
        return true;
    }

    public int getTargetTime() {
        return mConfigTargetTime;
    }

    public boolean setTargetTime(int time) {
        if (time < 0 || time > 24 * 60 * 60) {
            return false;
        }

        putInt(LineageSettings.System.CHARGING_CONTROL_TARGET_TIME, time);
        return true;
    }

    public int getLimit() {
        return mConfigLimit;
    }

    public boolean setLimit(int limit) {
        if (limit < 0 || limit > 100) {
            return false;
        }

        putInt(LineageSettings.System.CHARGING_CONTROL_LIMIT, limit);
        return true;
    }

    public boolean reset() {
        return setEnabled(mDefaultEnabled) && setMode(mDefaultMode) && setLimit(mDefaultLimit)
                && setStartTime(mDefaultStartTime) && setTargetTime(mDefaultTargetTime);
    }

    public boolean isChargingModeSupported(int mode) {
        try {
            return (mChargingControl.getSupportedMode() & mode) != 0;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onStart() {
        if (mChargingControl == null) {
            return;
        }

        // Register setting observer
        registerSettings(MODE_URI, LIMIT_URI, ENABLED_URI, START_TIME_URI, TARGET_TIME_URI);

        // For devices that do not support bypass, we can only always listen to battery change
        // because we can't distinguish between "unplugged" and "plugged in but not charging".
        if (mShouldAlwaysMonitorBattery) {
            mIsPowerConnected = true;
            onPowerStatus(true);
            handleSettingChange();
            return;
        }

        // Start monitor battery status when power connected
        IntentFilter connectedFilter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Power connected, start monitoring battery");
                mIsPowerConnected = true;
                onPowerStatus(true);
            }
        }, connectedFilter);

        // Stop monitor battery status when power disconnected
        IntentFilter disconnectedFilter = new IntentFilter(Intent.ACTION_POWER_DISCONNECTED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Power disconnected, stop monitoring battery");
                mIsPowerConnected = false;
                onPowerStatus(false);
            }
        }, disconnectedFilter);

        // Initial monitor
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, ifilter);
        mIsPowerConnected = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0;
        if (mIsPowerConnected) {
            onPowerConnected();
        }

        // Restore settings
        handleSettingChange();
    }

    private void resetInternalState() {
        mIsControlCancelledOnce = false;
        mChargingNotification.cancel();

        if (mConfigMode == MODE_LIMIT) {
            assert mPreferredLimitProvider != null;
            mPreferredLimitProvider.reset();
        } else {
            assert mPreferredTimeProvider != null;
            mPreferredLimitProvider.reset();
        }
    }

    private void onPowerConnected() {
        if (mBattReceiver == null) {
            mBattReceiver = new LineageHealthBatteryBroadcastReceiver();
        }
        IntentFilter battFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(mBattReceiver, battFilter);
    }

    private void onPowerDisconnected() {
        if (mBattReceiver != null) {
            mContext.unregisterReceiver(mBattReceiver);
        }

        // On disconnected, reset internal state
        resetInternalState();
    }

    private void onPowerStatus(boolean enable) {
        if (enable) {
            onPowerConnected();
        } else {
            onPowerDisconnected();
        }

        updateChargeControl();
    }

    private ChargeTime getChargeTime() {
        // Get duration to target full time
        final long currentTime = System.currentTimeMillis();
        Log.i(TAG, "Current time is " + msToString(currentTime));
        long targetTime = 0, startTime = currentTime;
        if (mConfigMode == MODE_AUTO) {
            // Use alarm as the target time. Maybe someday we can use a model.
            AlarmManager m = mContext.getSystemService(AlarmManager.class);
            if (m == null) {
                Log.e(TAG, "Failed to get alarm service!");
                mChargingNotification.cancel();
                return null;
            }
            AlarmManager.AlarmClockInfo alarmClockInfo = m.getNextAlarmClock();
            if (alarmClockInfo == null) {
                // We didn't find an alarm. Clear waiting flags because we can't predict anyway
                mChargingNotification.cancel();
                return null;
            }
            targetTime = alarmClockInfo.getTriggerTime();
        } else if (mConfigMode == MODE_MANUAL) {
            // User manually controlled time
            startTime = getTimeMillisFromSecondOfDay(mConfigStartTime);
            targetTime = getTimeMillisFromSecondOfDay(mConfigTargetTime);

            if (startTime > targetTime) {
                if (currentTime > targetTime) {
                    targetTime += DateUtils.DAY_IN_MILLIS;
                } else {
                    startTime -= DateUtils.DAY_IN_MILLIS;
                }
            }
        } else {
            Log.e(TAG, "Invalid charging control mode " + mConfigMode);
            return null;
        }

        Log.i(TAG, "Target time is " + msToString(targetTime));

        return new ChargeTime(startTime, targetTime);
    }

    private void updateChargeControl() {
        // First, get current provider
        ChargingControlProvider provider;
        if (mConfigMode == MODE_LIMIT) {
            provider = mPreferredLimitProvider;
        } else {
            provider = mPreferredTimeProvider;
        }

        if (provider == null) {
            Log.wtf(TAG, "Selected a mode but no provider is available!");
        }

        if (mCurrentProvider != provider) {
            if (mCurrentProvider != null) {
                mCurrentProvider.disable();
            }
            mCurrentProvider = provider;
        }

        if (!mConfigEnabled || mIsControlCancelledOnce || !mIsPowerConnected) {
            assert mCurrentProvider != null;
            mCurrentProvider.disable();
            mChargingNotification.cancel();
            return;
        }

        assert mCurrentProvider != null;

        boolean done;
        if (mConfigMode == MODE_LIMIT) {
            done = mCurrentProvider.onBatteryChanged(mBatteryPct, mConfigLimit);
            mChargingNotification.post(null, done);
        } else {
            ChargeTime t = getChargeTime();
            if (t == null) {
                mCurrentProvider.disable();
                mChargingNotification.cancel();
                return;
            }
            done = mCurrentProvider.onBatteryChanged(mBatteryPct, t.getStartTime(), t.getTargetTime(),
                    mConfigMode);
            mChargingNotification.post(t.getTargetTime(), done);
        }
    }

    public static String msToString(long ms) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(ms);
        return mDateFormatter.format(calendar.getTime());
    }

    /**
     * Convert the seconds of the day to UTC milliseconds from epoch.
     *
     * @param time seconds of the day
     * @return UTC milliseconds from epoch
     */
    private long getTimeMillisFromSecondOfDay(int time) {
        ZoneId utcZone = ZoneOffset.UTC;
        LocalDate currentDate = LocalDate.now();
        LocalTime timeOfDay = LocalTime.ofSecondOfDay(time);

        ZonedDateTime zonedDateTime = ZonedDateTime.of(currentDate, timeOfDay,
                        ZoneId.systemDefault())
                .withZoneSameInstant(utcZone);
        return zonedDateTime.toInstant().toEpochMilli();
    }

    private LocalTime getLocalTimeFromEpochMilli(long time) {
        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalTime();
    }

    private void handleSettingChange() {
        mConfigEnabled = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_ENABLED, 0)
                != 0;
        mConfigLimit = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_LIMIT,
                mDefaultLimit);
        mConfigMode = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_MODE,
                mDefaultMode);
        mConfigStartTime = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_START_TIME,
                mDefaultStartTime);
        mConfigTargetTime = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_TARGET_TIME,
                mDefaultTargetTime);

        // Reset internal states
        resetInternalState();

        // Update based on those values
        updateChargeControl();
    }


    @Override
    protected void onSettingsChanged(Uri uri) {
        handleSettingChange();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("ChargingControlController Configuration:");
        pw.println("  mConfigEnabled: " + mConfigEnabled);
        pw.println("  mConfigMode: " + mConfigMode);
        pw.println("  mConfigLimit: " + mConfigLimit);
        pw.println("  mConfigStartTime: " + mConfigStartTime);
        pw.println("  mConfigTargetTime: " + mConfigTargetTime);
        pw.println();
        pw.println("ChargingControlController State:");
        pw.println("  mBatteryPct: " + mBatteryPct);
        pw.println("  mIsPowerConnected: " + mIsPowerConnected);
        pw.println("  mIsNotificationPosted: " + mChargingNotification.isPosted());
        pw.println("  mIsDoneNotification: " + mChargingNotification.isDoneNotification());
        pw.println("  mIsControlCancelledOnce: " + mIsControlCancelledOnce);
        pw.println();
        mCurrentProvider.dump(pw);
    }

    /* Battery Broadcast Receiver */
    private class LineageHealthBatteryBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mIsPowerConnected) {
                return;
            }

            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level == -1 || scale == -1) {
                return;
            }

            mBatteryPct = level * 100 / (float) scale;
            updateChargeControl();
        }
    }

    /* Notification class */
    class ChargingControlNotification {
        private final NotificationManager mNotificationManager;
        private final Context mContext;

        private static final int CHARGING_CONTROL_NOTIFICATION_ID = 1000;
        private static final String ACTION_CHARGING_CONTROL_CANCEL_ONCE =
                "lineageos.platform.intent.action.CHARGING_CONTROL_CANCEL_ONCE";
        private static final String CHARGING_CONTROL_CHANNEL_ID = "LineageHealthChargingControl";

        private boolean mIsDoneNotification = false;
        private boolean mIsNotificationPosted = false;

        ChargingControlNotification(Context context) {
            mContext = context;

            // Get notification manager
            mNotificationManager = mContext.getSystemService(NotificationManager.class);

            // Register notification monitor
            IntentFilter notificationFilter = new IntentFilter(ACTION_CHARGING_CONTROL_CANCEL_ONCE);
            mContext.registerReceiver(new LineageHealthNotificationBroadcastReceiver(),
                    notificationFilter);
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
                mIsControlCancelledOnce = true;

                if (!isChargingModeSupported(ChargingControlSupportedMode.BYPASS)) {
                    IntentFilter disconnectFilter = new IntentFilter(
                            Intent.ACTION_POWER_DISCONNECTED);

                    // Register a one-time receiver that resets internal state on power
                    // disconnection
                    mContext.registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            Log.i(TAG, "Power disconnected, reset internal states");
                            resetInternalState();
                            mContext.unregisterReceiver(this);
                        }
                    }, disconnectFilter);
                }

                updateChargeControl();
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
                        mConfigLimit);
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
                message = String.format(
                        mContext.getString(
                                R.string.charging_control_notification_content_limit_reached),
                        mConfigLimit);
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

    /* A representation of start and target time */
    private static final class ChargeTime {
        private final long mStartTime;
        private final long mTargetTime;

        ChargeTime(long startTime, long targetTime) {
            mStartTime = startTime;
            mTargetTime = targetTime;
        }

        public long getStartTime() {
            return mStartTime;
        }

        public long getTargetTime() {
            return mTargetTime;
        }
    }
}
