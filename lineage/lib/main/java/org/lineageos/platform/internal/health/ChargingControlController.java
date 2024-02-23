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

import static org.lineageos.platform.internal.health.Util.getTimeMillisFromSecondOfDay;
import static org.lineageos.platform.internal.health.Util.msToString;

import static lineageos.health.HealthInterface.MODE_AUTO;
import static lineageos.health.HealthInterface.MODE_LIMIT;
import static lineageos.health.HealthInterface.MODE_MANUAL;
import static lineageos.health.HealthInterface.MODE_NONE;

import android.app.AlarmManager;
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
import org.lineageos.platform.internal.health.ccprovider.ChargingControlProvider;
import org.lineageos.platform.internal.health.ccprovider.Deadline;
import org.lineageos.platform.internal.health.ccprovider.Toggle;

import java.io.PrintWriter;

import lineageos.providers.LineageSettings;
import vendor.lineage.health.IChargingControl;

public class ChargingControlController extends LineageHealthFeature {
    private final IChargingControl mChargingControl;
    private final ContentResolver mContentResolver;
    private ChargingControlNotification mChargingNotification;
    private LineageHealthBatteryBroadcastReceiver mBattReceiver;

    // Defaults
    private boolean mDefaultEnabled = false;
    private int mDefaultMode;
    private int mDefaultLimit;
    private int mDefaultStartTime;
    private int mDefaultTargetTime;

    // User configs
    private boolean mConfigEnabled;
    private int mConfigStartTime;
    private int mConfigTargetTime;
    private int mConfigMode = MODE_NONE;
    private int mConfigLimit = 100;

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
    private float mBatteryPct;
    private long mSavedAlarmTime;
    private boolean mIsPowerConnected;
    private boolean mIsControlCancelledOnce;

    // Current selected provider
    private ChargingControlProvider mCurrentProvider;

    public ChargingControlController(Context context, Handler handler) {
        super(context, handler);

        mContentResolver = mContext.getContentResolver();
        mChargingControl = IChargingControl.Stub.asInterface(
                ServiceManager.waitForDeclaredService(
                        IChargingControl.DESCRIPTOR + "/default"));

        if (mChargingControl == null) {
            Log.i(TAG, "Lineage Health HAL not found");
            return;
        }

        mChargingNotification = new ChargingControlNotification(context, this);

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

        // Set up charging control providers
        mCurrentProvider = new Toggle(mChargingControl, mContext);
        if (!mCurrentProvider.isSupported()) {
            mCurrentProvider = null;
        }

        if (mCurrentProvider == null) {
            mCurrentProvider = new Deadline(mChargingControl, mContext);
            if (!mCurrentProvider.isSupported()) {
                mCurrentProvider = null;
            }
        }

        if (mCurrentProvider == null) {
            Log.wtf(TAG, "No charging control provider is supported");
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

    @Override
    public void onStart() {
        if (mChargingControl == null) {
            return;
        }

        // Register setting observer
        registerSettings(MODE_URI, LIMIT_URI, ENABLED_URI, START_TIME_URI, TARGET_TIME_URI);

        // For devices that do not support bypass, we can only always listen to battery change
        // because we can't distinguish between "unplugged" and "plugged in but not charging".
        if (mCurrentProvider.requiresBatteryLevelMonitoring()) {
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

    public boolean isChargingModeSupported(int mode) {
        try {
            return isSupported() && (mChargingControl.getSupportedMode() & mode) != 0;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    protected void resetInternalState() {
        mSavedAlarmTime = 0;
        mIsControlCancelledOnce = false;
        mChargingNotification.cancel();
    }

    protected void setChargingCancelledOnce() {
        mIsControlCancelledOnce = true;

        if (mCurrentProvider.requiresBatteryLevelMonitoring()) {
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

        mCurrentProvider.disable();
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
            updateChargeControl();
        } else {
            onPowerDisconnected();
        }
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
            Log.e(TAG, "invalid charging control mode " + mConfigMode);
            return null;
        }

        Log.i(TAG, "Target time is " + msToString(targetTime));

        return new ChargeTime(startTime, targetTime);
    }

    protected void updateChargeControl() {
        if (!mConfigEnabled) {
            Log.i(TAG, "updateChargeControl: Disable provider if needed");
            mCurrentProvider.disable();
            return;
        }

        mCurrentProvider.enable();
        Log.i(TAG, "updateChargeControl: Enable provider if needed");

        if (mConfigMode == MODE_LIMIT) {
            Log.i(TAG, "updateChargeControl: Limit mode");
            if (mCurrentProvider.update(mBatteryPct, mConfigLimit)) {
                mChargingNotification.post(mConfigLimit, mBatteryPct == mConfigLimit);
            }
        } else {
            ChargeTime chargeTime = getChargeTime();
            if (chargeTime != null) {
                Log.i(TAG, "updateChargeControl: Auto/Manual mode, startTime: " + msToString(
                        chargeTime.getStartTime())
                        + ", targetTime: " + msToString(chargeTime.getTargetTime()));
                if (mCurrentProvider.update(mBatteryPct, chargeTime.getStartTime(),
                        chargeTime.getTargetTime(), mConfigMode)) {
                    mChargingNotification.post(chargeTime.getTargetTime(),
                            mBatteryPct == 100);
                }
            }
        }
    }

    private void handleSettingChange() {
        // Read all setting values
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
        pw.println("  mSavedAlarmTime: " + msToString(mSavedAlarmTime));
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

    /* A representation of start and target time */
    static final class ChargeTime {
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
