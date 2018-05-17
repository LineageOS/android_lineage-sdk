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

package org.lineageos.internal.notification;

import android.content.Context;
import android.hardware.light.V2_0.*;
import android.os.RemoteException;
import android.util.Log;
import android.util.Singleton;

import java.util.ArrayList;

public final class LightsCapabilities {
    private static final String TAG = "LightsCapabilities";
    private ILight mILight;

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

    private static final Singleton<ILight> gDefault =
            new Singleton<ILight>() {
        @Override
        protected ILight create() {
            try {
                return ILight.getService();
            } catch (RemoteException e) {}
            return null;
        }
    };

    static ILight getLightService() {
        return gDefault.get();
    }

    public static boolean supports(Context context, final int capability) {
        // Attempt to read the capabilities straight from the Lights HAL first
        ILight service = getLightService();

        if (service != null) {
            try {
                //ArrayList<Type> types = service.getSupportedTypes();
                ArrayList<Integer> types = service.getSupportedTypes();
                if (types.size() == 0) {
                    //return null;
                }
                int[] results = new int[types.size()];
                for (int i = 0; i < results.length; i++) {
                    //results[i] = new int(types.get(i));
                    results[i] = types.get(i);
                    Log.e(TAG, "getSupportedTypes: found light" + Integer.toString(results[i]));
                }
                //return results;
            } catch (RemoteException e) {
                Log.e(TAG, "getSupportedTypes failed", e);
            }
	} else {
            Log.e(TAG, "getSupportedTypes: No ILight HAL!");
        }
        //return null;

        final int capabilities = context.getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceLightCapabilities);
        return (capabilities & capability) != 0;
    }
}
