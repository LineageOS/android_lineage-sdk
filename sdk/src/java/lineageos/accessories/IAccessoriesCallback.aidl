/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

import lineageos.accessories.IAccessory;

/**
 * Callback for accessories information updates.
 */
oneway interface IAccessoriesCallback {
    /**
     * Called when an accessory is added.
     *
     * This method will be called for all accessories during the callback registration.
     *
     * @param accessory The accessory interface
     */
    void onAccessoryAdded(IAccessory accessory);

    /**
     * Called when an accessory is removed.
     *
     * The accessory must be forgotten by the client and any reference to the interface
     * must be dropped.
     *
     * @param accessory The removed accessory interface
     */
    void onAccessoryRemoved(IAccessory accessory);
}
