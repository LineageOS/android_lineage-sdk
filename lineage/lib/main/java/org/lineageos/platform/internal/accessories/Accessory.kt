/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories

import android.util.Log
import java.io.PrintWriter
import lineageos.accessories.AccessoryInfo
import lineageos.accessories.AccessoryStatus
import lineageos.accessories.BatteryInfo
import lineageos.accessories.IAccessory
import lineageos.accessories.IAccessoryCallback

class Accessory private constructor(
    private val accessoryInterface: vendor.lineage.accessories.IAccessory,
) : Dumpable {
    // Vendor data
    private lateinit var accessoryInfo: AccessoryInfo
    private var accessoryStatus: Byte = 0
    private var batteryInfo: BatteryInfo? = null

    // Callbacks
    private val callbacks = CallbacksManager<IAccessoryCallback>()

    // Vendor accessory callback
    private val accessoryCallback = object : vendor.lineage.accessories.IAccessoryCallback.Stub() {
        override fun onAccessoryInfoUpdated(
            accessoryInfo: vendor.lineage.accessories.AccessoryInfo?,
        ) {
            accessoryInfo?.toFwk()?.also {
                // Update the accessory info
                this@Accessory.accessoryInfo = it

                // Notify the callbacks
                callbacks.forEachCallback { callback ->
                    callback.onAccessoryInfoUpdated(it)
                }
            } ?: Log.wtf(LOG_TAG, "Received null AccessoryInfo, this shouldn't happen, ignoring")
        }

        override fun onAccessoryStatusUpdated(accessoryStatus: Byte) {
            accessoryStatusToFwk(accessoryStatus).let {
                // Update the accessory status
                this@Accessory.accessoryStatus = it

                // Notify the callbacks
                callbacks.forEachCallback { callback ->
                    callback.onAccessoryStatusUpdated(it)
                }
            }
        }

        override fun onBatteryInfoUpdated(batteryInfo: vendor.lineage.accessories.BatteryInfo?) {
            batteryInfo?.toFwk().let {
                // Update the battery info
                this@Accessory.batteryInfo = it

                // Notify the callbacks
                callbacks.forEachCallback { callback ->
                    callback.onBatteryInfoUpdated(it)
                }
            }
        }

        override fun getInterfaceVersion() = vendor.lineage.accessories.IAccessoryCallback.VERSION

        override fun getInterfaceHash() = vendor.lineage.accessories.IAccessoryCallback.HASH
    }

    private val service = object : IAccessory.Stub() {
        override fun getAccessoryInfo() = accessoryInterface.accessoryInfo.toFwk()

        override fun getAccessoryStatus() = accessoryStatusToFwk(accessoryInterface.accessoryStatus)

        override fun registerCallback(callback: IAccessoryCallback) {
            callbacks.registerCallback(callback)
        }

        override fun unregisterCallback(callback: IAccessoryCallback) {
            callbacks.unregisterCallback(callback)
        }

        override fun isEnabled() = accessoryInterface.isEnabled()

        override fun setEnabled(enabled: Boolean) = accessoryInterface.setEnabled(enabled)

        override fun getBatteryInfo() = accessoryInterface.batteryInfo.toFwk()
    }

    init {
        // Set the callback, this will also populate the accessory data during this call
        accessoryInterface.setCallback(accessoryCallback)
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

        // Pass the current accessory data to the new callback
        callback.onAccessoryInfoUpdated(accessoryInfo)
        callback.onAccessoryStatusUpdated(accessoryStatus)
        callback.onBatteryInfoUpdated(batteryInfo)
    }

    fun unregisterCallback(callback: IAccessoryCallback) {
        callbacks.unregisterCallback(callback)
    }

    fun asInterface() = service

    companion object {
        private const val LOG_TAG = "Accessory"

        fun from(accessoryInterface: vendor.lineage.accessories.IAccessory) = Accessory(
            accessoryInterface
        )

        private fun vendor.lineage.accessories.AccessoryInfo.toFwk() = AccessoryInfo().also {
            it.id = id
            it.type = type
            it.isPersistent = isPersistent
            it.displayName = displayName
            it.displayId = displayId
            it.supportsToggling = supportsToggling
            it.supportsBatteryQuerying = supportsBatteryQuerying
        }

        private fun accessoryStatusToFwk(accessoryStatus: Byte) = when (accessoryStatus) {
            vendor.lineage.accessories.AccessoryStatus.DISCONNECTED -> AccessoryStatus.DISCONNECTED
            vendor.lineage.accessories.AccessoryStatus.CHARGING -> AccessoryStatus.CHARGING
            vendor.lineage.accessories.AccessoryStatus.DISABLED -> AccessoryStatus.DISABLED
            vendor.lineage.accessories.AccessoryStatus.ENABLED -> AccessoryStatus.ENABLED
            else -> throw Exception("Unknown AccessoryStatus: $this")
        }

        private fun vendor.lineage.accessories.BatteryInfo.toFwk() = BatteryInfo().also {
            it.status = accessoryStatusToFwk(status)
            it.levelPercentage = levelPercentage
        }
    }
}
