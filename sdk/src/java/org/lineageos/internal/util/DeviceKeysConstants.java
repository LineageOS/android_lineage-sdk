/*
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

public class DeviceKeysConstants {
    // Masks for checking presence of hardware keys.
    // Must match values in lineage/res/res/values/config.xml
    public static final int KEY_MASK_HOME = 0x01;
    public static final int KEY_MASK_BACK = 0x02;
    public static final int KEY_MASK_MENU = 0x04;
    public static final int KEY_MASK_ASSIST = 0x08;
    public static final int KEY_MASK_APP_SWITCH = 0x10;
    public static final int KEY_MASK_CAMERA = 0x20;
    public static final int KEY_MASK_VOLUME = 0x40;

    // Available custom actions to perform on a key press.
    // Must match values for KEY_HOME_LONG_PRESS_ACTION in
    // sdk/src/java/lineageos/providers/LineageSettings.java
    public static final int KEY_ACTION_NOTHING = 0;
    public static final int KEY_ACTION_MENU = 1;
    public static final int KEY_ACTION_APP_SWITCH = 2;
    public static final int KEY_ACTION_SEARCH = 3;
    public static final int KEY_ACTION_VOICE_SEARCH = 4;
    public static final int KEY_ACTION_IN_APP_SEARCH = 5;
    public static final int KEY_ACTION_LAUNCH_CAMERA = 6;
    public static final int KEY_ACTION_SLEEP = 7;
    public static final int KEY_ACTION_LAST_APP = 8;
    public static final int KEY_ACTION_SPLIT_SCREEN = 9;
    public static final int KEY_ACTION_SINGLE_HAND_LEFT = 10;
    public static final int KEY_ACTION_SINGLE_HAND_RIGHT = 11;
}

