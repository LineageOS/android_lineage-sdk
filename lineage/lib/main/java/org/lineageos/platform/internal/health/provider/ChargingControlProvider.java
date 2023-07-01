/*
 * Copyright (C) 2023 The Android Open Source Project
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
import java.text.SimpleDateFormat;
import java.util.Calendar;

import vendor.lineage.health.IChargingControl;

public abstract class ChargingControlProvider {
    protected final IChargingControl mChargingControl;
    protected final Context mContext;
    private static final SimpleDateFormat mDateFormatter = new SimpleDateFormat("hh:mm:ss a");

    protected static final String TAG = "LineageHealth";

    ChargingControlProvider(IChargingControl chargingControl, Context context) {
        mContext = context;
        mChargingControl = chargingControl;
    }

    public boolean isModeSupported(int mode) {
        try {
            return (mChargingControl.getSupportedMode() & mode) == mode;
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to get supported mode from HAL!", e);
            return false;
        }
    }

    /**
     * Given current device setup, whether the charging control provider is supported.
     *
     * @return Whether this charging control provider is supported.
     */
    public abstract boolean isSupported();

    /**
     * Whether this charging control provider requires always monitoring battery.
     *
     * @return Whether it should always monitor battery.
     */
    public abstract boolean shouldAlwaysMonitorBattery();

    /**
     * Disable any effect of this provider.
     */
    public abstract void disable();

    /**
     * Reset internal states
     */
    public abstract void reset();

    /**
     * Dump internal states
     */
    public abstract void dump(PrintWriter pw);

    public boolean onBatteryChanged(float currentPct, int targetPct) {
        throw new RuntimeException("Unsupported operation");
    }

    public boolean onBatteryChanged(float batteryPct, long startTime, long targetTime,
            int configMode) {
        throw new RuntimeException("Unsupported operation");
    }
}
