/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

/**
 * The status of an accessory.
 */
enum AccessoryStatus {
    /**
     * The accessory is not connected.
     * If it is a persistent accessory, it should be shown as disconnected.
     * If this isn't a persistent accessory, it should be removed from the UI and
     * IAccessoriesCallback.onAccessoryRemoved will also be called to allow the client to clean up.
     */
    DISCONNECTED,

    /**
     * The accessory, while technically connected to the device, is not in a state where it can be
     * used. Instead it is currently charging.
     * This is the case for an active pen physically connected to the device.
     * The accessory should be able to provide battery information.
     */
    CHARGING,

    /**
     * The device is connected but disabled.
     */
    DISABLED,

    /**
     * The device is connected and enabled.
     */
    ENABLED,
}
