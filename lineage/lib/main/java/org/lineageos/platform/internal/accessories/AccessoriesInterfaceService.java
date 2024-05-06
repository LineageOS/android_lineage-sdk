/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories;

import android.Manifest;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import com.android.server.ServiceThread;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import lineageos.accessories.IAccessoriesCallback;
import lineageos.accessories.IAccessoriesInterface;
import lineageos.accessories.IAccessory;
import lineageos.app.LineageContextConstants;

import org.lineageos.platform.internal.LineageSystemService;

public class AccessoriesInterfaceService extends LineageSystemService {
    private static final String TAG = "AccessoriesInterfaceService";

    private final Context mContext;
    private final Handler mHandler;

    // Features
    private final AccessoriesController mAccessoriesController;
    private final List<LineageAccessoriesFeature> mFeatures = new ArrayList<>();

    private final IBinder mService = new IAccessoriesInterface.Stub() {
        @Override
        public IAccessory[] getAccessories() {
            return mAccessoriesController.getAccessories();
        }

        @Override
        public void registerCallback(IAccessoriesCallback callback) {
            mAccessoriesController.registerCallback(callback);
        }

        @Override
        public void unregisterCallback(IAccessoriesCallback callback) {
            mAccessoriesController.unregisterCallback(callback);
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            mContext.enforceCallingOrSelfPermission(Manifest.permission.DUMP, TAG);

            pw.println();
            pw.println("LineageAccessories service status:");

            for (LineageAccessoriesFeature feature : mFeatures) {
                feature.dump(pw);
            }
        }
    };

    public AccessoriesInterfaceService(Context context) {
        super(context);
        mContext = context;

        ServiceThread handlerThread = new ServiceThread(TAG, Process.THREAD_PRIORITY_DEFAULT,
                false);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        mAccessoriesController = new AccessoriesController(mContext, mHandler);
        if (mAccessoriesController.isSupported()) {
            mFeatures.add(mAccessoriesController);
        }
    }

    @Override
    public boolean isCoreService() {
        return false;
    }

    @Override
    public void onStart() {
        if (!mContext.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.ACCESSORIES)) {
            Log.wtf(TAG, "Lineage Accessories service started by system server but feature xml "
                    + "not declared. Not publishing binder service!");
            return;
        }

        if (!mFeatures.isEmpty()) {
            publishBinderService(LineageContextConstants.LINEAGE_ACCESSORIES_INTERFACE, mService);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase != PHASE_BOOT_COMPLETED) {
            return;
        }

        for (LineageAccessoriesFeature feature : mFeatures) {
            feature.start();
        }
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.ACCESSORIES;
    }
}
