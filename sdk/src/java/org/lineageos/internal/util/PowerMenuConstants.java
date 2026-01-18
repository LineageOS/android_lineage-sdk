/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.util;

/* Master list of all actions for the power menu */
public class PowerMenuConstants {
    public static final String GLOBAL_ACTION_KEY_POWER = "power";
    public static final String GLOBAL_ACTION_KEY_RESTART = "restart";
    public static final String GLOBAL_ACTION_KEY_SCREENSHOT = "screenshot";
    public static final String GLOBAL_ACTION_KEY_AIRPLANE = "airplane";
    public static final String GLOBAL_ACTION_KEY_USERS = "users";
    public static final String GLOBAL_ACTION_KEY_SETTINGS = "settings";
    public static final String GLOBAL_ACTION_KEY_LOCKDOWN = "lockdown";
    public static final String GLOBAL_ACTION_KEY_BUGREPORT = "bugreport";
    public static final String GLOBAL_ACTION_KEY_SILENT = "silent";
    public static final String GLOBAL_ACTION_KEY_VOICEASSIST = "voiceassist";
    public static final String GLOBAL_ACTION_KEY_ASSIST = "assist";
    public static final String GLOBAL_ACTION_KEY_LOGOUT = "logout";
    public static final String GLOBAL_ACTION_KEY_EMERGENCY = "emergency";
    public static final String GLOBAL_ACTION_KEY_DEVICECONTROLS = "devicecontrols";
    public static final String GLOBAL_ACTION_KEY_SYSTEM_UPDATE = "system_update";
    public static final String GLOBAL_ACTION_KEY_STANDBY = "standby";
    public static final String GLOBAL_ACTION_KEY_LOCK = "lock";

    /**
     * Advanced restart menu actions
     */
    public static final String GLOBAL_ACTION_KEY_RESTART_RECOVERY = "restart_recovery";
    public static final String GLOBAL_ACTION_KEY_RESTART_BOOTLOADER = "restart_bootloader";
    public static final String GLOBAL_ACTION_KEY_RESTART_DOWNLOAD = "restart_download";
    public static final String GLOBAL_ACTION_KEY_RESTART_FASTBOOT = "restart_fastboot";

    private static String[] ALL_ACTIONS = {
        GLOBAL_ACTION_KEY_EMERGENCY,
        GLOBAL_ACTION_KEY_LOCKDOWN,
        GLOBAL_ACTION_KEY_POWER,
        GLOBAL_ACTION_KEY_RESTART,
        GLOBAL_ACTION_KEY_SCREENSHOT,
        GLOBAL_ACTION_KEY_AIRPLANE,
        GLOBAL_ACTION_KEY_USERS,
        GLOBAL_ACTION_KEY_SETTINGS,
        GLOBAL_ACTION_KEY_BUGREPORT,
        GLOBAL_ACTION_KEY_SILENT,
        GLOBAL_ACTION_KEY_VOICEASSIST,
        GLOBAL_ACTION_KEY_ASSIST,
        GLOBAL_ACTION_KEY_DEVICECONTROLS,
        GLOBAL_ACTION_KEY_LOGOUT,
        GLOBAL_ACTION_KEY_SYSTEM_UPDATE,
        GLOBAL_ACTION_KEY_STANDBY,
        GLOBAL_ACTION_KEY_LOCK,
    };

    public static String[] getAllActions() {
        return ALL_ACTIONS;
    }
}
