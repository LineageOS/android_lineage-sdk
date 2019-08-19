/*
 * Copyright (c) 2019 The LineageOS Project
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

package org.lineageos.platform.internal;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import com.android.server.SystemService;

import lineageos.app.LineageContextConstants;
import lineageos.health.HealthStore;
import lineageos.health.IHealthStore;
import lineageos.health.MedicalProfile;
import lineageos.health.Record;

import org.lineageos.platform.internal.health.HealthRepository;

import java.util.ArrayList;
import java.util.List;

/** @hide */
public class HealthStoreService extends LineageSystemService {
    private static final String TAG = "HealthStoreService";

    private HealthRepository mRepo;

    private Context mContext;
    private PackageManager mPkgManager;

    public HealthStoreService(Context context) {
        super(context);
        mContext = context;
        if (context.getPackageManager().hasSystemFeature(LineageContextConstants.Features.HEALTH)) {
            publishBinderService(LineageContextConstants.LINEAGE_HEALTH_STORE, mService);
        } else {
            Log.wtf(TAG, "Lineage HealthStore service started by system server but feature xml not" +
                    " declared. Not publishing binder service!");
        }
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.HEALTH;
    }

    @Override
    public void onStart() {
        /* No-op */
    }

    @Override
    public void onBootPhase(int phase) {
        switch (phase) {
            case SystemService.PHASE_SYSTEM_SERVICES_READY:
                mPkgManager = mContext.getPackageManager();
                break;
            case SystemService.PHASE_BOOT_COMPLETED:
                mRepo = HealthRepository.getInstance();
                break;
        }
    }

    private void enforceManagePermission() {
        mContext.enforceCallingPermission(HealthStore.HEALTH_STORE_MANAGE_PERMISSION,
                "You do not have permissions to manage core HealthStore configurations");
    }

    private void enforceReadPermission() {
        mContext.enforceCallingPermission(HealthStore.HEALTH_STORE_READ_PERMISSION,
                "You do not have permissions to read to the HealthStore");
    }

    private void enforceWritePermission() {
        mContext.enforceCallingPermission(HealthStore.HEALTH_STORE_WRITE_PERMISSION,
                "You do not have permissions to write to the HealthStore");
    }

    /* Public methods implementation */

    private boolean writeRecordImpl(@Nullable Record record,
            final int callingUid) {
        if (record == null) {
            return false;
        }
        return hasAccess(callingUid, record.getCategory()) && mRepo.write(record);
    }

    private boolean deleteRecordImpl(@Nullable Record record) {
        if (record == null) {
            return false;
        }
        return mRepo.delete(record);
    }

    private Record getRecordImpl(long uid, int callingUid) {
        final Record record = mRepo.getById(uid);
        if (record == null || !hasAccess(callingUid, record.getCategory())) {
            return null;
        }
        return record;
    }

    private List<Record> getRecordsImpl(int category, int callingUid) {
        if (!hasAccess(callingUid, category)) {
            return new ArrayList<>();
        }

        return mRepo.getByCategory(category);
    }

    private List<Record> getRecordsImpl(int category, long start, long end,
            int callingUid) {
        if (!hasAccess(callingUid, category)) {
            return new ArrayList<>();
        }

        return mRepo.getByCategory(category, start, end);
    }

    private boolean setMedicalProfileImpl(@NonNull MedicalProfile profile) {
        return mRepo.writeMedicalProfile(profile);
    }

    @NonNull
    private MedicalProfile getMedicalProfileImpl() {
        return mRepo.getMedicalProfile();
    }

    private boolean deleteAllInCategoryImpl(int category) {
        return mRepo.deleteAllInCategory(category);
    }

    private boolean deleteAllDataImpl() {
        return mRepo.deleteAllData();
    }

    private boolean blackListAppImpl(@NonNull String pkgName,
            int category, boolean newState) {
        return mRepo.blackList(pkgName, category, newState);
    }

    private boolean isBlackListedImpl(@Nullable String pkgName, int category) {
        try {
            if (pkgName == null || mPkgManager.getPackageInfo(pkgName, 1) == null) {
                // Calling uid is not a valid app, do not engage
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Package not found, do not engage
            return true;
        }

        return mRepo.isBlackListed(pkgName, category);
    }

    /* Utils */

    private boolean hasAccess(int uid, int category) {
        final String pkgName = mPkgManager.getNameForUid(uid);
        return !isBlackListedImpl(pkgName, category);
    }

    /* Binder */

    private final IBinder mService = new IHealthStore.Stub() {
        @Override
        public boolean writeRecord(@Nullable Record record) {
            enforceWritePermission();
            return writeRecordImpl(record, getCallingUid());
        }

        @Override
        public boolean deleteRecord(@Nullable Record record) {
            enforceWritePermission();
            return deleteRecordImpl(record);
        }

        @Override
        public Record getRecord(long uid) {
            enforceReadPermission();
            return getRecordImpl(uid, getCallingUid());
        }

        @Override
        public List<Record> getRecords(int category) {
            enforceReadPermission();
            return getRecordsImpl(category, getCallingUid());
        }

        @Override
        public List<Record> getRecordsInTimeFrame(int category,
                long start, long end) {
            enforceReadPermission();
            return getRecordsImpl(category, start, end, getCallingUid());
        }

        @Override
        public void setMedicalProfile(@NonNull MedicalProfile profile) {
            enforceWritePermission();
            boolean result = setMedicalProfileImpl(profile);
            if (!result) {
                Log.e(TAG, "Failed to set MedicalProfile");
            }
        }

        @NonNull
        @Override
        public MedicalProfile getMedicalProfile() {
            enforceReadPermission();
            return getMedicalProfileImpl();
        }

        @Override
        public boolean deleteAllInCategory(int category) {
            enforceManagePermission();
            return deleteAllInCategoryImpl(category);
        }

        @Override
        public boolean deleteAllData() {
            enforceManagePermission();
            return deleteAllDataImpl();
        }

        @Override
        public boolean blackListApp(@NonNull String pkgName, int category) {
            enforceManagePermission();
            return blackListAppImpl(pkgName, category, true);
        }

        @Override
        public boolean whiteListApp(@Nullable String pkgName, int category) {
            enforceManagePermission();
            return blackListAppImpl(pkgName, category, false);
        }

        @Override
        public boolean isBlackListed(@Nullable String pkgName, int category) {
            enforceManagePermission();
            return isBlackListedImpl(pkgName, category);
        }
    };
}
