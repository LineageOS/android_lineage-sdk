/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.internal.util;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;

import org.codeaurora.internal.IExtTelephony;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class TelephonyExtUtils {
    private static final boolean DEBUG = false;
    private static final String TAG = "TelephonyExtUtils";

    public static final String ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED =
            "org.codeaurora.intent.action.ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED";

    public static final String EXTRA_NEW_PROVISION_STATE = "newProvisionState";

    // This is the list of possible values that
    // IExtTelephony.getCurrentUiccCardProvisioningStatus() can return
    public static final int CARD_NOT_PRESENT = -2;
    public static final int INVALID_STATE = -1;
    public static final int NOT_PROVISIONED = 0;
    public static final int PROVISIONED = 1;

    private static boolean sNoServiceAvailable = false;
    private static IExtTelephony sExtTelephony;

    private static BroadcastReceiver sReceiver;

    private static final List<ProvisioningChangedListener> sListeners =
            new ArrayList<ProvisioningChangedListener>();

    // Get a single instance of this class
    private static final TelephonyExtUtils sInstance = new TelephonyExtUtils();

    private TelephonyExtUtils() {
        // This class is not supposed to be instantiated externally
        if (DEBUG) Log.d(TAG, "Contructor!");
        registerListeners(AppHolder.getContext());
    }

    /**
     * Determines whether the ITelephonyExt service is available on the device
     * Any result of the methods in this class are only valid if this method returns true
     *
     * @return true on success
     */
    public static boolean hasService() {
        return getService() != null;
    }

    /**
     * Determines whether the SIM associated with the given SubscriptionId is provisioned
     *
     * @return true if the SIM associated with the given subId is provisioned
     */
    public static boolean isSubProvisioned(int subId) {
        return isSlotProvisioned(SubscriptionManager.getSlotIndex(subId));
    }

    /**
     * Determines whether the given SIM is provisioned
     *
     * @return true if the SIM is provisioned
     */
    public static boolean isSlotProvisioned(int slotId) {
        return getCurrentUiccCardProvisioningStatus(slotId) == PROVISIONED;
    }

    /**
     * Get the current provisioning status for a given SIM slot
     *
     * @return The provisioning status from the extension or INVALID_STATE if not possible
     */
    public static int getCurrentUiccCardProvisioningStatus(int slotId) {
        int provisioningStatus;

        if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX || sExtTelephony == null) {
            return INVALID_STATE;
        }

        try {
            provisioningStatus = sExtTelephony.getCurrentUiccCardProvisioningStatus(slotId);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to get provisioning status for slotId: "
                    + slotId + "! Exception: " + ex);
            provisioningStatus = INVALID_STATE;
        }

        return provisioningStatus;
    }

    /**
     * Activate the SIM card with the given slotId
     *
     * @return The result of the activation or -1
     */
    public static int activateUiccCard(int slotId) {
        int result = -1;

        if (sExtTelephony == null) {
            return result;
        }

        try {
            result = sExtTelephony.activateUiccCard(slotId);
        } catch (RemoteException ex) {
            Log.e(TAG, "Activating sub failed " + result + " phoneId " + slotId);
        }

        return result;
    }

    /**
     * Deactivate the SIM card with the given slotId
     *
     * @return The result of the deactivation or -1
     */
    public static int deactivateUiccCard(int slotId) {
        int result = -1;

        if (sExtTelephony == null) {
            return result;
        }

        try {
            result = sExtTelephony.deactivateUiccCard(slotId);
        } catch (RemoteException ex) {
            Log.e(TAG, "Deactivating sub failed " + result + " phoneId " + slotId);
        }

        return result;
    }

    /**
     * Add a ProvisioningChangedListener to get notified in case of provisioning changes
     */
    public static void addListener(ProvisioningChangedListener listener) {
        if (listener != null) {
            sListeners.remove(listener);
            sListeners.add(listener);
        }
    }

    /**
     *  Remove a ProvisioningChangedListener to not get notified of provisioning changes anymore
     */
    public static void removeListener(ProvisioningChangedListener listener) {
        if (listener != null) {
            sListeners.remove(listener);
        }
    }

    /**
     * Notify all registered listeners about provisioning changes
     */
    private static void notifyListeners(int slotId, boolean provisioned) {
        for (ProvisioningChangedListener listener : sListeners) {
            if (listener != null) {
                listener.onProvisioningChanged(slotId, provisioned);
            }
        }
    }

    /**
     * Helper method to get an already instantiated service or instantiate it
     *
     * @return a valid service instance or null
     */
    private static IExtTelephony getService() {
        // We already tried to get the service but weren't successfull, so just return null here
        if (sNoServiceAvailable) {
            Log.v(TAG, "Already tried to get a service without success, returning!");
            return null;
        }

        if (DEBUG) Log.d(TAG, "Retrieving the service");

        // Instead of getting a new service instance, return an already existing one here
        if (sExtTelephony != null) {
            if (DEBUG) Log.d(TAG, "Returning cached service instance");
            return sExtTelephony;
        }

        synchronized(TelephonyExtUtils.class) {
            try {
                sExtTelephony = IExtTelephony.Stub.asInterface(
                        ServiceManager.getService("extphone"));
            } catch (NoClassDefFoundError ex) {
                // Ignore, device does not ship with telephony-ext
                Log.v(TAG, "Failed to get telephony extension service! Exception: " + ex);
            }
        }

        return sExtTelephony;
    }

    /**
     * Register a broadcast receiver to get informed about provision status changes
     */
    private static void registerListeners(Context context) {
        if (DEBUG) Log.d(TAG, "Registering listeners!");
        sReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                    if (DEBUG) Log.d(TAG, "Boot completed, registering service");
                    sNoServiceAvailable = (getService() == null);
                } else if (ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED.equals(action)) {
                    int slotId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                            SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                    boolean provisioned = intent.getIntExtra(EXTRA_NEW_PROVISION_STATE,
                            NOT_PROVISIONED) == PROVISIONED;
                    if (DEBUG) Log.d(TAG, "Received ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED"
                            + " on slotId: " + slotId + ", sub provisioned: " + provisioned);
                    notifyListeners(slotId, provisioned);
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED);
        intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(sReceiver, intentFilter);
    }

    /**
     * Get a context via reflection
     */
    private static class AppHolder {
        static Application sInstance;

        static {
            try {
                Class<?> activityThread = Class.forName("android.app.ActivityThread");
                sInstance = (Application) activityThread.getDeclaredMethod("currentApplication").
                        invoke(null);
            } catch (Throwable ex) {
                Log.e(TAG, "Failed to get an instance!");
            }
        }

        public static Context getContext() {
            return sInstance.getApplicationContext();
        }
    }

    /**
     * Interface definition so we can register callbacks to get the provisioning status whenever
     * it changes
     */
    public interface ProvisioningChangedListener {
        public void onProvisioningChanged(int slotId, boolean isProvisioned);
    }
}
