/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.input;

import lineageos.input.BatteryStatus;

/**
 * Battery information for an accessory.
 */
parcelable BatteryInfo {
    /**
     * Value used in BatteryInfo.batteryLevel when the battery level is unknown.
     */
    const int BATTERY_LEVEL_UNKNOWN = -1;

    /**
     * The current battery status.
     */
    BatteryStatus status;

    /**
     * The battery level of the device.
     * This value is in the range [0, 100] or BatteryInfo.BATTERY_LEVEL_UNKNOWN if unknown.
     */
    int levelPercentage;
}
