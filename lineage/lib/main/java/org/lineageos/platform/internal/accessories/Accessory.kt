/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories

import java.lang.ref.WeakReference
import vendor.lineage.accessories.AccessoryInfo
import vendor.lineage.accessories.BatteryInfo
import vendor.lineage.accessories.IAccessoryCallback
import vendor.lineage.accessories.IAccessory

class Accessory private constructor(
    private val accessoryInterface: WeakReference<IAccessory>,
) {
    // Accessory callback
    private val accessoryCallback = object : IAccessoryCallback.Stub() {
        override fun onAccessoryInfoUpdated(accessoryInfo: AccessoryInfo?) {

        }

        override fun onAccessoryStatusUpdated(status: Byte) {

        }

        override fun onBatteryInfoUpdated(batteryInfo: BatteryInfo?) {

        }

        override fun getInterfaceVersion() = IAccessoryCallback.VERSION

        override fun getInterfaceHash() = IAccessoryCallback.HASH
    }

    private val accessoryCallbacks: WeakReference<lineageos.input.IAccessoryCallback>? = null

    init {
        accessoryInterface.get()?.let {

            it.setCallback(accessoryCallback)
        }
    }

    companion object {
        fun from(accessoryInterface: IAccessory) = Accessory(WeakReference(accessoryInterface))
    }
}
