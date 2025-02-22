/*
 * SPDX-FileCopyrightText: 2024-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.health.ccprovider;

import static android.os.BatteryManager.CHARGING_POLICY_DEFAULT;

import static lineageos.health.HealthInterface.MODE_AUTO;
import static lineageos.health.HealthInterface.MODE_LIMIT;
import static lineageos.health.HealthInterface.MODE_MANUAL;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import vendor.lineage.health.ChargingControlSupportedMode;
import vendor.lineage.health.ChargingLimitInfo;
import vendor.lineage.health.IChargingControl;

import java.io.PrintWriter;

public class Limit extends ChargingControlProvider {

    public Limit(IChargingControl chargingControl, Context context) {
        super(context, chargingControl);
    }

    @Override
    protected boolean onBatteryChanged(float currentPct, int targetPct) {
        setChargingLimit(targetPct);
        return false;
    }

    @Override
    protected boolean onBatteryChanged(float batteryPct, long startTime, long targetTime,
            int configMode) {
        return false;
    }

    @Override
    protected void onEnabled() {
    }

    @Override
    protected void onDisable() {
        setChargingLimit(100);
    }

    @Override
    protected void onReset() {
    }

    @Override
    protected int onGetStatus() {
        return CHARGING_POLICY_DEFAULT;
    }

    private void setChargingLimit(int targetPct) {
        try {
            if (mChargingControl.getChargingLimit().max != targetPct) {
                ChargingLimitInfo limit = new ChargingLimitInfo();
                limit.min = 0;
                limit.max = targetPct;
                mChargingControl.setChargingLimit(limit);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set charging limit", e);
        }
    }

    @Override
    public boolean isSupported() {
        return isHALModeSupported(ChargingControlSupportedMode.LIMIT);
    }

    @Override
    public boolean requiresBatteryLevelMonitoring() {
        return !isHALModeSupported(ChargingControlSupportedMode.BYPASS);
    }

    @Override
    public boolean isChargingControlModeSupported(int mode) {
        return mode == MODE_AUTO || mode == MODE_MANUAL || mode == MODE_LIMIT;
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("Provider: " + getClass().getName());
    }
}
