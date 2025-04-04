/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package eco.shift.settingsstorage;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.util.Objects;

/**
 * The ShiftOsDatabaseHelper allows creation of a database to store ShiftOS specific settings for a user
 * in System, Secure, and Global tables.
 */
public class ShiftOsDatabaseHelper extends SQLiteOpenHelper{
    private static final String TAG = "ShiftOsDatabaseHelper";
    private static final boolean LOCAL_LOGV = false;

    private static final String DATABASE_NAME = "shiftossettings.db";
    private static final int DATABASE_VERSION = 2;

    public static class ShiftOsTableNames {
        public static final String TABLE_SYSTEM = "system";
        public static final String TABLE_SECURE = "secure";
        public static final String TABLE_GLOBAL = "global";
    }

    private static final String CREATE_TABLE_SQL_FORMAT = "CREATE TABLE %s (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name TEXT UNIQUE ON CONFLICT REPLACE," +
            "value TEXT" +
            ");)";

    private static final String CREATE_INDEX_SQL_FORMAT = "CREATE INDEX %sIndex%d ON %s (name);";

    private static final String DROP_TABLE_SQL_FORMAT = "DROP TABLE IF EXISTS %s;";

    private static final String DROP_INDEX_SQL_FORMAT = "DROP INDEX IF EXISTS %sIndex%d;";

    private static final String MCC_PROP_NAME = "ro.prebundled.mcc";

    private Context mContext;
    private int mUserHandle;
    private String mPublicSrcDir;

    /**
     * Gets the appropriate database path for a specific user
     * @param userId The database path for this user
     * @return The database path string
     */
    static String dbNameForUser(final int userId) {
        // The owner gets the unadorned db name;
        if (userId == UserHandle.USER_SYSTEM) {
            return DATABASE_NAME;
        } else {
            // Place the database in the user-specific data tree so that it's
            // cleaned up automatically when the user is deleted.
            File databaseFile = new File(
                    Environment.getUserSystemDirectory(userId), DATABASE_NAME);
            return databaseFile.getPath();
        }
    }

    /**
     * Creates an instance of {@link ShiftOsDatabaseHelper}
     * @param context
     * @param userId
     */
    public ShiftOsDatabaseHelper(Context context, int userId) {
        super(context, dbNameForUser(userId), null, DATABASE_VERSION);
        mContext = context;
        mUserHandle = userId;

        try {
            String packageName = mContext.getPackageName();
            mPublicSrcDir = mContext.getPackageManager().getApplicationInfo(packageName, 0)
                    .publicSourceDir;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates System, Secure, and Global tables in the specified {@link SQLiteDatabase} and loads
     * default values into the created tables.
     * @param db The database.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();

        try {
            createDbTable(db, ShiftOsTableNames.TABLE_SYSTEM);
            createDbTable(db, ShiftOsTableNames.TABLE_SECURE);

            if (mUserHandle == UserHandle.USER_SYSTEM) {
                createDbTable(db, ShiftOsTableNames.TABLE_GLOBAL);
            }

            loadSettings(db);

            db.setTransactionSuccessful();

            if (LOCAL_LOGV) Log.d(TAG, "Successfully created tables for ShiftOS settings db");
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Creates a table and index for the specified database and table name
     * @param db The {@link SQLiteDatabase} to create the table and index in.
     * @param tableName The name of the database table to create.
     */
    private void createDbTable(SQLiteDatabase db, String tableName) {
        if (LOCAL_LOGV) Log.d(TAG, "Creating table and index for: " + tableName);

        String createTableSql = String.format(CREATE_TABLE_SQL_FORMAT, tableName);
        db.execSQL(createTableSql);

        String createIndexSql = String.format(CREATE_INDEX_SQL_FORMAT, tableName, 1, tableName);
        db.execSQL(createIndexSql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (LOCAL_LOGV) Log.d(TAG, "Upgrading from version: " + oldVersion + " to " + newVersion);
        int upgradeVersion = oldVersion;

        if (upgradeVersion < 2) {
            // Used to run loadSettings()
            upgradeVersion = 2;
        }

        // *** Remember to update DATABASE_VERSION above!
        if (upgradeVersion != newVersion) {
            Log.wtf(TAG, "warning: upgrading settings database to version "
                            + newVersion + " left it at "
                            + upgradeVersion +
                            " instead; this is probably a bug. Did you update DATABASE_VERSION?",
                    new RuntimeException("db upgrade error"));
        }
    }

    private void moveSettingsToNewTable(SQLiteDatabase db,
                                        String sourceTable, String destTable,
                                        String[] settingsToMove, boolean doIgnore) {
        // Copy settings values from the source table to the dest, and remove from the source
        SQLiteStatement insertStmt = null;
        SQLiteStatement deleteStmt = null;

        db.beginTransaction();
        try {
            insertStmt = db.compileStatement("INSERT "
                    + (doIgnore ? " OR IGNORE " : "")
                    + " INTO " + destTable + " (name,value) SELECT name,value FROM "
                    + sourceTable + " WHERE name=?");
            deleteStmt = db.compileStatement("DELETE FROM " + sourceTable + " WHERE name=?");

            for (String setting : settingsToMove) {
                insertStmt.bindString(1, setting);
                insertStmt.execute();

                deleteStmt.bindString(1, setting);
                deleteStmt.execute();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            if (insertStmt != null) {
                insertStmt.close();
            }
            if (deleteStmt != null) {
                deleteStmt.close();
            }
        }
    }

    /**
     * Drops the table and index for the specified database and table name
     * @param db The {@link SQLiteDatabase} to drop the table and index in.
     * @param tableName The name of the database table to drop.
     */
    private void dropDbTable(SQLiteDatabase db, String tableName) {
        if (LOCAL_LOGV) Log.d(TAG, "Dropping table and index for: " + tableName);

        String dropTableSql = String.format(DROP_TABLE_SQL_FORMAT, tableName);
        db.execSQL(dropTableSql);

        String dropIndexSql = String.format(DROP_INDEX_SQL_FORMAT, tableName, 1);
        db.execSQL(dropIndexSql);
    }

    /**
     * Loads default values for specific settings into the database.
     * @param db The {@link SQLiteDatabase} to insert into.
     */
    private void loadSettings(SQLiteDatabase db) {
        loadSystemSettings(db);
        loadSecureSettings(db);
        // The global table only exists for the 'owner' user
        if (mUserHandle == UserHandle.USER_SYSTEM) {
            loadGlobalSettings(db);
        }
    }

    private void loadSecureSettings(SQLiteDatabase db) {
        SQLiteStatement stmt = null;
        try {
            stmt = db.compileStatement("INSERT OR IGNORE INTO secure(name,value)"
                    + " VALUES(?,?);");
            // Secure
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void loadSystemSettings(SQLiteDatabase db) {
        SQLiteStatement stmt = null;
        try {
            stmt = db.compileStatement("INSERT OR IGNORE INTO system(name,value)"
                    + " VALUES(?,?);");
            // System
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void loadGlobalSettings(SQLiteDatabase db) {
        SQLiteStatement stmt = null;
        try {
            stmt = db.compileStatement("INSERT OR IGNORE INTO global(name,value)"
                    + " VALUES(?,?);");
            // Global
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    /**
     * Loads a region locked string setting into a database table. If the resource for the specific
     * mcc is not found, the setting is loaded from the default resources.
     * @param stmt The SQLLiteStatement (transaction) for this setting.
     * @param name The name of the value to insert into the table.
     * @param resId The name of the string resource.
     */
    private void loadRegionLockedStringSetting(SQLiteStatement stmt, String name, int resId) {
        String mcc = SystemProperties.get(MCC_PROP_NAME);
        Resources customResources = null;

        if (!TextUtils.isEmpty(mcc)) {
            Configuration tempConfiguration = new Configuration();
            boolean useTempConfig = false;

            try {
                tempConfiguration.mcc = Integer.parseInt(mcc);
                useTempConfig = true;
            } catch (NumberFormatException e) {
                // not able to parse mcc, catch exception and exit out of this logic
                Log.e(TAG, "Unable to parse mcc", e);
            }

            if (useTempConfig) {
                AssetManager assetManager = new AssetManager();

                if (!TextUtils.isEmpty(mPublicSrcDir)) {
                    assetManager.addAssetPath(mPublicSrcDir);
                }

                customResources = new Resources(assetManager, new DisplayMetrics(),
                        tempConfiguration);
            }
        }

        String value = customResources == null ? mContext.getResources().getString(resId)
                : customResources.getString(resId);
        loadSetting(stmt, name, value);
    }

    /**
     * Loads a string resource into a database table. If a conflict occurs, that value is not
     * inserted into the database table.
     * @param stmt The SQLLiteStatement (transaction) for this setting.
     * @param name The name of the value to insert into the table.
     * @param resId The name of the string resource.
     */
    private void loadStringSetting(SQLiteStatement stmt, String name, int resId) {
        loadSetting(stmt, name, mContext.getResources().getString(resId));
    }

    /**
     * Loads a boolean resource into a database table. If a conflict occurs, that value is not
     * inserted into the database table.
     * @param stmt The SQLLiteStatement (transaction) for this setting.
     * @param name The name of the value to insert into the table.
     * @param resId The name of the boolean resource.
     */
    private void loadBooleanSetting(SQLiteStatement stmt, String name, int resId) {
        loadSetting(stmt, name,
                mContext.getResources().getBoolean(resId) ? "1" : "0");
    }

    /**
     * Loads an integer resource into a database table. If a conflict occurs, that value is not
     * inserted into the database table.
     * @param stmt The SQLLiteStatement (transaction) for this setting.
     * @param name The name of the value to insert into the table.
     * @param resId The name of the integer resource.
     */
    private void loadIntegerSetting(SQLiteStatement stmt, String name, int resId) {
        loadSetting(stmt, name,
                Integer.toString(mContext.getResources().getInteger(resId)));
    }

    private void loadSetting(SQLiteStatement stmt, String key, Object value) {
        stmt.bindString(1, key);
        stmt.bindString(2, value.toString());
        stmt.execute();
    }

    private static void ensureTableIsValid(final String tableName) {
        switch (tableName) {
            case ShiftOsTableNames.TABLE_GLOBAL:
            case ShiftOsTableNames.TABLE_SECURE:
            case ShiftOsTableNames.TABLE_SYSTEM:
                break;
            default:
                throw new IllegalArgumentException(
                        "Table '" + tableName + "' is not a valid ShiftOS database table");
        }
    }

    /**
     * Read a setting from a given database table.
     * @param db The {@link SQLiteDatabase} to read from.
     * @param tableName The name of the database table to read from.
     * @param name The name of the setting to read.
     * @param defaultValue the value to return if setting cannot be read.
     */
    private static String readSetting(final SQLiteDatabase db, final String tableName,
            final String name, final String defaultValue) {
        ensureTableIsValid(tableName);
        SQLiteStatement stmt = null;
        try {
            stmt = db.compileStatement("SELECT value FROM " + tableName + " WHERE name=?");
            stmt.bindString(1, name);
            return stmt.simpleQueryForString();
        } catch (SQLiteDoneException ex) {
            // Value is not set
        } finally {
            if (stmt != null) stmt.close();
        }
        return defaultValue;
    }

    /**
     * Read an Integer setting from a given database table.
     * @param db The {@link SQLiteDatabase} to read from.
     * @param tableName The name of the database table to read from.
     * @param name The name of the setting to read.
     * @param defaultValue the value to return if setting cannot be read or is not an Integer.
     */
    private static Integer readIntegerSetting(final SQLiteDatabase db, final String tableName,
            final String name, final Integer defaultValue) {
        ensureTableIsValid(tableName);
        final String value = readSetting(db, tableName, name, null);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * Read a Long setting from a given database table.
     * @param db The {@link SQLiteDatabase} to read from.
     * @param tableName The name of the database table to read from.
     * @param name The name of the setting to read.
     * @param defaultValue the value to return if setting cannot be read or is not a Long.
     */
    private static Long readLongSetting(final SQLiteDatabase db, final String tableName,
            final String name, final Long defaultValue) {
        ensureTableIsValid(tableName);
        final String value = readSetting(db, tableName, name, null);
        try {
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * Write a setting to a given database table, overriding existing values
     * @param db The {@link SQLiteDatabase} to write to.
     * @param tableName The name of the database table to write to.
     * @param name The name of the setting to write.
     * @param value the value of the setting to write.
     */
    private static void writeSetting(final SQLiteDatabase db, final String tableName,
            final String name, final Object value) throws SQLiteDoneException {
        writeSetting(db, tableName, name, value, true /* replaceIfExists */);
    }

    /**
     * Write a setting to a given database table, only if it doesn't already exist
     * @param db The {@link SQLiteDatabase} to write to.
     * @param tableName The name of the database table to write to.
     * @param name The name of the setting to write.
     * @param value the value of the setting to write.
     */
    private static void writeSettingIfNotPresent(final SQLiteDatabase db, final String tableName,
            final String name, final Object value) throws SQLiteDoneException {
        writeSetting(db, tableName, name, value, false /* replaceIfExists */);
    }

    /** Write a setting to a given database table. */
    private static void writeSetting(final SQLiteDatabase db, final String tableName,
            final String name, final Object value, final boolean replaceIfExists)
            throws SQLiteDoneException {
        ensureTableIsValid(tableName);
        SQLiteStatement stmt = null;
        try {
            stmt = db.compileStatement("INSERT OR " + (replaceIfExists ? "REPLACE" : "IGNORE")
                    + " INTO " + tableName + "(name,value) VALUES(?,?);");
            stmt.bindString(1, name);
            stmt.bindString(2, Objects.toString(value));
            stmt.execute();
        } catch (SQLiteDoneException ex) {
            // Value is not set
            throw ex;
        } finally {
            if (stmt != null) stmt.close();
        }
    }
}
