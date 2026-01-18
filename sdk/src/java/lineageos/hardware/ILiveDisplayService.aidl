/*
 * SPDX-FileCopyrightText: 2016, The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.hardware;

import lineageos.hardware.HSIC;
import lineageos.hardware.LiveDisplayConfig;

/** @hide */
interface ILiveDisplayService {
    LiveDisplayConfig getConfig();

    int getMode();
    boolean setMode(int mode);

    float[] getColorAdjustment();
    boolean setColorAdjustment(in float[] adj);

    boolean isAutoContrastEnabled();
    boolean setAutoContrastEnabled(boolean enabled);

    boolean isCABCEnabled();
    boolean setCABCEnabled(boolean enabled);

    boolean isColorEnhancementEnabled();
    boolean setColorEnhancementEnabled(boolean enabled);

    int getDayColorTemperature();
    boolean setDayColorTemperature(int temperature);

    int getNightColorTemperature();
    boolean setNightColorTemperature(int temperature);

    int getColorTemperature();

    boolean isAutomaticOutdoorModeEnabled();
    boolean setAutomaticOutdoorModeEnabled(boolean enabled);

    HSIC getPictureAdjustment();
    HSIC getDefaultPictureAdjustment();
    boolean setPictureAdjustment(in HSIC adj);
    boolean isNight();

    boolean isAntiFlickerEnabled();
    boolean setAntiFlickerEnabled(boolean enabled);
}
