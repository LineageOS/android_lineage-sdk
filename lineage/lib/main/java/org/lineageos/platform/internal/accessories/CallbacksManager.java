/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of the registered callbacks, dropping them once their process dies.
 *
 * @param <T> The callback type
 */
public class CallbacksManager<T extends IInterface> {
    private static final String TAG = "CallbacksManager";

    public interface Block<T> {
        void run(T callback) throws RemoteException;
    }

    private final Map<T, IBinder.DeathRecipient> mCallbacks = new ConcurrentHashMap<>();

    /**
     * Adds a callback to the manager.
     *
     * @param callback The callback to add
     */
    public void registerCallback(T callback) {
        Log.i(TAG, "registerCallback: " + callback);

        IBinder.DeathRecipient deathRecipient = () -> {
            Log.w(TAG, "DeathRecipient: " + callback);
            unregisterCallback(callback);
        };

        try {
            callback.asBinder().linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            Log.w(TAG, "Callback already dead: " + callback);
            return;
        }

        mCallbacks.put(callback, deathRecipient);
    }

    /**
     * Removes a callback from the manager.
     *
     * @param callback The callback to remove
     */
    public void unregisterCallback(T callback) {
        IBinder.DeathRecipient deathRecipient = mCallbacks.remove(callback);
        Log.i(TAG, "unregisterCallback: Was present: " + (deathRecipient != null));
        if (deathRecipient != null) {
            try {
                callback.asBinder().unlinkToDeath(deathRecipient, 0);
            } catch (Exception e) {
                Log.w(TAG, "Failed to unlink death recipient", e);
            }
        }
    }

    /**
     * Calls a block for each callback in the manager.
     * A failing callback doesn't prevent the others from being called.
     *
     * @param block The block to call for each callback
     */
    public void forEachCallback(Block<T> block) {
        for (T callback : mCallbacks.keySet()) {
            try {
                block.run(callback);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to notify " + callback, e);
            }
        }
    }
}
