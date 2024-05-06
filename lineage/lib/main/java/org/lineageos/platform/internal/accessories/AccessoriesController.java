/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories;

import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lineageos.accessories.IAccessoriesCallback;
import lineageos.accessories.IAccessory;

import org.lineageos.platform.internal.common.VendorInterfaceManager;

public class AccessoriesController extends LineageAccessoriesFeature {
    private static final String TAG = "AccessoriesController";

    // Accessories service
    private final VendorInterfaceManager<vendor.lineage.accessories.IAccessories> mService;

    // Accessories
    private final Map<String, Accessory> mIdToAccessories = new ConcurrentHashMap<>();
    private final Object mAccessoriesLock = new Object();

    // Vendor accessories callback
    private final vendor.lineage.accessories.IAccessoriesCallback mAccessoriesCallback =
            new vendor.lineage.accessories.IAccessoriesCallback.Stub() {
        @Override
        public void onAccessoryAdded(vendor.lineage.accessories.IAccessory accessory) {
            if (accessory != null) {
                mHandler.post(() -> addAccessory(accessory));
            }
        }

        @Override
        public void onAccessoryRemoved(vendor.lineage.accessories.IAccessory accessory) {
            if (accessory != null) {
                mHandler.post(() -> removeAccessory(accessory));
            }
        }

        @Override
        public int getInterfaceVersion() {
            return VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return HASH;
        }
    };

    // Callbacks
    private final CallbacksManager<IAccessoriesCallback> mCallbacks = new CallbacksManager<>();

    // Bluetooth pairing
    private final AccessoryBluetoothPairer mBluetoothPairer;

    public AccessoriesController(Context context, Handler handler) {
        super(context, handler);

        mService = new VendorInterfaceManager<>(
                vendor.lineage.accessories.IAccessories.DESCRIPTOR + "/default",
                vendor.lineage.accessories.IAccessories.Stub.class,
                service -> {
                    // Register the callback, this will also populate accessories
                    try {
                        service.setCallback(mAccessoriesCallback);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Failed to set the accessories callback", e);
                    }
                },
                this::removeAllAccessories);

        mBluetoothPairer = new AccessoryBluetoothPairer(context, handler);
    }

    @Override
    public boolean isSupported() {
        return mService.isDeclared();
    }

    @Override
    public void onStart() {
        mBluetoothPairer.start();
        mService.start();
    }

    @Override
    protected void onSettingsChanged(Uri uri) {
        // Do nothing
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("AccessoriesController configuration:");
        pw.println("isSupported: " + isSupported());
        pw.println("Accessories:");
        for (Accessory accessory : mIdToAccessories.values()) {
            accessory.dump(pw);
        }
        mBluetoothPairer.dump(pw);
    }

    public IAccessory[] getAccessories() {
        return mIdToAccessories.values().stream()
                .map(Accessory::asInterface)
                .toArray(IAccessory[]::new);
    }

    public void registerCallback(IAccessoriesCallback callback) {
        mCallbacks.registerCallback(callback);

        // Pass to the callback the current accessories
        for (Accessory accessory : mIdToAccessories.values()) {
            try {
                callback.onAccessoryAdded(accessory.asInterface());
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to notify " + callback, e);
            }
        }
    }

    public void unregisterCallback(IAccessoriesCallback callback) {
        mCallbacks.unregisterCallback(callback);
    }

    private void addAccessory(vendor.lineage.accessories.IAccessory accessoryInterface) {
        synchronized (mAccessoriesLock) {
            Binder.allowBlocking(accessoryInterface.asBinder());

            String accessoryId;
            Accessory accessory;
            try {
                accessoryId = accessoryInterface.getAccessoryInfo().id;

                if (mIdToAccessories.containsKey(accessoryId)) {
                    Log.w(TAG, "Adding already present accessory, assuming same interface");
                    return;
                }

                accessory = Accessory.from(accessoryInterface,
                        updated -> mHandler.post(() -> onAccessoryUpdated(accessoryId, updated)));
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to add accessory", e);
                return;
            }

            mIdToAccessories.put(accessoryId, accessory);

            mCallbacks.forEachCallback(callback ->
                    callback.onAccessoryAdded(accessory.asInterface()));
        }
    }

    private void removeAccessory(vendor.lineage.accessories.IAccessory accessoryInterface) {
        synchronized (mAccessoriesLock) {
            Binder.allowBlocking(accessoryInterface.asBinder());

            String accessoryId;
            try {
                accessoryId = accessoryInterface.getAccessoryInfo().id;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to remove accessory", e);
                return;
            }

            Accessory accessory = mIdToAccessories.get(accessoryId);
            if (accessory == null) {
                Log.w(TAG, "Removing non present accessory");
                return;
            }

            mCallbacks.forEachCallback(callback ->
                    callback.onAccessoryRemoved(accessory.asInterface()));

            mIdToAccessories.remove(accessoryId);

            mHandler.post(() -> mBluetoothPairer.onAccessoryRemoved(accessoryId));
        }
    }

    private void removeAllAccessories() {
        synchronized (mAccessoriesLock) {
            for (Accessory accessory : mIdToAccessories.values()) {
                mCallbacks.forEachCallback(callback ->
                        callback.onAccessoryRemoved(accessory.asInterface()));
            }

            mIdToAccessories.clear();

            mHandler.post(mBluetoothPairer::onAllAccessoriesRemoved);
        }
    }

    // Called on the handler thread
    private void onAccessoryUpdated(String accessoryId, Accessory accessory) {
        // Ignore late updates from removed accessories
        if (mIdToAccessories.get(accessoryId) != accessory) {
            return;
        }

        mBluetoothPairer.onAccessoryUpdated(accessoryId, accessory.getBluetoothAddress(),
                accessory.isCharging(), accessory.isEnabled());
    }
}
