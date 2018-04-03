/**
 * Copyright (c) 2015, The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lineageos.trust;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import lineageos.app.LineageContextConstants;

public class TrustInterface {
    /**
     * Allows an application to use the Trust interface to display trusted
     * security messages to the user.
     * This is a system-only permission, user-installed apps cannot use it
     */
    public static final String TRUST_INTERFACE_PERMISSION = "lineageos.permission.TRUST_INTERFACE";

    /**
     * Unable to determine status, an error occured
     *
     * @see #getLevelForFeature
     */
    public static final int ERROR_UNDEFINED = -1;

    /**
     * Trust feature status: good. The feature is in a configuration
     * that will help protect the user's data
     *
     * @see #getLevelForFeature
     */
    public static final int TRUST_FEATURE_LEVEL_GOOD = 0;

    /**
     * Trust feature status: poor. The feature is in a configuration
     * that might be used to harm the user's data
     *
     * @see #getLevelForFeature
     */
    public static final int TRUST_FEATURE_LEVEL_POOR = 1;

    /**
     * Trust feature status: bad. The feature is in a configuration
     * that is dangerous for the user's data
     *
     * @see #getLevelForFeature
     */
    public static final int TRUST_FEATURE_LEVEL_BAD = 2;

    /**
     * Trust feature indicator: SELinux status
     *
     * Possible status:
     *    * {@link #TRUST_FEATURE_LEVEL_GOOD}: enforcing
     *    * {@link #TRUST_FEATURE_LEVEL_BAD}: permissive / disabled
     *
     * @see #getLevelForFeature
     */
    public static final int TRUST_FEATURE_SELINUX = 0;

    /**
     * Trust feature indicator: Root access
     *
     * Possible status:
     *    * {@link #TRUST_FEATURE_LEVEL_GOOD}: disabled
     *    * {@link #TRUST_FEATURE_LEVEL_POOR}: ADB only
     *    * {@link #TRUST_FEATURE_LEVEL_BAD}: apps and ADB
     *
     * @see #getLevelForFeature
     */
    public static final int TRUST_FEATURE_ROOT = 1;

    /**
     * Trust feature indicator: Platform Security patches
     *
     * Possible status:
     *    * {@link #TRUST_FEATURE_LEVEL_GOOD}: less than 3 months old
     *    * {@link #TRUST_FEATURE_LEVEL_POOR}: less than 9 months old
     *    * {@link #TRUST_FEATURE_LEVEL_BAD}: older than 9 months
     *
     * @see #getLevelForFeature
     */
    public static final int TRUST_FEATURE_PLATFORM_SECURITY_PATCH = 2;

    /**
     * Trust feature indicator: Vendor Security patches
     *
     * Possible status:
     *    * {@link #TRUST_FEATURE_LEVEL_GOOD}: less than 3 months old
     *    * {@link #TRUST_FEATURE_LEVEL_POOR}: less than 9 months old
     *    * {@link #TRUST_FEATURE_LEVEL_BAD}: older than 9 months
     *
     * @see #getLevelForFeature
     */
    public static final int TRUST_FEATURE_VENDOR_SECURITY_PATCH = 3;

    /**
     * Trust feature indicator: Encryption
     *
     * Some older devices have a significant performance loss when running
     * encrypted, so the status will be different when Trust is executed on
     *  these devices.
     *
     * Possible status for old device:
     *    * {@link #TRUST_FEATURE_LEVEL_GOOD}: enabled
     *    * {@link #TRUST_FEATURE_LEVEL_POOR}: disabled
     *
     * Possible status for recent device:
     *    * {@link #TRUST_FEATURE_LEVEL_GOOD}: enabled
     *    * {@link #TRUST_FEATURE_LEVEL_BAD}: disabled
     *
     * @see #getLevelForFeature
     */
    public static final int TRUST_FEATURE_ENCRYPTION = 4;

    private static final String TAG = "TrustInterface";

    private static ITrustInterface sService;
    private static TrustInterface sInstance;

    private Context mContext;

    private TrustInterface(Context context) {
        Context appContext = context.getApplicationContext();
        mContext = appContext == null ? context : appContext;
        sService = getService();
        if (context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.TRUST) && sService == null) {
            throw new RuntimeException("Unable to get TrustInterfaceService. The service" +
                    " either crashed, was not started, or the interface has been called to early" +
                    " in SystemServer init");
                }
    }

    /**
     * Get or create an instance of the {@link lineageos.trust.TrustInterface}
     *
     * @param context Used to get the service
     * @return {@link TrustInterface}
     */
    public static TrustInterface getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TrustInterface(context);
        }
        return sInstance;
    }

    /** @hide **/
    public static ITrustInterface getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_TRUST_INTERFACE);
        sService = ITrustInterface.Stub.asInterface(b);

        if (b == null) {
            Log.e(TAG, "null service. SAD!");
            return null;
        }

        sService = ITrustInterface.Stub.asInterface(b);
        return sService;
    }

    public boolean postNotificationForFeature(int feature) {
        if (sService == null) {
            return false;
        }
        try {
            return sService.postNotificationForFeature(feature);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return false;
    }

    public boolean removeNotificationForFeature(int feature) {
        if (sService == null) {
            return false;
        }
        try {
            return sService.removeNotificationForFeature(feature);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return false;
    }

    public int getLevelForFeature(int feature) {
        if (sService == null) {
            return ERROR_UNDEFINED;
        }
        try {
            return sService.getLevelForFeature(feature);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return ERROR_UNDEFINED;
    }
}

