/*
 * Copyright (c) 2019, The LineageOS Project
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

package lineageos.health;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import lineageos.app.LineageContextConstants;
import lineageos.health.MedicalProfile;
import lineageos.health.Record;

import java.util.ArrayList;
import java.util.List;

/**
 * HealthStore provides access to the Records and MedicalInfo
 * of the user.
 */
public class HealthStore {

    /**
     * Allows an application to read health data.
     * This is a dangerous permission, your app must request
     * it at runtime as any other dangerous permission
     */
    public static final String HEALTH_STORE_READ_PERMISSION =
            "lineageos.permission.READ_HEALTH_STORE";

    /**
     * Allows an application to write health data.
     * This is a dangerous permission, your app must request
     * it at runtime as any other dangerous permission
     */
    public static final String HEALTH_STORE_WRITE_PERMISSION =
            "lineageos.permission.WRITE_HEALTH_STORE";

    /**
     * Allows an application to manage health data.
     * This is permission is for system / OEM apps only
     */
    public static final String HEALTH_STORE_MANAGE_PERMISSION =
            "lineageos.permission.MANAGE_HEALTH_STORE";

    private static final String TAG = "HealthStore";

    private static IHealthStore sService;
    private static HealthStore sInstance;

    private final Context mContext;

    private HealthStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }
        sService = getService();
        if (context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.HEALTH) && sService == null) {
            throw new RuntimeException("Unable to get HealthStore. The service" +
                    " either crashed, was not started, or the interface has been called to early" +
                    " in SystemServer init");
            }
    }

    /**
     * Get or create an instance of the {@link lineageos.health.HealthStore}
     *
     * @param context Used to get the service
     * @return {@link HealthStore}
     */
    @NonNull
    public static HealthStore getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new HealthStore(context);
        }
        return sInstance;
    }

    /** @hide **/
    public static IHealthStore getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_HEALTH_STORE);
        sService = IHealthStore.Stub.asInterface(b);

        if (b != null) {
            sService = IHealthStore.Stub.asInterface(b);
            return sService;
        } else {
            Log.e(TAG, "null service. SAD!");
            return null;
        }
    }

    /**
     * Write a {@link lineageos.health.Record}
     *
     * You will need {@link #HEALTH_STORE_WRITE_PERMISSION}
     * to utilize this functionality.
     *
     * @param record The record to write
     * @return Whether the process failed
     */
    public boolean writeRecord(@NonNull Record record) {
        if (sService == null) return false;
        try {
            return sService.writeRecord(record);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return false;
    }

    /**
     * Delete a {@link lineageos.health.Record}
     *
     * You will need {@link #HEALTH_STORE_WRITE_PERMISSION}
     * to utilize this functionality.
     *
     * @param record The record to delete
     * @return Whether the process failed
     */
    public boolean deleteRecord(@NonNull Record record) {
        if (sService == null) return false;
        try {
            return sService.deleteRecord(record);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return false;
    }


    /**
     * Get all the records of a specific category
     *
     * You will need {@link #HEALTH_STORE_MANAGE_PERMISSION}
     * to utilize this functionality.
     *
     * May be null.
     *
     * @param uid the required records category
     * @return The record for given uid, or null if not found
     */
    @Nullable
    public Record getRecord(long uid) {
        if (sService == null) return null;
        try {
            return sService.getRecord(uid);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return null;
    }

    /**
     * Get all the records of a specific category
     *
     * You will need {@link #HEALTH_STORE_READ_PERMISSION}
     * to utilize this functionality.
     *
     * The system may refuse to provide any data and returned
     * an empty list of records.
     *
     * @param category the required records category
     * @return A list of records for that given
     */
    @NonNull
    public List<Record> getRecords(int category) {
        if (sService == null) return new ArrayList<>();
        try {
            return sService.getRecords(category);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return new ArrayList<>();
    }

    /**
     * Get all the records of a specific category in a given time frame
     *
     * You will need {@link #HEALTH_STORE_READ_PERMISSION}
     * to utilize this functionality.
     *
     * The system may refuse to provide any data and returned
     * an empty list of records.
     *
     * @param category the required records category
     * @param start the min timestamp in millisec
     * @param end the max timestamp in millisec
     * @return A list of records for that given
     */
    @NonNull
    public List<Record> getRecordsInTimeFrame(int category,
            long start, long end) {
        if (sService == null) return new ArrayList<>();
        try {
            return sService.getRecordsInTimeFrame(category, start, end);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return new ArrayList<>();
    }

    /**
     * Write the user's {@link lineageos.health.MedicalProfile}
     *
     * You will need {@link #HEALTH_STORE_WRITE_PERMISSION}
     * to utilize this functionality.
     *
     * @param info The human information object to write
     */
    public void setMedicalProfile(@NonNull MedicalProfile profile) {
        if (sService == null) return;
        try {
            sService.setMedicalProfile(profile);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /**
     * Read the user's {@link lineageos.health.MedicalProfile}
     *
     * You will need {@link #HEALTH_STORE_READ_PERMISSION}
     * to utilize this functionality.
     *
     * @return Information about the user
     */
    @NonNull
    public MedicalProfile getMedicalProfile() {
        if (sService == null) return new MedicalProfile();
        try {
            return sService.getMedicalProfile();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return new MedicalProfile();
    }

    /**
     * Delete all the records of a given category from the HealthStore.
     *
     * You will need {@link #HEALTH_STORE_MANAGE_PERMISSION}
     * to utilize this functionality.
     */
    public boolean deleteAllInCategory(int category) {
        if (sService == null) return false;
        try {
            return sService.deleteAllInCategory(category);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return false;
    }

    /**
     * Delete all the user data from the HealthStore.
     *
     * You will need {@link #HEALTH_STORE_MANAGE_PERMISSION}
     * to utilize this functionality.
     */
    public boolean deleteAllData() {
        if (sService == null) return false;
        try {
            return sService.deleteAllData();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return false;
    }

    /**
     * Backlist an app for a particular category.
     * A blacklisted app will not be provided access
     * (both read and write) to any {@link lineageos.health.Record}
     * of that category.
     *
     * You will need {@link #HEALTH_STORE_MANAGE_PERMISSION}
     * to utilize this functionality.
     *
     * @param pkgName The package name of the app
     * @param category The category for which the app should be blacklisted
     * @return Whether the process failed
     */
    public boolean blackListApp(@NonNull String pkgName, int category) {
        if (sService == null) return false;
        try {
            return sService.blackListApp(pkgName, category);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return false;
    }

    /**
     * Whitelist an app for a particular category.
     * Whitelisting an app removes it from the blacklist
     * for that category.
     *
     * You will need {@link #HEALTH_STORE_MANAGE_PERMISSION}
     * to utilize this functionality.
     *
     * @param pkgName The package name of the app
     * @param category The category for which the app should be blacklisted
     * @return Whether the process failed
     */
     public boolean whiteListApp(@NonNull String pkgName, int category) {
         if (sService == null) return false;
         try {
             return sService.whiteListApp(pkgName, category);
         } catch (RemoteException e) {
             Log.e(TAG, e.getLocalizedMessage(), e);
         }
         return false;
     }

    /**
     * Check whether an app is currently being blacklisted from
     * a specific category.
     *
     * You will need {@link #HEALTH_STORE_MANAGE_PERMISSION}
     * to utilize this functionality.
     *
     * @param pkgName The package name of the app
     * @param category The category for which the app should be blacklisted
     * @return Whether the app is blacklisted. In case of any internal
     *         failure, the app will report as blacklisted.
     */
    public boolean isBlackListed(@NonNull String pkgName, int category) {
        if (sService == null) return true;
        try {
            return sService.isBlackListed(pkgName, category);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return true;
    }
}
