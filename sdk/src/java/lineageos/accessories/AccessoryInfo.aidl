/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

import lineageos.accessories.AccessoryType;
import lineageos.accessories.BatteryInfo;

/**
 * Information about an accessory.
 */
parcelable AccessoryInfo {
    /**
     * A unique identifier for this accessory. This ID will be used to interact with the accessory
     * and call methods on it.
     * This ID must be unique and stable across reboots, since it will be used to also store user
     * preferences regarding the accessory.
     * It might be a good idea to namespace the ID with the following format, all lowercase:
     * <accessory type>:<connection protocol>[:<protocol unique identifier(s)>]
     * For example, a USB keyboard might use the following ID: "keyboard:usb:1234:5678"
     * An active pen connected to the device using an internal port could use: "pen:internal:1"
     */
    String id;

    /**
     * The type of the device.
     */
    AccessoryType type;

    /**
     * Whether the accessory is persistent.
     * If true, the accessory should be listed even if disconnected.
     */
    boolean isPersistent;

    /**
     * A user friendly name for the accessory, either a specific name specified by the user or the
     * brand and model name of the device.
     */
    String displayName;

    /**
     * A display identifier for this accessory.
     * For example, a USB device might use the USB vendor and product ID as the identifier.
     * A Bluetooth device might use the Bluetooth MAC address as the identifier.
     * If there's no unique identifier for the device, this field can be left empty.
     */
    String displayId = "";

    /**
     * Whether the accessory can be enabled or disabled while connected.
     * If true, IAccessory.getEnabled and IAccessory.setEnabled should be implemented.
     */
    boolean supportsToggling;

    /**
     * Whether the accessory has a battery and supports querying battery information.
     * If true, IAccessory.getBatteryInfo should return a valid BatteryInfo object on a compatible
     * mode.
     */
    boolean supportsBatteryQuerying;
}
