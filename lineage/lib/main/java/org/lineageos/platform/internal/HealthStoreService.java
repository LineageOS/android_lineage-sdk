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

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import com.android.server.SystemService;

import lineageos.app.LineageContextConstants;
import lineageos.health.HealthStore;
import lineageos.health.HumanInfo;
import lineageos.health.IHealthStore;
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
        mPkgManager = mContext.getPackageManager();
        if (mPkgManager.hasSystemFeature(LineageContextConstants.Features.HEALTH)) {
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
        if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            mRepo = HealthRepository.getInstance(mContext);
        }
    }

    private void enforceManagePermission() {
        mContext.enforceCallingOrSelfPermission(HealthStore.HEALTH_STORE_MANAGE_PERMISSION,
                "You do not have permissions to manage core HealthStore configurations");
    }

    private void enforceReadPermission() {
        mContext.enforceCallingOrSelfPermission(HealthStore.HEALTH_STORE_READ_PERMISSION,
                "You do not have permissions to read to the HealthStore");
    }

    private void enforceWritePermission() {
        mContext.enforceCallingOrSelfPermission(HealthStore.HEALTH_STORE_WRITE_PERMISSION,
                "You do not have permissions to write to the HealthStore");
    }

    /* Public methods implementation */

    private boolean writeRecordImpl(Record record, int callingUid) {
        return hasAccess(callingUid, record.getCategory()) && mRepo.write(record);
    }

    private boolean deleteRecordImpl(Record record) {
        return mRepo.delete(record);
    }

    private Record getRecordImpl(long uid, int callingUid) {
        Record record = mRepo.getById(uid);
        return hasAccess(callingUid, record.getCategory()) ? record : null;
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

    public boolean writeHumanInfoImpl(HumanInfo info) {
        return mRepo.writeHumanInfo(info);
    }

    public HumanInfo getHumanInfoImpl() {
        return mRepo.getHumanInfo();
    }

    private boolean blackListAppImpl(String pkgName, int category,
            boolean newState) {
        return mRepo.blackList(pkgName, category, newState);
    }

    private boolean isBlackListedImpl(String pkgName, int category) {
        try {
            if (pkgName == null ||
                    mPkgManager.getPackageInfo(pkgName, 1) == null) {
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
        String pkgName = mPkgManager.getNameForUid(uid);
        return isBlackListedImpl(pkgName, category);
    }

    /* Binder */

    private final IBinder mService = new IHealthStore.Stub() {
        @Override
        public boolean writeRecord(Record record) {
            enforceWritePermission();
            return writeRecordImpl(record, getCallingUid());
        }

        @Override
        public boolean deleteRecord(Record record) {
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
        public boolean writeHumanInfo(HumanInfo info) {
            enforceWritePermission();
            return writeHumanInfoImpl(info);
        }

        @Override
        public HumanInfo getHumanInfo() {
            enforceReadPermission();
            return getHumanInfoImpl();
        }

        @Override
        public boolean blackListApp(String pkgName, int category) {
            enforceManagePermission();
            return blackListAppImpl(pkgName, category, true);
        }

        @Override
        public boolean whiteListApp(String pkgName, int category) {
            enforceManagePermission();
            return blackListAppImpl(pkgName, category, false);
        }

        @Override
        public boolean isBlackListed(String pkgName, int category) {
            enforceManagePermission();
            return isBlackListedImpl(pkgName, category);
        }
    };
}
