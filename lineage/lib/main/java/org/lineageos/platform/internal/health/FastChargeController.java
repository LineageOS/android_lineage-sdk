/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.health;

import android.content.res.Resources;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import lineageos.providers.LineageSettings;

import org.lineageos.platform.internal.health.LineageHealthFeature;
import org.lineageos.platform.internal.R;

import vendor.lineage.health.FastChargeMode;
import vendor.lineage.health.IFastCharge;

import java.io.PrintWriter;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;

public class FastChargeController extends LineageHealthFeature {
    private final int[] mChargingSpeedValues;
    private final ContentResolver mContentResolver;
    private final IFastCharge mFastCharge;

    // Settings uris
    private final Uri MODE_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.FAST_CHARGE_MODE);

    public FastChargeController(Context context, Handler handler) {
        super(context, handler);

        mContentResolver = mContext.getContentResolver();
        mFastCharge = IFastCharge.Stub.asInterface(
                ServiceManager.waitForDeclaredService(
                        IFastCharge.DESCRIPTOR + "/default"));

        Resources res = mContext.getResources();
        mChargingSpeedValues = Stream.of(res.getStringArray(R.array.charging_speed_values))
                .mapToInt(Integer::parseInt)
                .toArray();

        if (mFastCharge == null) {
            Log.i(TAG, "Lineage Health HAL not found");
            return;
        }
    }

    @Override
    public boolean isSupported() {
        try {
            return mFastCharge != null && mFastCharge.getSupportedFastChargeModes() > 0;
        } catch (RemoteException e) {
            return false;
        }
    }

    public int[] getSupportedFastChargeModes() {
        try {
            long supportedFastChargeModes = mFastCharge.getSupportedFastChargeModes();

            return IntStream.of(mChargingSpeedValues)
                    .filter(mode -> (supportedFastChargeModes & mode) != 0)
                    .toArray();
        } catch (RemoteException e) {
            return new int[0];
        }
    }

    public int getFastChargeMode() {
        int[] supportedFastChargeModes = getSupportedFastChargeModes();
        int defaultMode = supportedFastChargeModes[supportedFastChargeModes.length - 1];

        int mode = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.FAST_CHARGE_MODE,
                defaultMode);
        if (mode != defaultMode && !ArrayUtils.contains(supportedFastChargeModes, mode)) {
            return defaultMode;
        }

        return mode;
    }

    public boolean setFastChargeMode(int mode) {
        putInt(LineageSettings.System.FAST_CHARGE_MODE, mode);
        return true;
    }

    @Override
    public void onStart() {
        if (mFastCharge == null) {
            return;
        }

        // Register setting observer
        registerSettings(MODE_URI);

        handleSettingChange();
    }

    private void handleSettingChange() {
        try {
            mFastCharge.setFastChargeMode(getFastChargeMode());
        } catch (RemoteException e) {
        }
    }

    @Override
    protected void onSettingsChanged(Uri uri) {
        handleSettingChange();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("FastChargeController Configuration:");
        pw.println("  Mode: " + getFastChargeMode());
        pw.println();
    }
}
