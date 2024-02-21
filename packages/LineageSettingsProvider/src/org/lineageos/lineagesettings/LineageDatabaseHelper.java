/**
 * Copyright (C) 2015-2016 The CyanogenMod Project
 *               2017-2023 The LineageOS Project
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

import android.Manifest;
import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.net.ConnectivitySettingsManager;
import android.os.Environment;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;

import lineageos.providers.LineageSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The LineageDatabaseHelper allows creation of a database to store Lineage specific settings for a user
 * in System, Secure, and Global tables.
 */
public class LineageDatabaseHelper extends SQLiteOpenHelper{
    private static final String TAG = "LineageDatabaseHelper";
    private static final boolean LOCAL_LOGV = false;

    private static final String DATABASE_NAME = "lineagesettings.db";
    private static final int DATABASE_VERSION = 19;

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
     * Creates an instance of {@link LineageDatabaseHelper}
     * @param context
     * @param userId
     */
    public LineageDatabaseHelper(Context context, int userId) {
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
            createDbTable(db, LineageTableNames.TABLE_SYSTEM);
            createDbTable(db, LineageTableNames.TABLE_SECURE);

            if (mUserHandle == UserHandle.USER_SYSTEM) {
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
            // Used to run loadSettings()
            upgradeVersion = 2;
        }

        if (upgradeVersion < 3) {
            // Used to set LineageSettings.Secure.PROTECTED_COMPONENT_MANAGERS
            upgradeVersion = 3;
        }

        if (upgradeVersion < 4) {
            // Used to set LineageSettings.Secure.LINEAGE_SETUP_WIZARD_COMPLETE
            upgradeVersion = 4;
        }

        if (upgradeVersion < 5) {
            // Used to set LineageSettings.Global.WEATHER_TEMPERATURE_UNIT
            upgradeVersion = 5;
        }

        if (upgradeVersion < 6) {
            // Used to move LineageSettings.Secure.DEV_FORCE_SHOW_NAVBAR to global
            upgradeVersion = 6;
        }

        if (upgradeVersion < 7) {
            // Used to migrate LineageSettings.System.STATUS_BAR_CLOCK
            upgradeVersion = 7;
        }

        if (upgradeVersion < 8) {
            // Used to set LineageSettings.Secure.PROTECTED_COMPONENT_MANAGERS
            upgradeVersion = 8;
        }

        if (upgradeVersion < 9) {
            // Used to migrate LineageSettings.System.KEY_* actions
            upgradeVersion = 9;
        }

        if (upgradeVersion < 10) {
            // Used to migrate LineageSettings.System.STATUS_BAR_CLOCK
            upgradeVersion = 10;
        }

        if (upgradeVersion < 11) {
            // Used to move LineageSettings.Global.DEV_FORCE_SHOW_NAVBAR to system
            upgradeVersion = 11;
        }

        if (upgradeVersion < 12) {
            // Used to migrate LineageSettings.System.STATUS_BAR_BATTERY_STYLE
            upgradeVersion = 12;
        }

        if (upgradeVersion < 13) {
            // Used to migrate LineageSettings.Global.POWER_NOTIFICATIONS_RINGTONE
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

        if (upgradeVersion < 15) {
            if (mUserHandle == UserHandle.USER_OWNER) {
                loadRestrictedNetworkingModeSetting();
            }
            upgradeVersion = 15;
        }

        if (upgradeVersion < 16) {
            // Move trust_restrict_usb to global
            if (mUserHandle == UserHandle.USER_OWNER) {
                moveSettingsToNewTable(db, LineageTableNames.TABLE_SECURE,
                        LineageTableNames.TABLE_GLOBAL, new String[] {
                        LineageSettings.Global.TRUST_RESTRICT_USB
                }, true);
            }
            upgradeVersion = 16;
        }

        if (upgradeVersion < 17) {
            // Move berry_black_theme to secure
            moveSettingsToNewTable(db, LineageTableNames.TABLE_SYSTEM,
                    LineageTableNames.TABLE_SECURE, new String[] {
                    LineageSettings.Secure.BERRY_BLACK_THEME
            }, true);
            upgradeVersion = 17;
        }

        if (upgradeVersion < 18) {
            Integer defaultValue = mContext.getResources().getBoolean(
                    org.lineageos.platform.internal.R.bool.config_fingerprintWakeAndUnlock)
                    ? 1 : 0; // Reversed since they're reversed again below

            // Used to be LineageSettings.System.FINGERPRINT_WAKE_UNLOCK
            Integer oldSetting = readIntegerSetting(db, LineageTableNames.TABLE_SYSTEM,
                    "fingerprint_wake_unlock", defaultValue);

            // Reverse 0/1 values, migrate 2 to 1
            if (oldSetting.equals(0) || oldSetting.equals(2)) {
                oldSetting = 1;
            } else if (oldSetting.equals(1)) {
                oldSetting = 0;
            }

            // Previously Settings.Secure.SFPS_REQUIRE_SCREEN_ON_TO_AUTH_ENABLED
            Settings.Secure.putInt(mContext.getContentResolver(),
                    "sfps_require_screen_on_to_auth_enabled",
                    oldSetting);
            upgradeVersion = 18;
        }

        if (upgradeVersion < 19) {
            // Set default value based on config_fingerprintWakeAndUnlock
            boolean fingerprintWakeAndUnlock = mContext.getResources().getBoolean(
                    org.lineageos.platform.internal.R.bool.config_fingerprintWakeAndUnlock);
            // Previously Settings.Secure.SFPS_REQUIRE_SCREEN_ON_TO_AUTH_ENABLED
            Integer oldSetting = Settings.Secure.getInt(mContext.getContentResolver(),
                    "sfps_require_screen_on_to_auth_enabled", fingerprintWakeAndUnlock ? 0 : 1);
            // Flip value
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.SFPS_PERFORMANT_AUTH_ENABLED, oldSetting.equals(1) ? 0 : 1);
            upgradeVersion = 19;
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
            loadBooleanSetting(stmt, LineageSettings.Secure.STATS_COLLECTION,
                    R.bool.def_stats_collection);

            loadBooleanSetting(stmt, LineageSettings.Secure.LOCKSCREEN_VISUALIZER_ENABLED,
                    R.bool.def_lockscreen_visualizer);

            loadBooleanSetting(stmt, LineageSettings.Secure.VOLUME_PANEL_ON_LEFT,
                    R.bool.def_volume_panel_on_left);

            loadBooleanSetting(stmt, LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER,
                    R.bool.def_qs_show_brightness_slider);
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
            loadRestrictedNetworkingModeSetting();
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void loadRestrictedNetworkingModeSetting() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.RESTRICTED_NETWORKING_MODE, 1);
        try {
            List<PackageInfo> packages = new ArrayList<>();
            for (UserInfo userInfo : UserManager.get(mContext).getAliveUsers()) {
                packages.addAll(
                        AppGlobals.getPackageManager().getPackagesHoldingPermissions(
                                new String[]{Manifest.permission.INTERNET},
                                PackageManager.MATCH_UNINSTALLED_PACKAGES,
                                userInfo.id
                        ).getList());
            }
            Set<Integer> uids = packages.stream().map(
                    packageInfo -> packageInfo.applicationInfo.uid)
                    .collect(Collectors.toSet());
            ConnectivitySettingsManager.setUidsAllowedOnRestrictedNetworks(mContext, uids);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set uids allowed on restricted networks");
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

    private static void ensureTableIsValid(final String tableName) {
        switch (tableName) {
            case LineageTableNames.TABLE_GLOBAL:
            case LineageTableNames.TABLE_SECURE:
            case LineageTableNames.TABLE_SYSTEM:
                break;
            default:
                throw new IllegalArgumentException(
                        "Table '" + tableName + "' is not a valid Lineage database table");
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
