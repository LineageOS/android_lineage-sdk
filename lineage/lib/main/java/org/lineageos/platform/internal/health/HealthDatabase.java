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
import android.annotation.Nullable;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.os.UserHandle;

import lineageos.health.MedicalProfile;
import lineageos.health.Record;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** @hide */
class HealthDatabase extends AltSQLiteOpenHelper {
    private static HealthDatabase sInstance;

    private static final int DB_VERSION = 1;
    private static final File DB_FILE = new File(
        Environment.getDataSystemCeDirectory(0),
        "HealthStoreDb.db");
    private static final String DB_PKG = "org.lineageos.platform";

    private static final String TABLE_RECORD = "records";
    private static final String TABLE_PROFILE = "profile";
    private static final String TABLE_BLACKLIST = "blacklist";

    private static final String RECORD_UID = "uid";
    private static final String RECORD_CATEGORY = "category";
    private static final String RECORD_TIME = "time";
    private static final String RECORD_VALUE = "value";

    private static final String PROFILE_UID = "uid";
    private static final String PROFILE_NAME = "name";
    private static final String PROFILE_BIRTH = "birthdate";
    private static final String PROFILE_BIO_SEX = "biologicalsex";
    private static final String PROFILE_WEIGHT = "weight";
    private static final String PROFILE_HEIGHT = "height";
    private static final String PROFILE_BLOOD_TYPE = "bloodtype";
    private static final String PROFILE_ORGAN_DONOR = "organdonor";
    private static final String PROFILE_ALLERGIES = "allergies";
    private static final String PROFILE_MEDICATIONS = "medications";
    private static final String PROFILE_CONDITIONS = "conditions";
    private static final String PROFILE_NOTES = "notes";

    private static final String BLACKLIST_NAME = "name";
    private static final String BLACKLIST_CATEGORY = "category";

    private static final String CREATE_RECORD =
            "CREATE TABLE IF NOT EXISTS " + TABLE_RECORD + " (" +
            RECORD_UID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            RECORD_CATEGORY + " INTEGER, " +
            RECORD_TIME + " INTEGER, " +
            RECORD_VALUE + " BLOB)";
    private static final String CREATE_INFO =
            "CREATE TABLE IF NOT EXISTS " + TABLE_PROFILE + " (" +
            PROFILE_UID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            PROFILE_NAME + " TEXT, " +
            PROFILE_BIRTH + " INTEGER, " +
            PROFILE_BIO_SEX + " INTEGER, " +
            PROFILE_WEIGHT + " REAL, " +
            PROFILE_HEIGHT + " REAL, " +
            PROFILE_BLOOD_TYPE + " INTEGER, " +
            PROFILE_ORGAN_DONOR + " INTEGER, " +
            PROFILE_ALLERGIES + " TEXT, " +
            PROFILE_MEDICATIONS + " TEXT, " +
            PROFILE_CONDITIONS + " TEXT, " +
            PROFILE_NOTES + " TEXT)";
    private static final String CREATE_BLACKLIST =
            "CREATE TABLE IF NOT EXISTS " + TABLE_BLACKLIST + " (" +
            BLACKLIST_NAME + " TEXT, " +
            BLACKLIST_CATEGORY + " INTEGER)";

    private static final String QUERY_RECORD_ID =
            "SELECT * FROM " + TABLE_RECORD + " WHERE " +
            RECORD_UID + " = ? LIMIT 1";
    private static final String QUERY_RECORD_CATEGORY =
            "SELECT " + RECORD_VALUE + " FROM " + TABLE_RECORD + " WHERE " +
            RECORD_CATEGORY + " = ? " +
            "ORDER BY " + RECORD_TIME + " DESC";
    private static final String QUERY_RECORD_CATEGORY_TIME =
            "SELECT " + RECORD_VALUE + " FROM " + TABLE_RECORD + " WHERE " +
            RECORD_CATEGORY + " = ? AND " +
            RECORD_TIME + " >= ? AND " +
            RECORD_TIME + " <= ? " +
            "ORDER BY " + RECORD_TIME + " DESC";
    private static final String QUERY_INFO =
            "SELECT * FROM " + TABLE_PROFILE + " WHERE " +
            PROFILE_UID + " = 1 LIMIT 1";
    private static final String QUERY_BLACKLIST =
            "SELECT " + BLACKLIST_CATEGORY + " FROM " + TABLE_BLACKLIST +
            " WHERE " +
            BLACKLIST_NAME + " = ? AND " +
            BLACKLIST_CATEGORY + " = ?";

    private HealthDatabase() {
        /*
         * System ('android') package has the db directory
         * in an uncrypted directory on fbe devices.
         * Workaround this issue by using a class that mocks the
         * db path to system credential encrypted folder.
         * Note that this requires that this class is initialized only
         * When the device has been unlocked at least one time.
         */
        super(DB_FILE, null, DB_VERSION);
    }

    static HealthDatabase getInstance() {
        if (sInstance == null) {
            sInstance = new HealthDatabase();
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

    boolean writeRecord(@Nullable Record record) {
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

    boolean deleteRecord(@Nullable Record record) {
        if (record == null) return false;
        final long uid = record.getUid();
        if (uid < 0L) return true; // nothing to delete
        final SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RECORD, RECORD_UID + "=?",
            new String[] { String.valueOf(uid) });
        db.close();
        return true;
    }

    @Nullable
    Record getById(long uid) {
        if (uid < 0L) return null;
        final SQLiteDatabase db = getReadableDatabase();
        final Cursor cursor = db.rawQuery(QUERY_RECORD_ID,
            new String[] { String.valueOf(uid) });
        Record record = null;
        if (cursor.moveToFirst()) {
            final int category = cursor.getInt(1);
            final byte[] source = cursor.getBlob(3);
            record = RecordFactory.build(category, source);
        }

        cursor.close();
        db.close();
        return record;
    }

    @NonNull
    List<Record> getByCategory(int category) {
        final List<Record> list = new ArrayList<>();
        final SQLiteDatabase db = getReadableDatabase();
        final Cursor cursor = db.rawQuery(QUERY_RECORD_CATEGORY,
                new String[] { String.valueOf(category) });
        if (cursor.moveToFirst()) {
            do {
                final byte[] source = cursor.getBlob(0);
                list.add(RecordFactory.build(category, source));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return list;
    }

    @NonNull
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
                final byte[] source = cursor.getBlob(0);
                list.add(RecordFactory.build(category, source));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return list;
    }

    boolean writeMedicalProfile(@Nullable MedicalProfile profile) {
        if (profile == null) return false;
        final ContentValues cv = new ContentValues();
        cv.put(PROFILE_UID, 1L);
        cv.put(PROFILE_NAME, profile.getName());
        cv.put(PROFILE_BIRTH, profile.getBirthDate());
        cv.put(PROFILE_BIO_SEX, profile.getBiologicalSex());
        cv.put(PROFILE_WEIGHT, profile.getWeight());
        cv.put(PROFILE_HEIGHT, profile.getHeight());
        cv.put(PROFILE_BLOOD_TYPE, profile.getBloodType());
        cv.put(PROFILE_ORGAN_DONOR, profile.getOrganDonor());
        cv.put(PROFILE_ALLERGIES, profile.getAllergies());
        cv.put(PROFILE_MEDICATIONS, profile.getMedications());
        cv.put(PROFILE_CONDITIONS, profile.getConditions());
        cv.put(PROFILE_NOTES, profile.getNotes());

        final SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict(TABLE_PROFILE, null, cv,
            SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        return true;
    }

    @NonNull
    MedicalProfile getMedicalProfile() {
        final SQLiteDatabase db = getReadableDatabase();
        final Cursor cursor = db.rawQuery(QUERY_INFO, null);
        MedicalProfile profile = new MedicalProfile();
        if (cursor.moveToFirst()) {
            // cursor[0] -> constant uid
            String name = cursor.getString(1);
            long birthDate = cursor.getLong(2);
            int biologicalSex = cursor.getInt(3);
            float weight = cursor.getFloat(4);
            float height = cursor.getFloat(5);
            int bloodType = cursor.getInt(6);
            int organDonor = cursor.getInt(7);
            String allergies = cursor.getString(8);
            String medications = cursor.getString(9);
            String conditions = cursor.getString(10);
            String notes = cursor.getString(11);
            profile = new MedicalProfile(name, birthDate, biologicalSex,
                weight, height, bloodType, organDonor,
                allergies, medications, conditions, notes);
        }

        cursor.close();
        db.close();
        return profile;
    }

    boolean deleteAllInCategory(int category) {
        final SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RECORD, RECORD_CATEGORY + " = ?",
            new String[] { String.valueOf(category) });
        db.close();
        return true;
    }

    boolean deleteAllData() {
        final SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DROP TABLE " + TABLE_RECORD);
        db.execSQL("DROP TABLE " + TABLE_PROFILE);
        db.close();
        return true;
    }

    boolean blackList(@Nullable String pkgName, int category, boolean newState) {
        return newState ?
            addToBlackList(pkgName, category) :
            removeFromBlackList(pkgName, category);
    }

    boolean isBlackListed(@Nullable String pkgName, int category) {
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

    private boolean addToBlackList(@Nullable String pkgName, int category) {
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

    private boolean removeFromBlackList(@Nullable String pkgName, int category) {
        if (pkgName == null || pkgName.equals("")) return false;
        final SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_BLACKLIST,
            BLACKLIST_NAME + " = ? AND " + BLACKLIST_CATEGORY + " = ?",
            new String[] { pkgName, String.valueOf(category) });
        db.close();
        return true;
    }
}
