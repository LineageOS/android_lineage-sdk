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

import android.content.Context;

import lineageos.health.HumanInfo;
import lineageos.health.Record;

import java.util.List;

/** @hide */
public class HealthRepository {
    private static HealthRepository sInstance;

    private final HealthDatabase mDb;

    private HealthRepository(Context context) {
        mDb = HealthDatabase.getInstance(context);
    }

    public static HealthRepository getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new HealthRepository(context);
        }
        return sInstance;
    }

    public boolean write(Record record) {
        return mDb.writeRecord(record);
    }

    public boolean delete(Record record) {
        return mDb.deleteRecord(record);
    }

    public Record getById(long uid) {
        return mDb.getById(uid);
    }

    public List<Record> getByCategory(int category) {
        return mDb.getByCategory(category);
    }

    public List<Record> getByCategory(int category, long start, long end) {
        return mDb.getByCategory(category, start, end);
    }

    public boolean writeHumanInfo(HumanInfo info) {
        return mDb.writeHumanInfo(info);
    }

    public HumanInfo getHumanInfo() {
        return mDb.getHumanInfo();
    }


    public boolean isBlackListed(String pkgName, int category) {
        return mDb.isBlackListed(pkgName, category);
    }

    public boolean blackList(String pkgName, int category, boolean newState) {
        return mDb.blackList(pkgName, category, newState);
    }
}
