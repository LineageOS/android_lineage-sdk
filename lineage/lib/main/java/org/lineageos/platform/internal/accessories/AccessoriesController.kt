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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import lineageos.accessories.IAccessory
import lineageos.accessories.IAccessoriesCallback

class AccessoriesController(
    context: Context,
    handler: Handler
) : LineageAccessoriesFeature(context, handler) {
    // Accessories service
    private var service: vendor.lineage.accessories.IAccessories? = null

    // Accessories
    private val idToAccessories = ConcurrentHashMap(mutableMapOf<String, Accessory>())
    private val accessoriesLock = ReentrantLock()

    // Vendor accessories callback
    private val accessoriesCallback =
        object : vendor.lineage.accessories.IAccessoriesCallback.Stub() {
            override fun onAccessoryAdded(accessory: vendor.lineage.accessories.IAccessory?) {
                accessory?.let {
                    addAccessory(it)
                }
            }

            override fun onAccessoryRemoved(accessory: vendor.lineage.accessories.IAccessory?) {
                accessory?.let {
                    removeAccessory(it)
                }
            }

            override fun getInterfaceVersion() = VERSION

            override fun getInterfaceHash() = HASH
        }

    // Callbacks
    private val callbacks = CallbacksManager<IAccessoriesCallback>()

    override fun isSupported() = ServiceManager.isDeclared(INTERFACE_NAME)

    override fun onStart() {
        service = vendor.lineage.accessories.IAccessories.Stub.asInterface(
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
            println("Accessories:")
            idToAccessories.values.forEach {
                it.dump(this)
            }
        }
    }

    fun getAccessories(): Array<IAccessory> = idToAccessories.values.map {
        it.asInterface()
    }.toTypedArray()

    fun registerCallback(callback: IAccessoriesCallback) {
        callbacks.registerCallback(callback)

        // Pass to the callback the current accessories
        idToAccessories.values.forEach {
            callback.onAccessoryAdded(it.asInterface())
        }
    }

    fun unregisterCallback(callback: IAccessoriesCallback) {
        callbacks.unregisterCallback(callback)
    }

    private fun addAccessory(accessory: vendor.lineage.accessories.IAccessory) {
        try {
            accessoriesLock.lock()

            Log.i(LOG_TAG, "addAccessory $accessory")
            val accessoryId = accessory.accessoryInfo.id
            Log.i(LOG_TAG, "Accessory ID: $accessoryId")

            if (idToAccessories.containsKey(accessoryId)) {
                Log.w(LOG_TAG, "Adding already present accessory, assuming same interface")
                return
            }

            val accessory = Accessory.from(accessory)

            idToAccessories[accessoryId] = accessory

            Log.i(LOG_TAG, "Calling callbacks for $accessoryId")
            callbacks.forEachCallback {
                Log.i(LOG_TAG, "Calling callback $it")
                it.onAccessoryAdded(accessory.asInterface())
            }
        } finally {
            accessoriesLock.unlock()
        }
    }

    private fun removeAccessory(accessory: vendor.lineage.accessories.IAccessory) {
        try {
            accessoriesLock.lock()

            val accessoryId = accessory.accessoryInfo.id

            val accessory = idToAccessories[accessoryId] ?: run {
                Log.w(LOG_TAG, "Removing non present accessory")
                return
            }

            callbacks.forEachCallback {
                it.onAccessoryRemoved(accessory.asInterface())
            }

            idToAccessories.remove(accessoryId)
        } finally {
            accessoriesLock.unlock()
        }
    }

    companion object {
        private const val LOG_TAG = "AccessoriesController"

        private val INTERFACE_NAME = "${vendor.lineage.accessories.IAccessories.DESCRIPTOR}/default"
    }
}
