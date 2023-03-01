/*
 * Copyright (C) 2023 The LineageOS Project
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

package lineageos.health;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import lineageos.app.LineageContextConstants;
import lineageos.trust.TrustInterface;

public class HealthInterface {
    private static final String TAG = "HealthInterface";
    private static IHealthInterface sService;
    private static HealthInterface sInstance;
    private Context mContext;

    private HealthInterface(Context context) {
        Context appContext = context.getApplicationContext();
        mContext = appContext == null ? context : appContext;
        sService = getService();

        if (context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.HEALTH) && sService == null) {
            throw new RuntimeException("Unable to get HealthInterfaceService. The service" +
                    " either crashed, was not started, or the interface has been called to early" +
                    " in SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link lineageos.health.HealthInterface}
     *
     * @param context Used to get the service
     * @return {@link HealthInterface}
     */
    public static HealthInterface getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new HealthInterface(context);
        }

        return sInstance;
    }

    /** @hide **/
    public static IHealthInterface getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_HEALTH_INTERFACE);
        sService = IHealthInterface.Stub.asInterface(b);

        if (sService == null) {
            Log.e(TAG, "null health service, SAD!");
            return null;
        }

        return sService;
    }

    public boolean isLineageHealthSupported() {
        if (sService == null) {
            return false;
        }
        try {
            return sService.isLineageHealthSupported();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }

        return false;
    }
}
