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

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import java.io.PrintWriter;

import vendor.lineage.health.ChargingControlSupportedMode;
import vendor.lineage.health.IChargingControl;

import static org.lineageos.platform.internal.health.ChargingControlController.msToString;

public class TimeProviderDeadline extends ChargingControlProvider {

    private long mSavedTargetTime;

    public TimeProviderDeadline(IChargingControl chargingControl, Context context) {
        super(chargingControl, context);
    }

    @Override
    public boolean isSupported() {
        return isModeSupported(ChargingControlSupportedMode.DEADLINE);
    }

    @Override
    public boolean shouldAlwaysMonitorBattery() {
        return false;
    }

    @Override
    public ChargeControlInfo onBatteryChanged(float batteryPct, long startTime, long targetTime,
            int configMode) {
        long deadline = 0;
        final long currentTime = System.currentTimeMillis();

        if (currentTime < startTime) {
            Log.i(TAG, "Not yet entering user set time-frame.");
            disable();
            return new ChargeControlInfo(false, false);
        }

        if (targetTime == mSavedTargetTime) {
            return new ChargeControlInfo(true, batteryPct == 100);
        }

        deadline = (targetTime - currentTime) / 1000;
        Log.i(TAG, "Setting charge deadline: Current time: " + msToString(currentTime));
        Log.i(TAG, "Setting charge deadline: Target time: " + msToString(targetTime));
        Log.i(TAG, "Setting charge deadline: Deadline (seconds): " + deadline);

        try {
            mChargingControl.setChargingDeadline(deadline);
            mSavedTargetTime = targetTime;
        } catch (IllegalStateException | RemoteException | UnsupportedOperationException e) {
            Log.e(TAG, "Failed to set charge deadline", e);
        }

        return new ChargeControlInfo(true, batteryPct == 100);
    }

    @Override
    public void disable() {
        try {
            mChargingControl.setChargingDeadline(-1);
            mSavedTargetTime = 0;
        } catch (IllegalStateException | RemoteException | UnsupportedOperationException e) {
            Log.e(TAG, "Failed to set charge deadline", e);
        }
    }

    @Override
    public void reset() {
        mSavedTargetTime = 0;
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("Provider: " + getClass().getName());
        pw.println("  mSavedTargetTime: " + msToString(mSavedTargetTime, "UTC"));
    }
}
