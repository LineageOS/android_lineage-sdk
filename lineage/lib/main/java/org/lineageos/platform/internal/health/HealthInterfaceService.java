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

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.ServiceThread;

import org.lineageos.platform.internal.LineageSystemService;

import lineageos.app.LineageContextConstants;
import lineageos.health.IHealthInterface;

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
        public boolean isChargingControlSupported() throws RemoteException {
            return mCCC.isSupported();
        }
    };
}
