/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

/**
 * The status of the automatic Bluetooth pairing of an accessory.
 */
enum PairingStatus {
    /**
     * No pairing is needed, either because the accessory isn't charging and enabled, it has no
     * Bluetooth data link or it is already paired.
     */
    IDLE,

    /**
     * The accessory is being searched for or bonded with.
     */
    PAIRING,

    /**
     * Pairing failed, IAccessory.retryPairing can be called to try again.
     */
    FAILED,

    /**
     * Pairing is deferred until Bluetooth gets enabled.
     */
    BLUETOOTH_DISABLED,
}
