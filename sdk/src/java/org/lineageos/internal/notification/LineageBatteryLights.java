/**
 * Copyright (C) 2017-2019,2021 The LineageOS Project
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

package org.lineageos.internal.notification;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.util.Slog;

import lineageos.providers.LineageSettings;

public final class LineageBatteryLights {
    private final String TAG = "LineageBatteryLights";
    private final boolean DEBUG = false;

    // Battery light capabilities.
    private final boolean mHasBatteryLed;
    private final boolean mMultiColorBatteryLed;
    // Whether the lights HAL supports changing brightness.
    private final boolean mHALAdjustableBrightness;
    // Whether the light should be considered brightness adjustable
    // (via HAL or via modifying RGB values).
    private final boolean mCanAdjustBrightness;
    private final boolean mUseSegmentedBatteryLed;

    // Battery light intended operational state.
    private boolean mLightEnabled = false; // Disable until observer is started
    private boolean mLightFullChargeDisabled;
    private boolean mLedPulseEnabled;
    private int mBatteryLowARGB;
    private int mBatteryMediumARGB;
    private int mBatteryFullARGB;
    private int mBatteryBrightnessLevel;
    private int mBatteryBrightnessZenLevel;

    private final Context mContext;

    private NotificationManager mNotificationManager;
    private int mZenMode;

    public interface LedUpdater {
        public void update();
    }
    private final LedUpdater mLedUpdater;

    public LineageBatteryLights(Context context, LedUpdater ledUpdater) {
        mContext = context;
        mLedUpdater = ledUpdater;

        // Does the device have a battery LED?
        mHasBatteryLed = LightsCapabilities.supports(
                mContext, LightsCapabilities.LIGHTS_BATTERY_LED);

        // Does the device support changing battery LED colors?
        mMultiColorBatteryLed = LightsCapabilities.supports(
                mContext, LightsCapabilities.LIGHTS_RGB_BATTERY_LED);

        mHALAdjustableBrightness = LightsCapabilities.supports(
                mContext, LightsCapabilities.LIGHTS_ADJUSTABLE_BATTERY_LED_BRIGHTNESS);

        // We support brightness adjustment if either the HAL supports it
        // or the light is RGB adjustable.
        mCanAdjustBrightness = mHALAdjustableBrightness || mMultiColorBatteryLed;

        // Does the device have segmented battery LED support? In this case, we send the level
        // in the alpha channel of the color and let the HAL sort it out.
        mUseSegmentedBatteryLed = LightsCapabilities.supports(
                mContext, LightsCapabilities.LIGHTS_SEGMENTED_BATTERY_LED);

        // Watch for zen mode changes
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        IntentFilter filter = new IntentFilter(
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        context.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        mZenMode = mNotificationManager.getZenMode();
                        mLedUpdater.update();
                    }
                }, filter);
        mZenMode = mNotificationManager.getZenMode();

        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();
    }

    public boolean isSupported() {
        return mHasBatteryLed;
    }

    public void calcLights(LedValues ledValues, int level, int status, boolean low) {
        if (DEBUG) {
            Slog.i(TAG, "calcLights input:"
                    + " ledValues={ " + ledValues + " }"
                    + " level=" + level
                    + " status=" + status
                    + " low=" + low
                    + " mCanAdjustBrightness=" + mCanAdjustBrightness
                    + " mHALAdjustableBrightness=" + mHALAdjustableBrightness
                    + " mMultiColorBatteryLed=" + mMultiColorBatteryLed
                    + " mUseSegmentedBatteryLed=" + mUseSegmentedBatteryLed
                    + " mBatteryBrightnessLevel=" + mBatteryBrightnessLevel
                    + " mBatteryBrightnessZenLevel=" + mBatteryBrightnessZenLevel
                    + " mZenMode=" + mZenMode
                    + " mLightFullChargeDisabled=" + mLightFullChargeDisabled
            );
        }

        final boolean isChargingOrFull = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        final boolean isCharged = status == BatteryManager.BATTERY_STATUS_FULL || level >= 100;

        // The only meaningful ledValues values received by frameworks BatteryService
        // are the pulse times (for low battery). Explicitly set enabled state and
        // color to ensure that we arrive at a deterministic outcome.
        ledValues.setEnabled(false);
        ledValues.setColor(0);

        if (!mLightEnabled || (mLightFullChargeDisabled && isCharged)) {
            return;
        }

        final int brightness;
        if (mUseSegmentedBatteryLed) {
            brightness = level;
        } else if (!mCanAdjustBrightness) {
            // No brightness support available
            brightness = LedValues.LIGHT_BRIGHTNESS_MAXIMUM;
        } else if (mZenMode == Global.ZEN_MODE_OFF) {
            brightness = mBatteryBrightnessLevel;
        } else {
            brightness = mBatteryBrightnessZenLevel;
        }
        ledValues.setBrightness(brightness);

        if (low) {
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                // Battery is charging and low.
                ledValues.setColor(mBatteryLowARGB);
                ledValues.setSolid();
            } else if (mLedPulseEnabled) {
                // Battery is low, not charging and pulse is enabled
                // (pulsing values are set by frameworks BatteryService).
                ledValues.setColor(mBatteryLowARGB);
            }
        } else if (isChargingOrFull) {
            if (isCharged || level >= 90) {
                // Battery is full or charging and nearly full.
                ledValues.setColor(mBatteryFullARGB);
                ledValues.setSolid();
            } else {
                // Battery is charging and not nearly full.
                ledValues.setColor(mBatteryMediumARGB);
                ledValues.setSolid();
            }
        }

        // If a color was set, enable light.
        if (ledValues.getColor() != 0) {
            ledValues.setEnabled(true);
        }
        if (mCanAdjustBrightness) {
            if (!mHALAdjustableBrightness) {
                // If lights HAL does not support adjustable brightness then
                // scale color value here instead.
                ledValues.applyAlphaToBrightness();
                ledValues.applyBrightnessToColor();
            } else if (mUseSegmentedBatteryLed && !mMultiColorBatteryLed) {
                // For non-RGB segmented LEDs, we must set the brightness as the
                // color, since the alpha channel contains the battery level
                int segmentLevel = mZenMode == Global.ZEN_MODE_OFF
                        ? mBatteryBrightnessLevel : mBatteryBrightnessZenLevel;
                ledValues.setColor(segmentLevel | (segmentLevel << 8) | (segmentLevel << 16));
            }
        }
        // If LED is segmented, reset brightness field to battery level
        // (applyBrightnessToColor() changes it to 255)
        if (mUseSegmentedBatteryLed) {
            ledValues.setBrightness(brightness);
        }

        if (DEBUG) {
            Slog.i(TAG, "calcLights output: ledValues={ " + ledValues + " }");
        }
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

            // Battery light disabled if fully charged
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.BATTERY_LIGHT_FULL_CHARGE_DISABLED), false, this,
                    UserHandle.USER_ALL);

            // Low battery pulse
            resolver.registerContentObserver(LineageSettings.System.getUriFor(
                    LineageSettings.System.BATTERY_LIGHT_PULSE), false, this,
                    UserHandle.USER_ALL);

            if (mMultiColorBatteryLed) {
                // Light colors
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

            if (mCanAdjustBrightness) {
                // Battery brightness level
                resolver.registerContentObserver(LineageSettings.System.getUriFor(
                        LineageSettings.System.BATTERY_LIGHT_BRIGHTNESS_LEVEL), false, this,
                        UserHandle.USER_ALL);
                // Battery brightness level in Do Not Disturb mode
                resolver.registerContentObserver(LineageSettings.System.getUriFor(
                        LineageSettings.System.BATTERY_LIGHT_BRIGHTNESS_LEVEL_ZEN), false, this,
                        UserHandle.USER_ALL);
            }

            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        private void update() {
            ContentResolver resolver = mContext.getContentResolver();
            Resources res = mContext.getResources();

            // Battery light enabled
            mLightEnabled = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_ENABLED,
                    1, UserHandle.USER_CURRENT) != 0;

            // Battery light disabled if fully charged
            mLightFullChargeDisabled = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_FULL_CHARGE_DISABLED,
                    1, UserHandle.USER_CURRENT) != 0;

            // Low battery pulse
            mLedPulseEnabled = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_PULSE,
                    1, UserHandle.USER_CURRENT) != 0;

            // Light colors
            mBatteryLowARGB = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_LOW_COLOR, res.getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryLowARGB),
                    UserHandle.USER_CURRENT);
            mBatteryMediumARGB = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_MEDIUM_COLOR, res.getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryMediumARGB),
                    UserHandle.USER_CURRENT);
            mBatteryFullARGB = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_FULL_COLOR, res.getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryFullARGB),
                    UserHandle.USER_CURRENT);

            // Adustable battery LED brightness.
            if (mCanAdjustBrightness) {
                // Battery brightness level
                mBatteryBrightnessLevel = LineageSettings.System.getIntForUser(resolver,
                        LineageSettings.System.BATTERY_LIGHT_BRIGHTNESS_LEVEL,
                        LedValues.LIGHT_BRIGHTNESS_MAXIMUM, UserHandle.USER_CURRENT);
                // Battery brightness level in Do Not Disturb mode
                mBatteryBrightnessZenLevel = LineageSettings.System.getIntForUser(resolver,
                        LineageSettings.System.BATTERY_LIGHT_BRIGHTNESS_LEVEL_ZEN,
                        LedValues.LIGHT_BRIGHTNESS_MAXIMUM, UserHandle.USER_CURRENT);
            }

            mLedUpdater.update();
        }
    }
}
