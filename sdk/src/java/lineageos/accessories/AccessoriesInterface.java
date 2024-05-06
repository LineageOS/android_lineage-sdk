/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import lineageos.app.LineageContextConstants;

public class AccessoriesInterface {
    private static final String TAG = "AccessoriesInterface";

    private static IAccessoriesInterface sService;
    private static AccessoriesInterface sInstance;

    private final Context mContext;

    private AccessoriesInterface(Context context) {
        Context appContext = context.getApplicationContext();
        mContext = appContext == null ? context : appContext;
        sService = getService();

        if (context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.ACCESSORIES) && sService == null) {
            throw new RuntimeException("Unable to get AccessoriesInterfaceService. The service" +
                    " either crashed, was not started, or the interface has been called too early" +
                    " in SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link lineageos.accessories.AccessoriesInterface}
     *
     * @param context Used to get the service
     * @return {@link AccessoriesInterface}
     */
    public static synchronized AccessoriesInterface getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AccessoriesInterface(context);
        }

        return sInstance;
    }

    /** @hide **/
    public static IAccessoriesInterface getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(
                LineageContextConstants.LINEAGE_ACCESSORIES_INTERFACE);
        sService = IAccessoriesInterface.Stub.asInterface(b);

        if (sService == null) {
            Log.e(TAG, "null accessories service, SAD!");
            return null;
        }

        return sService;
    }

    /**
     * @return true if service is valid
     */
    public boolean checkService() {
        if (sService == null) {
            Log.w(TAG, "not connected to AccessoriesInterfaceService");
            return false;
        }

        return true;
    }

    /**
     * @return The currently known accessories
     */
    public IAccessory[] getAccessories() {
        if (!checkService()) {
            return new IAccessory[0];
        }

        try {
            return sService.getAccessories();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }

        return new IAccessory[0];
    }

    /**
     * Register a callback to be notified when accessories are added or removed.
     *
     * @param callback The callback
     */
    public void registerCallback(IAccessoriesCallback callback) {
        if (!checkService()) {
            return;
        }

        try {
            sService.registerCallback(callback);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /**
     * Unregister a previously registered callback.
     *
     * @param callback The callback
     */
    public void unregisterCallback(IAccessoriesCallback callback) {
        if (!checkService()) {
            return;
        }

        try {
            sService.unregisterCallback(callback);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }
}
