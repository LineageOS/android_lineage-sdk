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
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;

import org.codeaurora.internal.IExtTelephony;

import java.util.ArrayList;
import java.util.List;

public final class TelephonyExtUtils {
    private static final String TAG = "TelephonyExtUtils";

    public static final String ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED =
            "org.codeaurora.intent.action.ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED";

    public static final String EXTRA_NEW_PROVISION_STATE = "newProvisionState";

    // These are the list of possible values that
    // IExtTelephony.getCurrentUiccCardProvisioningStatus() can return
    public static final int NOT_PROVISIONED = 0;
    public static final int PROVISIONED = 1;
    public static final int INVALID_STATE = -1;
    public static final int CARD_NOT_PRESENT = -2;

    private static boolean mNoServiceAvailable;
    private static IExtTelephony mExtTelephony;

    private static BroadcastReceiver mReceiver;

    private static List<ProvisioningChangedListener> mListeners =
            new ArrayList<ProvisioningChangedListener>();

    private TelephonyExtUtils() {
        // This class is not supposed to be instantiated
    }

    /**
     * Determines whether the ITelephonyExt service is available on the device
     * Any result of the methods in this class are only valid if this method returns true
     *
     * @return true on success
     */
    public static boolean hasService(Context context) {
        return getService(context) != null;
    }

    /**
     * Determines whether the SIM associated with the given SubscriptionId is provisioned
     *
     * @return true if the SIM associated with the given subId is provisioned
     */
    public static boolean isSubProvisioned(int subId, Context context) {
        return isSlotProvisioned(getSimSlotIndexForSubId(subId, context));
    }

    /**
     * Determines whether the given is provisioned
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

        if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            return INVALID_STATE;
        }

        try {
            provisioningStatus = mExtTelephony.getCurrentUiccCardProvisioningStatus(slotId);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to get provisioning status, slotId: "+ slotId +" Exception: " + ex);
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

        try {
            result = mExtTelephony.activateUiccCard(slotId);
        } catch (RemoteException ex) {
            loge("Activate sub failed " + result + " phoneId " + slotId);
            // Ignore
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

        try {
            result = mExtTelephony.deactivateUiccCard(slotId);
        } catch (RemoteException ex) {
            loge("Deactivate sub failed " + result + " phoneId " + slotId);
            // Ignore
        }

        return result;
    }

    /**
     * Add a ProvisioningChangedListener to get notified in case of provisioning changes
     */
    public static void addListener(ProvisioningChangedListener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    /**
     *  Remove a ProvisioningChangedListener to not get notified of provisioning changes anymore
     */
    public static void removeListener(ProvisioningChangedListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /**
     * Notify all registered listeners about provisioning changes
     */
    private static void notifyListeners(int slotId, boolean provisioned) {
        for (ProvisioningChangedListener listener : mListeners) {
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
    private static IExtTelephony getService(Context context) {
        // We already tried to get the service but weren't successfull, so just return null here
        if (mNoServiceAvailable) {
            Log.w("Already tried to get a service without success, returning!");
            return null;
        }

        // Instead of getting a new service instance, return an already existing one here
        if (mExtTelephony != null) {
            return mExtTelephony;
        }

        try {
            mExtTelephony = IExtTelephony.Stub.asInterface(ServiceManager.getService("extphone"));
            registerListener(context);
        } catch (NoClassDefFoundError ex) {
            // ignore, device does not compile telephony-ext.
            Log.e("Failed to get telephony extension service! Exception: " + ex);
        }

        mNoServiceAvailable = (mExtTelephony == null);
        return mExtTelephony;
    }

    /**
     * Register a broadcast receiver to get informed about provision status changes
     */
    private static void registerListener(Context context) {
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED.equals(action)) {
                    int slotId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                            SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                    boolean provisioned = intent.getIntExtra(EXTRA_NEW_PROVISION_STATE,
                            NOT_PROVISIONED) == PROVISIONED;
                    notifyListeners(slotId, provisioned);
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter(ACTION_UICC_MANUAL_PROVISION_STATUS_CHANGED);
        context.registerReceiver(mReceiver, intentFilter);
    }

    /**
     * Helper method to find the SIM slot associated with a given SubscriptionId
     *
     * @return The SIM slot found for the subId or INVALID_SIM_SLOT_INDEX
     */
    private static int getSimSlotIndexForSubId(int subId, Context context) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        }

        SubscriptionManager subManager = SubscriptionManager.from(context);
        List<SubscriptionInfo> subInfoLists = subManager.getActiveSubscriptionInfoList();
        if (subInfoLists != null) {
            for (SubscriptionInfo subInfo : subInfoLists) {
                if (subInfo.getSubscriptionId() == subId) {
                    return subInfo.getSimSlotIndex();
                }
            }
        }
        return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    /**
     * Interface definition so we can register callbacks to get the provisioning status when
     * it changes
     */
    public interface ProvisioningChangedListener {
        public void onProvisioningChanged(int slotId, boolean isProvisioned);
    }
}
