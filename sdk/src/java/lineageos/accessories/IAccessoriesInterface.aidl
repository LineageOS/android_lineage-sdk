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
     * Get the current list of accessories.
     *
     * @return The current list of accessories
     */
    IAccessory[] getAccessories();

    /**
     * Register a callback that will receive accessory events.
     *
     * The callback will also receive all accessories through the
     * IAccessoriesCallback.onAccessoryAdded method during this call.
     *
     * @param callback The callback to register
     */
    oneway void registerCallback(IAccessoriesCallback callback);

    /**
     * Unregister a callback.
     *
     * @param callback The callback to unregister
     */
    oneway void unregisterCallback(IAccessoriesCallback callback);
}
