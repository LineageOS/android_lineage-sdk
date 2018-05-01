/**
 * Copyright (C) 2018 The LineageOS project
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

package org.lineageos.internal.applications;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import lineageos.providers.LineageSettings;

public class LongScreen {
    private Set<String> mApps = new HashSet<>();
    private Context mContext;

    public LongScreen(Context context) {
        mContext = context;

        SettingsObserver observer = new SettingsObserver(
                new Handler(Looper.getMainLooper()), this);
        observer.observe();
    }

    public boolean isSupported() {
        return mContext.getResources().getBoolean(
                org.lineageos.platform.internal.R.bool.config_haveHigherAspectRatioScreen);
    }

    public Set<String> getApps() {
        return mApps;
    }

    public void addApp(String packageName) {
        mApps.add(packageName);
        LineageSettings.System.putString(mContext.getContentResolver(),
                LineageSettings.System.LONG_SCREEN_APPS, String.join(",", mApps));
    }

    public void removeApp(String packageName) {
        mApps.remove(packageName);
        LineageSettings.System.putString(mContext.getContentResolver(),
                LineageSettings.System.LONG_SCREEN_APPS, String.join(",", mApps));
    }

    public void setApps(Set<String> apps) {
        mApps = apps;
    }

    class SettingsObserver extends ContentObserver {
        private LongScreen mLongScreen;

        SettingsObserver(Handler handler, LongScreen longScreen) {
            super(handler);

            mLongScreen = longScreen;
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.LONG_SCREEN_APPS), false, this,
                    UserHandle.USER_ALL);

            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();

            String apps = LineageSettings.System.getStringForUser(resolver,
                    LineageSettings.System.LONG_SCREEN_APPS,
                    UserHandle.USER_CURRENT);
            if (apps != null) {
                mLongScreen.setApps(new HashSet<>(Arrays.asList(apps.split(","))));
            } else {
                mLongScreen.setApps(new HashSet<>());
            }
        }
    }
}
