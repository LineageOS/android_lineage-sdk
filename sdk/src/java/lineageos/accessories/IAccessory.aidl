/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

import lineageos.accessories.AccessoryInfo;
import lineageos.accessories.AccessoryStatus;
import lineageos.accessories.BatteryInfo;
import lineageos.accessories.IAccessoryCallback;

/**
 * Interface for interacting with an accessory.
 */
interface IAccessory {
    /**
     * Get the accessory information.
     *
     * @return The accessory information
     */
    AccessoryInfo getAccessoryInfo();

    /**
     * Get the current accessory status.
     *
     * @return The accessory status
     */
    AccessoryStatus getAccessoryStatus();

    /**
     * Register a callback that will receive accessory status updates.
     *
     * The callback will also receive all accessory informations through the available methods
     * as soon as the callback is registered.
     *
     * @param callback The callback to register
     */
    oneway void registerCallback(IAccessoryCallback callback);

    /**
     * Unregister a callback.
     *
     * @param callback The callback to unregister
     */
    oneway void unregisterCallback(IAccessoryCallback callback);

    /**
     * Set the accessory enabled state.
     *
     * @param enabled True to enable the accessory, false to disable it
     * @throw EX_UNSUPPORTED_OPERATION if either the accessory or the current state does not support
     *        toggling
     * @see AccessoryInfo.supportsToggling
     */
    void setEnabled(boolean enabled);

    /**
     * Get the battery information.
     *
     * @return The battery information if supported, else null
     * @throw EX_UNSUPPORTED_OPERATION if either the accessory or the current state does not support
     *        querying the battery information
     * @see AccessoryInfo.supportsBatteryQuerying
     */
    BatteryInfo getBatteryInfo();
}
