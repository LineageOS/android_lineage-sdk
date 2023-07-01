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

package org.lineageos.platform.internal.health.provider;

import vendor.lineage.health.ChargingControlSupportedMode;
import vendor.lineage.health.IChargingControl;

import android.content.Context;
import android.os.BatteryStatsManager;
import android.os.BatteryUsageStats;
import android.util.Log;

import org.lineageos.platform.internal.R;

import java.io.PrintWriter;

import static lineageos.health.HealthInterface.MODE_AUTO;
import static lineageos.health.HealthInterface.MODE_MANUAL;

import static org.lineageos.platform.internal.health.ChargingControlController.msToString;

public class TimeProviderToggle extends ChargingControlToggleProvider {

    // Only when the battery level is above this limit will the charging control be activated.
    private static final int CHARGE_CTRL_MIN_LEVEL = 80;
    private final int mChargingTimeMargin;

    // Internal states
    private long mSavedAlarmTime = 0;
    private boolean mAlwaysContinueCharging = false;
    private boolean mIsCurrentlyWaiting = false;
    private long mEstimatedFullTime;

    public TimeProviderToggle(IChargingControl chargingControl, Context context) {
        super(chargingControl, context);

        mChargingTimeMargin = mContext.getResources().getInteger(
                R.integer.config_chargingControlTimeMargin) * 60 * 1000;
    }

    @Override
    public boolean isSupported() {
        return isModeSupported(
                ChargingControlSupportedMode.TOGGLE | ChargingControlSupportedMode.BYPASS);
    }

    @Override
    public boolean shouldAlwaysMonitorBattery() {
        return false;
    }

    private boolean shouldStopCharging(float batteryPct, long startTime, long targetTime,
            int configMode) {
        final long currentTime = System.currentTimeMillis();

        Log.i(TAG, "Got target time " + msToString(targetTime) + ", start time " + msToString(
                startTime) + ", current time " + msToString(currentTime));

        if (mAlwaysContinueCharging) {
            return false;
        }

        if (mSavedAlarmTime != targetTime) {
            if (mSavedAlarmTime != 0 && mSavedAlarmTime < currentTime) {
                Log.i(TAG, "Not fully charged when alarm goes off, continue charging.");
                mAlwaysContinueCharging = true;
                return false;
            }

            Log.i(TAG, "User changed alarm, update saved alarm time");
            mSavedAlarmTime = targetTime;
        }

        if (configMode == MODE_AUTO) {
            // Don't activate if we are more than 9 hrs away from the target alarm
            if (targetTime - currentTime >= 9 * 60 * 60 * 1000) {
                return false;
            }
        } else if (configMode == MODE_MANUAL) {
            if (startTime > currentTime) {
                // Not yet entering user configured time frame
                return false;
            }
        }

        if (batteryPct == 100) {
            return true;
        }

        // If current battery level is less than the fast charge limit, don't set this flag
        if (batteryPct < CHARGE_CTRL_MIN_LEVEL) {
            Log.i(TAG, "Continue charging to a decent level " + CHARGE_CTRL_MIN_LEVEL);
            return false;
        }

        long deltaTime = targetTime - currentTime;
        Log.i(TAG, "Current time to target: " + msToString(deltaTime, "UTC"));

        if (mIsCurrentlyWaiting) {
            Log.i(TAG, "Current saved estimation to full: " + msToString(mEstimatedFullTime));
            if (deltaTime <= mEstimatedFullTime) {
                Log.i(TAG, "Unset waiting flag");
                mIsCurrentlyWaiting = false;
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
        Log.i(TAG, "Current estimated time to full: " + msToString(remaining, "UTC"));
        if (deltaTime > remaining) {
            Log.i(TAG, "Stop charging and wait, saving remaining time");
            mEstimatedFullTime = remaining;
            return true;
        }

        return false;
    }

    @Override
    public ChargeControlInfo onBatteryChanged(float batteryPct, long startTime, long targetTime,
            int configMode) {
        boolean shouldStopCharging = shouldStopCharging(batteryPct, startTime, targetTime,
                configMode);
        if (setChargingEnabled(!shouldStopCharging)) {
            mIsCurrentlyWaiting = shouldStopCharging;
        }

        return new ChargeControlInfo(true, batteryPct == 100);
    }

    @Override
    public void disable() {
        reset();
        setChargingEnabled(true);
    }

    @Override
    public void reset() {
        mAlwaysContinueCharging = false;
        mIsCurrentlyWaiting = false;
        mSavedAlarmTime = 0;
        mEstimatedFullTime = 0;
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("Provider: " + getClass().getName());
        pw.println("  mSavedAlarmTime: " + msToString(mSavedAlarmTime));
        pw.println("  mAlwaysContinueCharging: " + mAlwaysContinueCharging);
        pw.println("  mIsCurrentlyWaiting: " + mIsCurrentlyWaiting);
        pw.println("  mEstimatedFullTime: " + msToString(mEstimatedFullTime, "UTC"));
    }
}
