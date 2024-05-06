/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.input;

/**
 * Accessory's battery status
 */
enum BatteryStatus {
    /**
     * The battery status is unknown.
     * Note: This should only be used on internal issues, on device disconnection instead set
     * BatteryInfo to null.
     */
    UNKNOWN,

    /**
     * The battery is charging.
     * Battery flow is >0mA.
     */
    CHARGING,

    /**
     * The battery is discharging.
     * Battery flow is <0mA.
     */
    DISCHARGING,

    /**
     * The device is not charging nor discharging its battery.
     * Battery flow is ~0mA.
     */
    NOT_CHARGING,
}
