/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

import lineageos.accessories.IAccessory;
import lineageos.accessories.IAccessoriesCallback;

/**
 * An interface for managing accessories.
 */
interface IAccessoriesInterface {
    /**
     * Get the list of accessories that won't disappear even if disconnected.
     *
     * To obtain the list of all accessories, use the callback.
     *
     * @return The list of persistent accessories
     */
    IAccessory[] getPersistentAccessories();

    /**
     * Register a callback that will receive accessory events.
     *
     * The callback will also receive all accessories through the
     * IAccessoriesCallback.onAccessoryAdded method during this call.
     *
     * @param callback The callback to register
     */
    void registerCallback(IAccessoriesCallback callback);

    /**
     * Unregister a callback.
     *
     * @param callback The callback to unregister
     */
    void unregisterCallback(IAccessoriesCallback callback);
}
