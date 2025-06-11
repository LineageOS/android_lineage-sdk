/*
 * SPDX-FileCopyrightText: 2018,2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.util;

import android.content.ContentResolver;
import android.os.UserHandle;

import lineageos.providers.LineageSettings;

public class DeviceKeysConstants {
    // Available custom actions to perform on a key press.
    // Must match values for KEY_HOME_LONG_PRESS_ACTION in:
    //   sdk/src/java/lineageos/providers/LineageSettings.java
    public enum Action {
        NOTHING,
        MENU,
        APP_SWITCH,
        SEARCH,
        VOICE_SEARCH,
        IN_APP_SEARCH,
        LAUNCH_CAMERA,
        SLEEP,
        LAST_APP,
        SPLIT_SCREEN,
        KILL_APP,
        PLAY_PAUSE_MUSIC;

        public static Action fromIntSafe(int id) {
            if (id < NOTHING.ordinal() || id > Action.values().length) {
                return NOTHING;
            }
            return Action.values()[id];
        }

        public static Action fromSettings(ContentResolver cr, String setting, Action def) {
            return fromIntSafe(LineageSettings.System.getIntForUser(cr,
                    setting, def.ordinal(), UserHandle.USER_CURRENT));
        }
    }

    // Masks for checking presence of hardware keys.
    // Must match values in:
    //   lineage/res/res/values/config.xml
    public static final int KEY_MASK_HOME = 0x01;
    public static final int KEY_MASK_BACK = 0x02;
    public static final int KEY_MASK_MENU = 0x04;
    public static final int KEY_MASK_ASSIST = 0x08;
    public static final int KEY_MASK_APP_SWITCH = 0x10;
    public static final int KEY_MASK_CAMERA = 0x20;
    public static final int KEY_MASK_VOLUME = 0x40;
}

