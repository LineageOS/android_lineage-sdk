/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.input

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.ServiceManager
import android.util.Log
import java.io.PrintWriter
import vendor.lineage.input.AccessoryInfo
import vendor.lineage.input.BatteryInfo
import vendor.lineage.input.IAccessories
import vendor.lineage.input.IAccessoriesCallback
import vendor.lineage.input.IAccessory

class AccessoriesController(
    context: Context,
    handler: Handler
) : LineageInputFeature(context, handler) {
    // Accessories service
    private var service: IAccessories? = null

    // Accessories
    private val idToAccessories = mutableMapOf<String, Accessory>()

    // Accessories callback
    private val accessoriesCallback = object : IAccessoriesCallback.Stub() {
        override fun onAccessoryAdded(accessoryInfo: AccessoryInfo?, accessory: IAccessory?) {
            accessory?.let {
                addAccessory(it)
            }
        }

        override fun onAccessoryRemoved(accessoryInfo: AccessoryInfo?) {
            accessoryInfo?.let {
                removeAccessory(it.id)
            }
        }

        override fun onAccessoryInfoUpdated(accessoryInfo: AccessoryInfo?) {

        }

        override fun onAccessoryStatusUpdated(accessoryInfo: AccessoryInfo?, status: Byte) {
            TODO("Not yet implemented")
        }

        override fun onBatteryInfoUpdated(accessoryInfo: AccessoryInfo?,
            batteryInfo: BatteryInfo?) {
            TODO("Not yet implemented")
        }

        override fun getInterfaceVersion() = IAccessories::VERSION.get()

        override fun getInterfaceHash() = IAccessories::HASH.get()
    }

    override fun isSupported() = ServiceManager.isDeclared(INTERFACE_NAME)

    override fun onStart() {
        service = IAccessories.Stub.asInterface(
            ServiceManager.waitForDeclaredService(INTERFACE_NAME)
        )?.also {
            // Get the static accessories
            for (accessoryInterface in it.persistentAccessories) {
                addAccessory(accessoryInterface)
            }

            // Register the callback
            it.registerCallback(accessoriesCallback)
        }
    }

    override fun onSettingsChanged(uri: Uri?) {

    }

    override fun dump(pw: PrintWriter?) {
        pw?.apply {
            println()
            println("AccessoriesController configuration:")
            println("isSupported: ${isSupported()}")
        }
    }

    private fun addAccessory(accessory: IAccessory) {
        val accessoryId = accessory.accessoryInfo.id

        if (idToAccessories.containsKey(accessoryId)) {
            Log.w(LOG_TAG, "Adding already present accessory, assuming same interface")
            return
        }

        idToAccessories[accessoryId] = Accessory.from(accessory)

    }

    private fun removeAccessory(accessoryId: String) {
        idToAccessories.remove(accessoryId)
    }

    private fun removeAccessory(accessory: IAccessory) {
        val accessoryId = accessory.accessoryInfo.id

        return removeAccessory(accessoryId)
    }

    private fun onAccessoriesChanged() {

    }

    companion object {
        private const val LOG_TAG = "AccessoriesController"

        private val INTERFACE_NAME = "${IAccessories.DESCRIPTOR}/default"
    }
}
