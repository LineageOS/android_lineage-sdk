/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

import lineageos.accessories.PairingStatus;

oneway interface IAccessoryPairingCallback {
    /**
     * Called when the pairing status of the accessory changes.
     * Also called with the current status as soon as the callback is registered.
     */
    void onPairingStatusUpdated(PairingStatus status);
}
