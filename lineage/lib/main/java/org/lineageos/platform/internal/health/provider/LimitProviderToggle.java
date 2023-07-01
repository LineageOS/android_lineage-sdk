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

import java.io.PrintWriter;

import vendor.lineage.health.IChargingControl;
import vendor.lineage.health.ChargingControlSupportedMode;

public class LimitProviderToggle extends ChargingControlToggleProvider {

    private boolean isLimitSet;
    protected int mChargingLimitMargin;

    public LimitProviderToggle(IChargingControl chargingControl,
            Context context) {
        super(chargingControl, context);
        mChargingLimitMargin = 1;
    }

    @Override
    public boolean isSupported() {
        return isModeSupported(
                ChargingControlSupportedMode.TOGGLE & ChargingControlSupportedMode.BYPASS);
    }

    @Override
    public boolean shouldAlwaysMonitorBattery() {
        return false;
    }

    private boolean shouldStopCharging(float currentPct, int targetPct) {
        if (isLimitSet) {
            return currentPct >= targetPct - mChargingLimitMargin;
        }

        return currentPct >= targetPct;
    }

    @Override
    public ChargeControlInfo onBatteryChanged(float currentPct, int targetPct) {
        boolean shouldStopCharging = shouldStopCharging(currentPct, targetPct);
        if (setChargingEnabled(!shouldStopCharging)) {
            isLimitSet = shouldStopCharging;
        }

        return new ChargeControlInfo(true, isLimitSet);
    }

    @Override
    public void disable() {
        reset();
        setChargingEnabled(false);
    }

    @Override
    public void reset() {
        isLimitSet = false;
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("Provider: " + getClass().getName());
        pw.println("  isLimitSet: " + isLimitSet);
        pw.println("  mChargeLimitMargin: " + mChargingLimitMargin);
    }
}
