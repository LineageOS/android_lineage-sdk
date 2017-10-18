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
package org.lineageos.internal.util;

import android.content.Context;

public final class LightsUtils {
    public static final int LIGHTS_RGB_NOTIFICATION_LED = 1;
    public static final int LIGHTS_RGB_BATTERY_LED = 2;
    public static final int LIGHTS_MULTIPLE_NOTIFICATION_LED = 4;
    public static final int LIGHTS_PULSATING_LED = 8;
    public static final int LIGHTS_SEGMENTED_BATTERY_LED = 16;
    public static final int LIGHTS_ADJUSTABLE_NOTIFICATION_LED_BRIGHTNESS = 32;

    public static boolean hasCapability(Context context, final int capability) {
        final int capabilities = context.getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceLightCapabilities);
        return (capabilities & capability) != 0;
    }
}
