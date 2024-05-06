/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.input

import java.lang.ref.WeakReference
import vendor.lineage.input.AccessoryInfo
import vendor.lineage.input.BatteryInfo
import vendor.lineage.input.IAccessoryCallback
import vendor.lineage.input.IAccessory

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
