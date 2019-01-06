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

    private boolean mNoServiceAvailable = false;
    private IExtTelephony mExtTelephony;

    private static TelephonyExtUtils sInstance;

    private final List<ProvisioningChangedListener> mListeners = new ArrayList<>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                if (DEBUG) Log.d(TAG, "Boot completed, registering service");
                mNoServiceAvailable = getService() == null;
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

    // This class is not supposed to be instantiated externally
    private TelephonyExtUtils(Context context) {
        if (DEBUG) Log.d(TAG, "Registering listeners!");

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED);
        intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(mReceiver, intentFilter);
    }

    /**
     * Get an existing instance of the utils or create a new one if not existant
     *
     * @return An instance of this class
     */
    public static TelephonyExtUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TelephonyExtUtils(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Determines whether the ITelephonyExt service is available on the device
     * Any result of the methods in this class are only valid if this method returns true
     *
     * @return true on success
     */
    public boolean hasService() {
        return getService() != null;
    }

    /**
     * Determines whether the SIM associated with the given SubscriptionId is provisioned
     *
     * @return true if the SIM associated with the given subId is provisioned
     */
    public boolean isSubProvisioned(int subId) {
        return isSlotProvisioned(SubscriptionManager.getSlotIndex(subId));
    }

    /**
     * Determines whether the given SIM is provisioned
     *
     * @return true if the SIM is provisioned
     */
    public boolean isSlotProvisioned(int slotId) {
        return getCurrentUiccCardProvisioningStatus(slotId) == PROVISIONED;
    }

    /**
     * Get the current provisioning status for a given SIM slot
     *
     * @return The provisioning status from the extension or INVALID_STATE if not possible
     */
    public int getCurrentUiccCardProvisioningStatus(int slotId) {
        IExtTelephony service = getService();
        if (service != null && slotId != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            try {
                return mExtTelephony.getCurrentUiccCardProvisioningStatus(slotId);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to get provisioning status for slotId: " + slotId, ex);
            }
        }
        return INVALID_STATE;
    }

    /**
     * Activate the SIM card with the given slotId
     *
     * @return The result of the activation or -1
     */
    public int activateUiccCard(int slotId) {
        IExtTelephony service = getService();
        if (service != null) {
            try {
                return mExtTelephony.activateUiccCard(slotId);
            } catch (RemoteException ex) {
                Log.e(TAG, "Activating sub failed for slotId: " + slotId);
            }
        }
        return -1;
    }

    /**
     * Deactivate the SIM card with the given slotId
     *
     * @return The result of the deactivation or -1
     */
    public int deactivateUiccCard(int slotId) {
        IExtTelephony service = getService();
        if (service != null) {
            try {
                return mExtTelephony.deactivateUiccCard(slotId);
            } catch (RemoteException ex) {
                Log.e(TAG, "Deactivating sub failed for slotId: " + slotId);
            }
        }
        return -1;
    }

    /**
     * Add a ProvisioningChangedListener to get notified in case of provisioning changes
     */
    public void addListener(ProvisioningChangedListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
            mListeners.add(listener);
        }
    }

    /**
     * Remove a ProvisioningChangedListener to not get notified of provisioning changes anymore
     */
    public void removeListener(ProvisioningChangedListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /**
     * Notify all registered listeners about provisioning changes
     */
    private void notifyListeners(int slotId, boolean provisioned) {
        for (ProvisioningChangedListener listener : mListeners) {
            listener.onProvisioningChanged(slotId, provisioned);
        }
    }

    /**
     * Helper method to get an already instantiated service or instantiate it
     *
     * @return a valid service instance or null
     */
    private IExtTelephony getService() {
        // We already tried to get the service but weren't successful, so just return null here
        if (mNoServiceAvailable) {
            if (DEBUG) Log.v(TAG, "Already tried to get a service without success, returning!");
            return null;
        }

        if (DEBUG) Log.d(TAG, "Retrieving the service");

        // Instead of getting a new service instance, return an already existing one here
        if (mExtTelephony != null) {
            if (DEBUG) Log.d(TAG, "Returning cached service instance");
            return mExtTelephony;
        }

        synchronized(TelephonyExtUtils.class) {
            try {
                mExtTelephony =
                        IExtTelephony.Stub.asInterface(ServiceManager.getService("extphone"));
            } catch (NoClassDefFoundError ex) {
                // Ignore, device does not ship with telephony-ext
                Log.d(TAG, "Failed to get telephony extension service!");
                mNoServiceAvailable = true;
            }
        }

        return mExtTelephony;
    }

    /**
     * Interface definition so we can register callbacks to get the provisioning status
     *  whenever it changes
     */
    public interface ProvisioningChangedListener {
        public void onProvisioningChanged(int slotId, boolean isProvisioned);
    }
}
