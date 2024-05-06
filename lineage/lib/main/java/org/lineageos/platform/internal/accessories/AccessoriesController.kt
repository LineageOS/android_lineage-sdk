/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.ServiceManager
import android.util.Log
import java.io.PrintWriter
import vendor.lineage.accessories.IAccessories
import vendor.lineage.accessories.IAccessoriesCallback
import vendor.lineage.accessories.IAccessory

class AccessoriesController(
    context: Context,
    handler: Handler
) : LineageAccessoriesFeature(context, handler) {
    // Accessories service
    private var service: IAccessories? = null

    // Accessories
    private val idToAccessories = mutableMapOf<String, Accessory>()

    // Accessories callback
    private val accessoriesCallback = object : IAccessoriesCallback.Stub() {
        override fun onAccessoryAdded(accessory: IAccessory?) {
            accessory?.let {
                addAccessory(it)
            }
        }

        override fun onAccessoryRemoved(accessory: IAccessory?) {
            accessory?.let {
                removeAccessory(it)
            }
        }

        override fun getInterfaceVersion() = IAccessories::VERSION.get()

        override fun getInterfaceHash() = IAccessories::HASH.get()
    }

    override fun isSupported() = ServiceManager.isDeclared(INTERFACE_NAME)

    override fun onStart() {
        service = IAccessories.Stub.asInterface(
            ServiceManager.waitForDeclaredService(INTERFACE_NAME)
        )?.also {
            // Register the callback, this will also populate accessories
            it.setCallback(accessoriesCallback)
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

        onAccessoriesChanged()
    }

    private fun removeAccessory(accessory: IAccessory) {
        val accessoryId = accessory.accessoryInfo.id

        if (!idToAccessories.containsKey(accessoryId)) {
            Log.w(LOG_TAG, "Removing non present accessory")
            return
        }

        idToAccessories.remove(accessoryId)

        onAccessoriesChanged()
    }

    private fun onAccessoriesChanged() {

    }

    companion object {
        private const val LOG_TAG = "AccessoriesController"

        private val INTERFACE_NAME = "${IAccessories.DESCRIPTOR}/default"
    }
}
