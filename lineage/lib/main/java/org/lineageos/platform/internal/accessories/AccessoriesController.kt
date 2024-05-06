/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import lineageos.accessories.IAccessory
import lineageos.accessories.IAccessoriesCallback
import org.lineageos.platform.internal.common.VendorInterfaceManager

class AccessoriesController(
    context: Context,
    handler: Handler
) : LineageAccessoriesFeature(context, handler) {
    // Accessories service
    private val service = VendorInterfaceManager<vendor.lineage.accessories.IAccessories>(
        "${vendor.lineage.accessories.IAccessories.DESCRIPTOR}/default",
        vendor.lineage.accessories.IAccessories.Stub::class.java,
        onBind = { service ->
            // Register the callback, this will also populate accessories
            service.setCallback(accessoriesCallback)
        },
        onDeath = {
            removeAllAccessories()
        },
    )

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

    override fun isSupported() = service.isDeclared()

    override fun onStart() {
        service.start()
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

    private fun addAccessory(accessoryInterface: vendor.lineage.accessories.IAccessory) {
        try {
            accessoriesLock.lock()

            val accessoryId = accessoryInterface.accessoryInfo.id

            if (idToAccessories.containsKey(accessoryId)) {
                Log.w(LOG_TAG, "Adding already present accessory, assuming same interface")
                return
            }

            val accessory = Accessory.from(accessoryInterface)

            idToAccessories[accessoryId] = accessory

            callbacks.forEachCallback {
                it.onAccessoryAdded(accessory.asInterface())
            }
        } finally {
            accessoriesLock.unlock()
        }
    }

    private fun removeAccessory(accessoryInterface: vendor.lineage.accessories.IAccessory) {
        try {
            accessoriesLock.lock()

            val accessoryId = accessoryInterface.accessoryInfo.id

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

    private fun removeAllAccessories() {
        try {
            accessoriesLock.lock()

            for (accessory in idToAccessories.values) {
                callbacks.forEachCallback {
                    it.onAccessoryRemoved(accessory.asInterface())
                }
            }

            idToAccessories.clear()
        } finally {
            accessoriesLock.unlock()
        }
    }

    companion object {
        private const val LOG_TAG = "AccessoriesController"
    }
}
