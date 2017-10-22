/**
 * Copyright (C) 2017 The LineageOS Project
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
package org.lineageos.internal.lights;

import android.content.Context;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;

import lineageos.providers.LineageSettings;
import org.lineageos.internal.lights.LightsCapabilities;

public final class LineageBatteryLights {
    // Battery light maximum brightness value to use.
    public static final int LIGHT_BRIGHTNESS_MAXIMUM = 255;

    // Battery light capabilities.
    public final boolean mAdjustableBatteryLedBrightness;
    public final boolean mMultiColorLed;
    public final boolean mUseSegmentedBatteryLed;

    // Battery light intended operational state.
    public boolean mLightEnabled = false; // Disable until observer is started
    public boolean mLedPulseEnabled;
    public int mBatteryLowARGB;
    public int mBatteryMediumARGB;
    public int mBatteryFullARGB;
    public int mBatteryLedBrightness;

    private final Context mContext;

    public interface LedUpdater {
        public void update();
    }
    private final LedUpdater mLedUpdater;

    public LineageBatteryLights(Context context, LedUpdater ledUpdater) {
        mContext = context;
        mLedUpdater = ledUpdater;

        // Is the battery LED brightness changeable ?
        mAdjustableBatteryLedBrightness = LightsCapabilities.supports(
                mContext, LightsCapabilities.LIGHTS_ADJUSTABLE_NOTIFICATION_LED_BRIGHTNESS);

        // Does the Device support changing battery LED colors?
        mMultiColorLed = LightsCapabilities.supports(
                mContext, LightsCapabilities.LIGHTS_RGB_BATTERY_LED);

        // Does the Device have segmented battery LED support? In this case, we send the level
        // in the alpha channel of the color and let the HAL sort it out.
        mUseSegmentedBatteryLed = LightsCapabilities.supports(
                mContext, LightsCapabilities.LIGHTS_SEGMENTED_BATTERY_LED);

        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            // Battery light enabled
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.BATTERY_LIGHT_ENABLED), false, this,
                   UserHandle.USER_ALL);

            // Low battery pulse
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.BATTERY_LIGHT_PULSE), false, this,
                UserHandle.USER_ALL);

            // Battery LED brightness
            if (mAdjustableBatteryLedBrightness) {
               resolver.registerContentObserver(LineageSettings.System.getUriFor(
                        LineageSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL),
                        false, this, UserHandle.USER_ALL);
            }

            // Light colors
            if (mMultiColorLed) {
                resolver.registerContentObserver(LineageSettings.System.getUriFor(
                        LineageSettings.System.BATTERY_LIGHT_LOW_COLOR), false, this,
                        UserHandle.USER_ALL);
                resolver.registerContentObserver(LineageSettings.System.getUriFor(
                        LineageSettings.System.BATTERY_LIGHT_MEDIUM_COLOR), false, this,
                        UserHandle.USER_ALL);
                resolver.registerContentObserver(LineageSettings.System.getUriFor(
                        LineageSettings.System.BATTERY_LIGHT_FULL_COLOR), false, this,
                        UserHandle.USER_ALL);
            }

            readSettings();
            mLedUpdater.update();
        }

        @Override public void onChange(boolean selfChange) {
            readSettings();
            mLedUpdater.update();
        }

        public void readSettings() {
            ContentResolver resolver = mContext.getContentResolver();
            Resources res = mContext.getResources();

            // Battery light enabled
            mLightEnabled = LineageSettings.System.getInt(resolver,
                    LineageSettings.System.BATTERY_LIGHT_ENABLED, 1) != 0;

            // Low battery pulse
            mLedPulseEnabled = LineageSettings.System.getInt(resolver,
                        LineageSettings.System.BATTERY_LIGHT_PULSE, 1) != 0;

           // Light colors
            mBatteryLowARGB = LineageSettings.System.getInt(resolver,
                    LineageSettings.System.BATTERY_LIGHT_LOW_COLOR, res.getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryLowARGB));
            mBatteryMediumARGB = LineageSettings.System.getInt(resolver,
                    LineageSettings.System.BATTERY_LIGHT_MEDIUM_COLOR, res.getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryMediumARGB));
            mBatteryFullARGB = LineageSettings.System.getInt(resolver,
                    LineageSettings.System.BATTERY_LIGHT_FULL_COLOR, res.getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryFullARGB));

            // Light brightness
            if (mAdjustableBatteryLedBrightness) {
                mBatteryLedBrightness = LineageSettings.System.getInt(resolver,
                        LineageSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                        LIGHT_BRIGHTNESS_MAXIMUM);
            }
        }
    }
}
