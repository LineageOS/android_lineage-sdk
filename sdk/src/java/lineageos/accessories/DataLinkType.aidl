/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

/**
 * The link used to exchange data with an accessory.
 */
enum DataLinkType {
    /**
     * Bluetooth Low Energy. The ID is the Bluetooth address, in the "XX:XX:XX:XX:XX:XX" format.
     */
    BLUETOOTH,

    /**
     * USB. The ID is the USB vendor and product ID, in the "XXXX:XXXX" hex format.
     */
    USB,
}
