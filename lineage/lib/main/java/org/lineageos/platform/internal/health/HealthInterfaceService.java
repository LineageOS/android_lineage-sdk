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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import org.lineageos.platform.internal.LineageSystemService;

import lineageos.app.LineageContextConstants;
import lineageos.health.IHealthInterface;
import lineageos.providers.LineageSettings;
import vendor.lineage.health.ILineageHealth;

public class HealthInterfaceService extends LineageSystemService {
    private static final String TAG = "LineageHealth";

    private ILineageHealth mLineageHealth;

    private final ContentResolver mContentResolver;
    private Context mContext;
    private LineageHealthSettingsObserver mObserver;

    // Settings uris
    private final Uri MODE_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_MODE);
    private final Uri LIMIT_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_CHARGING_LIMIT);
    private final Uri ENABLED_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_ENABLED);
    private final Uri START_TIME_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_START_TIME);
    private final Uri TARGET_TIME_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.BATTERY_HEALTH_TARGET_TIME);

    private static class State {
        private static final int STATE_CHARGING = 0;
        private static final int STATE_STOP_LIMIT = 1;
        private static final int STATE_STOP_TIME = 2;
    }

    // User configs
    private int configMode = 0;
    private int configLimit = 100;
    private int configEnabled = 0;
    private int configStartTime = 0;
    private int configTargetTime = 0;

    private float mPreviousBatteryPct = 0;

    public HealthInterfaceService(Context context) {
        super(context);
        mContext = context;
        mContentResolver = mContext.getContentResolver();
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
        }

        mLineageHealth = ILineageHealth.Stub.asInterface(
                ServiceManager.getService(ILineageHealth.DESCRIPTOR + "/default"));
        if (mLineageHealth != null) {
            publishBinderService(LineageContextConstants.LINEAGE_HEALTH_INTERFACE, mService);
        } else {
            Log.i(TAG, "Lineage Health HAL not found, not registering service");
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (mLineageHealth == null) {
            return;
        }
        if (phase == PHASE_BOOT_COMPLETED) {
            Log.i(TAG, "Restore settings");
            // Restore settings
            handleSettingChange();

            // Register observer
            Log.i(TAG, "Registering observer");
            mObserver = new LineageHealthSettingsObserver(mContext, null);
            mObserver.observe(true);

            // Register battery changed intent
            Log.i(TAG, "Registering intent");
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            mContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // TODO: do something
                    Log.e(TAG, "Battery changed!");
                }
            }, ifilter);

            Log.i(TAG, "All Done!");
        }
    }

    /* Content Observer */
    private class LineageHealthSettingsObserver extends ContentObserver {
        public LineageHealthSettingsObserver(Context context, Handler handler) {
            super(handler);
        }

        public void observe(boolean enabled) {
            if (enabled) {
                mContentResolver.registerContentObserver(MODE_URI, false, this);
                mContentResolver.registerContentObserver(LIMIT_URI, false, this);
                mContentResolver.registerContentObserver(ENABLED_URI, false, this);
                mContentResolver.registerContentObserver(START_TIME_URI, false, this);
                mContentResolver.registerContentObserver(TARGET_TIME_URI, false, this);
            } else {
                mContentResolver.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            handleSettingChange();
        }
    }

    private void handleBatteryIntent(Intent intent) {
        int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        if (chargePlug == 0 || configEnabled == 0 || configLimit == 100) {
            return;
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level * 100 / (float)scale;

        // Only update if we are charging
        if (batteryPct != mPreviousBatteryPct) {
            handleBatteryChange(batteryPct);
        }
    }

    private void handleBatteryChange(float batteryPct) {
        // TODO: Main logic go there

        // Stop charging if it reaches target level
        try {
            if (batteryPct < configLimit)
                mLineageHealth.setChargingEnabled(true);
            else
                mLineageHealth.setChargingEnabled(false);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set charging status: "  + e);
        }
    }

    private void handleSettingChange() {
        configEnabled = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.BATTERY_HEALTH_ENABLED, 0);
        configLimit = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.BATTERY_HEALTH_CHARGING_LIMIT, 100);
        configMode = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.BATTERY_HEALTH_MODE, 0);

        // Only set start/target time if it is not in auto mode
        if (configMode != 1) {
            configStartTime = LineageSettings.System.getInt(mContentResolver,
                    LineageSettings.System.BATTERY_HEALTH_START_TIME, 0);
            configTargetTime = LineageSettings.System.getInt(mContentResolver,
                    LineageSettings.System.BATTERY_HEALTH_TARGET_TIME, 0);
        }
    }

    /* Service */
    private final IBinder mService = new IHealthInterface.Stub() {
        @Override
        public boolean isLineageHealthSupported() throws RemoteException {
            return mLineageHealth != null;
        }
    };
}
