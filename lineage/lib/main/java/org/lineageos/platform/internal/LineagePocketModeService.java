/*
 * Copyright (C) 2021 The LineageOS Project
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

package org.lineageos.platform.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import lineageos.app.LineageContextConstants;

import vendor.lineage.pocketmode.V1_0.IFingerprintDisabler;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.NoSuchElementException;

/** @hide */
public class LineagePocketModeService extends LineageSystemService {

    private static final String TAG = LineagePocketModeService.class.getSimpleName();

    private final Context mContext;
    private final PocketSensor mPocketSensor;

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                mPocketSensor.disable();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                mPocketSensor.enable();
            }
        }
    };

    public LineagePocketModeService(Context context) {
        super(context);
        mContext = context;
        mPocketSensor = new PocketSensor(mContext);

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mScreenStateReceiver, screenStateFilter);
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.POCKETMODE;
    }

    @Override
    public void onBootPhase(int phase) {
    }

    @Override
    public void onStart() {
    }

    private class PocketSensor implements SensorEventListener {
        private Context mContext;
        private ExecutorService mExecutorService;
        private IFingerprintDisabler mFingerprintDisablerDaemon;
        private Sensor mSensor;
        private SensorManager mSensorManager;
        private float mSensorValue;

        public PocketSensor(Context context) {
            mContext = context;
            mExecutorService = Executors.newSingleThreadExecutor();
            mFingerprintDisablerDaemon = getFingerprintDisablerDaemon();
            mSensorManager = mContext.getSystemService(SensorManager.class);

            for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_ALL)) {
                if (TextUtils.equals(sensor.getStringType(), mSensorType)) {
                    mSensor = sensor;
                    break;
                }
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            try {
                getFingerprintDisablerDaemon().setEnabled(event.values[0] == mSensorValue);
            } catch (RemoteException e) {
                // do nothing
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            /* Empty */
        }

        private IFingerprintDisabler getFingerprintDisablerDaemon() {
            if (mFingerprintDisablerDaemon == null) {
                try {
                    mFingerprintDisablerDaemon = IFingerprintDisabler.getService();
                    if (mFingerprintDisablerDaemon != null) {
                        mSensorValue = mFingerprintDisablerDaemon.getSensorValue();

                        String sensorType = mFingerprintDisablerDaemon.getSensorType();
                        for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_ALL)) {
                            if (TextUtils.equals(sensor.getStringType(), sensorType)) {
                                mSensor = sensor;
                                break;
                            }
                        }

                        mFingerprintDisablerDaemon.asBinder().linkToDeath((cookie) -> {
                            mFingerprintDisablerDaemon = null;
                        }, 0);
                    }
                } catch (NoSuchElementException | RemoteException e) {
                    // do nothing
                }
            }
            return mFingerprintDisablerDaemon;
        }

        void enable() {
            if (mSensor != null) {
                Log.d(TAG, "Enabling pocket sensor");
                mExecutorService.submit(() -> {
                    mSensorManager.registerListener(this, mSensor,
                            SensorManager.SENSOR_DELAY_NORMAL);
                });
            }
        }

        void disable() {
            if (mSensor != null) {
                Log.d(TAG, "Disabling pocket sensor");
                mExecutorService.submit(() -> {
                    mSensorManager.unregisterListener(this, mSensor);
                    try {
                        // Ensure FP is left enabled
                        getFingerprintDisablerDaemon().setEnabled(false);
                    } catch (RemoteException e) {
                        // do nothing
                    }
                });
            }
        }
    }
}
