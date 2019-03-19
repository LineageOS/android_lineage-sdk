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

public class LineageNotification {

    ///////////////////////////////////////////////////
    // Lineage-specific Notification bundle extras
    ///////////////////////////////////////////////////

    /**
     * Used by light picker in Settings to force
     * notification lights on when screen is on.
     */
    public static final String EXTRA_FORCE_SHOW_LIGHTS = "lineage.forceShowLights";

    /**
     * Used by light picker in Settings to force
     * a specific light brightness.
     */
    public static final String EXTRA_FORCE_LIGHT_BRIGHTNESS = "lineage.forceLightBrightness";

    /**
     * Used by light picker in Settings to force
     * a specific light color for preview.
     */
    public static final String EXTRA_FORCE_PREVIEW_COLOR = "lineage.forcePreviewColor";

    /**
     * Used by light picker in Settings to force
     * a specific light on duration for preview.
     *
     * Value must be greater than or equal to 0.
     */
    public static final String EXTRA_FORCE_PREVIEW_LIGHT_ON_MS = "lineage.forcePreviewLightOnMs";

    /**
     * Used by light picker in Settings to force
     * a specific light off duration for preview.
     *
     * Value must be greater than or equal to 0.
     */
    public static final String EXTRA_FORCE_PREVIEW_LIGHT_OFF_MS = "lineage.forcePreviewLightOffMs";
}
