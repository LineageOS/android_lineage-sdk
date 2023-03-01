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
import android.os.BatteryStatsManager;
import android.os.BatteryUsageStats;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.format.DateUtils;
import android.util.Log;

import org.lineageos.platform.internal.R;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import lineageos.providers.LineageSettings;

import vendor.lineage.health.IChargingControl;

import static lineageos.health.HealthInterface.MODE_NONE;
import static lineageos.health.HealthInterface.MODE_AUTO;
import static lineageos.health.HealthInterface.MODE_MANUAL;
import static lineageos.health.HealthInterface.MODE_LIMIT;

public class ChargingControlController extends LineageHealthFeature {
    private IChargingControl mChargingControl;
    private ContentResolver mContentResolver;
    private LineageHealthBatteryBroadcastReceiver mBattReceiver;
    private ChargingControlNotification mChargingNotification;

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
    private int mChargingTimeMargin = 30 * 60 * 1000;
    private boolean mIsPowerConnected = false;
    private int mChargingStopReason = 0;
    private long mEstimatedFullTime = 0;
    private long mSavedAlarmTime = 0;
    private boolean mIsControlCancelledOnce = false;

    private static final DateTimeFormatter mFormatter = DateTimeFormatter.ofLocalizedTime(SHORT);

    // Only when the battery level is above this limit will the charging control be activated.
    private static int CHARGE_CTRL_MIN_LEVEL = 80;

    private static class ChargingStopReason {
        /**
         * No stop charging
         */
        public static final int NONE = 0;

        /**
         * The charging stopped because it reaches limit
         */
        public static final int REACH_LIMIT = 1 << 0;

        /**
         * The charging stopped because the battery level is decent, and we are waiting to resume
         * charging when the time approaches the target time.
         */
        public static final int WAITING = 1 << 1;
    }

    public ChargingControlController(Context context, Handler handler) {
        super(context, handler);

        mContentResolver = mContext.getContentResolver();
        mChargingControl = IChargingControl.Stub.asInterface(
                ServiceManager.getService(IChargingControl.DESCRIPTOR + "/default"));

        if (mChargingControl == null) {
            Log.i(TAG, "Lineage Health HAL not found");
        }

        mChargingNotification = new ChargingControlNotification(context);

        mChargingTimeMargin = mContext.getResources().getInteger(
                R.integer.config_chargingControlTimeMargin) * 60 * 1000;

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

    @Override
    public void onStart() {
        if (mChargingControl == null) {
            return;
        }

        // Register setting observer
        registerSettings(MODE_URI, LIMIT_URI, ENABLED_URI, START_TIME_URI, TARGET_TIME_URI);

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
        mSavedAlarmTime = 0;
        mEstimatedFullTime = 0;
        mChargingStopReason = 0;
        mIsControlCancelledOnce = false;
        mChargingNotification.cancel();
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

        updateChargeState();
    }

    private void handleBatteryIntent(Intent intent) {
        int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        if (chargePlug == 0) {
            mIsControlCancelledOnce = false;
            return;
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level * 100 / (float) scale;

        mBatteryPct = batteryPct;
        updateChargeState();
    }

    private void setChargingReason(int flag, boolean set) {
        if (set) {
            mChargingStopReason |= flag;
        } else {
            mChargingStopReason &= ~flag;
        }
    }

    private boolean isChargingReasonSet(int flag) {
        return (mChargingStopReason & flag) != 0;
    }

    private boolean shouldSetLimitFlag() {
        if (mConfigMode != MODE_LIMIT) {
            return false;
        }

        if (mBatteryPct >= mConfigLimit) {
            mChargingNotification.post(null, true);
            return true;
        } else {
            mChargingNotification.post(null, false);
            return false;
        }
    }

    private boolean shouldSetWaitFlag() {
        if (mConfigMode != MODE_AUTO && mConfigMode != MODE_MANUAL) {
            return false;
        }

        // Now it is time to see whether charging should be stopped. We make decisions in the
        // following manner:
        //
        //  1. If STOP_REASON_WAITING is set, compare the remaining time with the saved estimated
        //     full time. Resume charging the remain time <= saved estimated time
        //  2. If the system estimated remaining time already exceeds the target full time, continue
        //  3. Otherwise, stop charging, save the estimated time, set stop reason to
        //     STOP_REASON_WAITING.
        //

        // Get duration to target full time
        final long currentTime = System.currentTimeMillis();
        Log.i(TAG, "Current time is " + msToString(currentTime));
        long targetTime = 0;
        if (mConfigMode == MODE_AUTO) {
            // Use alarm as the target time. Maybe someday we can use a model.
            AlarmManager m = mContext.getSystemService(AlarmManager.class);
            if (m == null) {
                Log.e(TAG, "Failed to get alarm service!");
                mChargingNotification.cancel();
                return false;
            }
            AlarmManager.AlarmClockInfo alarmClockInfo = m.getNextAlarmClock();
            if (alarmClockInfo == null) {
                // We didn't find an alarm. Clear waiting flags because we can't predict anyway
                mChargingNotification.cancel();
                return false;
            }
            targetTime = alarmClockInfo.getTriggerTime();

            if (mSavedAlarmTime != targetTime) {
                mChargingNotification.cancel();

                if (mSavedAlarmTime != 0 && mSavedAlarmTime < currentTime) {
                    Log.i(TAG, "Not fully charged when alarm goes off, continue charging.");
                    mIsControlCancelledOnce = true;
                    return false;
                }

                Log.i(TAG, "User changed alarm, reconstruct notification");
                mSavedAlarmTime = targetTime;
            }

            // Don't activate if we are more than 9 hrs away from the target alarm
            if (targetTime - currentTime >= 9 * 60 * 60 * 1000) {
                mChargingNotification.cancel();
                return false;
            }
            Log.i(TAG, "Target time: " + msToString(targetTime));
        } else if (mConfigMode == MODE_MANUAL) {
            // User manually controlled time
            long startTime = getTimeMillisFromSecondOfDay(mConfigStartTime);
            targetTime = getTimeMillisFromSecondOfDay(mConfigTargetTime);

            if (startTime > targetTime) {
                if (currentTime > targetTime) {
                    targetTime += DateUtils.DAY_IN_MILLIS;
                } else {
                    startTime -= DateUtils.DAY_IN_MILLIS;
                }
            }
            Log.i(TAG, "Start time: " + msToString(startTime));
            Log.i(TAG, "Target time: " + msToString(targetTime));
            if (startTime > currentTime) {
                // Not yet entering user configured time frame
                mChargingNotification.cancel();
                return false;
            }
        } else {
            Log.e(TAG, "invalid charging control mode " + mConfigMode);
            mChargingNotification.cancel();
            return false;
        }

        if (mBatteryPct == 100) {
            mChargingNotification.post(targetTime, true);
            return true;
        }

        // Now we have the target time and current time, we can post a notification stating that
        // the system will be charged by targetTime.
        mChargingNotification.post(targetTime, false);

        // If current battery level is less than the fast charge limit, don't set this flag
        if (mBatteryPct < CHARGE_CTRL_MIN_LEVEL) {
            return false;
        }

        long deltaTime = targetTime - currentTime;
        Log.i(TAG, "Current time to target: " + msToString(deltaTime));

        if (isChargingReasonSet(ChargingStopReason.WAITING)) {
            Log.i(TAG, "Current saved estimation to full: " + msToString(mEstimatedFullTime));
            if (deltaTime <= mEstimatedFullTime) {
                Log.i(TAG, "Unset waiting flag");
                return false;
            }
            return true;
        }

        final BatteryUsageStats batteryUsageStats = mContext.getSystemService(
                BatteryStatsManager.class).getBatteryUsageStats();
        if (batteryUsageStats == null) {
            Log.e(TAG, "Failed to get battery usage stats");
            return false;
        }
        long remaining = batteryUsageStats.getChargeTimeRemainingMs();
        if (remaining == -1) {
            Log.i(TAG, "not enough data for prediction for now, waiting for more data");
            return false;
        }

        // Add margin here
        remaining += mChargingTimeMargin;
        Log.i(TAG, "Current estimated time to full: " + msToString(remaining));
        if (deltaTime > remaining) {
            Log.i(TAG, "Stop charging and wait, saving remaining time");
            mEstimatedFullTime = remaining;
            return true;
        }

        return false;
    }

    private void updateChargingStopReason() {
        if (mIsControlCancelledOnce) {
            mChargingStopReason = ChargingStopReason.NONE;
            return;
        }

        if (!mConfigEnabled) {
            mChargingStopReason = ChargingStopReason.NONE;
            return;
        }

        if (!mIsPowerConnected) {
            mChargingStopReason = ChargingStopReason.NONE;
            return;
        }

        setChargingReason(ChargingStopReason.REACH_LIMIT, shouldSetLimitFlag());
        setChargingReason(ChargingStopReason.WAITING, shouldSetWaitFlag());
    }

    private void updateChargeState() {
        updateChargingStopReason();
        Log.i(TAG, "Current mChargingStopReason: " + mChargingStopReason);
        boolean isChargingEnabled = false;
        try {
            isChargingEnabled = mChargingControl.getChargingEnabled();
        } catch (RemoteException | UnsupportedOperationException | IllegalStateException e) {
            Log.e(TAG, "Failed to get charging enabled status!");
        }
        if (isChargingEnabled != (mChargingStopReason == 0)) {
            try {
                mChargingControl.setChargingEnabled(!isChargingEnabled);
            } catch (RemoteException | UnsupportedOperationException e) {
                Log.e(TAG, "Failed to set charging status");
            }
        }
    }

    private String msToString(long ms) {
        long millis = ms % 1000;
        long second = (ms / 1000) % 60;
        long minute = (ms / (1000 * 60)) % 60;
        long hour = (ms / (1000 * 60 * 60)) % 24;

        return String.format("%02d:%02d:%02d.%d", hour, minute, second, millis);
    }

    private long getTimeMillisFromSecondOfDay(int time) {
        ZoneId utcZone = ZoneOffset.UTC;
        LocalDate currentDate = LocalDate.now(utcZone);
        LocalTime timeOfDay = LocalTime.ofSecondOfDay(time);

        ZonedDateTime zonedDateTime = ZonedDateTime.of(currentDate, timeOfDay, utcZone);
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

        // Cancel notification, so that it can be updated later
        mChargingNotification.cancel();

        // Update based on those values
        updateChargeState();
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
        pw.println("  mChargingTimeMargin: " + mChargingTimeMargin);
        pw.println();
        pw.println("ChargingControlController State:");
        pw.println("  mBatteryPct: " + mBatteryPct);
        pw.println("  mIsPowerConnected: " + mIsPowerConnected);
        pw.println("  mChargingStopReason: " + mChargingStopReason);
        pw.println("  mIsNotificationPosted: " + mChargingNotification.isPosted());
        pw.println("  mIsDoneNotification: " + mChargingNotification.isDoneNotification());
        pw.println("  mIsControlCancelledOnce: " + mIsControlCancelledOnce);
        pw.println("  mSavedAlarmTime: " + msToString(mSavedAlarmTime));
    }

    /* Battery Broadcast Receiver */
    private class LineageHealthBatteryBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleBatteryIntent(intent);
        }
    }

    /* Notification class */
    class ChargingControlNotification {
        private NotificationManager mNotificationManager;
        private Context mContext;

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
                updateChargeState();
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

            Intent cancelOnceIntent = new Intent(ACTION_CHARGING_CONTROL_CANCEL_ONCE);
            PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(mContext, 0,
                    cancelOnceIntent, PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder notification =
                    new Notification.Builder(mContext, CHARGING_CONTROL_CHANNEL_ID)
                            .setContentTitle(title)
                            .setContentText(message)
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

            Notification.Builder notification = new Notification.Builder(mContext,
                    CHARGING_CONTROL_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_charging_control)
                    .setOngoing(false);

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
}
