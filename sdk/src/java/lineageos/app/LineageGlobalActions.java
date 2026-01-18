/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.app;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.List;

public class LineageGlobalActions {

    private Context mContext;

    private static ILineageGlobalActions sService;
    private static LineageGlobalActions sLineageGlobalActionsInstance;

    private static final String TAG = "LineageGlobalActions";

    private LineageGlobalActions(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }

        try {
            sService = getService();
        } catch (RemoteException e) {
            sService = null;
        }

        if (sService == null) {
            Log.wtf(TAG, "Unable to get LineageGlobalActionsService. The service either" +
                    " crashed, was not started, or the interface has been called to early in" +
                    " SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link lineageos.app.LineageGlobalActions}
     * @param context
     * @return {@link LineageGlobalActions}
     */
    public static LineageGlobalActions getInstance(Context context) {
        if (sLineageGlobalActionsInstance == null) {
            sLineageGlobalActionsInstance = new LineageGlobalActions(context);
        }
        return sLineageGlobalActionsInstance;
    }

    /** @hide */
    static public ILineageGlobalActions getService() throws RemoteException {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(
                LineageContextConstants.LINEAGE_GLOBAL_ACTIONS_SERVICE);
        sService = ILineageGlobalActions.Stub.asInterface(b);

        if (sService == null) {
            throw new RemoteException("Couldn't get " +
                    LineageContextConstants.LINEAGE_GLOBAL_ACTIONS_SERVICE +
                    " on binder");
        }
        return sService;
    }


    /**
     * Update the action to the state.
     * @param enabled a {@link Boolean} value
     * @param action a {@link String} value
     */
    public void updateUserConfig(boolean enabled, String action) {
        try {
            getService().updateUserConfig(enabled, action);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the user configuration as {@link List<String>}.
     * @return {@link List<String>}
     */
    public List<String> getLocalUserConfig() {
        try {
            return getService().getLocalUserConfig();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the user configuration as {@link String[]} in the same order as in the power menu.
     * Actions are separated with | delimiter.
     * @return {@link String[]}
     */
    public String[] getUserActionsArray() {
        try {
            return getService().getUserActionsArray();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Check if user configuration ({@link List<String>}) contains
     * preference ({@link String})
     * @param preference {@link String}
     * @return {@link boolean}
     */
    public boolean userConfigContains(String preference) {
        try {
            return getService().userConfigContains(preference);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
