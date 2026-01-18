/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.applications;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import lineageos.providers.LineageSettings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LongScreen {
    private Set<String> mApps = new HashSet<>();
    private Context mContext;

    private final boolean mLongScreenAvailable;

    public LongScreen(Context context) {
        mContext = context;
        final Resources resources = mContext.getResources();

        mLongScreenAvailable = resources.getBoolean(
                org.lineageos.platform.internal.R.bool.config_haveHigherAspectRatioScreen);

        if (!mLongScreenAvailable) {
            return;
        }

        SettingsObserver observer = new SettingsObserver(
                new Handler(Looper.getMainLooper()));
        observer.observe();
    }

    public boolean isSupported() {
        return mLongScreenAvailable;
    }

    public boolean shouldForceLongScreen(String packageName) {
        return isSupported() && mApps.contains(packageName);
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
        SettingsObserver(Handler handler) {
            super(handler);
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
                setApps(new HashSet<>(Arrays.asList(apps.split(","))));
            } else {
                setApps(new HashSet<>());
            }
        }
    }
}
