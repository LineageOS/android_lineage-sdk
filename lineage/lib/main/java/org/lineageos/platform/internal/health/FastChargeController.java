/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.health;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.google.common.primitives.Ints;

import lineageos.providers.LineageSettings;

import vendor.lineage.health.FastChargeMode;
import vendor.lineage.health.IFastCharge;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.lineageos.platform.internal.health.LineageHealthFeature;

public class FastChargeController extends LineageHealthFeature {
    private final IFastCharge mFastCharge;
    private final ContentResolver mContentResolver;

    // Settings uris
    private final Uri MODE_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.FAST_CHARGE_MODE);

    public FastChargeController(Context context, Handler handler) {
        super(context, handler);

        mContentResolver = mContext.getContentResolver();
        mFastCharge = IFastCharge.Stub.asInterface(
                ServiceManager.waitForDeclaredService(
                        IFastCharge.DESCRIPTOR + "/default"));

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
        long supportedFastChargeModes = 0;

        try {
            supportedFastChargeModes = mFastCharge.getSupportedFastChargeModes();
        } catch (RemoteException e) {
            return new int[0];
        }

        List<Integer> ret = new ArrayList<Integer>();

        if ((supportedFastChargeModes & FastChargeMode.NONE) != 0) {
            ret.add(Integer.numberOfTrailingZeros(FastChargeMode.NONE));
        }

        if ((supportedFastChargeModes & FastChargeMode.FAST_CHARGE) != 0) {
            ret.add(Integer.numberOfTrailingZeros(FastChargeMode.FAST_CHARGE));
        }

        if ((supportedFastChargeModes & FastChargeMode.SUPER_FAST_CHARGE) != 0) {
            ret.add(Integer.numberOfTrailingZeros(FastChargeMode.SUPER_FAST_CHARGE));
        }

        return Ints.toArray(ret);
    }

    public int getFastChargeMode() {
        try {
            return Integer.numberOfTrailingZeros(mFastCharge.getFastChargeMode());
        } catch (RemoteException e) {
            return 0;
        }
    }

    public boolean setFastChargeMode(int mode) {
        try {
            int fastChargeMode = 1 << mode;
            return mFastCharge.setFastChargeMode(fastChargeMode) == fastChargeMode;
        } catch (RemoteException e) {
            return false;
        }
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

    public int getDefaultMode() {
        int[] supportedFastChargeModes = getSupportedFastChargeModes();
        return supportedFastChargeModes.length > 0
                ? supportedFastChargeModes[supportedFastChargeModes.length - 1]
                : 0;
    }

    public int getMode() {
        return LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.FAST_CHARGE_MODE,
                getDefaultMode());
    }

    private void handleSettingChange() {
        setFastChargeMode(getMode());
    }

    @Override
    protected void onSettingsChanged(Uri uri) {
        handleSettingChange();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("FastChargeController Configuration:");
        pw.println("  Mode: " + getMode());
        pw.println();
    }
}
