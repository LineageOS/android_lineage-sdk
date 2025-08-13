/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.health;

import android.Manifest;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import com.android.server.ServiceThread;

import lineageos.app.LineageContextConstants;
import lineageos.health.IHealthInterface;

import org.lineageos.platform.internal.LineageSystemService;

import vendor.lineage.health.ChargingControlSupportedMode;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class HealthInterfaceService extends LineageSystemService {

    private static final String TAG = "LineageHealth";
    private final Context mContext;
    private final Handler mHandler;
    private final ServiceThread mHandlerThread;

    private final List<LineageHealthFeature> mFeatures = new ArrayList<LineageHealthFeature>();

    // Health features
    private ChargingControlController mCCC;
    private FastChargeController mFCC;

    public HealthInterfaceService(Context context) {
        super(context);
        mContext = context;

        mHandlerThread = new ServiceThread(TAG, Process.THREAD_PRIORITY_DEFAULT, false);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.HEALTH;
    }

    @Override
    public boolean isCoreService() {
        return false;
    }

    @Override
    public void onStart() {
        if (!mContext.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.HEALTH)) {
            Log.wtf(TAG, "Lineage Health service started by system server but feature xml "
                    + "not declared. Not publishing binder service!");
            return;
        }
        mCCC = new ChargingControlController(mContext, mHandler);
        if (mCCC.isSupported()) {
            mFeatures.add(mCCC);
        }
        mFCC = new FastChargeController(mContext, mHandler);
        if (mFCC.isSupported()) {
            mFeatures.add(mFCC);
        }

        if (!mFeatures.isEmpty()) {
            publishBinderService(LineageContextConstants.LINEAGE_HEALTH_INTERFACE, mService);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase != PHASE_BOOT_COMPLETED) {
            return;
        }

        // start and update all features
        for (LineageHealthFeature feature : mFeatures) {
            feature.start();
        }
    }

    /* Service */
    private final IBinder mService = new IHealthInterface.Stub() {
        @Override
        public boolean isChargingControlSupported() {
            return mCCC.isSupported();
        }

        @Override
        public boolean getChargingControlEnabled() {
            return mCCC.isEnabled();
        }

        @Override
        public boolean setChargingControlEnabled(boolean enabled) {
            return mCCC.setEnabled(enabled);
        }

        @Override
        public int getChargingControlMode() {
            return mCCC.getMode();
        }

        @Override
        public boolean setChargingControlMode(int mode) {
            return mCCC.setMode(mode);
        }

        @Override
        public int getChargingControlStartTime() {
            return mCCC.getStartTime();
        }

        @Override
        public boolean setChargingControlStartTime(int startTime) {
            return mCCC.setStartTime(startTime);
        }

        @Override
        public int getChargingControlTargetTime() {
            return mCCC.getTargetTime();
        }

        @Override
        public boolean setChargingControlTargetTime(int targetTime) {
            return mCCC.setTargetTime(targetTime);
        }

        @Override
        public int getChargingControlLimit() {
            return mCCC.getLimit();
        }

        @Override
        public boolean setChargingControlLimit(int limit) {
            return mCCC.setLimit(limit);
        }

        @Override
        public boolean resetChargingControl() {
            return mCCC.reset();
        }

        @Override
        public boolean allowFineGrainedSettings() {
            // We allow fine-grained settings if bypass and toggle or limit modes are supported
            return mCCC.isChargingModeSupported(ChargingControlSupportedMode.TOGGLE)
                    || mCCC.isChargingModeSupported(ChargingControlSupportedMode.LIMIT);
        }

        @Override
        public boolean isFastChargeSupported() {
            return mFCC.isSupported();
        }

        @Override
        public int[] getSupportedFastChargeModes() {
            return mFCC.getSupportedFastChargeModes();
        }

        @Override
        public int getFastChargeMode() {
            return mFCC.getFastChargeMode();
        }

        @Override
        public boolean setFastChargeMode(int mode) {
            return mFCC.setFastChargeMode(mode);
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            mContext.enforceCallingOrSelfPermission(Manifest.permission.DUMP, TAG);

            pw.println();
            pw.println("LineageHealth Service State:");

            for (LineageHealthFeature feature : mFeatures) {
                feature.dump(pw);
            }
        }
    };
}
