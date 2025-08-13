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

import org.lineageos.platform.internal.health.LineageHealthFeature;

import vendor.lineage.health.FastChargeMode;
import vendor.lineage.health.IFastCharge;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class FastChargeController extends LineageHealthFeature {
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

        for (int mode : new int[] {
            FastChargeMode.NONE,
            FastChargeMode.FAST_CHARGE,
            FastChargeMode.SUPER_FAST_CHARGE,
        }) {
            if ((supportedFastChargeModes & mode) != 0) {
                ret.add(mode);
            }
        }

        return Ints.toArray(ret);
    }

    public int getFastChargeMode() {
        try {
            return mFastCharge.getFastChargeMode();
        } catch (RemoteException e) {
            return 0;
        }
    }

    public boolean setFastChargeMode(int mode) {
        try {
            if (mFastCharge.setFastChargeMode(mode) == mode) {
                putInt(LineageSettings.System.FAST_CHARGE_MODE, mode);
                return true;
            }
            return false;
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

    private int getMode() {
        int[] supportedFastChargeModes = getSupportedFastChargeModes();
        int defaultMode = supportedFastChargeModes[supportedFastChargeModes.length - 1];

        int mode = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.FAST_CHARGE_MODE,
                defaultMode);
        if (mode != defaultMode && !Ints.contains(supportedFastChargeModes, mode)) {
            return defaultMode;
        }

        return mode;
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
