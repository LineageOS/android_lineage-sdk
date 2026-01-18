/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
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
     * a specific light color.
     */
    public static final String EXTRA_FORCE_COLOR = "lineage.forceColor";

    /**
     * Used by light picker in Settings to force
     * a specific light on duration.
     *
     * Value must be greater than or equal to 0.
     */
    public static final String EXTRA_FORCE_LIGHT_ON_MS = "lineage.forceLightOnMs";

    /**
     * Used by light picker in Settings to force
     * a specific light off duration.
     *
     * Value must be greater than or equal to 0.
     */
    public static final String EXTRA_FORCE_LIGHT_OFF_MS = "lineage.forceLightOffMs";
}
