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

import static lineageos.providers.LineageSettings.Secure.POWER_MENU_ACTIONS;
import static lineageos.providers.LineageSettings.Secure.getStringForUser;
import static lineageos.providers.LineageSettings.Secure.putStringForUser;

import static org.lineageos.internal.util.PowerMenuConstants.GLOBAL_ACTION_KEY_BUGREPORT;
import static org.lineageos.internal.util.PowerMenuConstants.GLOBAL_ACTION_KEY_LOCKDOWN;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;

import lineageos.app.LineageContextConstants;
import lineageos.app.ILineageGlobalActions;

import org.lineageos.internal.util.PowerMenuConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @hide
 */
public class LineageGlobalActionsService extends LineageSystemService {

    private static final String TAG = "LineageGlobalActions";

    private final Context mContext;
    private final ContentResolver mContentResolver;

    private final List<String> mLocalUserConfig = new ArrayList<String>();

    // Observes user-controlled settings
    private GlobalActionsSettingsObserver mObserver;

    public LineageGlobalActionsService(Context context) {
        super(context);

        mContext = context;
        mContentResolver = mContext.getContentResolver();
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
                mContentResolver.registerContentObserver(BUGREPORT_URI, false, this);
                mContentResolver.registerContentObserver(LOCKDOWN_URI, false, this);
            } else {
                mContentResolver.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            updateUserConfigInternal(Settings.Global.getInt(mContentResolver,
                    Settings.Global.BUGREPORT_IN_POWER_MENU, 0) == 1,
                    GLOBAL_ACTION_KEY_BUGREPORT);

            updateUserConfigInternal(Settings.Secure.getIntForUser(mContentResolver,
                    Settings.Secure.LOCKDOWN_IN_POWER_MENU, 0, UserHandle.USER_CURRENT) == 1,
                    GLOBAL_ACTION_KEY_LOCKDOWN);
        }
    };

    private void populateUserConfig() {
        mLocalUserConfig.clear();
        mLocalUserConfig.addAll(Arrays.asList(getUserConfig()));
    }

    private String[] getUserConfig() {
        String savedActions = getStringForUser(mContentResolver,
                POWER_MENU_ACTIONS, UserHandle.USER_CURRENT);

        if (savedActions == null) {
            return mContext.getResources().getStringArray(
                    com.android.internal.R.array.config_globalActionsList);
        } else {
            return savedActions.split("\\|");
        }
    }

    private void updateUserConfigInternal(boolean enabled, String action) {
        if (enabled) {
            if (!mLocalUserConfig.contains(action)) {
                mLocalUserConfig.add(action);
            }
        } else {
            if (mLocalUserConfig.contains(action)) {
                mLocalUserConfig.remove(action);
            }
        }
        saveUserConfig();
    }

    private void saveUserConfig() {
        List<String> actions = new ArrayList<String>();
        for (String action : PowerMenuConstants.getAllActions()) {
            if (mLocalUserConfig.contains(action)) {
                actions.add(action);
            }
        }

        String s = String.join("|", actions);
        putStringForUser(mContentResolver, POWER_MENU_ACTIONS, s, UserHandle.USER_CURRENT);
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.GLOBAL_ACTIONS;
    }

    @Override
    public void onStart() {
        publishBinderService(LineageContextConstants.LINEAGE_GLOBAL_ACTIONS_SERVICE, mBinder);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            populateUserConfig();

            mObserver = new GlobalActionsSettingsObserver(mContext, null);
            mObserver.observe(true);
        }
    }

    private final IBinder mBinder = new ILineageGlobalActions.Stub() {

        @Override
        public void updateUserConfig(boolean enabled, String action) {
            updateUserConfigInternal(enabled, action);
        }

        @Override
        public List<String> getLocalUserConfig() {
            populateUserConfig();
            return mLocalUserConfig;
        }

        @Override
        public String[] getUserActionsArray() {
            return getUserConfig();
        }

        @Override
        public boolean userConfigContains(String preference) {
            return getLocalUserConfig().contains(preference);
        }
    };
}
