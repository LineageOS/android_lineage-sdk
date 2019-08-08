/*
 * Copyright (C) 2015 The CyanogenMod Project
 * This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
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

package lineageos.content;

import android.Manifest;

/**
 * LineageOS specific intent definition class.
 */
public class Intent {

    /**
     * Activity Action: Start action associated with long press on the recents key.
     * <p>Input: {@link #EXTRA_LONG_PRESS_RELEASE} is set to true if the long press
     * is released
     * <p>Output: Nothing
     * @hide
     */
    public static final String ACTION_RECENTS_LONG_PRESS =
            "lineageos.intent.action.RECENTS_LONG_PRESS";

    /**
     * This field is part of the intent {@link #ACTION_RECENTS_LONG_PRESS}.
     * The type of the extra is a boolean that indicates if the long press
     * is released.
     * @hide
     */
    public static final String EXTRA_RECENTS_LONG_PRESS_RELEASE =
            "lineageos.intent.extra.RECENTS_LONG_PRESS_RELEASE";

    /**
     * Intent filter to update protected app component's settings
     */
    public static final String ACTION_PROTECTED = "lineageos.intent.action.PACKAGE_PROTECTED";

    /**
     * Intent filter to notify change in state of protected application.
     */
    public static final String ACTION_PROTECTED_CHANGED =
            "lineageos.intent.action.PROTECTED_COMPONENT_UPDATE";

    /**
     * This field is part of the intent {@link #ACTION_PROTECTED_CHANGED}.
     * Intent extra field for the state of protected application
     */
    public static final String EXTRA_PROTECTED_STATE =
            "lineageos.intent.extra.PACKAGE_PROTECTED_STATE";

    /**
     * This field is part of the intent {@link #ACTION_PROTECTED_CHANGED}.
     * Intent extra field to indicate protected component value
     */
    public static final String EXTRA_PROTECTED_COMPONENTS =
            "lineageos.intent.extra.PACKAGE_PROTECTED_COMPONENTS";

    /**
     * Broadcast action: notify the system that the user has performed a gesture on the screen
     * to launch the camera. Broadcast should be protected to receivers holding the
     * {@link Manifest.permission#STATUS_BAR_SERVICE} permission.
     * @hide
     */
    public static final String ACTION_SCREEN_CAMERA_GESTURE =
            "lineageos.intent.action.SCREEN_CAMERA_GESTURE";

    /**
     * Broadcast action: perform any initialization required for LineageHW services.
     * Runs when the service receives the signal the device has booted, but
     * should happen before {@link android.content.Intent#ACTION_BOOT_COMPLETED}.
     *
     * Requires {@link lineageos.platform.Manifest.permission#HARDWARE_ABSTRACTION_ACCESS}.
     * @hide
     */
    public static final String ACTION_INITIALIZE_LINEAGE_HARDWARE =
            "lineageos.intent.action.INITIALIZE_LINEAGE_HARDWARE";

    /**
     * Broadcast action: lid state changed
     * @hide
     */
    public static final String ACTION_LID_STATE_CHANGED =
            "lineageos.intent.action.LID_STATE_CHANGED";

    /**
     * This field is part of the intent {@link #ACTION_LID_STATE_CHANGED}.
     * Intent extra field for the state of lid/cover
     * @hide
     */
    public static final String EXTRA_LID_STATE =
            "lineageos.intent.extra.LID_STATE";

    /**
     * Broadcast Action: Update preferences for the power menu dialog. This is to provide a
     * way for the preferences that need to be enabled/disabled to update because they were
     * toggled elsewhere in the settings (ie screenshot, user switcher, etc) so we don't have
     * to do constant lookups while we wait for the menu to be created. Getting the values once
     * when necessary is enough.
     *@hide
     */
    public static final String ACTION_UPDATE_POWER_MENU =
            "lineageos.intent.action.UPDATE_POWER_MENU";

    /**
     * Broadcast action: notify SystemUI that LiveDisplay service has finished initialization.
     * @hide
     */
    public static final String ACTION_INITIALIZE_LIVEDISPLAY =
            "lineageos.intent.action.INITIALIZE_LIVEDISPLAY";
}
