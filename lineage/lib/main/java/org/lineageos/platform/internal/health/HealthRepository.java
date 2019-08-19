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

package org.lineageos.platform.internal.health;

import android.annotation.NonNull;
import android.content.Context;

import lineageos.health.MedicalProfile;
import lineageos.health.Record;

import java.util.List;

/** @hide */
public class HealthRepository {
    private static HealthRepository sInstance;

    private final HealthDatabase mDb;

    private HealthRepository(@NonNull Context context) {
        mDb = HealthDatabase.getInstance(context);
    }

    @NonNull
    public static HealthRepository getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new HealthRepository(context);
        }
        return sInstance;
    }

    public boolean write(@NonNull Record record) {
        return mDb.writeRecord(record);
    }

    public boolean delete(@NonNull Record record) {
        return mDb.deleteRecord(record);
    }

    public Record getById(long uid) {
        return mDb.getById(uid);
    }

    @NonNull
    public List<Record> getByCategory(int category) {
        return mDb.getByCategory(category);
    }

    @NonNull
    public List<Record> getByCategory(int category, long start, long end) {
        return mDb.getByCategory(category, start, end);
    }

    public boolean writeMedicalProfile(@NonNull MedicalProfile profile) {
        return mDb.writeMedicalProfile(profile);
    }

    @NonNull
    public MedicalProfile getMedicalProfile() {
        return mDb.getMedicalProfile();
    }

    public boolean deleteAllData() {
        return mDb.deleteAllData();
    }

    public boolean isBlackListed(@NonNull String pkgName, int category) {
        return mDb.isBlackListed(pkgName, category);
    }

    public boolean blackList(@NonNull String pkgName, int category,
            boolean newState) {
        return mDb.blackList(pkgName, category, newState);
    }
}
