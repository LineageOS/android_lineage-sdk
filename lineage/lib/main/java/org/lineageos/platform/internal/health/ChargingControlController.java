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
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.format.DateUtils;
import android.util.Log;

import java.io.PrintWriter;

import lineageos.providers.LineageSettings;

import org.lineageos.platform.internal.R;
import org.lineageos.platform.internal.health.provider.ChargingControlProvider;
import org.lineageos.platform.internal.health.provider.ChargingControlProvider.ChargeControlInfo;
import org.lineageos.platform.internal.health.provider.LimitProviderToggle;
import org.lineageos.platform.internal.health.provider.LimitProviderToggleNB;
import org.lineageos.platform.internal.health.provider.TimeProviderDeadline;
import org.lineageos.platform.internal.health.provider.TimeProviderToggle;
import org.lineageos.platform.internal.health.provider.TimeProviderToggleNB;

import vendor.lineage.health.IChargingControl;

import static lineageos.health.HealthInterface.MODE_NONE;
import static lineageos.health.HealthInterface.MODE_AUTO;
import static lineageos.health.HealthInterface.MODE_MANUAL;
import static lineageos.health.HealthInterface.MODE_LIMIT;
import static org.lineageos.platform.internal.health.Utils.msToString;
import static org.lineageos.platform.internal.health.Utils.getTimeMillisFromSecondOfDay;

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
    private float mBatteryPct;
    private boolean mIsPowerConnected;
    private boolean mIsControlCancelledOnce;
    private boolean mShouldAlwaysMonitorBattery;

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

        mDefaultEnabled = mContext.getResources().getBoolean(R.bool.config_chargingControlEnabled);
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

        mChargingNotification = new ChargingControlNotification(context, mShouldAlwaysMonitorBattery, this);
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
            return isSupported() && (mChargingControl.getSupportedMode() & mode) != 0;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    protected void setControlCancelledOnce() {
        mIsControlCancelledOnce = true;
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
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, filter);
        mIsPowerConnected = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0;
        if (mIsPowerConnected) {
            onPowerConnected();
        }

        // Restore settings
        handleSettingChange();
    }

    protected void resetInternalState() {
        mIsControlCancelledOnce = false;
        mChargingNotification.cancel();
        if (mCurrentProvider != null) {
            mCurrentProvider.onReset();
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
        long targetTime, startTime = currentTime;
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

    protected void updateChargeControl() {
        if (!mConfigEnabled && mCurrentProvider != null) {
            mCurrentProvider.disable();
            mChargingNotification.cancel();
            return;
        }

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

        assert mCurrentProvider != null;

        if (mIsControlCancelledOnce || !mIsPowerConnected) {
            mCurrentProvider.disable();
            mChargingNotification.cancel();
            return;
        }

        mCurrentProvider.enable();

        ChargeControlInfo chargeControlInfo;
        if (mConfigMode == MODE_LIMIT) {
            chargeControlInfo = mCurrentProvider.onBatteryChanged(mBatteryPct, mConfigLimit);
            mChargingNotification.post(null, chargeControlInfo);
        } else {
            ChargeTime t = getChargeTime();
            if (t == null) {
                mCurrentProvider.disable();
                mChargingNotification.cancel();
                return;
            }
            chargeControlInfo = mCurrentProvider.onBatteryChanged(mBatteryPct, t.getStartTime(),
                    t.getTargetTime(), mConfigMode);
            mChargingNotification.post(t.getTargetTime(), chargeControlInfo);
        }
    }

    private void handleSettingChange() {
        mConfigEnabled = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_ENABLED, 0) != 0;
        mConfigLimit = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_LIMIT, mDefaultLimit);
        mConfigMode = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_MODE, mDefaultMode);
        mConfigStartTime = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_START_TIME, mDefaultStartTime);
        mConfigTargetTime = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.CHARGING_CONTROL_TARGET_TIME, mDefaultTargetTime);

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
