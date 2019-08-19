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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import lineageos.health.HumanInfo;
import lineageos.health.Record;

import java.util.ArrayList;
import java.util.List;

/** @hide */
class HealthDatabase extends SQLiteOpenHelper {
    private static HealthDatabase sInstance;

    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "HealthStoreDb";

    private static final String TABLE_RECORD = "records";
    private static final String TABLE_INFO = "info";
    private static final String TABLE_BLACKLIST = "blacklist";

    private static final String RECORD_UID = "uid";
    private static final String RECORD_CATEGORY = "category";
    private static final String RECORD_TIME = "time";
    private static final String RECORD_VALUE = "value";

    private static final String INFO_UID = "uid";
    private static final String INFO_NAME = "name";
    private static final String INFO_BIRTH = "birthdate";
    private static final String INFO_BIO_SEX = "biologicalsex";
    private static final String INFO_WEIGHT = "weight";
    private static final String INFO_HEIGHT = "height";
    private static final String INFO_BLOOD_TYPE = "bloodtype";
    private static final String INFO_ALLERGIES = "allergies";
    private static final String INFO_MEDICATIONS = "medications";
    private static final String INFO_ORGAN_DONOR = "organdonor";

    private static final String BLACKLIST_NAME = "name";
    private static final String BLACKLIST_CATEGORY = "category";

    private static final String CREATE_RECORD =
            "CREATE TABLE IF NOT EXISTS " + TABLE_RECORD + " (" +
            RECORD_UID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            RECORD_CATEGORY + " INTEGER, " +
            RECORD_TIME + " INTEGER, " +
            RECORD_VALUE + " BLOB)";
    private static final String CREATE_INFO =
            "CREATE TABLE IF NOT EXISTS " + TABLE_INFO + " (" +
            INFO_UID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            INFO_NAME + " TEXT, " +
            INFO_BIRTH + " INTEGER, " +
            INFO_BIO_SEX + " INTEGER, " +
            INFO_WEIGHT + " REAL, " +
            INFO_HEIGHT + " REAL, " +
            INFO_BLOOD_TYPE + " INTEGER, " +
            INFO_ALLERGIES + " TEXT, " +
            INFO_MEDICATIONS + " TEXT, " +
            INFO_ORGAN_DONOR + " INTEGER)";
    private static final String CREATE_BLACKLIST =
            "CREATE TABLE IF NOT EXISTS " + TABLE_BLACKLIST + " (" +
            BLACKLIST_NAME + " TEXT, " +
            BLACKLIST_CATEGORY + " INTEGER)";

    private static final String QUERY_RECORD_ID =
            "SELECT " + RECORD_VALUE + " FROM " + TABLE_RECORD + " WHERE " +
            RECORD_UID + " = ? LIMIT 1";
    private static final String QUERY_RECORD_CATEGORY =
            "SELECT " + RECORD_VALUE + " FROM " + TABLE_RECORD + " WHERE " +
            RECORD_CATEGORY + " = ? " +
            "ORDER BY" + RECORD_TIME + " DESC";
    private static final String QUERY_RECORD_CATEGORY_TIME =
            "SELECT " + RECORD_VALUE + " FROM " + TABLE_RECORD + " WHERE " +
            RECORD_CATEGORY + " = ? " +
            RECORD_TIME + " >= ? AND " +
            RECORD_TIME + " <= ? " +
            "ORDER BY" + RECORD_TIME + " DESC";
    private static final String QUERY_INFO =
            "SELECT * FROM " + TABLE_INFO + " WHERE " +
            INFO_UID + " = 1 LIMIT 1";
    private static final String QUERY_BLACKLIST =
            "SELECT " + BLACKLIST_CATEGORY + " FROM " + TABLE_BLACKLIST +
            " WHERE " +
            BLACKLIST_NAME + " = ? AND " +
            BLACKLIST_CATEGORY + " = ?";

    private HealthDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    static HealthDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new HealthDatabase(context);
        }
        return sInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_RECORD);
        db.execSQL(CREATE_INFO);
        db.execSQL(CREATE_BLACKLIST);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Update this on version change
    }

    boolean writeRecord(Record record) {
        if (record == null) return false;
        final ContentValues cv = new ContentValues();
        if (record.getUid() >= 0L) {
            cv.put(RECORD_UID, record.getUid());
        }
        cv.put(RECORD_CATEGORY, record.getCategory());
        cv.put(RECORD_TIME, record.getTime());
        cv.put(RECORD_VALUE, record.asByteArray());

        final SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict(TABLE_RECORD, null, cv,
            SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        return true;
    }

    boolean deleteRecord(Record record) {
        if (record == null) return false;
        final long uid = record.getUid();
        if (uid < 0L) return true; // nothing to delete
        final SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RECORD, RECORD_UID + "=?",
            new String[] { String.valueOf(uid) });
        db.close();
        return true;
    }

    Record getById(long uid) {
        if (uid < 0L) return null;
        final SQLiteDatabase db = getReadableDatabase();
        final Cursor cursor = db.rawQuery(QUERY_RECORD_ID,
            new String[] { String.valueOf(uid) });
        Record record = null;
        if (cursor.moveToFirst()) {
            final byte[] value = cursor.getBlob(0);
            record = new Record(value);
        }

        cursor.close();
        db.close();
        return record;
    }

    List<Record> getByCategory(int category) {
        final List<Record> list = new ArrayList<>();
        final SQLiteDatabase db = getReadableDatabase();
        final Cursor cursor = db.rawQuery(QUERY_RECORD_CATEGORY,
                new String[] { String.valueOf(category) });
        if (cursor.moveToFirst()) {
            do {
                final byte[] value = cursor.getBlob(0);
                list.add(new Record(value));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return list;
    }

    List<Record> getByCategory(int category, long start, long end) {
        final List<Record> list = new ArrayList<>();
        final SQLiteDatabase db = getReadableDatabase();
        final String[] args = new String[] {
            String.valueOf(category),
            String.valueOf(start),
            String.valueOf(end)
        };
        final Cursor cursor = db.rawQuery(QUERY_RECORD_CATEGORY_TIME, args);
        if (cursor.moveToFirst()) {
            do {
                final byte[] value = cursor.getBlob(0);
                list.add(new Record(value));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return list;
    }

    boolean writeHumanInfo(HumanInfo info) {
        if (info == null) return false;
        final ContentValues cv = new ContentValues();
        cv.put(INFO_UID, 1L);
        cv.put(INFO_NAME, info.name);
        cv.put(INFO_BIRTH, info.birthDate);
        cv.put(INFO_BIO_SEX, info.biologicalSex);
        cv.put(INFO_WEIGHT, info.weight);
        cv.put(INFO_HEIGHT, info.height);
        cv.put(INFO_BLOOD_TYPE, info.bloodType);
        cv.put(INFO_ALLERGIES, info.allergies);
        cv.put(INFO_MEDICATIONS, info.medications);
        cv.put(INFO_ORGAN_DONOR, info.organDonor ? 1 : 0);

        final SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict(TABLE_INFO, null, cv,
            SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        return true;
    }

    HumanInfo getHumanInfo() {
        final SQLiteDatabase db = getReadableDatabase();
        final Cursor cursor = db.rawQuery(QUERY_INFO, null);
        HumanInfo info = null;
        if (cursor.moveToFirst()) {
            String name = cursor.getString(0);
            long birthDate = cursor.getLong(1);
            int biologicalSex = cursor.getInt(2);
            float weight = cursor.getFloat(3);
            float height = cursor.getFloat(4);
            int bloodType = cursor.getInt(5);
            String allergies = cursor.getString(6);
            String medications = cursor.getString(7);
            boolean organDonor = cursor.getInt(8) == 1;
            info = new HumanInfo(name, birthDate, biologicalSex,
                weight, height, bloodType, allergies, medications, organDonor);
        }

        cursor.close();
        db.close();
        return info;
    }

    boolean blackList(String pkgName, int category, boolean newState) {
        return newState ?
            addToBlackList(pkgName, category) :
            removeFromBlackList(pkgName, category);
    }

    boolean isBlackListed(String pkgName, int category) {
        if (pkgName == null || pkgName.equals("")) return true;
        final SQLiteDatabase db = getReadableDatabase();
        final String[] args = new String[] {
            pkgName,
            String.valueOf(category)
        };
        final Cursor cursor = db.rawQuery(QUERY_BLACKLIST, args);
        final boolean result = cursor.moveToFirst();
        cursor.close();
        db.close();
        return result;
    }

    private boolean addToBlackList(String pkgName, int category) {
        if (pkgName == null || pkgName.equals("")) return false;
        final ContentValues cv = new ContentValues();
        cv.put(BLACKLIST_NAME, pkgName);
        cv.put(BLACKLIST_CATEGORY, category);
        final SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict(TABLE_RECORD, null, cv,
            SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        return true;
    }

    private boolean removeFromBlackList(String pkgName, int category) {
        if (pkgName == null || pkgName.equals("")) return false;
        final SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_BLACKLIST,
            BLACKLIST_NAME + " = ? AND " + BLACKLIST_CATEGORY + " = ?",
            new String[] { pkgName, String.valueOf(category) });
        db.close();
        return true;
    }
}
