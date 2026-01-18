/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.notification;

import android.content.Context;

public final class LightsCapabilities {
    // Device has a color adjustable notification light.
    public static final int LIGHTS_RGB_NOTIFICATION_LED = 1;

    // Device has a color adjustable battery light.
    public static final int LIGHTS_RGB_BATTERY_LED = 2;

    // Deprecated
    // public static final int LIGHTS_MULTIPLE_NOTIFICATION_LED = 4;

    // The notification light has adjustable pulsing capability.
    public static final int LIGHTS_PULSATING_LED = 8;

    // Device has a multi-segment battery light that is able to
    // use the light brightness value to determine how many
    // segments to show (in order to represent battery level).
    public static final int LIGHTS_SEGMENTED_BATTERY_LED = 16;

    // The notification light supports HAL adjustable brightness
    // via the alpha channel.
    // Note: if a device notification light supports LIGHTS_RGB_NOTIFICATION_LED
    // then HAL support is not necessary for brightness control.  In this case,
    // brightness support will be provided by lineage-sdk through the scaling of
    // RGB color values.
    public static final int LIGHTS_ADJUSTABLE_NOTIFICATION_LED_BRIGHTNESS = 32;

    // Device has a battery light.
    public static final int LIGHTS_BATTERY_LED = 64;

    // The battery light supports HAL adjustable brightness via
    // the alpha channel.
    // Note: if a device battery light supports LIGHTS_RGB_BATTERY_LED then HAL
    // support is not necessary for brightness control.  In this case,
    // brightness support will be provided by lineage-sdk through the scaling of
    // RGB color values.
    public static final int LIGHTS_ADJUSTABLE_BATTERY_LED_BRIGHTNESS = 128;

    // The notification light has non-adjustable pulsing capability.
    public static final int LIGHTS_BREATHING_LED = 256;

    public static boolean blinks(Context context) {
        final int capabilities = context.getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceLightCapabilities);
        return (capabilities & (LIGHTS_PULSATING_LED | LIGHTS_BREATHING_LED)) != 0;
    }

    public static boolean supports(Context context, final int capability) {
        final int capabilities = context.getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceLightCapabilities);
        return (capabilities & capability) != 0;
    }
}
