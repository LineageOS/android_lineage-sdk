/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.common;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * A manager for AIDL vendor interfaces.
 * Call {@link #start()} to start fetching the interface.
 *
 * @param <T> The interface type
 */
public class VendorInterfaceManager<T extends IInterface> {
    private static final String TAG = "VendorInterfaceManager";

    private final String mInterfaceName;
    private final Method mAsInterfaceMethod;
    private final Consumer<T> mOnBind;
    private final Runnable mOnDeath;

    private T mService;

    private final IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            Log.e(TAG, mInterfaceName + ": Service died");

            mService = null;

            mOnDeath.run();

            // Retry fetching the service
            fetchInterface();
        }
    };

    /**
     * @param interfaceName The name of the interface
     * @param stubClass T.Stub.class
     * @param onBind The callback when the interface is bound
     * @param onDeath The callback when the interface dies
     */
    public VendorInterfaceManager(String interfaceName, Class<?> stubClass, Consumer<T> onBind,
            Runnable onDeath) {
        mInterfaceName = interfaceName;
        try {
            mAsInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(stubClass + " is not an AIDL stub", e);
        }
        mOnBind = onBind;
        mOnDeath = onDeath;
    }

    /**
     * Attach to the interface.
     */
    public void start() {
        fetchInterface();
    }

    public boolean isDeclared() {
        return ServiceManager.isDeclared(mInterfaceName);
    }

    public boolean isAvailable() {
        return mService != null;
    }

    public T getInterface() {
        return mService;
    }

    /**
     * Fetches the interface from the service manager and update {@link #mService}.
     * It may still be null if the interface is not declared.
     */
    private void fetchInterface() {
        if (mService != null) {
            return;
        }

        if (!isDeclared()) {
            return;
        }

        Log.i(TAG, mInterfaceName + ": Connecting");

        T service = asInterface(ServiceManager.waitForDeclaredService(mInterfaceName));
        if (service == null) {
            return;
        }

        Log.i(TAG, mInterfaceName + ": Connected");

        try {
            service.asBinder().linkToDeath(mDeathRecipient, 0);
        } catch (RemoteException e) {
            Log.e(TAG, mInterfaceName + ": Died while connecting", e);
            return;
        }

        mService = service;

        mOnBind.accept(service);
    }

    @SuppressWarnings("unchecked")
    private T asInterface(IBinder binder) {
        try {
            return (T) mAsInterfaceMethod.invoke(null, binder);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to call asInterface", e);
        }
    }
}
