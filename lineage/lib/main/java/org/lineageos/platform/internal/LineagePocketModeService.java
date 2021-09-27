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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import lineageos.app.LineageContextConstants;
import lineageos.providers.LineageSettings;

import vendor.lineage.pocketmode.V1_0.IFingerprintDisabler;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.NoSuchElementException;

/** @hide */
public class LineagePocketModeService extends LineageSystemService {

    private static final String TAG = LineagePocketModeService.class.getSimpleName();

    private final Context mContext;
    private final ContentResolver mContentResolver;

    private PocketSensor mPocketSensor;
    private SettingsObserver mSettingsObserver;

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
        mContentResolver = mContext.getContentResolver();
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.POCKETMODE;
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            mPocketSensor = new PocketSensor();
            mSettingsObserver = new SettingsObserver(new Handler());
        }
    }

    @Override
    public void onStart() {
    }

    private class PocketSensor implements SensorEventListener {
        private ExecutorService mExecutorService;
        private IFingerprintDisabler mFingerprintDisablerDaemon;
        private Sensor mSensor;
        private SensorManager mSensorManager;
        private float mSensorValue;

        public PocketSensor() {
            mExecutorService = Executors.newSingleThreadExecutor();
            mSensorManager = mContext.getSystemService(SensorManager.class);

            mFingerprintDisablerDaemon = getFingerprintDisablerDaemon();
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
        }

        private IFingerprintDisabler getFingerprintDisablerDaemon() {
            if (mFingerprintDisablerDaemon == null) {
                try {
                    mFingerprintDisablerDaemon = IFingerprintDisabler.getService();
                    if (mFingerprintDisablerDaemon != null) {
                        mSensor = findSensor(mFingerprintDisablerDaemon.getSensorType());
                        mSensorValue = mFingerprintDisablerDaemon.getSensorValue();

                        mFingerprintDisablerDaemon.asBinder().linkToDeath((cookie) -> {
                            mFingerprintDisablerDaemon = null;
                            disable();
                        }, 0);
                    }
                } catch (NoSuchElementException | RemoteException e) {
                    // do nothing
                }
            }
            return mFingerprintDisablerDaemon;
        }

        private Sensor findSensor(String sensorType) {
            for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_ALL)) {
                if (TextUtils.equals(sensor.getStringType(), sensorType)) {
                    return sensor;
                }
            }
            return null;
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

    private class SettingsObserver extends ContentObserver {
        private boolean mIsRegistered;
        private boolean mProximityWakeCheckEnabledByDefault;

        public SettingsObserver(Handler handler) {
            super(handler);

            mProximityWakeCheckEnabledByDefault = mContext.getResources()
                    .getBoolean(org.lineageos.platform.internal.R.bool
                            .config_proximityCheckOnWakeEnabledByDefault);

            mContentResolver.registerContentObserver(
                    LineageSettings.System.getUriFor(LineageSettings.System.PROXIMITY_ON_WAKE),
                    false, this);

            onChange(true);
        }

        @Override
        public void onChange(boolean selfChange) {
            boolean proximityWakeCheckEnabled = LineageSettings.System.getIntForUser(
                    mContentResolver, LineageSettings.System.PROXIMITY_ON_WAKE,
                    mProximityWakeCheckEnabledByDefault ? 1 : 0, UserHandle.USER_CURRENT) == 1;
            if (proximityWakeCheckEnabled) {
                IntentFilter screenStateFilter = new IntentFilter();
                screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
                screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
                mContext.registerReceiver(mScreenStateReceiver, screenStateFilter);
                mIsRegistered = true;
            } else if (mIsRegistered) {
                mContext.unregisterReceiver(mScreenStateReceiver);
                mPocketSensor.disable();
                mIsRegistered = false;
            }
        }
    };
}
