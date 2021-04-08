/**
 * Copyright (C) 2015-2016 The CyanogenMod Project
 *               2017-2021 The LineageOS Project
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

package org.lineageos.lineagesettings;

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

import lineageos.providers.LineageSettings;

import org.lineageos.internal.util.FileUtils;

import java.io.File;

/**
 * The LineageDatabaseHelper allows creation of a database to store Lineage specific settings for a user
 * in System, Secure, and Global tables.
 */
public class LineageDatabaseHelper extends SQLiteOpenHelper{
    private static final String TAG = "LineageDatabaseHelper";
    private static final boolean LOCAL_LOGV = false;

    private static final String DATABASE_NAME = "lineagesettings.db";
    private static final int DATABASE_VERSION = 14;

    private static final String DATABASE_NAME_OLD = "cmsettings.db";

    public static class LineageTableNames {
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
    private static String dbNameForUser(Context context, int userId, String baseName) {
        if (userId == UserHandle.USER_OWNER) {
            return context.getDatabasePath(baseName).getPath();
        } else {
            // Place the database in the user-specific data tree so that it's
            // cleaned up automatically when the user is deleted.
            File databaseFile = new File(
                    Environment.getUserSystemDirectory(userId), baseName);
            return databaseFile.getPath();
        }
    }

    /**
     * Migrate db files (if needed).
     */
    public static void migrateDbFiles(Context context, int userId) {
        final String dbPath = dbNameForUser(context, userId, DATABASE_NAME);
        final String dbPathOld = dbNameForUser(context, userId, DATABASE_NAME_OLD);

        // Only rename databases that we know we can write to later.
        if (!FileUtils.isFileWritable(dbPathOld)) {
            return;
        }
        if (FileUtils.fileExists(dbPath) && !FileUtils.delete(dbPath)) {
            Log.e(TAG, "Unable to delete existing settings db file " + dbPath);
            return;
        }
        if (!FileUtils.rename(dbPathOld, dbPath)) {
            Log.e(TAG, "Found old settings db " + dbPathOld + " but could not rename it to "
                    + dbPath);
            return;
        }
        // Move any additional sqlite files that might exist.
        // The list of suffixes is taken from fw/b SQLiteDatabase.java deleteDatabase().
        final String[] suffixes = { "-journal", "-shm", "-wal" };
        for (String s: suffixes) {
            final String oldFile = dbPathOld + s;
            final String newFile = dbPath + s;
            if (!FileUtils.fileExists(oldFile)) {
                continue;
            }
            if (FileUtils.fileExists(newFile) && !FileUtils.delete(newFile)) {
                Log.e(TAG, "Unable to delete existing settings db file " + newFile);
                continue;
            }
            if (!FileUtils.rename(oldFile, newFile)) {
                Log.e(TAG, "Unable to rename existing settings db file " + oldFile + " to "
                        + newFile);
            }
        }
    }

    /**
     * Creates an instance of {@link LineageDatabaseHelper}
     * @param context
     * @param userId
     */
    public LineageDatabaseHelper(Context context, int userId) {
        super(context, dbNameForUser(context, userId, DATABASE_NAME), null, DATABASE_VERSION);
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
            createDbTable(db, LineageTableNames.TABLE_SYSTEM);
            createDbTable(db, LineageTableNames.TABLE_SECURE);

            if (mUserHandle == UserHandle.USER_OWNER) {
                createDbTable(db, LineageTableNames.TABLE_GLOBAL);
            }

            loadSettings(db);

            db.setTransactionSuccessful();

            if (LOCAL_LOGV) Log.d(TAG, "Successfully created tables for lineage settings db");
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
            db.beginTransaction();
            try {
                loadSettings(db);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            upgradeVersion = 2;
        }

        if (upgradeVersion < 3) {
            db.beginTransaction();
            SQLiteStatement stmt = null;
            try {
                stmt = db.compileStatement("INSERT INTO secure(name,value)"
                        + " VALUES(?,?);");
                loadStringSetting(stmt, LineageSettings.Secure.PROTECTED_COMPONENT_MANAGERS,
                        R.string.def_protected_component_managers);
                db.setTransactionSuccessful();
            } finally {
                if (stmt != null) stmt.close();
                db.endTransaction();
            }
            upgradeVersion = 3;
        }

        if (upgradeVersion < 4) {
            /* Was set LineageSettings.Secure.LINEAGE_SETUP_WIZARD_COMPLETE
             * but this is no longer used
             */
            upgradeVersion = 4;
        }

        if (upgradeVersion < 5) {
            if (mUserHandle == UserHandle.USER_OWNER) {
                db.beginTransaction();
                SQLiteStatement stmt = null;
                try {
                    stmt = db.compileStatement("INSERT INTO global(name,value)"
                            + " VALUES(?,?);");
                    loadIntegerSetting(stmt, LineageSettings.Global.WEATHER_TEMPERATURE_UNIT,
                            R.integer.def_temperature_unit);
                    db.setTransactionSuccessful();
                } finally {
                    if (stmt != null) stmt.close();
                    db.endTransaction();
                }
            }
            upgradeVersion = 5;
        }

        if (upgradeVersion < 6) {
            // Move force_show_navbar to global
            if (mUserHandle == UserHandle.USER_OWNER) {
                moveSettingsToNewTable(db, LineageTableNames.TABLE_SECURE,
                        LineageTableNames.TABLE_GLOBAL, new String[] {
                        LineageSettings.Secure.DEV_FORCE_SHOW_NAVBAR
                }, true);
            }
            upgradeVersion = 6;
        }

        if (upgradeVersion < 7) {
            if (mUserHandle == UserHandle.USER_OWNER) {
                db.beginTransaction();
                SQLiteStatement stmt = null;
                try {
                    stmt = db.compileStatement("SELECT value FROM system WHERE name=?");
                    stmt.bindString(1, LineageSettings.System.STATUS_BAR_CLOCK);
                    long value = stmt.simpleQueryForLong();

                    if (value != 0) {
                        stmt = db.compileStatement("UPDATE system SET value=? WHERE name=?");
                        stmt.bindLong(1, value - 1);
                        stmt.bindString(2, LineageSettings.System.STATUS_BAR_CLOCK);
                        stmt.execute();
                    }
                    db.setTransactionSuccessful();
                } catch (SQLiteDoneException ex) {
                    // LineageSettings.System.STATUS_BAR_CLOCK is not set
                } finally {
                    if (stmt != null) stmt.close();
                    db.endTransaction();
                }
            }
            upgradeVersion = 7;
        }

        if (upgradeVersion < 8) {
            db.beginTransaction();
            SQLiteStatement stmt = null;
            try {
                stmt = db.compileStatement("UPDATE secure SET value=? WHERE name=?");
                stmt.bindString(1, mContext.getResources()
                        .getString(R.string.def_protected_component_managers));
                stmt.bindString(2, LineageSettings.Secure.PROTECTED_COMPONENT_MANAGERS);
                stmt.execute();
                db.setTransactionSuccessful();
            } finally {
                if (stmt != null) stmt.close();
                db.endTransaction();
            }
            upgradeVersion = 8;
        }

        if (upgradeVersion < 9) {
            if (mUserHandle == UserHandle.USER_OWNER) {
                db.execSQL("UPDATE system SET value = '0' WHERE value IN ('10', '11') AND name IN ("
                        + "'" + LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION + "',"
                        + "'" + LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION + "',"
                        + "'" + LineageSettings.System.KEY_MENU_ACTION + "',"
                        + "'" + LineageSettings.System.KEY_MENU_LONG_PRESS_ACTION + "',"
                        + "'" + LineageSettings.System.KEY_ASSIST_ACTION + "',"
                        + "'" + LineageSettings.System.KEY_ASSIST_LONG_PRESS_ACTION + "',"
                        + "'" + LineageSettings.System.KEY_APP_SWITCH_ACTION + "',"
                        + "'" + LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION + "')");
            }
            upgradeVersion = 9;
        }

        if (upgradeVersion < 10) {
            if (mUserHandle == UserHandle.USER_OWNER) {
                // Update STATUS_BAR_CLOCK
                db.beginTransaction();
                SQLiteStatement stmt = null;
                try {
                    stmt = db.compileStatement("SELECT value FROM system WHERE name=?");
                    stmt.bindString(1, LineageSettings.System.STATUS_BAR_CLOCK);
                    long value = stmt.simpleQueryForLong();

                    if (value == 0) {
                        stmt = db.compileStatement("UPDATE system SET value=? WHERE name=?");
                        stmt.bindLong(1, 2);
                        stmt.bindString(2, LineageSettings.System.STATUS_BAR_CLOCK);
                        stmt.execute();
                    }
                    db.setTransactionSuccessful();
                } catch (SQLiteDoneException ex) {
                    // LineageSettings.System.STATUS_BAR_CLOCK is not set
                } finally {
                    if (stmt != null) stmt.close();
                    db.endTransaction();
                }

                // Remove LINEAGE_SETUP_WIZARD_COMPLETED
                db.beginTransaction();
                stmt = null;
                try {
                    stmt = db.compileStatement("DELETE FROM secure WHERE name=?");
                    stmt.bindString(1, LineageSettings.Secure.LINEAGE_SETUP_WIZARD_COMPLETED);
                    stmt.execute();
                    db.setTransactionSuccessful();
                } finally {
                    if (stmt != null) stmt.close();
                    db.endTransaction();
                }
            }
            upgradeVersion = 10;
        }

        if (upgradeVersion < 11) {
            // Move force_show_navbar to system
            if (mUserHandle == UserHandle.USER_OWNER) {
                db.beginTransaction();
                SQLiteStatement stmt = null;
                try {
                    stmt = db.compileStatement("SELECT value FROM global WHERE name=?");
                    stmt.bindString(1, LineageSettings.Global.DEV_FORCE_SHOW_NAVBAR);
                    long value = stmt.simpleQueryForLong();

                    stmt = db.compileStatement("INSERT INTO system (name, value) VALUES (?, ?)");
                    stmt.bindString(1, LineageSettings.System.FORCE_SHOW_NAVBAR);
                    stmt.bindLong(2, value);
                    stmt.execute();

                    stmt = db.compileStatement("DELETE FROM global WHERE name=?");
                    stmt.bindString(1, LineageSettings.Global.DEV_FORCE_SHOW_NAVBAR);
                    stmt.execute();

                    db.setTransactionSuccessful();
                } catch (SQLiteDoneException ex) {
                    // LineageSettings.Global.DEV_FORCE_SHOW_NAVBAR is not set
                } finally {
                    if (stmt != null) stmt.close();
                    db.endTransaction();
                }
            }
            upgradeVersion = 11;
        }

        if (upgradeVersion < 12) {
            if (mUserHandle == UserHandle.USER_OWNER) {
                db.beginTransaction();
                SQLiteStatement stmt = null;
                try {
                    stmt = db.compileStatement("SELECT value FROM system WHERE name=?");
                    stmt.bindString(1, LineageSettings.System.STATUS_BAR_BATTERY_STYLE);
                    long value = stmt.simpleQueryForLong();

                    long newValue = 0;
                    switch ((int) value) {
                        case 2:
                            newValue = 1;
                            break;
                        case 5:
                            newValue = 0;
                            break;
                        case 6:
                            newValue = 2;
                            break;
                    }

                    stmt = db.compileStatement("UPDATE system SET value=? WHERE name=?");
                    stmt.bindLong(1, newValue);
                    stmt.bindString(2, LineageSettings.System.STATUS_BAR_BATTERY_STYLE);
                    stmt.execute();
                    db.setTransactionSuccessful();
                } catch (SQLiteDoneException ex) {
                    // LineageSettings.System.STATUS_BAR_BATTERY_STYLE is not set
                } finally {
                    if (stmt != null) stmt.close();
                    db.endTransaction();
                }
            }
            upgradeVersion = 12;
        }

        if (upgradeVersion < 13) {
            // Update custom charging sound setting
            if (mUserHandle == UserHandle.USER_OWNER) {
                db.beginTransaction();
                SQLiteStatement stmt = null;
                try {
                    stmt = db.compileStatement("UPDATE global SET value=? WHERE name=?");
                    stmt.bindString(1, mContext.getResources()
                            .getString(R.string.def_power_notifications_ringtone));
                    stmt.bindString(2, LineageSettings.Global.POWER_NOTIFICATIONS_RINGTONE);
                    stmt.execute();
                    db.setTransactionSuccessful();
                } finally {
                    if (stmt != null) stmt.close();
                    db.endTransaction();
                }
            }
            upgradeVersion = 13;
        }

        if (upgradeVersion < 14) {
            // Update button/keyboard brightness range
            if (mUserHandle == UserHandle.USER_OWNER) {
                for (String key : new String[] {
                    LineageSettings.Secure.BUTTON_BRIGHTNESS,
                    LineageSettings.Secure.KEYBOARD_BRIGHTNESS,
                }) {
                    db.beginTransaction();
                    SQLiteStatement stmt = null;
                    try {
                        stmt = db.compileStatement(
                                "UPDATE secure SET value=round(value / 255.0, 2) WHERE name=?");
                        stmt.bindString(1, key);
                        stmt.execute();
                        db.setTransactionSuccessful();
                    } catch (SQLiteDoneException ex) {
                        // key is not set
                    } finally {
                        if (stmt != null) stmt.close();
                        db.endTransaction();
                    }
                }
            }
            upgradeVersion = 14;
        }
        // *** Remember to update DATABASE_VERSION above!
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
        if (mUserHandle == UserHandle.USER_OWNER) {
            loadGlobalSettings(db);
        }
    }

    private void loadSecureSettings(SQLiteDatabase db) {
        SQLiteStatement stmt = null;
        try {
            stmt = db.compileStatement("INSERT OR IGNORE INTO secure(name,value)"
                    + " VALUES(?,?);");
            // Secure
            loadBooleanSetting(stmt, LineageSettings.Secure.ADVANCED_MODE,
                    R.bool.def_advanced_mode);

            loadBooleanSetting(stmt, LineageSettings.Secure.STATS_COLLECTION,
                    R.bool.def_stats_collection);

            loadBooleanSetting(stmt, LineageSettings.Secure.LOCKSCREEN_VISUALIZER_ENABLED,
                    R.bool.def_lockscreen_visualizer);

            loadBooleanSetting(stmt, LineageSettings.Secure.VOLUME_PANEL_ON_LEFT,
                    R.bool.def_volume_panel_on_left);

            loadStringSetting(stmt,
                    LineageSettings.Secure.PROTECTED_COMPONENT_MANAGERS,
                    R.string.def_protected_component_managers);
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
            loadIntegerSetting(stmt, LineageSettings.System.FORCE_SHOW_NAVBAR,
                    R.integer.def_force_show_navbar);

            loadIntegerSetting(stmt, LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN,
                    R.integer.def_qs_quick_pulldown);

            loadIntegerSetting(stmt, LineageSettings.System.BATTERY_LIGHT_BRIGHTNESS_LEVEL,
                    R.integer.def_battery_brightness_level);

            loadIntegerSetting(stmt, LineageSettings.System.BATTERY_LIGHT_BRIGHTNESS_LEVEL_ZEN,
                    R.integer.def_battery_brightness_level_zen);

            loadIntegerSetting(stmt, LineageSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                    R.integer.def_notification_brightness_level);

            loadIntegerSetting(stmt, LineageSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_ZEN,
                    R.integer.def_notification_brightness_level_zen);

            loadBooleanSetting(stmt, LineageSettings.System.SYSTEM_PROFILES_ENABLED,
                    R.bool.def_profiles_enabled);

            loadBooleanSetting(stmt, LineageSettings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE,
                    R.bool.def_notification_pulse_custom_enable);

            loadBooleanSetting(stmt, LineageSettings.System.SWAP_VOLUME_KEYS_ON_ROTATION,
                    R.bool.def_swap_volume_keys_on_rotation);

            loadIntegerSetting(stmt, LineageSettings.System.STATUS_BAR_BATTERY_STYLE,
                    R.integer.def_battery_style);

            loadIntegerSetting(stmt, LineageSettings.System.STATUS_BAR_CLOCK,
                    R.integer.def_clock_position);

            if (mContext.getResources().getBoolean(R.bool.def_notification_pulse_custom_enable)) {
                loadStringSetting(stmt, LineageSettings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES,
                        R.string.def_notification_pulse_custom_value);
            }
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
            loadBooleanSetting(stmt,
                    LineageSettings.Global.POWER_NOTIFICATIONS_ENABLED,
                    R.bool.def_power_notifications_enabled);

            loadBooleanSetting(stmt,
                    LineageSettings.Global.POWER_NOTIFICATIONS_VIBRATE,
                    R.bool.def_power_notifications_vibrate);

            loadStringSetting(stmt,
                    LineageSettings.Global.POWER_NOTIFICATIONS_RINGTONE,
                    R.string.def_power_notifications_ringtone);

            loadIntegerSetting(stmt, LineageSettings.Global.WEATHER_TEMPERATURE_UNIT,
                    R.integer.def_temperature_unit);
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
                e.printStackTrace();
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
}
