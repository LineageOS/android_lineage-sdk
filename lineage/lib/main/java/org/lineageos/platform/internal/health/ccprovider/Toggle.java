package org.lineageos.platform.internal.health.ccprovider;

import static org.lineageos.platform.internal.health.Util.msToString;
import static org.lineageos.platform.internal.health.Util.msToUTCString;

import static lineageos.health.HealthInterface.MODE_AUTO;
import static lineageos.health.HealthInterface.MODE_LIMIT;
import static lineageos.health.HealthInterface.MODE_MANUAL;

import android.content.Context;
import android.os.BatteryStatsManager;
import android.os.BatteryUsageStats;
import android.os.RemoteException;
import android.util.Log;

import org.lineageos.platform.internal.R;

import java.io.PrintWriter;
import java.util.Objects;

import vendor.lineage.health.ChargingControlSupportedMode;
import vendor.lineage.health.IChargingControl;

public class Toggle extends ChargingControlProvider {

    protected final int mChargingLimitMargin;
    private final int mChargingTimeMargin;

    private boolean mIsLimitSet;
    private long mSavedAlarmTime;
    private long mEstimatedFullTime;
    private boolean mContinueCharging;
    private boolean mIsWaiting;

    // Only when the battery level is above this limit will the charging control be activated.
    private final static int CHARGE_CTRL_MIN_LEVEL = 80;

    public Toggle(IChargingControl chargingControl,
            Context context) {
        super(context, chargingControl);

        boolean isBypassSupported = isHALModeSupported(
                ChargingControlSupportedMode.BYPASS | ChargingControlSupportedMode.TOGGLE);
        if (!isBypassSupported) {
            mChargingLimitMargin = mContext.getResources().getInteger(
                    R.integer.config_chargingControlBatteryRechargeMargin);
        } else {
            mChargingLimitMargin = 1;
        }
        Log.i(TAG, "isBypassSupported: " + isBypassSupported);

        mChargingTimeMargin = mContext.getResources().getInteger(
                R.integer.config_chargingControlTimeMargin) * 60 * 1000;
    }

    @Override
    public boolean isSupported() {
        return isHALModeSupported(ChargingControlSupportedMode.TOGGLE);
    }

    @Override
    public boolean requiresBatteryLevelMonitoring() {
        return !isHALModeSupported(ChargingControlSupportedMode.BYPASS);
    }

    @Override
    protected boolean onBatteryChanged(float currentPct, int targetPct) {
        mIsLimitSet = shouldStopCharging(currentPct, targetPct);
        Log.i(TAG, "Current battery level: " + currentPct + ", target: " + targetPct +
                ", limit set: " + mIsLimitSet);
        return setChargingEnabled(!mIsLimitSet);
    }

    @Override
    protected boolean onBatteryChanged(float batteryPct, long startTime, long targetTime,
            int configMode) {
        if (configMode != MODE_AUTO && configMode != MODE_MANUAL) {
            Log.e(TAG,
                    "Possible bug: onBatteryChanged called with unsupported mode: " + configMode);
            return false;
        }

        if (mContinueCharging) {
            return setChargingEnabled(true);
        }

        // Now it is time to see whether charging should be stopped. We make decisions in the
        // following manner:
        //
        //  1. If mContinueCharging is not set, compare the remaining time with the saved estimated
        //     full time. Resume charging the remain time <= saved estimated time
        //  2. If the system estimated remaining time already exceeds the target full time, continue
        //  3. Otherwise, stop charging, save the estimated time, set mIsWaiting to true

        final long currentTime = System.currentTimeMillis();

        if (configMode == MODE_AUTO) {
            if (mSavedAlarmTime != targetTime) {
                if (mSavedAlarmTime != 0 && mSavedAlarmTime < currentTime) {
                    Log.i(TAG, "Not fully charged when alarm goes off, continue charging.");
                    mContinueCharging = true;
                    return setChargingEnabled(true);
                }

                Log.i(TAG, "User changed alarm, reassign alarm time");
                mSavedAlarmTime = targetTime;
            }
        } else {
            if (startTime > currentTime) {
                // Not yet entering user configured time frame
                setChargingEnabled(true);
                return false;
            }
        }

        if (batteryPct == 100) {
            return true;
        }

        // Now we have the target time and current time, we can post a notification stating that
        // the system will be charged by targetTime.
        // mChargingNotification.post(targetTime, false);

        // If current battery level is less than the fast charge limit, don't set this flag
        if (batteryPct < CHARGE_CTRL_MIN_LEVEL) {
            return setChargingEnabled(true);
        }

        long deltaTime = targetTime - currentTime;
        Log.i(TAG, "Current time to target: " + msToUTCString(deltaTime));

        if (mIsWaiting) {
            Log.i(TAG, "Current saved estimation to full: " + msToUTCString(mEstimatedFullTime));
            if (deltaTime <= mEstimatedFullTime) {
                Log.i(TAG, "Unset waiting flag");
                return setChargingEnabled(true);
            }

            // Still waiting
            return setChargingEnabled(false);
        }

        final BatteryUsageStats batteryUsageStats = Objects.requireNonNull(
                mContext.getSystemService(
                        BatteryStatsManager.class)).getBatteryUsageStats();
        long remaining = batteryUsageStats.getChargeTimeRemainingMs();
        if (remaining == -1) {
            Log.i(TAG, "Not enough data for prediction for now, waiting for more data");
            return false;
        }

        // Add margin here
        remaining += mChargingTimeMargin;
        Log.i(TAG, "Current estimated time to full: " + msToUTCString(remaining));
        if (deltaTime > remaining) {
            Log.i(TAG, "Stop charging and wait, saving remaining time");
            mEstimatedFullTime = remaining;
            mIsWaiting = true;
            return setChargingEnabled(false);
        }

        return setChargingEnabled(true);
    }

    private boolean setChargingEnabled(boolean enabled) {
        try {
            mChargingControl.setChargingEnabled(enabled);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set charging enabled", e);
            return false;
        }
    }

    @Override
    protected void onEnabled() {
        onReset();
    }

    @Override
    protected void onDisable() {
        onReset();
    }

    @Override
    protected void onReset() {
        try {
            mChargingControl.setChargingEnabled(true);
            mIsLimitSet = false;
            mSavedAlarmTime = 0;
            mEstimatedFullTime = 0;
            mIsWaiting = false;
            mContinueCharging = false;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set charging enabled", e);
        }
    }

    @Override
    public boolean isChargingControlModeSupported(int mode) {
        return mode == MODE_AUTO || mode == MODE_MANUAL || mode == MODE_LIMIT;
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("Provider: " + getClass().getName());
        pw.println("  mIsLimitSet: " + mIsLimitSet);
        pw.println("  mChargeLimitMargin: " + mChargingLimitMargin);
    }

    private boolean shouldStopCharging(float currentPct, int targetPct) {
        if (mIsLimitSet) {
            return currentPct >= targetPct - mChargingLimitMargin;
        }
        return currentPct >= targetPct;
    }

}
