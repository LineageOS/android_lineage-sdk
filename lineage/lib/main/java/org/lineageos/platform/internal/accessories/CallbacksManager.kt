/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories

import android.os.IBinder
import android.os.IInterface
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.WeakHashMap

/**
 * A manager for callback binder interfaces that are stored as weak references.
 * Will also automatically remove the callback when it dies.
 *
 * @param T The type of the callback
 */
class CallbacksManager<T : IInterface> {
    private val callbacks = ConcurrentHashMap(WeakHashMap<T, IBinder.DeathRecipient>())

    /**
     * Adds a callback to the manager.
     *
     * @param callback The callback to add
     */
    fun registerCallback(callback: T) {
        Log.i("CallbacksManager ${callback::class.qualifiedName}", "registerCallback: $callback")

        val deathRecipient = IBinder.DeathRecipient {
            Log.w("CallbacksManager ${callback::class.qualifiedName}", "DeathRecipient: $callback")
            unregisterCallback(callback)
        }
        callback.asBinder().linkToDeath(deathRecipient, 0)

        callbacks[callback] = deathRecipient
    }

    /**
     * Removes a callback from the manager.
     *
     * @param callback The callback to remove
     */
    fun unregisterCallback(callback: T) {
        val deathRecipient = callbacks.remove(callback)
        Log.i("CallbacksManager ${callback::class.qualifiedName}", "unregisterCallback: Was present: ${deathRecipient != null}")
        deathRecipient?.let {
            try {
                callback.asBinder().unlinkToDeath(it, 0)
            } catch (e: Exception) {
                Log.w("CallbacksManager ${callback::class.qualifiedName}", "Failed to unlink death recipient", e)
            }
        }
    }

    /**
     * Calls a block for each callback in the manager.
     *
     * @param block The block to call for each callback
     */
    fun forEachCallback(block: (T) -> Unit) {
        callbacks.keys.forEach { callback: T ->
            Log.i("CallbacksManager ${callback::class.qualifiedName}", "forEachCallback: $callback")
            block(callback)
        }
    }
}
