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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;

import java.util.List;
import java.util.ArrayList;

import lineageos.app.LineageContextConstants;
import lineageos.app.IGlobalActionsManager;

import org.lineageos.internal.util.PowerMenuConstants;

import static lineageos.providers.LineageSettings.Secure.POWER_MENU_ACTIONS;
import static lineageos.providers.LineageSettings.Secure.getStringForUser;
import static lineageos.providers.LineageSettings.Secure.putStringForUser;
import static org.lineageos.internal.util.PowerMenuConstants.*;

/**
 * @hide
 */
public class GlobalActionsManagerService extends LineageSystemService {

    private static final String TAG = "GlobalActionsManager";

    private static final boolean DEBUG = false;

    private final Context mContext;
    private final ContentResolver mCR;

    private final List<String> mLocalUserConfig = new ArrayList<String>();

    // Observes user-controlled settings
    private GlobalActionsSettingsObserver mObserver;

    // Take lock when accessing mProfiles
    private final Object mLock = new Object();

    // Manipulate state variables under lock
    private boolean mSystemReady         = false;

    public GlobalActionsManagerService(Context context) {
        super(context);

        mContext = context;
        mCR = mContext.getContentResolver();
    }

    private class GlobalActionsSettingsObserver extends ContentObserver {

        private final Uri BUGREPORT_URI =
                Settings.Global.getUriFor(Settings.Global.BUGREPORT_IN_POWER_MENU);
        private final Uri LOCKDOWN_URI =
                Settings.Secure.getUriFor(Settings.Secure.LOCKDOWN_IN_POWER_MENU);

        public GlobalActionsSettingsObserver(Context context, Handler handler) {
            super(handler);
        }

        public void observe(boolean enabled) {
            if (enabled) {
                mCR.registerContentObserver(BUGREPORT_URI, false, this);
                mCR.registerContentObserver(LOCKDOWN_URI, false, this);
                onChange(false);
            } else {
                mCR.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            synchronized (mLock) {
                updateUserConfigLocked(Settings.Global.getInt(mCR,
                                Settings.Global.BUGREPORT_IN_POWER_MENU, 0) == 1, GLOBAL_ACTION_KEY_BUGREPORT);

                updateUserConfigLocked(Settings.Secure.getInt(mCR,
                                Settings.Secure.LOCKDOWN_IN_POWER_MENU, 0) == 1, GLOBAL_ACTION_KEY_LOCKDOWN);
            }
        }
    };

    private boolean populateUserConfigLocked() {
        mLocalUserConfig.clear();

        String[] actions;
        String savedActions = getStringForUser(mCR,
                POWER_MENU_ACTIONS, UserHandle.USER_CURRENT);

        if (savedActions == null) {
            actions = mContext.getResources().getStringArray(
                    com.android.internal.R.array.config_globalActionsList);
        } else {
            actions = savedActions.split("\\|");
        }

        for (String action : actions) {
            mLocalUserConfig.add(action);
        }

        return true;
    }

    private boolean updateUserConfigLocked(boolean enabled, String action) {
        if (enabled) {
            if (!mLocalUserConfig.contains(action)) {
                mLocalUserConfig.add(action);
            }
        } else {
            if (mLocalUserConfig.contains(action)) {
                mLocalUserConfig.remove(action);
            }
        }
        return saveUserConfig();
    }

    private boolean saveUserConfig() {
        StringBuilder s = new StringBuilder();

        List<String> actions = new ArrayList<String>();
        for (String action : PowerMenuConstants.getAllActions()) {
            if (mLocalUserConfig.contains(action)) {
                actions.add(action);
            } else {
                continue;
            }
        }

        for (int i = 0; i < actions.size(); i++) {
            s.append(actions.get(i).toString());
            if (i != actions.size() - 1) {
                s.append("|");
            }
        }

        putStringForUser(mCR,
                POWER_MENU_ACTIONS, s.toString(), UserHandle.USER_CURRENT);

        return true;
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.GLOBALACTIONS;
    }

    @Override
    public void onStart() {
        publishBinderService(LineageContextConstants.LINEAGE_GLOBALACTIONS_SERVICE, mBinder);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            synchronized (mLock) {
                populateUserConfigLocked();

                mObserver = new GlobalActionsSettingsObserver(mContext, null);
                mObserver.observe(true);
            }
        }
    }

    private final IBinder mBinder = new IGlobalActionsManager.Stub() {

        @Override
        public boolean updateUserConfig(boolean enabled, String action) {
            synchronized (mLock) {
                return updateUserConfigLocked(enabled, action);
            }
        }

        @Override
        public List<String> getUserConfig() {
            synchronized (mLock) {
                return mLocalUserConfig;
            }
        }

        @Override
        public boolean userConfigContains(String preference) {
            synchronized (mLock) {
                return mLocalUserConfig.contains(preference);
            }
        }
    };
}
