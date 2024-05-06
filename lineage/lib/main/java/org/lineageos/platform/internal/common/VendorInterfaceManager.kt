/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.common

import android.os.IBinder
import android.os.IInterface
import android.os.ServiceManager
import android.util.Log

/**
 * A manager for AIDL vendor interfaces.
 * Call [start] to start fetching the interface.
 *
 * @param interfaceName The name of the interface
 * @param stubClass [T.Stub::class.java]
 * @param onBind The callback when the interface is bound
 * @param onDeath The callback when the interface dies
 */
class VendorInterfaceManager<T : IInterface>(
    private val interfaceName: String,
    private val stubClass: Class<*>,
    private val onBind: (T) -> Unit = {},
    private val onDeath: () -> Unit = {},
) {
    private val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)

    private var service: T? = null

    private val deathRecipient = object : IBinder.DeathRecipient {
        override fun binderDied() {
            Log.e(LOG_TAG, "$interfaceName: Service died")

            service = null

            onDeath()

            // Retry fetching the service
            fetchInterface()
        }
    }

    /**
     * Attach to the interface.
     */
    fun start() {
        fetchInterface()
    }

    fun isDeclared() = ServiceManager.isDeclared(interfaceName)

    fun isAvailable() = service != null

    fun getInterface() = service

    /**
     * Fetches the interface from the service manager and update [service].
     * It may still be null if the interface is not declared.
     */
    private fun fetchInterface() {
        service?.let {
            return
        }

        if (!isDeclared()) {
            return
        }

        Log.i(LOG_TAG, "$interfaceName: Connecting")

        service = asInterface(
            ServiceManager.waitForDeclaredService(interfaceName)
        )?.also {
            Log.i(LOG_TAG, "$interfaceName: Connected")

            it.asBinder().linkToDeath(deathRecipient, 0)

            onBind(it)
        }
    }

    private fun asInterface(binder: IBinder?) = asInterfaceMethod.invoke(null, binder) as T?

    companion object {
        private const val LOG_TAG = "VendorInterfaceManager"
    }
}
