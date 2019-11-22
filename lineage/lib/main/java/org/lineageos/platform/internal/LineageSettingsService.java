/*
 * Copyright (C) 2018 The LineageOS Project
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

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemProperties;
import android.os.SystemService;
import android.os.UserHandle;
import android.provider.Settings;

import lineageos.app.LineageContextConstants;
import lineageos.providers.LineageSettings;
import lineageos.system.IAdbRootService;

/** @hide */
public class LineageSettingsService extends LineageSystemService {

    private static final String TAG = LineageSettingsService.class.getSimpleName();

    private final Context mContext;

    public LineageSettingsService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.SETTINGS;
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            // Load custom hostname
            String hostname = LineageSettings.Secure.getString(mContext.getContentResolver(),
                    LineageSettings.Secure.DEVICE_HOSTNAME);
            SystemProperties.set("net.hostname", hostname);
        }
    }

    @Override
    public void onStart() {
        publishBinderService(LineageContextConstants.LINEAGE_ADBROOT_SERVICE, mAdbRootService);
    }

    /* Utils */

    private boolean isCallerSystem() {
        final int callingUid = Binder.getCallingUid();
        return UserHandle.isSameApp(callingUid, Process.SYSTEM_UID);
    }

    private boolean isCallerShell() {
        final int callingUid = Binder.getCallingUid();
        return callingUid == Process.SHELL_UID || callingUid == Process.ROOT_UID;
    }

    private void enforceSystem() {
        if (!isCallerSystem()) {
            throw new SecurityException("Caller must be system");
        }
    }

    private void enforceSystemOrShell() {
        if (!(isCallerSystem() || isCallerShell())) {
            throw new SecurityException("Caller must be system or shell");
        }
    }

    /* Service */

    private final IBinder mAdbRootService = new IAdbRootService.Stub() {
        private final Object mLock = new Object();

        private void setEnabledLocked(boolean enabled) {
            final boolean curEnabled = getEnabledLocked();
            if (curEnabled != enabled) {
                LineageSettings.Secure.putInt(mContext.getContentResolver(),
                        LineageSettings.Secure.ADB_ROOT_ENABLED, enabled ? 1 : 0);

                // Turning off adb root, restart adbd.
                if (curEnabled) {
                    SystemProperties.set("lineage.service.adb.root", "0");
                    SystemService.restart("adbd");
                }
            }
        }

        private boolean getEnabledLocked() {
            return LineageSettings.Secure.getInt(mContext.getContentResolver(),
                    LineageSettings.Secure.ADB_ROOT_ENABLED, 0) != 0;
        }

        @Override
        public void setEnabled(boolean enabled) {
            enforceSystem();
            synchronized (mLock) {
                setEnabledLocked(enabled);
            }
        }

        @Override
        public boolean getEnabled() {
            enforceSystemOrShell();
            synchronized (mLock) {
                final long token = clearCallingIdentity();
                final boolean result = getEnabledLocked();
                restoreCallingIdentity(token);
                return result;
            }
        }
    };
}
