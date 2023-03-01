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

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.BatteryStatsManager;
import android.os.BatteryUsageStats;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.format.DateUtils;
import android.util.Log;

import org.lineageos.platform.internal.LineageSystemService;

import java.util.Calendar;

import lineageos.app.LineageContextConstants;
import lineageos.health.IHealthInterface;
import lineageos.providers.LineageSettings;
import vendor.lineage.health.ILineageHealth;

public class HealthInterfaceService extends LineageSystemService {
    private static final String TAG = "LineageHealth";

    private ILineageHealth mLineageHealth;

    private final ContentResolver mContentResolver;
    private Context mContext;
    private LineageHealthSettingsObserver mObserver;
    private LineateHealthBatteryBroadcastReceiver mBattReceiver;

    // Settings uris
    private final Uri MODE_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_MODE);
    private final Uri LIMIT_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_CHARGING_LIMIT);
    private final Uri ENABLED_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_ENABLED);
    private final Uri START_TIME_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_START_TIME);
    private final Uri TARGET_TIME_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_TARGET_TIME);

    // Internal state
    private boolean mIsChargingEnabled = true;
    private boolean mIsPowerConnected = false;
    private int mChargingStopReason = 0;
    private long mEstimatedFullTime = 0;

    // Considering system's estimation of time to full might not be correct, set a 30 minute
    // margin
    private static int CHARGE_TIME_MARGIN = 30 * 60 * 1000;

    // Only when the battery level is above this limit will the charging control be activated.
    private static int CHARGE_CTRL_MIN_LEVEL = 80;

    private static class ChargingStopReason {
        /**
         * The charging stopped because it reaches limit
         */
        public static final int STOP_REASON_REACH_LIMIT = 1 << 0;

        /**
         * The charging stopped because the battery level is decent, and we are waiting to resume
         * charging when the time approaches the target time.
         */
        public static final int STOP_REASON_WAITING = 1 << 1;
    }

    // User configs
    private int mConfigMode = 0;
    private int mConfigLimit = 100;
    private int mConfigEnabled = 0;
    private int mConfigStartTime = 0;
    private int mConfigTargetTime = 0;

    private float mBatteryPct = 0;

    public HealthInterfaceService(Context context) {
        super(context);
        mContext = context;
        mContentResolver = mContext.getContentResolver();
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.HEALTH;
    }

    @Override
    public boolean isCoreService() {
        return false;
    }

    @Override
    public void onStart() {
        if (!mContext.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.HEALTH)) {
            Log.wtf(TAG, "Lineage Health service started by system server but feature xml "
                    + "not declared. Not publishing binder service!");
            return;
        }

        mLineageHealth = ILineageHealth.Stub.asInterface(
                ServiceManager.getService(ILineageHealth.DESCRIPTOR + "/default"));
        if (mLineageHealth != null) {
            publishBinderService(LineageContextConstants.LINEAGE_HEALTH_INTERFACE, mService);
        } else {
            Log.i(TAG, "Lineage Health HAL not found, not registering service");
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (mLineageHealth == null || phase != PHASE_BOOT_COMPLETED) {
            return;
        }

        // Register observer
        mObserver = new LineageHealthSettingsObserver(mContext, null);
        mObserver.observe(true);

        // Start monitor battery status when power connected
        IntentFilter connectedFilter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Power connected, start monitoring battery");
                mIsPowerConnected = true;
                setBatteryMonitor(true);
            }
        }, connectedFilter);

        // Stop monitor battery status when power disconnected
        IntentFilter disconnectedFilter = new IntentFilter(Intent.ACTION_POWER_DISCONNECTED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Power disconnected, stop monitoring battery");
                mIsPowerConnected = false;
                setBatteryMonitor(false);
            }
        }, disconnectedFilter);

        // Initial monitor
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, ifilter);
        mIsPowerConnected = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0;
        if (mIsPowerConnected) {
            startMonitorBattery();
        }

        // Check initial health HAL status
        try {
            mIsChargingEnabled = mLineageHealth.getChargingEnabled();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get charging enabled status!");
        }

        // Restore settings
        handleSettingChange();
    }

    private void startMonitorBattery() {
        if (mBattReceiver == null) {
            mBattReceiver = new LineateHealthBatteryBroadcastReceiver();
        }
        IntentFilter battFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(mBattReceiver, battFilter);
    }

    private void stopMonitorBattery() {
        if (mBattReceiver != null) {
            mContext.unregisterReceiver(mBattReceiver);
        }
    }

    private void setBatteryMonitor(boolean enable) {
        if (enable) {
            startMonitorBattery();
        } else {
            stopMonitorBattery();
        }

        updateChargeState();
    }

    private void handleBatteryIntent(Intent intent) {
        int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        if (chargePlug == 0) {
            return;
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level * 100 / (float) scale;

        mBatteryPct = batteryPct;
        updateChargeState();
    }

    private void updateChargingStopReason() {
        if (mConfigEnabled == 0) {
            mChargingStopReason = 0;
            return;
        }

        if (!mIsPowerConnected) {
            mChargingStopReason = 0;
            return;
        }

        if (mBatteryPct >= mConfigLimit) {
            mChargingStopReason |= ChargingStopReason.STOP_REASON_REACH_LIMIT;
            return;
        } else {
            // Clear the flag
            mChargingStopReason &= ~ChargingStopReason.STOP_REASON_REACH_LIMIT;
        }

        // If we configured the charging limit to be less than 100, why do we want to optimize the
        // charging pattern?
        if (mConfigLimit < 100) {
            mChargingStopReason &= ~ChargingStopReason.STOP_REASON_WAITING;
            return;
        }

        // If current battery level is less than the fast charge limit, enable charging
        if (mBatteryPct < CHARGE_CTRL_MIN_LEVEL) {
            mChargingStopReason &= ~ChargingStopReason.STOP_REASON_WAITING;
            return;
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
        if (mConfigMode == 1) {
            // Use alarm as the target time. Maybe someday we can use a model.
            AlarmManager m = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            if (m == null) {
                Log.e(TAG, "Failed to get alarm service!");
                return;
            }
            AlarmManager.AlarmClockInfo alarmClockInfo = m.getNextAlarmClock();
            if (alarmClockInfo == null) {
                // We didn't find an alarm. Don't set any flags because we can't predict anyway
                return;
            }
            targetTime = alarmClockInfo.getTriggerTime();
            Log.i(TAG, "Target time: " + msToString(targetTime));
        } else if (mConfigMode == 2) {
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
                return;
            }
        }

        long deltaTime = targetTime - currentTime;
        Log.i(TAG, "Current time to target: " + msToString(deltaTime));

        if ((mChargingStopReason & ChargingStopReason.STOP_REASON_WAITING) != 0) {
            Log.i(TAG, "Current saved estimation to full: " + msToString(mEstimatedFullTime));
            if (deltaTime <= mEstimatedFullTime) {
                Log.i(TAG, "Start charging");
                mChargingStopReason &= ~ChargingStopReason.STOP_REASON_WAITING;
            }
            return;
        }

        final BatteryUsageStats batteryUsageStats = mContext.getSystemService(
                BatteryStatsManager.class).getBatteryUsageStats();
        if (batteryUsageStats == null) {
            Log.e(TAG, "Failed to get battery usage stats");
        }
        long remaining = batteryUsageStats.getChargeTimeRemainingMs();
        if (remaining == -1) {
            Log.i(TAG, "not enough data for prediction for now, waiting for more data");
            return;
        }

        // Add margin here
        remaining += CHARGE_TIME_MARGIN;
        Log.i(TAG, "Current estimated time to full: " + msToString(remaining));
        if (deltaTime > remaining) {
            Log.i(TAG, "Stop charging and wait, saving remaining time");
            mChargingStopReason |= ChargingStopReason.STOP_REASON_WAITING;
            mEstimatedFullTime = remaining;
        }
    }

    private void updateChargeState() {
        updateChargingStopReason();
        Log.i(TAG, "Current mChargingStopReason: " + mChargingStopReason);
        if (mIsChargingEnabled != (mChargingStopReason == 0)) {
            try {
                mIsChargingEnabled = !mIsChargingEnabled;
                mLineageHealth.setChargingEnabled(mIsChargingEnabled);
            } catch (RemoteException | RuntimeException e) {
                Log.e(TAG, "Failed to set charging status");
            }
        }
    }

    private String msToString(long ms) {
        long millis = ms  % 1000;
        long second = (ms  / 1000) % 60;
        long minute = (ms  / (1000 * 60)) % 60;
        long hour = (ms / (1000 * 60 * 60)) % 24;

        return String.format("%02d:%02d:%02d.%d", hour, minute, second, millis);
    }

    private long getTimeMillisFromSecondOfDay(int time) {
        Calendar cal = Calendar.getInstance();

        cal.setTimeInMillis(System.currentTimeMillis());
        cal.set(Calendar.HOUR_OF_DAY, time / 3600);
        cal.set(Calendar.MINUTE, time / 60 % 60);
        cal.set(Calendar.SECOND, time % 60);

        long ret = cal.getTimeInMillis();
        return ret;
    }

    private void handleSettingChange() {
        mConfigEnabled = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.BATTERY_HEALTH_ENABLED, 0);
        mConfigLimit = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.BATTERY_HEALTH_CHARGING_LIMIT, 100);
        mConfigMode = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.BATTERY_HEALTH_MODE, 0);

        // Only set start/target time if it is not in auto mode
        if (mConfigMode != 1) {
            mConfigStartTime = LineageSettings.System.getInt(mContentResolver,
                    LineageSettings.System.BATTERY_HEALTH_START_TIME, 0);
            mConfigTargetTime = LineageSettings.System.getInt(mContentResolver,
                    LineageSettings.System.BATTERY_HEALTH_TARGET_TIME, 0);
        }

        // Update based on those values
        updateChargeState();
    }

    /* Battery Broadcast Receiver */
    private class LineateHealthBatteryBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleBatteryIntent(intent);
        }
    }

    /* Content Observer */
    private class LineageHealthSettingsObserver extends ContentObserver {
        public LineageHealthSettingsObserver(Context context, Handler handler) {
            super(handler);
        }

        public void observe(boolean enabled) {
            if (enabled) {
                mContentResolver.registerContentObserver(MODE_URI, false, this);
                mContentResolver.registerContentObserver(LIMIT_URI, false, this);
                mContentResolver.registerContentObserver(ENABLED_URI, false, this);
                mContentResolver.registerContentObserver(START_TIME_URI, false, this);
                mContentResolver.registerContentObserver(TARGET_TIME_URI, false, this);
            } else {
                mContentResolver.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            handleSettingChange();
        }
    }

    /* Service */
    private final IBinder mService = new IHealthInterface.Stub() {
        @Override
        public boolean isLineageHealthSupported() throws RemoteException {
            return mLineageHealth != null;
        }
    };
}
