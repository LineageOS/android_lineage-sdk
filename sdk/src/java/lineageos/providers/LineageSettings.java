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

package lineageos.providers;

import com.android.internal.util.ArrayUtils;

import android.content.ContentResolver;
import android.content.IContentProvider;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AndroidException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import lineageos.trust.TrustInterface;

/**
 * LineageSettings contains Lineage specific preferences in System, Secure, and Global.
 */
public final class LineageSettings {
    private static final String TAG = "LineageSettings";
    private static final boolean LOCAL_LOGV = false;

    public static final String AUTHORITY = "lineagesettings";

    public static class LineageSettingNotFoundException extends AndroidException {
        public LineageSettingNotFoundException(String msg) {
            super(msg);
        }
    }

    // Intent actions for Settings
    /**
     * Activity Action: Show Data Usage Summary
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    public static final String ACTION_DATA_USAGE = "lineageos.settings.ACTION_DATA_USAGE";

    /**
     * Activity Action: Show LiveDisplay settings
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    public static final String ACTION_LIVEDISPLAY_SETTINGS =
            "lineageos.settings.LIVEDISPLAY_SETTINGS";

    /**
     * Activity Action: Show Trust interface settings
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    public static final String ACTION_TRUST_INTERFACE =
            "lineageos.settings.TRUST_INTERFACE";

    // region Call Methods

    /**
     * @hide - User handle argument extra to the fast-path call()-based requests
     */
    public static final String CALL_METHOD_USER_KEY = "_user";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'system' table.
     */
    public static final String CALL_METHOD_GET_SYSTEM = "GET_system";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'secure' table.
     */
    public static final String CALL_METHOD_GET_SECURE = "GET_secure";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'global' table.
     */
    public static final String CALL_METHOD_GET_GLOBAL = "GET_global";

    /**
     * @hide - Private call() method to write to 'system' table
     */
    public static final String CALL_METHOD_PUT_SYSTEM = "PUT_system";

    /**
     * @hide - Private call() method to write to 'secure' table
     */
    public static final String CALL_METHOD_PUT_SECURE = "PUT_secure";

    /**
     * @hide - Private call() method to write to 'global' table
     */
    public static final String CALL_METHOD_PUT_GLOBAL= "PUT_global";

    /**
     * @hide - Private call() method on LineageSettingsProvider to migrate Lineage settings
     */
    public static final String CALL_METHOD_MIGRATE_SETTINGS = "migrate_settings";

    /**
     * @hide - Private call() method on LineageSettingsProvider to migrate Lineage settings for a user
     */
    public static final String CALL_METHOD_MIGRATE_SETTINGS_FOR_USER = "migrate_settings_for_user";

    /**
     * @hide - Private call() method to list the entire system table
     */
    public static final String CALL_METHOD_LIST_SYSTEM = "LIST_system";

    /**
     * @hide - Private call() method to list the entire secure table
     */
    public static final String CALL_METHOD_LIST_SECURE = "LIST_secure";

    /**
     * @hide - Private call() method to list the entire global table
     */
    public static final String CALL_METHOD_LIST_GLOBAL = "LIST_global";

    /**
     * @hide - Private call() method to delete an entry from the system table
     */
    public static final String CALL_METHOD_DELETE_SYSTEM = "DELETE_system";

    /**
     * @hide - Private call() method to delete an entry from the secure table
     */
    public static final String CALL_METHOD_DELETE_SECURE = "DELETE_secure";

    /**
     * @hide - Private call() method to delete an entry from the global table
     */
    public static final String CALL_METHOD_DELETE_GLOBAL = "DELETE_global";

    // endregion

    // Thread-safe.
    private static class NameValueCache {
        private final String mVersionSystemProperty;
        private final Uri mUri;

        private static final String[] SELECT_VALUE_PROJECTION =
                new String[] { Settings.NameValueTable.VALUE };
        private static final String NAME_EQ_PLACEHOLDER = "name=?";

        // Must synchronize on 'this' to access mValues and mValuesVersion.
        private final HashMap<String, String> mValues = new HashMap<String, String>();
        private long mValuesVersion = 0;

        // Initially null; set lazily and held forever.  Synchronized on 'this'.
        private IContentProvider mContentProvider = null;

        // The method we'll call (or null, to not use) on the provider
        // for the fast path of retrieving settings.
        private final String mCallGetCommand;
        private final String mCallSetCommand;

        public NameValueCache(String versionSystemProperty, Uri uri,
                String getCommand, String setCommand) {
            mVersionSystemProperty = versionSystemProperty;
            mUri = uri;
            mCallGetCommand = getCommand;
            mCallSetCommand = setCommand;
        }

        private IContentProvider lazyGetProvider(ContentResolver cr) {
            IContentProvider cp;
            synchronized (this) {
                cp = mContentProvider;
                if (cp == null) {
                    cp = mContentProvider = cr.acquireProvider(mUri.getAuthority());
                }
            }
            return cp;
        }

        /**
         * Puts a string name/value pair into the content provider for the specified user.
         * @param cr The content resolver to use.
         * @param name The name of the key to put into the content provider.
         * @param value The value to put into the content provider.
         * @param userId The user id to use for the content provider.
         * @return Whether the put was successful.
         */
        public boolean putStringForUser(ContentResolver cr, String name, String value,
                final int userId) {
            try {
                Bundle arg = new Bundle();
                arg.putString(Settings.NameValueTable.VALUE, value);
                arg.putInt(CALL_METHOD_USER_KEY, userId);
                IContentProvider cp = lazyGetProvider(cr);
                cp.call(cr.getPackageName(), cr.getAttributionTag(),
                        AUTHORITY, mCallSetCommand, name, arg);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't set key " + name + " in " + mUri, e);
                return false;
            }
            return true;
        }

        /**
         * Gets a string value with the specified name from the name/value cache if possible. If
         * not, it will use the content resolver and perform a query.
         * @param cr Content resolver to use if name/value cache does not contain the name or if
         *           the cache version is older than the current version.
         * @param name The name of the key to search for.
         * @param userId The user id of the cache to look in.
         * @return The string value of the specified key.
         */
        public String getStringForUser(ContentResolver cr, String name, final int userId) {
            final boolean isSelf = (userId == UserHandle.myUserId());
            if (isSelf) {
                if (LOCAL_LOGV) Log.d(TAG, "get setting for self");
                long newValuesVersion = SystemProperties.getLong(mVersionSystemProperty, 0);

                // Our own user's settings data uses a client-side cache
                synchronized (this) {
                    if (mValuesVersion != newValuesVersion) {
                        if (LOCAL_LOGV || false) {
                            Log.v(TAG, "invalidate [" + mUri.getLastPathSegment() + "]: current "
                                    + newValuesVersion + " != cached " + mValuesVersion);
                        }

                        mValues.clear();
                        mValuesVersion = newValuesVersion;
                    }

                    if (mValues.containsKey(name)) {
                        return mValues.get(name);  // Could be null, that's OK -- negative caching
                    }
                }
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "get setting for user " + userId
                        + " by user " + UserHandle.myUserId() + " so skipping cache");
            }

            IContentProvider cp = lazyGetProvider(cr);

            // Try the fast path first, not using query().  If this
            // fails (alternate Settings provider that doesn't support
            // this interface?) then we fall back to the query/table
            // interface.
            if (mCallGetCommand != null) {
                try {
                    Bundle args = null;
                    if (!isSelf) {
                        args = new Bundle();
                        args.putInt(CALL_METHOD_USER_KEY, userId);
                    }
                    Bundle b = cp.call(cr.getPackageName(), cr.getAttributionTag(),
                            AUTHORITY, mCallGetCommand, name, args);
                    if (b != null) {
                        String value = b.getPairValue();
                        // Don't update our cache for reads of other users' data
                        if (isSelf) {
                            synchronized (this) {
                                mValues.put(name, value);
                            }
                        } else {
                            if (LOCAL_LOGV) Log.i(TAG, "call-query of user " + userId
                                    + " by " + UserHandle.myUserId()
                                    + " so not updating cache");
                        }
                        return value;
                    }
                    // If the response Bundle is null, we fall through
                    // to the query interface below.
                } catch (RemoteException e) {
                    // Not supported by the remote side?  Fall through
                    // to query().
                }
            }

            Cursor c = null;
            try {
                Bundle queryArgs = ContentResolver.createSqlQueryBundle(
                        NAME_EQ_PLACEHOLDER, new String[]{name}, null);
                c = cp.query(cr.getPackageName(), cr.getAttributionTag(), mUri,
                        SELECT_VALUE_PROJECTION, queryArgs, null);
                if (c == null) {
                    Log.w(TAG, "Can't get key " + name + " from " + mUri);
                    return null;
                }

                String value = c.moveToNext() ? c.getString(0) : null;
                synchronized (this) {
                    mValues.put(name, value);
                }
                if (LOCAL_LOGV) {
                    Log.v(TAG, "cache miss [" + mUri.getLastPathSegment() + "]: " +
                            name + " = " + (value == null ? "(null)" : value));
                }
                return value;
            } catch (RemoteException e) {
                Log.w(TAG, "Can't get key " + name + " from " + mUri, e);
                return null;  // Return null, but don't cache it.
            } finally {
                if (c != null) c.close();
            }
        }
    }

    // region Validators

    /** @hide */
    public static interface Validator {
        public boolean validate(String value);
    }

    private static final Validator sBooleanValidator =
            new DiscreteValueValidator(new String[] {"0", "1"});

    private static final Validator sNonNegativeIntegerValidator = new Validator() {
        @Override
        public boolean validate(String value) {
            try {
                return Integer.parseInt(value) >= 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    };

    private static final Validator sUriValidator = new Validator() {
        @Override
        public boolean validate(String value) {
            try {
                Uri.decode(value);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    };

    private static final Validator sColorValidator =
            new InclusiveIntegerRangeValidator(Integer.MIN_VALUE, Integer.MAX_VALUE);

    private static final Validator sAlwaysTrueValidator = new Validator() {
        @Override
        public boolean validate(String value) {
            return true;
        }
    };

    private static final Validator sNonNullStringValidator = new Validator() {
        @Override
        public boolean validate(String value) {
            return value != null;
        }
    };

    private static final class DiscreteValueValidator implements Validator {
        private final String[] mValues;

        public DiscreteValueValidator(String[] values) {
            mValues = values;
        }

        @Override
        public boolean validate(String value) {
            return ArrayUtils.contains(mValues, value);
        }
    }

    private static final class InclusiveIntegerRangeValidator implements Validator {
        private final int mMin;
        private final int mMax;

        public InclusiveIntegerRangeValidator(int min, int max) {
            mMin = min;
            mMax = max;
        }

        @Override
        public boolean validate(String value) {
            try {
                final int intValue = Integer.parseInt(value);
                return intValue >= mMin && intValue <= mMax;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    private static final class InclusiveFloatRangeValidator implements Validator {
        private final float mMin;
        private final float mMax;

        public InclusiveFloatRangeValidator(float min, float max) {
            mMin = min;
            mMax = max;
        }

        @Override
        public boolean validate(String value) {
            try {
                final float floatValue = Float.parseFloat(value);
                return floatValue >= mMin && floatValue <= mMax;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    private static final class DelimitedListValidator implements Validator {
        private final ArraySet<String> mValidValueSet;
        private final String mDelimiter;
        private final boolean mAllowEmptyList;

        public DelimitedListValidator(String[] validValues, String delimiter,
                                      boolean allowEmptyList) {
            mValidValueSet = new ArraySet<String>(Arrays.asList(validValues));
            mDelimiter = delimiter;
            mAllowEmptyList = allowEmptyList;
        }

        @Override
        public boolean validate(String value) {
            ArraySet<String> values = new ArraySet<String>();
            if (!TextUtils.isEmpty(value)) {
                final String[] array = TextUtils.split(value, Pattern.quote(mDelimiter));
                for (String item : array) {
                    if (TextUtils.isEmpty(item)) {
                        continue;
                    }
                    values.add(item);
                }
            }
            if (values.size() > 0) {
                values.removeAll(mValidValueSet);
                // values.size() will be non-zero if it contains any values not in
                // mValidValueSet
                return values.size() == 0;
            } else if (mAllowEmptyList) {
                return true;
            }

            return false;
        }
    }
    // endregion Validators

    /**
     * System settings, containing miscellaneous Lineage system preferences. This table holds simple
     * name/value pairs. There are convenience functions for accessing individual settings entries.
     */
    public static final class System extends Settings.NameValueTable {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/system");

        public static final String SYS_PROP_LINEAGE_SETTING_VERSION = "sys.lineage_settings_system_version";

        private static final NameValueCache sNameValueCache = new NameValueCache(
                SYS_PROP_LINEAGE_SETTING_VERSION,
                CONTENT_URI,
                CALL_METHOD_GET_SYSTEM,
                CALL_METHOD_PUT_SYSTEM);

        /** @hide */
        protected static final ArraySet<String> MOVED_TO_SECURE;
        static {
            MOVED_TO_SECURE = new ArraySet<>(1);
        }

        // region Methods

        /**
         * Put a delimited list as a string
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split
         * @param list to join and store
         * @hide
         */
        public static void putListAsDelimitedString(ContentResolver resolver, String name,
                                                    String delimiter, List<String> list) {
            String store = TextUtils.join(delimiter, list);
            putString(resolver, name, store);
        }

        /**
         * Get a delimited string returned as a list
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split the list with
         * @return list of strings for a specific Settings.Secure item
         * @hide
         */
        public static List<String> getDelimitedStringAsList(ContentResolver resolver, String name,
                                                            String delimiter) {
            String baseString = getString(resolver, name);
            List<String> list = new ArrayList<String>();
            if (!TextUtils.isEmpty(baseString)) {
                final String[] array = TextUtils.split(baseString, Pattern.quote(delimiter));
                for (String item : array) {
                    if (TextUtils.isEmpty(item)) {
                        continue;
                    }
                    list.add(item);
                }
            }
            return list;
        }

        /**
         * Construct the content URI for a particular name/value pair, useful for monitoring changes
         * with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI
         */
        public static Uri getUriFor(String name) {
            return Settings.NameValueTable.getUriFor(CONTENT_URI, name);
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @param def Value to return if the setting is not defined.
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name, String def) {
            String str = getStringForUser(resolver, name, UserHandle.myUserId());
            return str == null ? def : str;
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userId) {
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from LineageSettings.System"
                        + " to LineageSettings.Secure, value is unchanged.");
                return LineageSettings.Secure.getStringForUser(resolver, name, userId);
            }
            return sNameValueCache.getStringForUser(resolver, name, userId);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver, String name, String value,
               int userId) {
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from LineageSettings.System"
                        + " to LineageSettings.Secure, value is unchanged.");
                return false;
            }
            return sNameValueCache.putStringForUser(resolver, name, value, userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int def, int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link LineageSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws LineageSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws LineageSettingNotFoundException {
            return getIntForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int userId)
                throws LineageSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new LineageSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putIntForUser(ContentResolver cr, String name, int value,
                int userId) {
            return putStringForUser(cr, name, Integer.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            return getLongForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, long def,
                int userId) {
            String valString = getStringForUser(cr, name, userId);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link LineageSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws LineageSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws LineageSettingNotFoundException {
            return getLongForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, int userId)
                throws LineageSettingNotFoundException {
            String valString = getStringForUser(cr, name, userId);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new LineageSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putLongForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putLongForUser(ContentResolver cr, String name, long value,
                int userId) {
            return putStringForUser(cr, name, Long.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            return getFloatForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, float def,
                int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link LineageSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws LineageSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws LineageSettingNotFoundException {
            return getFloatForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, int userId)
                throws LineageSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            if (v == null) {
                throw new LineageSettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new LineageSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putFloatForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putFloatForUser(ContentResolver cr, String name, float value,
                int userId) {
            return putStringForUser(cr, name, Float.toString(value), userId);
        }

        // endregion

        // region System Settings

        /**
         * Whether to attach a queue to media notifications.
         * 0 = 0ff, 1 = on
         */
        public static final String NOTIFICATION_PLAY_QUEUE = "notification_play_queue";

        /** @hide */
        public static final Validator NOTIFICATION_PLAY_QUEUE_VALIDATOR = sBooleanValidator;

        /**
         * Whether the HighTouchSensitivity is activated or not.
         * 0 = off, 1 = on
         */
        public static final String HIGH_TOUCH_SENSITIVITY_ENABLE =
                "high_touch_sensitivity_enable";

        /** @hide */
        public static final Validator HIGH_TOUCH_SENSITIVITY_ENABLE_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to enable system profiles feature
         * 0 = off, 1 = on
         */
        public static final String SYSTEM_PROFILES_ENABLED = "system_profiles_enabled";

        /** @hide */
        public static final Validator SYSTEM_PROFILES_ENABLED_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to show the clock in the right or left position or show it in the center
         * 0: show the clock in the right position (LTR)
         * 1: show the clock in the center
         * 2: show the clock in the left position (LTR)
         * default: 0
         */
        public static final String STATUS_BAR_CLOCK = "status_bar_clock";

        /** @hide */
        public static final Validator STATUS_BAR_CLOCK_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

        /**
         * Whether the notification light will be allowed when in zen mode during downtime
         */
        public static final String ZEN_ALLOW_LIGHTS = "allow_lights";

        /** @hide */
        public static final Validator ZEN_ALLOW_LIGHTS_VALIDATOR = sBooleanValidator;

        /**
         * Whether the notification light will be allowed when in zen priority mode during downtime
         */
        public static final String ZEN_PRIORITY_ALLOW_LIGHTS = "zen_priority_allow_lights";

        /** @hide */
        public static final Validator ZEN_PRIORITY_ALLOW_LIGHTS_VALIDATOR = sBooleanValidator;

        /**
         * Whether vibrations are allowed when in zen priority mode during downtime
         * 0: no vibrations
         * 1: vibrations for calls only
         * 2: vibrations for calls and notifications
         * @hide
         */
        public static final String ZEN_PRIORITY_VIBRATION_MODE = "zen_priority_vibration_mode";

        /** @hide */
        public static final Validator ZEN_PRIORITY_VIBRATION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

        /**
         * Display style of AM/PM next to clock in status bar
         * 0: Normal display (Eclair stock)
         * 1: Small display (Froyo stock)
         * 2: No display (Gingerbread/ICS stock)
         * default: 2
         */
        public static final String STATUS_BAR_AM_PM = "status_bar_am_pm";

        /** @hide */
        public static final Validator STATUS_BAR_AM_PM_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

        /**
         * Display style of the status bar battery information
         * 0: Display the battery an icon in portrait mode
         * 1: Display the battery as a circle
         * 2: Display the battery as plain text
         * default: 0
         */
        public static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";

        /** @hide */
        public static final Validator STATUS_BAR_BATTERY_STYLE_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

        /**
         * Status bar battery %
         * 0: Hide the battery percentage
         * 1: Display the battery percentage inside the icon
         * 2: Display the battery percentage next to the icon
         */
        public static final String STATUS_BAR_SHOW_BATTERY_PERCENT =
                "status_bar_show_battery_percent";

        /** @hide */
        public static final Validator STATUS_BAR_SHOW_BATTERY_PERCENT_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

        /**
         * Whether the phone ringtone should be played in an increasing manner
         * 0 = 0ff, 1 = on
         */
        public static final String INCREASING_RING = "increasing_ring";

        /** @hide */
        public static final Validator INCREASING_RING_VALIDATOR = sBooleanValidator;

        /**
         * Start volume fraction for increasing ring volume
         */
        public static final String INCREASING_RING_START_VOLUME = "increasing_ring_start_vol";

        /** @hide */
        public static final Validator INCREASING_RING_START_VOLUME_VALIDATOR =
                new InclusiveFloatRangeValidator(0, 1);

        /**
         * Ramp up time (seconds) for increasing ring
         */
        public static final String INCREASING_RING_RAMP_UP_TIME = "increasing_ring_ramp_up_time";

        /** @hide */
        public static final Validator INCREASING_RING_RAMP_UP_TIME_VALIDATOR =
                new InclusiveIntegerRangeValidator(5, 60);

        /**
         * Volume Adjust Sounds Enable, This is the noise made when using volume hard buttons
         * Defaults to 1 - sounds enabled
         */
        public static final String VOLUME_ADJUST_SOUNDS_ENABLED = "volume_adjust_sounds_enabled";

        /** @hide */
        public static final Validator VOLUME_ADJUST_SOUNDS_ENABLED_VALIDATOR =
                sBooleanValidator;

        /**
         * Navigation controls to Use
         */
        public static final String NAV_BUTTONS = "nav_buttons";

        /** @hide */
        public static final Validator NAV_BUTTONS_VALIDATOR =
                new DelimitedListValidator(new String[] {"empty", "home", "back", "search",
                        "recent", "menu0", "menu1", "menu2", "dpad_left", "dpad_right"}, "|", true);

        /**
         * boolean value. toggles using arrow key locations on nav bar
         * as left and right dpad keys
         */
        public static final String NAVIGATION_BAR_MENU_ARROW_KEYS = "navigation_bar_menu_arrow_keys";

        /** @hide */
        public static final Validator NAVIGATION_BAR_MENU_ARROW_KEYS_VALIDATOR =
                sBooleanValidator;

        /**
         * boolean value. toggles swipe up hint in gestural nav mode
         */
        public static final String NAVIGATION_BAR_HINT = "navigation_bar_hint";

        /** @hide */
        public static final Validator NAVIGATION_BAR_HINT_VALIDATOR =
                sBooleanValidator;

        /**
         * Action to perform when the home key is long-pressed.
         * (Default can be configured via config_longPressOnHomeBehavior)
         * 0 - Nothing
         * 1 - Menu
         * 2 - App-switch
         * 3 - Search
         * 4 - Voice search
         * 5 - In-app search
         * 6 - Launch Camera
         * 7 - Action Sleep
         * 8 - Last app
         * 9 - Toggle split screen
         * 10 - Kill foreground app
         */
        public static final String KEY_HOME_LONG_PRESS_ACTION = "key_home_long_press_action";

        /** @hide */
        public static final Validator KEY_HOME_LONG_PRESS_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Action to perform when the home key is double-tapped.
         * (Default can be configured via config_doubleTapOnHomeBehavior)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         */
        public static final String KEY_HOME_DOUBLE_TAP_ACTION = "key_home_double_tap_action";

        /** @hide */
        public static final Validator KEY_HOME_DOUBLE_TAP_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Action to perform when the back key is long-pressed.
         * (Default can be configured via config_longPressOnBackBehavior)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         */
        public static final String KEY_BACK_LONG_PRESS_ACTION = "key_back_long_press_action";

        /** @hide */
        public static final Validator KEY_BACK_LONG_PRESS_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Whether to wake the screen with the back key, the value is boolean.
         * 0 = 0ff, 1 = on
         */
        public static final String BACK_WAKE_SCREEN = "back_wake_screen";

        /** @hide */
        public static final Validator BACK_WAKE_SCREEN_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to wake the screen with the menu key, the value is boolean.
         * 0 = 0ff, 1 = on
         */
        public static final String MENU_WAKE_SCREEN = "menu_wake_screen";

        /** @hide */
        public static final Validator MENU_WAKE_SCREENN_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to answer the call with the volume keys, the value is boolean.
         * 0 = 0ff, 1 = on
         */
        public static final String VOLUME_ANSWER_CALL = "volume_answer_call";

        /** @hide */
        public static final Validator VOLUME_ANSWER_CALL_VALIDATOR = sBooleanValidator;

        /**
         * Whether to wake the screen with the volume keys, the value is boolean.
         * 0 = 0ff, 1 = on
         */
        public static final String VOLUME_WAKE_SCREEN = "volume_wake_screen";

        /** @hide */
        public static final Validator VOLUME_WAKE_SCREEN_VALIDATOR =
                sBooleanValidator;

        /**
         * Action to perform when the menu key is pressed. (Default is 1)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         */
        public static final String KEY_MENU_ACTION = "key_menu_action";

        /** @hide */
        public static final Validator KEY_MENU_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Action to perform when the menu key is long-pressed.
         * (Default is 0 on devices with a search key, 3 on devices without)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         */
        public static final String KEY_MENU_LONG_PRESS_ACTION = "key_menu_long_press_action";

        /** @hide */
        public static final Validator KEY_MENU_LONG_PRESS_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Action to perform when the assistant (search) key is pressed. (Default is 3)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         */
        public static final String KEY_ASSIST_ACTION = "key_assist_action";

        /** @hide */
        public static final Validator KEY_ASSIST_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Action to perform when the assistant (search) key is long-pressed. (Default is 4)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         */
        public static final String KEY_ASSIST_LONG_PRESS_ACTION = "key_assist_long_press_action";

        /** @hide */
        public static final Validator KEY_ASSIST_LONG_PRESS_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Action to perform when the app switch key is pressed. (Default is 2)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         */
        public static final String KEY_APP_SWITCH_ACTION = "key_app_switch_action";

        /** @hide */
        public static final Validator KEY_APP_SWITCH_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Action to perform when the app switch key is long-pressed. (Default is 0)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         */
        public static final String KEY_APP_SWITCH_LONG_PRESS_ACTION = "key_app_switch_long_press_action";

        /** @hide */
        public static final Validator KEY_APP_SWITCH_LONG_PRESS_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Action to perform when the screen edge is long-swiped. (Default is 0)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         */
        public static final String KEY_EDGE_LONG_SWIPE_ACTION = "key_edge_long_swipe_action";

        /** @hide */
        public static final Validator KEY_EDGE_LONG_SWIPE_ACTION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 10);

        /**
         * Whether to wake the screen with the home key, the value is boolean.
         * 0 = 0ff, 1 = on
         */
        public static final String HOME_WAKE_SCREEN = "home_wake_screen";

        /** @hide */
        public static final Validator HOME_WAKE_SCREEN_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to wake the screen with the assist key, the value is boolean.
         * 0 = 0ff, 1 = on
         */
        public static final String ASSIST_WAKE_SCREEN = "assist_wake_screen";

        /** @hide */
        public static final Validator ASSIST_WAKE_SCREEN_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to wake the screen with the app switch key, the value is boolean.
         * 0 = 0ff, 1 = on
         */
        public static final String APP_SWITCH_WAKE_SCREEN = "app_switch_wake_screen";

        /** @hide */
        public static final Validator APP_SWITCH_WAKE_SCREEN_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to wake the screen with the camera key half-press.
         * 0 = 0ff, 1 = on
         */
        public static final String CAMERA_WAKE_SCREEN = "camera_wake_screen";

        /** @hide */
        public static final Validator CAMERA_WAKE_SCREEN_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether or not to send device back to sleep if Camera button is released ("Peek")
         * 0 = 0ff, 1 = on
         */
        public static final String CAMERA_SLEEP_ON_RELEASE = "camera_sleep_on_release";

        /** @hide */
        public static final Validator CAMERA_SLEEP_ON_RELEASE_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to launch secure camera app when key is longpressed
         * 0 = 0ff, 1 = on
         */
        public static final String CAMERA_LAUNCH = "camera_launch";

        /** @hide */
        public static final Validator CAMERA_LAUNCH_VALIDATOR =
                sBooleanValidator;

        /**
         * Show icon when stylus is used
         * The value is boolean (1 or 0).
         */
        public static final String STYLUS_ICON_ENABLED = "stylus_icon_enabled";

        /** @hide */
        public static final Validator STYLUS_ICON_ENABLED_VALIDATOR =
                sBooleanValidator;

        /**
         * Swap volume buttons when the screen is rotated
         * 0 - Disabled
         * 1 - Enabled (screen is rotated by 90 or 180 degrees: phone, hybrid)
         * 2 - Enabled (screen is rotated by 180 or 270 degrees: tablet)
         */
        public static final String SWAP_VOLUME_KEYS_ON_ROTATION = "swap_volume_keys_on_rotation";

        /** @hide */
        public static final Validator SWAP_VOLUME_KEYS_ON_ROTATION_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

        /**
         * Activate torchlight when power button is
         * long-pressed while the display is off
         * The value is boolean (1 or 0).
         */
        public static final String TORCH_LONG_PRESS_POWER_GESTURE =
                "torch_long_press_power_gesture";

        /** @hide */
        public static final Validator TORCH_LONG_PRESS_POWER_GESTURE_VALIDATOR = sBooleanValidator;

        /**
         * When the torch has been turned on by long press on power,
         * automatically turn off after a configurable number of seconds.
         * The value is an integer number of seconds in the range 0-3600.
         * 0 means never automatically turn off.
         */
        public static final String TORCH_LONG_PRESS_POWER_TIMEOUT =
                "torch_long_press_power_timeout";

        /** @hide */
        public static final Validator TORCH_LONG_PRESS_POWER_TIMEOUT_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 3600);

        /**
         * Whether the button backlight is only lit when pressed (and not when screen is touched)
         * The value is boolean (1 or 0).
         */
        public static final String BUTTON_BACKLIGHT_ONLY_WHEN_PRESSED =
                "button_backlight_only_when_pressed";

        /** @hide */
        public static final Validator BUTTON_BACKLIGHT_ONLY_WHEN_PRESSED_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether the battery light should be enabled (if hardware supports it)
         * The value is boolean (1 or 0).
         */
        public static final String BATTERY_LIGHT_ENABLED = "battery_light_enabled";

        /** @hide */
        public static final Validator BATTERY_LIGHT_ENABLED_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether the battery LED should be disabled when the battery is fully charged.
         * The value is boolean (1 or 0).
         */
        public static final String BATTERY_LIGHT_FULL_CHARGE_DISABLED =
                "battery_light_full_charge_disabled";

        /** @hide */
        public static final Validator BATTERY_LIGHT_FULL_CHARGE_DISABLED_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether the battery LED should repeatedly flash when the battery is low
         * on charge. The value is boolean (1 or 0).
         */
        public static final String BATTERY_LIGHT_PULSE = "battery_light_pulse";

        /** @hide */
        public static final Validator BATTERY_LIGHT_PULSE_VALIDATOR =
                sBooleanValidator;

        /**
         * What color to use for the battery LED while charging - low
         */
        public static final String BATTERY_LIGHT_LOW_COLOR = "battery_light_low_color";

        /** @hide */
        public static final Validator BATTERY_LIGHT_LOW_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * What color to use for the battery LED while charging - medium
         */
        public static final String BATTERY_LIGHT_MEDIUM_COLOR = "battery_light_medium_color";

        /** @hide */
        public static final Validator BATTERY_LIGHT_MEDIUM_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * What color to use for the battery LED while charging - full
         */
        public static final String BATTERY_LIGHT_FULL_COLOR = "battery_light_full_color";

        /** @hide */
        public static final Validator BATTERY_LIGHT_FULL_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * Sprint MWI Quirk: Show message wait indicator notifications
         * @hide
         */
        public static final String ENABLE_MWI_NOTIFICATION = "enable_mwi_notification";

        /** @hide */
        public static final Validator ENABLE_MWI_NOTIFICATION_VALIDATOR =
                sBooleanValidator;

        /**
         * Check the proximity sensor during wakeup
         * 0 = 0ff, 1 = on
         */
        public static final String PROXIMITY_ON_WAKE = "proximity_on_wake";

        /** @hide */
        public static final Validator PROXIMITY_ON_WAKE_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to use dark theme
         * 0: automatic - based on wallpaper
         * 1: time - based on LiveDisplay status
         * 2: force light
         * 3: force dark
         *
         * @deprecated
         */
        @Deprecated
        public static final String BERRY_GLOBAL_STYLE = "berry_global_style";

        /** @hide */
        @Deprecated
        public static final Validator BERRY_GLOBAL_STYLE_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 3);

        /**
         * Current accent package name
         */
        @Deprecated
        public static final String BERRY_CURRENT_ACCENT = "berry_current_accent";

        /** @hide */
        @Deprecated
        public static final Validator BERRY_CURRENT_ACCENT_VALIDATOR =
                sNonNullStringValidator;

        /**
         * Current dark overlay package name
         */
        @Deprecated
        public static final String BERRY_DARK_OVERLAY = "berry_dark_overlay";

        /** @hide */
        @Deprecated
        public static final Validator BERRY_DARK_OVERLAY_VALIDATOR =
                sNonNullStringValidator;

        /**
         * Current application managing the style
         */
        @Deprecated
        public static final String BERRY_MANAGED_BY_APP = "berry_managed_by_app";

        /** @hide */
        @Deprecated
        public static final Validator BERRY_MANAGED_BY_APP_VALIDATOR =
                sNonNullStringValidator;

        /**
         * Whether to use black theme for dark mode
         */
        public static final String BERRY_BLACK_THEME = "berry_black_theme";

        /** @hide */
        public static final Validator BERRY_BLACK_THEME_VALIDATOR =
                sBooleanValidator;

        /**
         * Enable looking up of phone numbers of nearby places
         * 0 = 0ff, 1 = on
         */
        @Deprecated
        public static final String ENABLE_FORWARD_LOOKUP = "enable_forward_lookup";

        /** @hide */
        @Deprecated
        public static final Validator ENABLE_FORWARD_LOOKUP_VALIDATOR =
                sBooleanValidator;

        /**
         * Enable looking up of phone numbers of people
         * 0 = 0ff, 1 = on
         */
        @Deprecated
        public static final String ENABLE_PEOPLE_LOOKUP = "enable_people_lookup";

        /** @hide */
        @Deprecated
        public static final Validator ENABLE_PEOPLE_LOOKUP_VALIDATOR =
                sBooleanValidator;

        /**
         * Enable looking up of information of phone numbers not in the contacts
         * 0 = 0ff, 1 = on
         */
        @Deprecated
        public static final String ENABLE_REVERSE_LOOKUP = "enable_reverse_lookup";

        /** @hide */
        @Deprecated
        public static final Validator ENABLE_REVERSE_LOOKUP_VALIDATOR =
                sBooleanValidator;

        /**
         * The forward lookup provider to be utilized by the Dialer
         */
        @Deprecated
        public static final String FORWARD_LOOKUP_PROVIDER = "forward_lookup_provider";

        /** @hide */
        @Deprecated
        public static final Validator FORWARD_LOOKUP_PROVIDER_VALIDATOR = sAlwaysTrueValidator;

        /**
         * The people lookup provider to be utilized by the Dialer
         */
        @Deprecated
        public static final String PEOPLE_LOOKUP_PROVIDER = "people_lookup_provider";

        /** @hide */
        @Deprecated
        public static final Validator PEOPLE_LOOKUP_PROVIDER_VALIDATOR = sAlwaysTrueValidator;

        /**
         * The reverse lookup provider to be utilized by the Dialer
         */
        @Deprecated
        public static final String REVERSE_LOOKUP_PROVIDER = "reverse_lookup_provider";

        /** @hide */
        @Deprecated
        public static final Validator REVERSE_LOOKUP_PROVIDER_VALIDATOR = sAlwaysTrueValidator;

        /**
         * The OpenCNAM paid account ID to be utilized by the Dialer
         */
        public static final String DIALER_OPENCNAM_ACCOUNT_SID = "dialer_opencnam_account_sid";

        /** @hide */
        public static final Validator DIALER_OPENCNAM_ACCOUNT_SID_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * The OpenCNAM authentication token to be utilized by the Dialer
         */
        public static final String DIALER_OPENCNAM_AUTH_TOKEN = "dialer_opencnam_auth_token";

        /** @hide */
        public static final Validator DIALER_OPENCNAM_AUTH_TOKEN_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * Color temperature of the display during the day
         */
        public static final String DISPLAY_TEMPERATURE_DAY = "display_temperature_day";

        /** @hide */
        public static final Validator DISPLAY_TEMPERATURE_DAY_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 100000);

        /**
         * Color temperature of the display at night
         */
        public static final String DISPLAY_TEMPERATURE_NIGHT = "display_temperature_night";

        /** @hide */
        public static final Validator DISPLAY_TEMPERATURE_NIGHT_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 100000);

        /**
         * Display color temperature adjustment mode, one of DAY (default), NIGHT, or AUTO.
         */
        public static final String DISPLAY_TEMPERATURE_MODE = "display_temperature_mode";

        /** @hide */
        public static final Validator DISPLAY_TEMPERATURE_MODE_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 4);

        /**
         * Automatic outdoor mode
         * 0 = 0ff, 1 = on
         */
        public static final String DISPLAY_AUTO_OUTDOOR_MODE = "display_auto_outdoor_mode";

        /** @hide */
        public static final Validator DISPLAY_AUTO_OUTDOOR_MODE_VALIDATOR =
                sBooleanValidator;

        /**
         * Anti flicker
         * 0 = 0ff, 1 = on
         */
        public static final String DISPLAY_ANTI_FLICKER = "display_anti_flicker";

        /** @hide */
        public static final Validator DISPLAY_ANTI_FLICKER_VALIDATOR =
                sBooleanValidator;

        /**
         * Reader mode
         * 0 = 0ff, 1 = on
         */
        public static final String DISPLAY_READING_MODE = "display_reading_mode";

        /** @hide */
        public static final Validator DISPLAY_READING_MODE_VALIDATOR =
                sBooleanValidator;

        /**
         * Use display power saving features such as CABC or CABL
         * 0 = 0ff, 1 = on
         */
        public static final String DISPLAY_CABC = "display_low_power";

        /**
         * @deprecated Use {@link lineageos.providers.LineageSettings.System#DISPLAY_CABC} instead
         */
        @Deprecated
        public static final String DISPLAY_LOW_POWER = DISPLAY_CABC;

        /** @hide */
        public static final Validator DISPLAY_CABC_VALIDATOR =
                sBooleanValidator;

        /**
         * Use color enhancement feature of display
         * 0 = 0ff, 1 = on
         */
        public static final String DISPLAY_COLOR_ENHANCE = "display_color_enhance";

        /** @hide */
        public static final Validator DISPLAY_COLOR_ENHANCE_VALIDATOR =
                sBooleanValidator;

        /**
         * Use auto contrast optimization feature of display
         * 0 = 0ff, 1 = on
         */
        public static final String DISPLAY_AUTO_CONTRAST = "display_auto_contrast";

        /** @hide */
        public static final Validator DISPLAY_AUTO_CONTRAST_VALIDATOR =
                sBooleanValidator;

        /**
         * Manual display color adjustments (RGB values as floats, separated by spaces)
         */
        public static final String DISPLAY_COLOR_ADJUSTMENT = "display_color_adjustment";

        /** @hide */
        public static final Validator DISPLAY_COLOR_ADJUSTMENT_VALIDATOR =
                new Validator() {
                    @Override
                    public boolean validate(String value) {
                        String[] colorAdjustment = value == null ?
                                null : value.split(" ");
                        if (colorAdjustment != null && colorAdjustment.length != 3) {
                            return false;
                        }
                        Validator floatValidator = new InclusiveFloatRangeValidator(0, 1);
                        return colorAdjustment == null ||
                                floatValidator.validate(colorAdjustment[0]) &&
                                floatValidator.validate(colorAdjustment[1]) &&
                                floatValidator.validate(colorAdjustment[2]);
                    }
                };

        /**
         * Did we tell about how they can stop breaking their eyes?
         * @hide
         */
        public static final String LIVE_DISPLAY_HINTED = "live_display_hinted";

        /** @hide */
        public static final Validator LIVE_DISPLAY_HINTED_VALIDATOR =
                new InclusiveIntegerRangeValidator(-3, 1);

        /**
         * Did we tell the user about the trust brand and interface?
         * @hide
         */
        public static final String TRUST_INTERFACE_HINTED = "trust_interface_hinted";

        /** @hide */
        public static final Validator TRUST_INTERFACE_HINTED_VALIDATOR = sBooleanValidator;

        /**
         *  Enable statusbar double tap gesture on to put device to sleep
         *  0 = 0ff, 1 = on
         */
        public static final String DOUBLE_TAP_SLEEP_GESTURE = "double_tap_sleep_gesture";

        /** @hide */
        public static final Validator DOUBLE_TAP_SLEEP_GESTURE_VALIDATOR =
                sBooleanValidator;

        /**
         * Boolean value on whether to show weather in the statusbar
         * 0 = 0ff, 1 = on
         */
        public static final String STATUS_BAR_SHOW_WEATHER = "status_bar_show_weather";

        /** @hide */
        public static final Validator STATUS_BAR_SHOW_WEATHER_VALIDATOR =
                sBooleanValidator;

        /**
         * Show search bar in recents
         * 0 = Off, 1 = on
         */
        public static final String RECENTS_SHOW_SEARCH_BAR = "recents_show_search_bar";

        /** @hide */
        public static final Validator RECENTS_SHOW_SEARCH_BAR_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether navigation bar is placed on the left side in landscape mode
         * 0 = 0ff, 1 = on
         */
        public static final String NAVBAR_LEFT_IN_LANDSCAPE = "navigation_bar_left";

        /** @hide */
        public static final Validator NAVBAR_LEFT_IN_LANDSCAPE_VALIDATOR =
                sBooleanValidator;

        /**
         * Locale for secondary overlay on dialer for t9 search input
         */
        public static final String T9_SEARCH_INPUT_LOCALE = "t9_search_input_locale";

        /** @hide */
        public static final Validator T9_SEARCH_INPUT_LOCALE_VALIDATOR =
                new Validator() {
                    @Override
                    public boolean validate(String value) {
                        final Locale locale = new Locale(value);
                        return ArrayUtils.contains(Locale.getAvailableLocales(), locale);
                    }
                };

        /**
         * If all file types can be accepted over Bluetooth OBEX.
         * 0 = 0ff, 1 = on
         */
        public static final String BLUETOOTH_ACCEPT_ALL_FILES =
                "bluetooth_accept_all_files";

        /** @hide */
        public static final Validator BLUETOOTH_ACCEPT_ALL_FILES_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to scramble a pin unlock layout
         * 0 = 0ff, 1 = on
         */
        public static final String LOCKSCREEN_PIN_SCRAMBLE_LAYOUT =
                "lockscreen_scramble_pin_layout";

        /** @hide */
        public static final Validator LOCKSCREEN_PIN_SCRAMBLE_LAYOUT_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether keyguard will rotate to landscape mode
         * 0 = false, 1 = true
         */
        public static final String LOCKSCREEN_ROTATION = "lockscreen_rotation";

        /** @hide */
        public static final Validator LOCKSCREEN_ROTATION_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to show the alarm clock icon in the status bar.
         * 0 = 0ff, 1 = on
         */
        public static final String SHOW_ALARM_ICON = "show_alarm_icon";

        /** @hide */
        public static final Validator SHOW_ALARM_ICON_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to show the IME switcher in the status bar
         * 0 = 0ff, 1 = on
         */
        public static final String STATUS_BAR_IME_SWITCHER = "status_bar_ime_switcher";

        /** @hide */
        public static final Validator STATUS_BAR_IME_SWITCHER_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to allow one finger quick settings expansion on the side of the statusbar.
         * 0 = 0ff, 1 = right, 2 = left
         */
        public static final String STATUS_BAR_QUICK_QS_PULLDOWN = "qs_quick_pulldown";

        /** @hide */
        public static final Validator STATUS_BAR_QUICK_QS_PULLDOWN_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

        /**
         * Whether to show the brightness slider in quick settings panel.
         * 0 = Never, 1 = show when expanded, 2 = show always
         */
        public static final String QS_SHOW_BRIGHTNESS_SLIDER = "qs_show_brightness_slider";

        /** @hide */
        public static final Validator QS_SHOW_BRIGHTNESS_SLIDER_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

        /**
         * Whether to control brightness from status bar
         * 0 = 0ff, 1 = on
         */
        public static final String STATUS_BAR_BRIGHTNESS_CONTROL = "status_bar_brightness_control";

        /** @hide */
        public static final Validator STATUS_BAR_BRIGHTNESS_CONTROL_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether or not volume button music controls should be enabled to seek media tracks
         * 0 = 0ff, 1 = on
         */
        public static final String VOLBTN_MUSIC_CONTROLS = "volbtn_music_controls";

        /** @hide */
        public static final Validator VOLBTN_MUSIC_CONTROLS_VALIDATOR =
                sBooleanValidator;

        /**
         * Use EdgeGesture Service for system gestures in PhoneWindowManager
         * 0 = 0ff, 1 = on
         */
        public static final String USE_EDGE_SERVICE_FOR_GESTURES = "edge_service_for_gestures";

        /** @hide */
        public static final Validator USE_EDGE_SERVICE_FOR_GESTURES_VALIDATOR =
                sBooleanValidator;

        /**
         * Show the pending notification counts as overlays on the status bar
         */
        public static final String STATUS_BAR_NOTIF_COUNT = "status_bar_notif_count";

        /** @hide */
        public static final Validator STATUS_BAR_NOTIF_COUNT_VALIDATOR =
                sBooleanValidator;

        /**
         * Call recording format value
         * 0: AMR_WB
         * 1: MPEG_4
         * Default: 0
         */
        public static final String CALL_RECORDING_FORMAT = "call_recording_format";

        /** @hide */
        public static final Validator CALL_RECORDING_FORMAT_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 1);

        /**
         * Contains the battery light maximum brightness to use.
         * Values range from 1 to 255
         */
        public static final String BATTERY_LIGHT_BRIGHTNESS_LEVEL =
                "battery_light_brightness_level";

        /**
         * Contains the battery light maximum brightness to use when Do Not
         * Disturb is active.
         * Values range from 1 to 255
         */
        public static final String BATTERY_LIGHT_BRIGHTNESS_LEVEL_ZEN =
                "battery_light_brightness_level_zen";

        /** @hide */
        public static final Validator BATTERY_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR =
                new InclusiveIntegerRangeValidator(1, 255);

        /**
         * Contains the notifications light maximum brightness to use.
         * Values range from 1 to 255
         */
        public static final String NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL =
                "notification_light_brightness_level";

        /**
         * Contains the notifications light maximum brightness to use when Do Not
         * Disturb is active.
         * Values range from 1 to 255
         */
        public static final String NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_ZEN =
                "notification_light_brightness_level_zen";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR =
                new InclusiveIntegerRangeValidator(1, 255);

        /**
         * Whether to allow notifications with the screen on or DayDreams.
         * The value is boolean (1 or 0). Default will always be false.
         */
        public static final String NOTIFICATION_LIGHT_SCREEN_ON =
                "notification_light_screen_on_enable";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_SCREEN_ON_VALIDATOR =
                sBooleanValidator;

        /**
         * What color to use for the notification LED by default
         */
        public static final String NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR =
                "notification_light_pulse_default_color";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * How long to flash the notification LED by default
         */
        public static final String NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON =
                "notification_light_pulse_default_led_on";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * How long to wait between flashes for the notification LED by default
         */
        public static final String NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF =
                "notification_light_pulse_default_led_off";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * What color to use for the missed call notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CALL_COLOR =
                "notification_light_pulse_call_color";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CALL_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * How long to flash the missed call notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CALL_LED_ON =
                "notification_light_pulse_call_led_on";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CALL_LED_ON_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * How long to wait between flashes for the missed call notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF =
                "notification_light_pulse_call_led_off";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * What color to use for the voicemail notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR =
                "notification_light_pulse_vmail_color";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * How long to flash the voicemail notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON =
                "notification_light_pulse_vmail_led_on";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * How long to wait between flashes for the voicemail notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF =
                "notification_light_pulse_vmail_led_off";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * Whether to use the custom LED values for the notification pulse LED.
         * 0 = 0ff, 1 = on
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE =
                "notification_light_pulse_custom_enable";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE_VALIDATOR =
                sBooleanValidator;

        /**
         * Which custom LED values to use for the notification pulse LED.
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES =
                "notification_light_pulse_custom_values";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES_VALIDATOR =
                new Validator() {
                    @Override
                    public boolean validate(String value) {
                        if (TextUtils.isEmpty(value)) {
                            return true;
                        }

                        for (String packageValuesString : value.split("\\|")) {
                            String[] packageValues = packageValuesString.split("=");
                            if (packageValues.length != 2) {
                                if (LOCAL_LOGV) {
                                    Log.d(TAG, "Incorrect number of package values: "
                                            + packageValues.length);
                                }
                                return false;
                            }
                            String packageName = packageValues[0];
                            if (TextUtils.isEmpty(packageName)) {
                                if (LOCAL_LOGV)  Log.d(TAG, "Empty package name");
                                return false;
                            }
                            String[] values = packageValues[1].split(";");
                            if (values.length != 3) {
                                if (LOCAL_LOGV) {
                                    Log.d(TAG, "Incorrect number of values: " + values.length);
                                }
                                return false;
                            }
                            try {
                                // values[0] is LED color
                                if (!sColorValidator.validate(values[0])) {
                                    if (LOCAL_LOGV) {
                                        Log.d(TAG, "Invalid LED color (" + values[0] + ") for "
                                                + packageName);
                                    }
                                    return false;
                                }
                                // values[1] is the LED on time and should be non-negative
                                if (!sNonNegativeIntegerValidator.validate(values[1])) {
                                    if (LOCAL_LOGV) {
                                        Log.d(TAG, "Invalid LED on time (" + values[1] + ") for "
                                                + packageName);
                                    }
                                    return false;
                                }
                                // values[1] is the LED off time and should be non-negative
                                if (!sNonNegativeIntegerValidator.validate(values[2])) {
                                    if (LOCAL_LOGV) {
                                        Log.d(TAG, "Invalid LED off time (" + values[2] + ") for "
                                                + packageName);
                                    }
                                    return false;
                                }
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        }
                        // if we make it all the way through then the data is considered valid
                        return true;
                    }
                };

        /**
         * Whether we automatically generate notification LED colors or just
         * use the boring default.
         *
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_COLOR_AUTO =
                "notification_light_color_auto";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_COLOR_AUTO_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether or not to launch default music player when headset is connected
         */
        public static final String HEADSET_CONNECT_PLAYER = "headset_connect_player";

        /** @hide */
        public static final Validator HEADSET_CONNECT_PLAYER_VALIDATOR = sBooleanValidator;

        /**
         * Whether or not to vibrate when a touchscreen gesture is detected
         */
        public static final String TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK =
                "touchscreen_gesture_haptic_feedback";

        /** @hide */
        public static final Validator TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK_VALIDATOR =
                sBooleanValidator;

        /**
         * The current custom picture adjustment values as a delimited string
         */
        public static final String DISPLAY_PICTURE_ADJUSTMENT =
                "display_picture_adjustment";

        /** @hide */
        public static final Validator DISPLAY_PICTURE_ADJUSTMENT_VALIDATOR =
                new Validator() {
                    @Override
                    public boolean validate(String value) {
                        if (TextUtils.isEmpty(value)) {
                            return true;
                        }
                        final String[] sp = TextUtils.split(value, ",");
                        for (String s : sp) {
                            final String[] sp2 = TextUtils.split(s, ":");
                            if (sp2.length != 2) {
                                return false;
                            }
                        }
                        return true;
                    }
                };

        /**
         * List of long-screen apps.
         */
        public static final String LONG_SCREEN_APPS = "long_screen_apps";

        /** @hide */
        public static final Validator LONG_SCREEN_APPS_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * Force show navigation bar setting.
         * @hide
         */
        public static final String FORCE_SHOW_NAVBAR = "force_show_navbar";

        /** @hide */
        public static final Validator FORCE_SHOW_NAVBAR_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to take partial screenshot with volume down + power click.
         */
        public static final String CLICK_PARTIAL_SCREENSHOT = "click_partial_screenshot";

        /** @hide */
        public static final Validator CLICK_PARTIAL_SCREENSHOT_VALIDATOR =
                sBooleanValidator;

        /**
         * I can haz more bukkits
         * @hide
         */
        public static final String __MAGICAL_TEST_PASSING_ENABLER =
                "___magical_test_passing_enabler";

        /**
         * Don't
         * @hide
         * me bro
         */
        public static final Validator __MAGICAL_TEST_PASSING_ENABLER_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * @hide
         */
        public static final String[] LEGACY_SYSTEM_SETTINGS = new String[]{
                LineageSettings.System.NAV_BUTTONS,
                LineageSettings.System.KEY_BACK_LONG_PRESS_ACTION,
                LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION,
                LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                LineageSettings.System.BACK_WAKE_SCREEN,
                LineageSettings.System.MENU_WAKE_SCREEN,
                LineageSettings.System.VOLUME_WAKE_SCREEN,
                LineageSettings.System.KEY_MENU_ACTION,
                LineageSettings.System.KEY_MENU_LONG_PRESS_ACTION,
                LineageSettings.System.KEY_ASSIST_ACTION,
                LineageSettings.System.KEY_ASSIST_LONG_PRESS_ACTION,
                LineageSettings.System.KEY_APP_SWITCH_ACTION,
                LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION,
                LineageSettings.System.HOME_WAKE_SCREEN,
                LineageSettings.System.ASSIST_WAKE_SCREEN,
                LineageSettings.System.APP_SWITCH_WAKE_SCREEN,
                LineageSettings.System.CAMERA_WAKE_SCREEN,
                LineageSettings.System.CAMERA_SLEEP_ON_RELEASE,
                LineageSettings.System.CAMERA_LAUNCH,
                LineageSettings.System.STYLUS_ICON_ENABLED,
                LineageSettings.System.SWAP_VOLUME_KEYS_ON_ROTATION,
                LineageSettings.System.BATTERY_LIGHT_ENABLED,
                LineageSettings.System.BATTERY_LIGHT_FULL_CHARGE_DISABLED,
                LineageSettings.System.BATTERY_LIGHT_PULSE,
                LineageSettings.System.BATTERY_LIGHT_LOW_COLOR,
                LineageSettings.System.BATTERY_LIGHT_MEDIUM_COLOR,
                LineageSettings.System.BATTERY_LIGHT_FULL_COLOR,
                LineageSettings.System.ENABLE_MWI_NOTIFICATION,
                LineageSettings.System.PROXIMITY_ON_WAKE,
                LineageSettings.System.ENABLE_FORWARD_LOOKUP,
                LineageSettings.System.ENABLE_PEOPLE_LOOKUP,
                LineageSettings.System.ENABLE_REVERSE_LOOKUP,
                LineageSettings.System.FORWARD_LOOKUP_PROVIDER,
                LineageSettings.System.PEOPLE_LOOKUP_PROVIDER,
                LineageSettings.System.REVERSE_LOOKUP_PROVIDER,
                LineageSettings.System.DIALER_OPENCNAM_ACCOUNT_SID,
                LineageSettings.System.DIALER_OPENCNAM_AUTH_TOKEN,
                LineageSettings.System.DISPLAY_TEMPERATURE_DAY,
                LineageSettings.System.DISPLAY_TEMPERATURE_NIGHT,
                LineageSettings.System.DISPLAY_TEMPERATURE_MODE,
                LineageSettings.System.DISPLAY_AUTO_OUTDOOR_MODE,
                LineageSettings.System.DISPLAY_ANTI_FLICKER,
                LineageSettings.System.DISPLAY_READING_MODE,
                LineageSettings.System.DISPLAY_CABC,
                LineageSettings.System.DISPLAY_COLOR_ENHANCE,
                LineageSettings.System.DISPLAY_COLOR_ADJUSTMENT,
                LineageSettings.System.LIVE_DISPLAY_HINTED,
                LineageSettings.System.DOUBLE_TAP_SLEEP_GESTURE,
                LineageSettings.System.STATUS_BAR_SHOW_WEATHER,
                LineageSettings.System.RECENTS_SHOW_SEARCH_BAR,
                LineageSettings.System.NAVBAR_LEFT_IN_LANDSCAPE,
                LineageSettings.System.T9_SEARCH_INPUT_LOCALE,
                LineageSettings.System.BLUETOOTH_ACCEPT_ALL_FILES,
                LineageSettings.System.LOCKSCREEN_PIN_SCRAMBLE_LAYOUT,
                LineageSettings.System.SHOW_ALARM_ICON,
                LineageSettings.System.STATUS_BAR_IME_SWITCHER,
                LineageSettings.System.QS_SHOW_BRIGHTNESS_SLIDER,
                LineageSettings.System.STATUS_BAR_BRIGHTNESS_CONTROL,
                LineageSettings.System.VOLBTN_MUSIC_CONTROLS,
                LineageSettings.System.USE_EDGE_SERVICE_FOR_GESTURES,
                LineageSettings.System.STATUS_BAR_NOTIF_COUNT,
                LineageSettings.System.CALL_RECORDING_FORMAT,
                LineageSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                LineageSettings.System.NOTIFICATION_LIGHT_SCREEN_ON,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_CALL_COLOR,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_CALL_LED_ON,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE,
                LineageSettings.System.NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES,
                LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN,
                LineageSettings.System.VOLUME_ADJUST_SOUNDS_ENABLED,
                LineageSettings.System.SYSTEM_PROFILES_ENABLED,
                LineageSettings.System.INCREASING_RING,
                LineageSettings.System.INCREASING_RING_START_VOLUME,
                LineageSettings.System.INCREASING_RING_RAMP_UP_TIME,
                LineageSettings.System.STATUS_BAR_CLOCK,
                LineageSettings.System.STATUS_BAR_AM_PM,
                LineageSettings.System.STATUS_BAR_BATTERY_STYLE,
                LineageSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT,
                LineageSettings.System.NAVIGATION_BAR_MENU_ARROW_KEYS,
                LineageSettings.System.HEADSET_CONNECT_PLAYER,
                LineageSettings.System.ZEN_ALLOW_LIGHTS,
                LineageSettings.System.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK,
        };

        /**
         * @hide
         */
        public static boolean isLegacySetting(String key) {
            return ArrayUtils.contains(LEGACY_SYSTEM_SETTINGS, key);
        }

        /**
         * Mapping of validators for all system settings.  This map is used to validate both valid
         * keys as well as validating the values for those keys.
         *
         * Note: Make sure if you add a new System setting you create a Validator for it and add
         *       it to this map.
         *
         * @hide
         */
        public static final Map<String, Validator> VALIDATORS =
                new ArrayMap<String, Validator>();
        static {
            VALIDATORS.put(NOTIFICATION_PLAY_QUEUE, NOTIFICATION_PLAY_QUEUE_VALIDATOR);
            VALIDATORS.put(HIGH_TOUCH_SENSITIVITY_ENABLE,
                    HIGH_TOUCH_SENSITIVITY_ENABLE_VALIDATOR);
            VALIDATORS.put(SYSTEM_PROFILES_ENABLED, SYSTEM_PROFILES_ENABLED_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_CLOCK, STATUS_BAR_CLOCK_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_AM_PM, STATUS_BAR_AM_PM_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_BATTERY_STYLE, STATUS_BAR_BATTERY_STYLE_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_SHOW_BATTERY_PERCENT,
                    STATUS_BAR_SHOW_BATTERY_PERCENT_VALIDATOR);
            VALIDATORS.put(INCREASING_RING, INCREASING_RING_VALIDATOR);
            VALIDATORS.put(INCREASING_RING_START_VOLUME,
                    INCREASING_RING_START_VOLUME_VALIDATOR);
            VALIDATORS.put(INCREASING_RING_RAMP_UP_TIME,
                    INCREASING_RING_RAMP_UP_TIME_VALIDATOR);
            VALIDATORS.put(VOLUME_ADJUST_SOUNDS_ENABLED,
                    VOLUME_ADJUST_SOUNDS_ENABLED_VALIDATOR);
            VALIDATORS.put(NAV_BUTTONS, NAV_BUTTONS_VALIDATOR);
            VALIDATORS.put(NAVIGATION_BAR_MENU_ARROW_KEYS,
                    NAVIGATION_BAR_MENU_ARROW_KEYS_VALIDATOR);
            VALIDATORS.put(NAVIGATION_BAR_HINT, NAVIGATION_BAR_HINT_VALIDATOR);
            VALIDATORS.put(KEY_BACK_LONG_PRESS_ACTION, KEY_BACK_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_HOME_LONG_PRESS_ACTION, KEY_HOME_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_HOME_DOUBLE_TAP_ACTION, KEY_HOME_DOUBLE_TAP_ACTION_VALIDATOR);
            VALIDATORS.put(BACK_WAKE_SCREEN, BACK_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(MENU_WAKE_SCREEN, MENU_WAKE_SCREENN_VALIDATOR);
            VALIDATORS.put(VOLUME_ANSWER_CALL, VOLUME_ANSWER_CALL_VALIDATOR);
            VALIDATORS.put(VOLUME_WAKE_SCREEN, VOLUME_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(KEY_MENU_ACTION, KEY_MENU_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_MENU_LONG_PRESS_ACTION, KEY_MENU_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_ASSIST_ACTION, KEY_ASSIST_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_ASSIST_LONG_PRESS_ACTION,
                    KEY_ASSIST_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_APP_SWITCH_ACTION, KEY_APP_SWITCH_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_APP_SWITCH_LONG_PRESS_ACTION,
                    KEY_APP_SWITCH_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_EDGE_LONG_SWIPE_ACTION, KEY_EDGE_LONG_SWIPE_ACTION_VALIDATOR);
            VALIDATORS.put(HOME_WAKE_SCREEN, HOME_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(ASSIST_WAKE_SCREEN, ASSIST_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(APP_SWITCH_WAKE_SCREEN, APP_SWITCH_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(CAMERA_WAKE_SCREEN, CAMERA_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(CAMERA_SLEEP_ON_RELEASE, CAMERA_SLEEP_ON_RELEASE_VALIDATOR);
            VALIDATORS.put(CAMERA_LAUNCH, CAMERA_LAUNCH_VALIDATOR);
            VALIDATORS.put(STYLUS_ICON_ENABLED, STYLUS_ICON_ENABLED_VALIDATOR);
            VALIDATORS.put(SWAP_VOLUME_KEYS_ON_ROTATION,
                    SWAP_VOLUME_KEYS_ON_ROTATION_VALIDATOR);
            VALIDATORS.put(TORCH_LONG_PRESS_POWER_GESTURE,
                    TORCH_LONG_PRESS_POWER_GESTURE_VALIDATOR);
            VALIDATORS.put(TORCH_LONG_PRESS_POWER_TIMEOUT,
                    TORCH_LONG_PRESS_POWER_TIMEOUT_VALIDATOR);
            VALIDATORS.put(BUTTON_BACKLIGHT_ONLY_WHEN_PRESSED,
                    BUTTON_BACKLIGHT_ONLY_WHEN_PRESSED_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_ENABLED, BATTERY_LIGHT_ENABLED_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_FULL_CHARGE_DISABLED,
                    BATTERY_LIGHT_FULL_CHARGE_DISABLED_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_PULSE, BATTERY_LIGHT_PULSE_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_LOW_COLOR, BATTERY_LIGHT_LOW_COLOR_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_MEDIUM_COLOR, BATTERY_LIGHT_MEDIUM_COLOR_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_FULL_COLOR, BATTERY_LIGHT_FULL_COLOR_VALIDATOR);
            VALIDATORS.put(ENABLE_MWI_NOTIFICATION, ENABLE_MWI_NOTIFICATION_VALIDATOR);
            VALIDATORS.put(PROXIMITY_ON_WAKE, PROXIMITY_ON_WAKE_VALIDATOR);
            VALIDATORS.put(BERRY_GLOBAL_STYLE, BERRY_GLOBAL_STYLE_VALIDATOR);
            VALIDATORS.put(BERRY_CURRENT_ACCENT, BERRY_CURRENT_ACCENT_VALIDATOR);
            VALIDATORS.put(BERRY_DARK_OVERLAY, BERRY_DARK_OVERLAY_VALIDATOR);
            VALIDATORS.put(BERRY_MANAGED_BY_APP, BERRY_MANAGED_BY_APP_VALIDATOR);
            VALIDATORS.put(BERRY_BLACK_THEME, BERRY_BLACK_THEME_VALIDATOR);
            VALIDATORS.put(ENABLE_FORWARD_LOOKUP, ENABLE_FORWARD_LOOKUP_VALIDATOR);
            VALIDATORS.put(ENABLE_PEOPLE_LOOKUP, ENABLE_PEOPLE_LOOKUP_VALIDATOR);
            VALIDATORS.put(ENABLE_REVERSE_LOOKUP, ENABLE_REVERSE_LOOKUP_VALIDATOR);
            VALIDATORS.put(FORWARD_LOOKUP_PROVIDER, FORWARD_LOOKUP_PROVIDER_VALIDATOR);
            VALIDATORS.put(PEOPLE_LOOKUP_PROVIDER, PEOPLE_LOOKUP_PROVIDER_VALIDATOR);
            VALIDATORS.put(REVERSE_LOOKUP_PROVIDER, REVERSE_LOOKUP_PROVIDER_VALIDATOR);
            VALIDATORS.put(DIALER_OPENCNAM_ACCOUNT_SID,
                    DIALER_OPENCNAM_ACCOUNT_SID_VALIDATOR);
            VALIDATORS.put(DIALER_OPENCNAM_AUTH_TOKEN, DIALER_OPENCNAM_AUTH_TOKEN_VALIDATOR);
            VALIDATORS.put(DISPLAY_TEMPERATURE_DAY, DISPLAY_TEMPERATURE_DAY_VALIDATOR);
            VALIDATORS.put(DISPLAY_TEMPERATURE_NIGHT, DISPLAY_TEMPERATURE_NIGHT_VALIDATOR);
            VALIDATORS.put(DISPLAY_TEMPERATURE_MODE, DISPLAY_TEMPERATURE_MODE_VALIDATOR);
            VALIDATORS.put(DISPLAY_AUTO_CONTRAST, DISPLAY_AUTO_CONTRAST_VALIDATOR);
            VALIDATORS.put(DISPLAY_AUTO_OUTDOOR_MODE, DISPLAY_AUTO_OUTDOOR_MODE_VALIDATOR);
            VALIDATORS.put(DISPLAY_ANTI_FLICKER, DISPLAY_ANTI_FLICKER_VALIDATOR);
            VALIDATORS.put(DISPLAY_READING_MODE, DISPLAY_READING_MODE_VALIDATOR);
            VALIDATORS.put(DISPLAY_CABC, DISPLAY_CABC_VALIDATOR);
            VALIDATORS.put(DISPLAY_COLOR_ENHANCE, DISPLAY_COLOR_ENHANCE_VALIDATOR);
            VALIDATORS.put(DISPLAY_COLOR_ADJUSTMENT, DISPLAY_COLOR_ADJUSTMENT_VALIDATOR);
            VALIDATORS.put(LIVE_DISPLAY_HINTED, LIVE_DISPLAY_HINTED_VALIDATOR);
            VALIDATORS.put(TRUST_INTERFACE_HINTED, TRUST_INTERFACE_HINTED_VALIDATOR);
            VALIDATORS.put(DOUBLE_TAP_SLEEP_GESTURE, DOUBLE_TAP_SLEEP_GESTURE_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_SHOW_WEATHER, STATUS_BAR_SHOW_WEATHER_VALIDATOR);
            VALIDATORS.put(RECENTS_SHOW_SEARCH_BAR, RECENTS_SHOW_SEARCH_BAR_VALIDATOR);
            VALIDATORS.put(NAVBAR_LEFT_IN_LANDSCAPE, NAVBAR_LEFT_IN_LANDSCAPE_VALIDATOR);
            VALIDATORS.put(T9_SEARCH_INPUT_LOCALE, T9_SEARCH_INPUT_LOCALE_VALIDATOR);
            VALIDATORS.put(BLUETOOTH_ACCEPT_ALL_FILES, BLUETOOTH_ACCEPT_ALL_FILES_VALIDATOR);
            VALIDATORS.put(LOCKSCREEN_PIN_SCRAMBLE_LAYOUT,
                    LOCKSCREEN_PIN_SCRAMBLE_LAYOUT_VALIDATOR);
            VALIDATORS.put(LOCKSCREEN_ROTATION, LOCKSCREEN_ROTATION_VALIDATOR);
            VALIDATORS.put(SHOW_ALARM_ICON, SHOW_ALARM_ICON_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_IME_SWITCHER, STATUS_BAR_IME_SWITCHER_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_QUICK_QS_PULLDOWN,
                    STATUS_BAR_QUICK_QS_PULLDOWN_VALIDATOR);
            VALIDATORS.put(QS_SHOW_BRIGHTNESS_SLIDER, QS_SHOW_BRIGHTNESS_SLIDER_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_BRIGHTNESS_CONTROL,
                    STATUS_BAR_BRIGHTNESS_CONTROL_VALIDATOR);
            VALIDATORS.put(VOLBTN_MUSIC_CONTROLS, VOLBTN_MUSIC_CONTROLS_VALIDATOR);
            VALIDATORS.put(USE_EDGE_SERVICE_FOR_GESTURES,
                    USE_EDGE_SERVICE_FOR_GESTURES_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_NOTIF_COUNT, STATUS_BAR_NOTIF_COUNT_VALIDATOR);
            VALIDATORS.put(CALL_RECORDING_FORMAT, CALL_RECORDING_FORMAT_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_BRIGHTNESS_LEVEL,
                    BATTERY_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_BRIGHTNESS_LEVEL_ZEN,
                    BATTERY_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                    NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_ZEN,
                    NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_SCREEN_ON,
                    NOTIFICATION_LIGHT_SCREEN_ON_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR,
                    NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON,
                    NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF,
                    NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CALL_COLOR,
                    NOTIFICATION_LIGHT_PULSE_CALL_COLOR_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CALL_LED_ON,
                    NOTIFICATION_LIGHT_PULSE_CALL_LED_ON_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF,
                    NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR,
                    NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON,
                    NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF,
                    NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE,
                    NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES,
                    NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_COLOR_AUTO,
                    NOTIFICATION_LIGHT_COLOR_AUTO_VALIDATOR);
            VALIDATORS.put(HEADSET_CONNECT_PLAYER, HEADSET_CONNECT_PLAYER_VALIDATOR);
            VALIDATORS.put(ZEN_ALLOW_LIGHTS, ZEN_ALLOW_LIGHTS_VALIDATOR);
            VALIDATORS.put(ZEN_PRIORITY_ALLOW_LIGHTS, ZEN_PRIORITY_ALLOW_LIGHTS_VALIDATOR);
            VALIDATORS.put(ZEN_PRIORITY_VIBRATION_MODE, ZEN_PRIORITY_VIBRATION_VALIDATOR);
            VALIDATORS.put(TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK,
                    TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK_VALIDATOR);
            VALIDATORS.put(DISPLAY_PICTURE_ADJUSTMENT,
                    DISPLAY_PICTURE_ADJUSTMENT_VALIDATOR);
            VALIDATORS.put(LONG_SCREEN_APPS,
                    LONG_SCREEN_APPS_VALIDATOR);
            VALIDATORS.put(FORCE_SHOW_NAVBAR,
                    FORCE_SHOW_NAVBAR_VALIDATOR);
            VALIDATORS.put(CLICK_PARTIAL_SCREENSHOT,
                    CLICK_PARTIAL_SCREENSHOT_VALIDATOR);
            VALIDATORS.put(__MAGICAL_TEST_PASSING_ENABLER,
                    __MAGICAL_TEST_PASSING_ENABLER_VALIDATOR);
        };
        // endregion
    }

    /**
     * Secure settings, containing miscellaneous Lineage secure preferences. This
     * table holds simple name/value pairs. There are convenience
     * functions for accessing individual settings entries.
     */
    public static final class Secure extends Settings.NameValueTable {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/secure");

        public static final String SYS_PROP_LINEAGE_SETTING_VERSION = "sys.lineage_settings_secure_version";

        private static final NameValueCache sNameValueCache = new NameValueCache(
                SYS_PROP_LINEAGE_SETTING_VERSION,
                CONTENT_URI,
                CALL_METHOD_GET_SECURE,
                CALL_METHOD_PUT_SECURE);

        /** @hide */
        protected static final ArraySet<String> MOVED_TO_GLOBAL;
        static {
            MOVED_TO_GLOBAL = new ArraySet<>(1);
        }

        // region Methods

        /**
         * Put a delimited list as a string
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split
         * @param list to join and store
         * @hide
         */
        public static void putListAsDelimitedString(ContentResolver resolver, String name,
                                                    String delimiter, List<String> list) {
            String store = TextUtils.join(delimiter, list);
            putString(resolver, name, store);
        }

        /**
         * Get a delimited string returned as a list
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split the list with
         * @return list of strings for a specific Settings.Secure item
         * @hide
         */
        public static List<String> getDelimitedStringAsList(ContentResolver resolver, String name,
                                                            String delimiter) {
            String baseString = getString(resolver, name);
            List<String> list = new ArrayList<String>();
            if (!TextUtils.isEmpty(baseString)) {
                final String[] array = TextUtils.split(baseString, Pattern.quote(delimiter));
                for (String item : array) {
                    if (TextUtils.isEmpty(item)) {
                        continue;
                    }
                    list.add(item);
                }
            }
            return list;
        }

        /**
         * Construct the content URI for a particular name/value pair, useful for monitoring changes
         * with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI
         */
        public static Uri getUriFor(String name) {
            return Settings.NameValueTable.getUriFor(CONTENT_URI, name);
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @param def Value to return if the setting is not defined.
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name, String def) {
            String str = getStringForUser(resolver, name, UserHandle.myUserId());
            return str == null ? def : str;
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userId) {
            if (MOVED_TO_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from LineageSettings.Secure"
                        + " to LineageSettings.Global, value is unchanged.");
                return LineageSettings.Global.getStringForUser(resolver, name, userId);
            }
            return sNameValueCache.getStringForUser(resolver, name, userId);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver, String name, String value,
               int userId) {
            if (MOVED_TO_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from LineageSettings.Secure"
                        + " to LineageSettings.Global, value is unchanged.");
                return false;
            }
            return sNameValueCache.putStringForUser(resolver, name, value, userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int def, int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link LineageSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws LineageSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws LineageSettingNotFoundException {
            return getIntForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int userId)
                throws LineageSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new LineageSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putIntForUser(ContentResolver cr, String name, int value,
                int userId) {
            return putStringForUser(cr, name, Integer.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            return getLongForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, long def,
                int userId) {
            String valString = getStringForUser(cr, name, userId);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link LineageSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws LineageSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws LineageSettingNotFoundException {
            return getLongForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, int userId)
                throws LineageSettingNotFoundException {
            String valString = getStringForUser(cr, name, userId);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new LineageSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putLongForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putLongForUser(ContentResolver cr, String name, long value,
                int userId) {
            return putStringForUser(cr, name, Long.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            return getFloatForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, float def,
                int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link LineageSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws LineageSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws LineageSettingNotFoundException {
            return getFloatForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, int userId)
                throws LineageSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            if (v == null) {
                throw new LineageSettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new LineageSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putFloatForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putFloatForUser(ContentResolver cr, String name, float value,
                int userId) {
            return putStringForUser(cr, name, Float.toString(value), userId);
        }

        // endregion

        // region Secure Settings

        /**
         * Whether to enable "advanced mode" for the current user.
         * Boolean setting. 0 = no, 1 = yes.
         * @hide
         */
        public static final String ADVANCED_MODE = "advanced_mode";

        /**
         * The time in ms to keep the button backlight on after pressing a button.
         * A value of 0 will keep the buttons on for as long as the screen is on.
         * @hide
         */
        public static final String BUTTON_BACKLIGHT_TIMEOUT = "button_backlight_timeout";

        /**
         * The button brightness to be used while the screen is on or after a button press,
         * depending on the value of {@link BUTTON_BACKLIGHT_TIMEOUT}.
         * Valid value range is between 0 and {@link PowerManager#getMaximumButtonBrightness()}
         * @hide
         */
        public static final String BUTTON_BRIGHTNESS = "button_brightness";

        /**
         * Developer options - Navigation Bar show switch
         * @deprecated
         * @hide
         */
        public static final String DEV_FORCE_SHOW_NAVBAR = "dev_force_show_navbar";

        /**
         * The keyboard brightness to be used while the screen is on.
         * Valid value range is between 0 and {@link PowerManager#getMaximumKeyboardBrightness()}
         * @hide
         */
        public static final String KEYBOARD_BRIGHTNESS = "keyboard_brightness";

        /**
         * Custom navring actions
         * @hide
         */
        public static final String[] NAVIGATION_RING_TARGETS = new String[] {
                "navigation_ring_targets_0",
                "navigation_ring_targets_1",
                "navigation_ring_targets_2",
        };

        /**
         * String to contain power menu actions
         * @hide
         */
        public static final String POWER_MENU_ACTIONS = "power_menu_actions";

        /**
         * Whether to show the brightness slider in quick settings panel.
         * @hide
         */
        public static final String QS_SHOW_BRIGHTNESS_SLIDER = "qs_show_brightness_slider";

        /**
         * Whether to show the auto brightness icon in quick settings panel.
         * @hide
         */
        public static final String QS_SHOW_AUTO_BRIGHTNESS = "qs_show_auto_brightness";

        /**
         * Global stats collection
         * @hide
         */
        public static final String STATS_COLLECTION = "stats_collection";

        /**
         * Whether the global stats collection setting has been successfully reported to server
         * @hide
         * @deprecated {@link org.lineageos.lineageparts.lineagestats.AnonymousStats} no longer uses this
         */
        @Deprecated
        public static final String STATS_COLLECTION_REPORTED = "stats_collection_reported";

        /**
         * Whether newly installed apps should run with privacy guard by default
         * @deprecated
         * @hide
         */
        @Deprecated
        public static final String PRIVACY_GUARD_DEFAULT = "privacy_guard_default";

        /**
         * Whether a notification should be shown if privacy guard is enabled
         * @deprecated
         * @hide
         */
        @Deprecated
        public static final String PRIVACY_GUARD_NOTIFICATION = "privacy_guard_notification";

        /**
         * The global recents long press activity chosen by the user.
         * This setting is stored as a flattened component name as
         * per {@link ComponentName#flattenToString()}.
         *
         * @hide
         */
        public static final String RECENTS_LONG_PRESS_ACTIVITY = "recents_long_press_activity";

        /**
         * What happens when the user presses the Home button when the
         * phone is ringing.<br/>
         * <b>Values:</b><br/>
         * 1 - Nothing happens. (Default behavior)<br/>
         * 2 - The Home button answer the current call.<br/>
         *
         * @hide
         */
        public static final String RING_HOME_BUTTON_BEHAVIOR = "ring_home_button_behavior";

        /**
         * RING_HOME_BUTTON_BEHAVIOR value for "do nothing".
         * @hide
         */
        public static final int RING_HOME_BUTTON_BEHAVIOR_DO_NOTHING = 0x1;

        /**
         * RING_HOME_BUTTON_BEHAVIOR value for "answer".
         * @hide
         */
        public static final int RING_HOME_BUTTON_BEHAVIOR_ANSWER = 0x2;

        /**
         * RING_HOME_BUTTON_BEHAVIOR default value.
         * @hide
         */
        public static final int RING_HOME_BUTTON_BEHAVIOR_DEFAULT =
                RING_HOME_BUTTON_BEHAVIOR_DO_NOTHING;

        /**
         * Performance profile
         * @hide
         */
        public static final String PERFORMANCE_PROFILE = "performance_profile";

        /**
         * App-based performance profile selection
         * @hide
         */
        public static final String APP_PERFORMANCE_PROFILES_ENABLED = "app_perf_profiles_enabled";

        /**
         * Launch actions for left/right lockscreen targets
         * @hide
         */
        public static final String LOCKSCREEN_TARGETS = "lockscreen_target_actions";

        /**
         * Whether to display a menu containing 'Wipe data', 'Force close' and other options
         * in the notification area and in the recent app list
         * @hide
         */
        public static final String DEVELOPMENT_SHORTCUT = "development_shortcut";

        /**
         * Whether to display the ADB notification.
         * @deprecated
         * @hide
         */
        @Deprecated
        public static final String ADB_NOTIFY = "adb_notify";

        /**
         * The TCP/IP port to run ADB on, or -1 for USB
         * @deprecated
         * @hide
         */
        @Deprecated
        public static final String ADB_PORT = "adb_port";

        /**
         * The hostname for this device
         * @deprecated
         * @hide
         */
        @Deprecated
        public static final String DEVICE_HOSTNAME = "device_hostname";

        /**
         * Whether to allow killing of the foreground app by long-pressing the Back button
         * @deprecated
         * @hide
         */
        @Deprecated
        public static final String KILL_APP_LONGPRESS_BACK = "kill_app_longpress_back";

        /**
         * Whether to exclude the top area of the screen from back gesture
         * @hide
         */
        public static final String GESTURE_BACK_EXCLUDE_TOP = "gesture_back_exclude_top";

        /**
         * Top to half of the screen height are the valid values
         * @gide
         */
        public static final Validator GESTURE_BACK_EXCLUDE_TOP_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 50);

        /** Protected Components
         * @hide
         */
        public static final String PROTECTED_COMPONENTS = "protected_components";

        /**
         * Stored color matrix for LiveDisplay. This is used to allow co-existence with
         * display tuning done by DisplayAdjustmentUtils when hardware support isn't
         * available.
         * @hide
         */
        public static final String LIVE_DISPLAY_COLOR_MATRIX = "live_display_color_matrix";

        /**
         * Whether to include options in power menu for rebooting into recovery or bootloader
         * @hide
         */
        public static final String ADVANCED_REBOOT = "advanced_reboot";

        /**
         * Whether detail view for the location tile is enabled
         * @hide
         */
        public static final String QS_LOCATION_ADVANCED = "qs_location_advanced";

        /**
         * Whether to show the keyguard visualizer.
         * Boolean setting. 0 = off, 1 = on.
         * @hide
         */
        public static final String LOCKSCREEN_VISUALIZER_ENABLED = "lockscreen_visualizer";

        /**
         * Whether to show media art on lockscreen
         * Boolean setting. 0 = off, 1 = on.
         * @hide
         */
        public static final String LOCKSCREEN_MEDIA_METADATA = "lockscreen_media_metadata";

        /**
         * Whether to have translucent background on lockscreen notifications
         * @hide
         */
        public static final String LOCKSCREEN_TRANSLUCENT_NOTIFICATIONS_BG_ENABLED
                = "lockscreen_translucent_notifications_bg_enabled";

        /**
         * Whether to activate double tap to sleep on keyguard
         * Boolean setting. 0 = off, 1 = on.
         * @hide
         */
        public static final String DOUBLE_TAP_SLEEP_ANYWHERE = "double_tap_sleep_anywhere";

        /**
         * Whether the lock screen is currently enabled/disabled by SystemUI (the QS tile likely).
         * Boolean settings. 0 = off. 1 = on.
         * @hide
         */
        public static final String LOCKSCREEN_INTERNALLY_ENABLED = "lockscreen_internally_enabled";

        /**
         * Delimited list of packages allowed to manage/launch protected apps (used for filtering)
         * @hide
         */
        public static final String PROTECTED_COMPONENT_MANAGERS = "protected_component_managers";

        /**
         * Whether keyguard will direct show security view (0 = false, 1 = true)
         * @hide
         */
        public static final String LOCK_PASS_TO_SECURITY_VIEW = "lock_screen_pass_to_security_view";

        /**
         * Whether touch hovering is enabled on supported hardware
         * @hide
         */
        public static final String FEATURE_TOUCH_HOVERING = "feature_touch_hovering";

        /**
         * Vibrator intensity setting for supported devices
         * @hide
         */
        public static final String VIBRATOR_INTENSITY = "vibrator_intensity";

        /**
         * Current active & enabled Weather Provider Service
         *
         * @hide
         */
        public static final String WEATHER_PROVIDER_SERVICE = "weather_provider_service";

        /**
         * Set to 0 when we enter the Lineage Setup Wizard.
         * Set to 1 when we exit the Lineage Setup Wizard.
         *
         * @deprecated Use {@link Secure#USER_SETUP_COMPLETE} or
         *             {@link Settings.Global#DEVICE_PROVISIONED} instead
         * @hide
         */
        @Deprecated
        public static final String LINEAGE_SETUP_WIZARD_COMPLETED = "lineage_setup_wizard_completed";

        /**
         * Whether lock screen bluring is enabled on devices that support this feature
         * @hide
         */
        public static final String LOCK_SCREEN_BLUR_ENABLED = "lock_screen_blur_enabled";

        /**
         * Whether to display weather information on the lock screen
         * @hide
         */
        public static final String LOCK_SCREEN_WEATHER_ENABLED = "lock_screen_weather_enabled";

        /**
         * Network traffic indicator mode
         * 0 = Don't show network traffic indicator
         * 1 = Display up-stream traffic only
         * 2 = Display down-stream traffic only
         * 3 = Display both up- and down-stream traffic
         * @hide
         */
        public static final String NETWORK_TRAFFIC_MODE = "network_traffic_mode";

        /** @hide */
        public static final Validator NETWORK_TRAFFIC_MODE_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 3);

        /**
         * Whether or not to hide the network traffic indicator when there is no activity
         * @hide
         */
        public static final String NETWORK_TRAFFIC_AUTOHIDE = "network_traffic_autohide";

        /** @hide */
        public static final Validator NETWORK_TRAFFIC_AUTOHIDE_VALIDATOR = sBooleanValidator;

        /**
         * Measurement unit preference for network traffic
         * @hide
         */
        public static final String NETWORK_TRAFFIC_UNITS = "network_traffic_units";

        /** @hide */
        public static final Validator NETWORK_TRAFFIC_UNITS_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 3);

        /**
         * Whether or not to show measurement units in the network traffic indiciator
         * @hide
         */
        public static final String NETWORK_TRAFFIC_SHOW_UNITS = "network_traffic_show_units";

        /** @hide */
        public static final Validator NETWORK_TRAFFIC_SHOW_UNITS_VALIDATOR = sBooleanValidator;

        /**
         * Enable displaying the Trust service's notifications
         * 0 = 0ff, 1 = on
         * @deprecated Rely on {@link lineageos.providers.TRUST_WARNINGS} instead
         */
         @Deprecated
        public static final String TRUST_NOTIFICATIONS = "trust_notifications";

        /** @hide */
        @Deprecated
        public static final Validator TRUST_NOTIFICATIONS_VALIDATOR =
                sBooleanValidator;

        /**
         * Restrict USB when the screen is locked
         * 0 = Off, 1 = On
         *
         * @hide
         */
        public static final String TRUST_RESTRICT_USB_KEYGUARD = "trust_restrict_usb";

        /** @hide */
        public static final Validator TRUST_RESTRICT_USB_KEYGUARD_VALIDATOR =
                sBooleanValidator;

        /**
         * Trust warnings status
         *
         * Stores flags for each feature
         *
         * @see {@link lineageos.trust.TrustInterface.TRUST_WARN_MAX_VALUE}
         */
        public static final String TRUST_WARNINGS = "trust_warnings";

        /** @hide */
        public static final Validator TRUST_WARNINGS_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, TrustInterface.TRUST_WARN_MAX_VALUE);

        /**
         * Whether volume panel should appear on the left (or right).
         * 0 = false (on the right)
         * 1 = true (on the left)
         */
        public static final String VOLUME_PANEL_ON_LEFT = "volume_panel_on_left";

        public static final Validator VOLUME_PANEL_ON_LEFT_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether tethering is allowed to use VPN upstreams
         * 0 = false, 1 = true
         */
        public static final String TETHERING_ALLOW_VPN_UPSTREAMS = "tethering_allow_vpn_upstreams";

        public static final Validator TETHERING_ALLOW_VPN_UPSTREAMS_VALIDATOR = sBooleanValidator;

        // endregion

        /**
         * I can haz more bukkits
         * @hide
         */
        public static final String __MAGICAL_TEST_PASSING_ENABLER =
                "___magical_test_passing_enabler";

        /**
         * @hide
         */
        public static final String[] LEGACY_SECURE_SETTINGS = new String[]{
                LineageSettings.Secure.ADVANCED_MODE,
                LineageSettings.Secure.BUTTON_BACKLIGHT_TIMEOUT,
                LineageSettings.Secure.BUTTON_BRIGHTNESS,
                LineageSettings.Secure.DEV_FORCE_SHOW_NAVBAR,
                LineageSettings.Secure.KEYBOARD_BRIGHTNESS,
                LineageSettings.Secure.POWER_MENU_ACTIONS,
                LineageSettings.Secure.STATS_COLLECTION,
                LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER,
                LineageSettings.Secure.NAVIGATION_RING_TARGETS[0],
                LineageSettings.Secure.NAVIGATION_RING_TARGETS[1],
                LineageSettings.Secure.NAVIGATION_RING_TARGETS[2],
                LineageSettings.Secure.RECENTS_LONG_PRESS_ACTIVITY,
                LineageSettings.Secure.ADB_NOTIFY,
                LineageSettings.Secure.ADB_PORT,
                LineageSettings.Secure.DEVICE_HOSTNAME,
                LineageSettings.Secure.KILL_APP_LONGPRESS_BACK,
                LineageSettings.Secure.PROTECTED_COMPONENTS,
                LineageSettings.Secure.LIVE_DISPLAY_COLOR_MATRIX,
                LineageSettings.Secure.ADVANCED_REBOOT,
                LineageSettings.Secure.LOCKSCREEN_TARGETS,
                LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR,
                LineageSettings.Secure.PRIVACY_GUARD_DEFAULT,
                LineageSettings.Secure.PRIVACY_GUARD_NOTIFICATION,
                LineageSettings.Secure.DEVELOPMENT_SHORTCUT,
                LineageSettings.Secure.PERFORMANCE_PROFILE,
                LineageSettings.Secure.APP_PERFORMANCE_PROFILES_ENABLED,
                LineageSettings.Secure.QS_LOCATION_ADVANCED,
                LineageSettings.Secure.LOCKSCREEN_VISUALIZER_ENABLED,
                LineageSettings.Secure.LOCK_PASS_TO_SECURITY_VIEW
        };

        /**
         * @hide
         */
        public static boolean isLegacySetting(String key) {
            return ArrayUtils.contains(LEGACY_SECURE_SETTINGS, key);
        }

        /**
         * @hide
         */
        public static final Validator PROTECTED_COMPONENTS_VALIDATOR = new Validator() {
            private final String mDelimiter = "|";

            @Override
            public boolean validate(String value) {
                if (!TextUtils.isEmpty(value)) {
                    final String[] array = TextUtils.split(value, Pattern.quote(mDelimiter));
                    for (String item : array) {
                        if (TextUtils.isEmpty(item)) {
                            return false; // Empty components not allowed
                        }
                    }
                }
                return true;  // Empty list is allowed though.
            }
        };

        /**
         * @hide
         */
        public static final Validator PROTECTED_COMPONENTS_MANAGER_VALIDATOR = new Validator() {
            private final String mDelimiter = "|";

            @Override
            public boolean validate(String value) {
                if (!TextUtils.isEmpty(value)) {
                    final String[] array = TextUtils.split(value, Pattern.quote(mDelimiter));
                    for (String item : array) {
                        if (TextUtils.isEmpty(item)) {
                            return false; // Empty components not allowed
                        }
                    }
                }
                return true;  // Empty list is allowed though.
            }
        };

        /**
         * Mapping of validators for all secure settings.  This map is used to validate both valid
         * keys as well as validating the values for those keys.
         *
         * Note: Make sure if you add a new Secure setting you create a Validator for it and add
         *       it to this map.
         *
         * @hide
         */
        public static final Map<String, Validator> VALIDATORS =
                new ArrayMap<String, Validator>();
        static {
            VALIDATORS.put(GESTURE_BACK_EXCLUDE_TOP, GESTURE_BACK_EXCLUDE_TOP_VALIDATOR);
            VALIDATORS.put(PROTECTED_COMPONENTS, PROTECTED_COMPONENTS_VALIDATOR);
            VALIDATORS.put(PROTECTED_COMPONENT_MANAGERS, PROTECTED_COMPONENTS_MANAGER_VALIDATOR);
            VALIDATORS.put(NETWORK_TRAFFIC_MODE, NETWORK_TRAFFIC_MODE_VALIDATOR);
            VALIDATORS.put(NETWORK_TRAFFIC_AUTOHIDE, NETWORK_TRAFFIC_AUTOHIDE_VALIDATOR);
            VALIDATORS.put(NETWORK_TRAFFIC_UNITS, NETWORK_TRAFFIC_UNITS_VALIDATOR);
            VALIDATORS.put(NETWORK_TRAFFIC_SHOW_UNITS, NETWORK_TRAFFIC_SHOW_UNITS_VALIDATOR);
            VALIDATORS.put(TETHERING_ALLOW_VPN_UPSTREAMS, TETHERING_ALLOW_VPN_UPSTREAMS_VALIDATOR);
            VALIDATORS.put(TRUST_NOTIFICATIONS, TRUST_NOTIFICATIONS_VALIDATOR);
            VALIDATORS.put(TRUST_RESTRICT_USB_KEYGUARD, TRUST_RESTRICT_USB_KEYGUARD_VALIDATOR);
            VALIDATORS.put(TRUST_WARNINGS, TRUST_WARNINGS_VALIDATOR);
            VALIDATORS.put(VOLUME_PANEL_ON_LEFT, VOLUME_PANEL_ON_LEFT_VALIDATOR);
        }
    }

    /**
     * Global settings, containing miscellaneous Lineage global preferences. This
     * table holds simple name/value pairs. There are convenience
     * functions for accessing individual settings entries.
     */
    public static final class Global extends Settings.NameValueTable {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/global");

        public static final String SYS_PROP_LINEAGE_SETTING_VERSION = "sys.lineage_settings_global_version";

        private static final NameValueCache sNameValueCache = new NameValueCache(
                SYS_PROP_LINEAGE_SETTING_VERSION,
                CONTENT_URI,
                CALL_METHOD_GET_GLOBAL,
                CALL_METHOD_PUT_GLOBAL);

        // region Methods


        /**
         * Put a delimited list as a string
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split
         * @param list to join and store
         * @hide
         */
        public static void putListAsDelimitedString(ContentResolver resolver, String name,
                                                    String delimiter, List<String> list) {
            String store = TextUtils.join(delimiter, list);
            putString(resolver, name, store);
        }

        /**
         * Get a delimited string returned as a list
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split the list with
         * @return list of strings for a specific Settings.Secure item
         * @hide
         */
        public static List<String> getDelimitedStringAsList(ContentResolver resolver, String name,
                                                            String delimiter) {
            String baseString = getString(resolver, name);
            List<String> list = new ArrayList<String>();
            if (!TextUtils.isEmpty(baseString)) {
                final String[] array = TextUtils.split(baseString, Pattern.quote(delimiter));
                for (String item : array) {
                    if (TextUtils.isEmpty(item)) {
                        continue;
                    }
                    list.add(item);
                }
            }
            return list;
        }

        /**
         * Construct the content URI for a particular name/value pair, useful for monitoring changes
         * with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI
         */
        public static Uri getUriFor(String name) {
            return Settings.NameValueTable.getUriFor(CONTENT_URI, name);
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @param def Value to return if the setting is not defined.
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name, String def) {
            String str = getStringForUser(resolver, name, UserHandle.myUserId());
            return str == null ? def : str;
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userId) {
            return sNameValueCache.getStringForUser(resolver, name, userId);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver, String name, String value,
                int userId) {
            return sNameValueCache.putStringForUser(resolver, name, value, userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int def, int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link LineageSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws LineageSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws LineageSettingNotFoundException {
            return getIntForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int userId)
                throws LineageSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new LineageSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putIntForUser(ContentResolver cr, String name, int value,
                int userId) {
            return putStringForUser(cr, name, Integer.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            return getLongForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, long def,
                int userId) {
            String valString = getStringForUser(cr, name, userId);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link LineageSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws LineageSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws LineageSettingNotFoundException {
            return getLongForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, int userId)
                throws LineageSettingNotFoundException {
            String valString = getStringForUser(cr, name, userId);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new LineageSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putLongForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putLongForUser(ContentResolver cr, String name, long value,
                int userId) {
            return putStringForUser(cr, name, Long.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            return getFloatForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, float def,
                int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link LineageSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws LineageSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws LineageSettingNotFoundException {
            return getFloatForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, int userId)
                throws LineageSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            if (v == null) {
                throw new LineageSettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new LineageSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putFloatForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putFloatForUser(ContentResolver cr, String name, float value,
                int userId) {
            return putStringForUser(cr, name, Float.toString(value), userId);
        }

        // endregion

        // region Global Settings
        /**
         * Whether to wake the display when plugging or unplugging the charger
         *
         * @hide
         */
        public static final String WAKE_WHEN_PLUGGED_OR_UNPLUGGED =
                "wake_when_plugged_or_unplugged";

        /**
         * Whether to sound when charger power is connected/disconnected
         * @hide
         * @deprecated Use {@link android.provider.Settings.Global#CHARGING_SOUNDS_ENABLED} instead
         */
        @Deprecated
        public static final String POWER_NOTIFICATIONS_ENABLED = "power_notifications_enabled";

        /**
         * Whether to vibrate when charger power is connected/disconnected
         * @hide
         */
        public static final String POWER_NOTIFICATIONS_VIBRATE = "power_notifications_vibrate";

        /**
         * URI for power notification sounds
         * @hide
         */
        public static final String POWER_NOTIFICATIONS_RINGTONE = "power_notifications_ringtone";

        /**
         * @hide
         */
        public static final String ZEN_DISABLE_DUCKING_DURING_MEDIA_PLAYBACK =
                "zen_disable_ducking_during_media_playback";

        /**
         * Whether the system auto-configure the priority of the wifi ap's or use
         * the manual settings established by the user.
         * <> 0 to autoconfigure, 0 to manual settings. Default is <> 0.
         * @hide
         */
        public static final String WIFI_AUTO_PRIORITIES_CONFIGURATION = "wifi_auto_priority";

        /**
         * Global temperature unit in which the weather data will be reported
         * Valid values are:
         * <p>{@link lineageos.providers.WeatherContract.WeatherColumns.TempUnit#CELSIUS}</p>
         * <p>{@link lineageos.providers.WeatherContract.WeatherColumns.TempUnit#FAHRENHEIT}</p>
         */
        public static final String WEATHER_TEMPERATURE_UNIT = "weather_temperature_unit";

        /**
         * Developer options - Navigation Bar show switch
         * @deprecated
         * @hide
         */
        public static final String DEV_FORCE_SHOW_NAVBAR = "dev_force_show_navbar";
        // endregion

        /**
         * I can haz more bukkits
         * @hide
         */
        public static final String __MAGICAL_TEST_PASSING_ENABLER =
                "___magical_test_passing_enabler";

        /**
         * @hide
         */
        public static final String[] LEGACY_GLOBAL_SETTINGS = new String[]{
                LineageSettings.Global.WAKE_WHEN_PLUGGED_OR_UNPLUGGED,
                LineageSettings.Global.POWER_NOTIFICATIONS_VIBRATE,
                LineageSettings.Global.POWER_NOTIFICATIONS_RINGTONE,
                LineageSettings.Global.ZEN_DISABLE_DUCKING_DURING_MEDIA_PLAYBACK,
                LineageSettings.Global.WIFI_AUTO_PRIORITIES_CONFIGURATION
        };

        /**
         * @hide
         */
        public static boolean isLegacySetting(String key) {
            return ArrayUtils.contains(LEGACY_GLOBAL_SETTINGS, key);
        }
    }
}
