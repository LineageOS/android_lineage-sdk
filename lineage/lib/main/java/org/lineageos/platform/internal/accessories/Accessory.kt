/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories

import android.util.Log
import java.io.PrintWriter
import java.lang.ref.WeakReference
import lineageos.accessories.AccessoryInfo
import lineageos.accessories.AccessoryStatus
import lineageos.accessories.BatteryInfo
import lineageos.accessories.IAccessory
import lineageos.accessories.IAccessoryCallback

class Accessory private constructor(
    private val accessoryInterface: WeakReference<vendor.lineage.accessories.IAccessory>,
) : Dumpable {
    // Vendor data
    private lateinit var accessoryInfo: AccessoryInfo
    private lateinit var accessoryStatus: AccessoryStatus
    private var batteryInfo: BatteryInfo? = null

    // Callbacks
    private val callbacks = object : CallbacksManager<IAccessoryCallback>() {
        override fun registerCallback(callback: IAccessoryCallback) {
            super.registerCallback(callback)

            // Pass the current accessory data to the new callback
            callback.onAccessoryInfoUpdated(accessoryInfo)
            callback.onAccessoryStatusUpdated(accessoryStatus)
            callback.onBatteryInfoUpdated(batteryInfo)
        }
    }

    // Vendor accessory callback
    private val accessoryCallback = object : vendor.lineage.accessories.IAccessoryCallback.Stub() {
        override fun onAccessoryInfoUpdated(
            accessoryInfo: vendor.lineage.accessories.AccessoryInfo?,
        ) {
            accessoryInfo?.toFwk()?.also {
                // Update the accessory info
                this.accessoryInfo = it

                // Notify the callbacks
                forEachCallback { callback ->
                    callback.onAccessoryInfoUpdated(it)
                }
            } ?: Log.wtf(LOG_TAG, "Received null AccessoryInfo, this shouldn't happen, ignoring")
        }

        override fun onAccessoryStatusUpdated(accessoryStatus: vendor.lineage.accessories.AccessoryStatus) {
            accessoryStatus.toFwk().let {
                // Update the accessory status
                this.accessoryStatus = it

                // Notify the callbacks
                forEachCallback { callback ->
                    callback.onAccessoryStatusUpdated(it)
                }
            }
        }

        override fun onBatteryInfoUpdated(batteryInfo: vendor.lineage.accessories.BatteryInfo?) {
            it.toFwk().let {
                // Update the battery info
                this.batteryInfo = it

                // Notify the callbacks
                forEachCallback { callback ->
                    callback.onBatteryInfoUpdated(it)
                }
            }
        }

        override fun getInterfaceVersion() = vendor.lineage.accessories.IAccessoryCallback.VERSION

        override fun getInterfaceHash() = vendor.lineage.accessories.IAccessoryCallback.HASH
    }

    private val service = object : IAccessory.Stub() {
        override fun getAccessoryInfo() = accessoryInfo.toFwk()
    }

    init {
        accessoryInterface.get()?.let {
            // Set the callback, this will also populate the accessory data during this call
            it.setCallback(accessoryCallback)
        }
    }

    override fun dump(pw: PrintWriter) {
        pw.println("Accessory ${accessoryInfo.id}:")
        pw.println("  Info: $accessoryInfo")
        pw.println("  Status: $accessoryStatus")
        batteryInfo?.let {
            pw.println("  Battery: $it")
        }
    }

    fun registerCallback(callback: IAccessoryCallback) {
        callbacks.registerCallback(callback)
    }

    fun unregisterCallback(callback: IAccessoryCallback) {
        callbacks.unregisterCallback(callback)
    }

    companion object {
        private const val LOG_TAG = "Accessory"

        fun from(accessoryInterface: vendor.lineage.accessories.IAccessory) = Accessory(
            WeakReference(accessoryInterface),
        )

        private fun vendor.lineage.accessories.AccessoryInfo.toFwk() = AccessoryInfo(
            id,
            type,
            isPersistent,
            displayName,
            displayId,
            supportsToggling,
            supportsBatteryQuerying,
        )

        private fun vendor.lineage.accessories.AccessoryStatus.toFwk() = when (this) {
            vendor.lineage.accessories.AccessoryStatus.DISCONNECTED -> AccessoryStatus.DISCONNECTED
            vendor.lineage.accessories.AccessoryStatus.CHARGING -> AccessoryStatus.CHARGING
            vendor.lineage.accessories.AccessoryStatus.DISABLED -> AccessoryStatus.DISABLED
            vendor.lineage.accessories.AccessoryStatus.ENABLED -> AccessoryStatus.ENABLED
            else -> throw Exception("Unknown AccessoryStatus: $this")
        }

        private fun vendor.lineage.accessories.BatteryInfo.toFwk() = BatteryInfo(
            status.toFwk(),
            level,
        )
    }
}
