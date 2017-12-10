/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lineageos.preference;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceViewHolder;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import lineageos.hardware.LineageHardwareManager;
import lineageos.platform.R;


/**
 * Helpers for checking if a device supports various features.
 *
 * @hide
 */
public class ConstraintsHelper {

    private static final String TAG = "ConstraintsHelper";

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.VERBOSE);

    private final Context mContext;

    private final AttributeSet mAttrs;

    private final Preference mPref;

    private boolean mAvailable = true;

    private boolean mVerifyIntent = true;

    private int mSummaryMinLines = -1;

    private String mReplacesKey = null;

    public ConstraintsHelper(Context context, AttributeSet attrs, Preference pref) {
        mContext = context;
        mAttrs = attrs;
        mPref = pref;

        TypedArray a = context.getResources().obtainAttributes(attrs,
                R.styleable.lineage_SelfRemovingPreference);
        mSummaryMinLines = a.getInteger(R.styleable.lineage_SelfRemovingPreference_minSummaryLines, -1);
        mReplacesKey = a.getString(R.styleable.lineage_SelfRemovingPreference_replacesKey);
        setAvailable(checkConstraints());

        Log.d(TAG, "construct key=" + mPref.getKey() + " available=" + mAvailable);
    }

    public void setAvailable(boolean available) {
        mAvailable = available;
        if (!available) {
            Graveyard.get(mContext).addTombstone(mPref.getKey());
        }
    }

    public boolean isAvailable() {
        return mAvailable;
    }

    public void setVerifyIntent(boolean verifyIntent) {
        mVerifyIntent = verifyIntent;
    }

    private PreferenceGroup getParent(Preference preference) {
        return getParent(mPref.getPreferenceManager().getPreferenceScreen(), preference);
    }

    private PreferenceGroup getParent(PreferenceGroup root, Preference preference) {
        for (int i = 0; i < root.getPreferenceCount(); i++) {
            Preference p = root.getPreference(i);
            if (p == preference)
                return root;
            if (PreferenceGroup.class.isInstance(p)) {
                PreferenceGroup parent = getParent((PreferenceGroup) p, preference);
                if (parent != null)
                    return parent;
            }
        }
        return null;
    }

    private boolean isNegated(String key) {
        return key != null && key.startsWith("!");
    }

    private void checkIntent() {
        Intent i = mPref.getIntent();
        if (i != null) {
            if (!resolveIntent(mContext, i)) {
                Graveyard.get(mContext).addTombstone(mPref.getKey());
                mAvailable = false;
            }
        }
    }

    private boolean checkConstraints() {
        if (mAttrs == null) {
            return true;
        }

        TypedArray a = mContext.getResources().obtainAttributes(mAttrs,
                R.styleable.lineage_SelfRemovingPreference);

        try {

            // Check if the current user is an owner
            boolean rOwner = a.getBoolean(R.styleable.lineage_SelfRemovingPreference_requiresOwner, false);
            if (rOwner && UserHandle.myUserId() != UserHandle.USER_OWNER) {
                return false;
            }

            // Check if a specific package is installed
            String rPackage = a.getString(R.styleable.lineage_SelfRemovingPreference_requiresPackage);
            if (rPackage != null) {
                boolean negated = isNegated(rPackage);
                if (negated) {
                    rPackage = rPackage.substring(1);
                }
                boolean available = isPackageInstalled(mContext, rPackage, false);
                if (available == negated) {
                    return false;
                }
            }

            // Check if an intent can be resolved to handle the given action
            String rAction = a.getString(R.styleable.lineage_SelfRemovingPreference_requiresAction);
            if (rAction != null) {
                boolean negated = isNegated(rAction);
                if (negated) {
                    rAction = rAction.substring(1);
                }
                boolean available = resolveIntent(mContext, rAction);
                if (available == negated) {
                    return false;
                }
            }

            // Check if a system feature is available
            String rFeature = a.getString(R.styleable.lineage_SelfRemovingPreference_requiresFeature);
            if (rFeature != null) {
                boolean negated = isNegated(rFeature);
                if (negated) {
                    rFeature = rFeature.substring(1);
                }
                boolean available = rFeature.startsWith("lineagehardware:") ?
                        LineageHardwareManager.getInstance(mContext).isSupported(
                                rFeature.substring("lineagehardware:".length())) :
                        hasSystemFeature(mContext, rFeature);
                if (available == negated) {
                    return false;
                }
            }

            // Check a boolean system property
            String rProperty = a.getString(R.styleable.lineage_SelfRemovingPreference_requiresProperty);
            if (rProperty != null) {
                boolean negated = isNegated(rProperty);
                if (negated) {
                    rProperty = rFeature.substring(1);
                }
                String value = SystemProperties.get(rProperty);
                boolean available = value != null && Boolean.parseBoolean(value);
                if (available == negated) {
                    return false;
                }
            }

            // Check a config resource. This can be a bool, string or integer.
            // The preference is removed if any of the following are true:
            // * A bool resource is false.
            // * A string resource is null.
            // * An integer resource is zero.
            // * An integer is non-zero and when bitwise logically ANDed with
            //   attribute requiresConfigMask, the result is zero.
            TypedValue tv = a.peekValue(R.styleable.lineage_SelfRemovingPreference_requiresConfig);
            if (tv != null && tv.resourceId != 0) {
                if (tv.type == TypedValue.TYPE_STRING &&
                        mContext.getResources().getString(tv.resourceId) == null) {
                    return false;
                } else if (tv.type == TypedValue.TYPE_INT_BOOLEAN && tv.data == 0) {
                    return false;
                } else if (tv.type == TypedValue.TYPE_INT_DEC) {
                    int mask = a.getInt(
                            R.styleable.lineage_SelfRemovingPreference_requiresConfigMask, -1);
                    if (tv.data == 0 || (mask >= 0 && (tv.data & mask) == 0)) {
                        return false;
                    }
                }
            }
        } finally {
            a.recycle();
        }

        return true;
    }

    /**
     * Returns whether the device supports a particular feature
     */
    public static boolean hasSystemFeature(Context context, String feature) {
        return context.getPackageManager().hasSystemFeature(feature);
    }

    /**
     * Returns whether the device is voice-capable (meaning, it is also a phone).
     */
    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null && telephony.isVoiceCapable();
    }

    /**
     * Checks if a package is installed. Set the ignoreState argument to true if you don't
     * care if the package is enabled/disabled.
     */
    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if a package is available to handle the given action.
     */
    public static boolean resolveIntent(Context context, Intent intent) {
        if (DEBUG) Log.d(TAG, "resolveIntent " + Objects.toString(intent));
        // check whether the target handler exist in system
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> results = pm.queryIntentActivitiesAsUser(intent,
                PackageManager.MATCH_SYSTEM_ONLY,
                UserHandle.myUserId());
        for (ResolveInfo resolveInfo : results) {
            // check is it installed in system.img, exclude the application
            // installed by user
            if (DEBUG) Log.d(TAG, "resolveInfo: " + Objects.toString(resolveInfo));
            if ((resolveInfo.activityInfo.applicationInfo.flags &
                    ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean resolveIntent(Context context, String action) {
        return resolveIntent(context, new Intent(action));
    }

    public static int getAttr(Context context, int attr, int fallbackAttr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        if (value.resourceId != 0) {
            return attr;
        }
        return fallbackAttr;
    }

    public void onAttached() {
        checkIntent();

        if (isAvailable() && mReplacesKey != null) {
            Graveyard.get(mContext).addTombstone(mReplacesKey);
        }

        Graveyard.get(mContext).summonReaper(mPref.getPreferenceManager());
    }

    public void onBindViewHolder(PreferenceViewHolder holder) {
        if (!isAvailable()) {
            return;
        }

        if (mSummaryMinLines > 0) {
            TextView textView = (TextView) holder.itemView.findViewById(android.R.id.summary);
            if (textView != null) {
                textView.setMinLines(mSummaryMinLines);
            }
        }
    }

    /**
     * If we want to keep this at the preference level vs the fragment level, we need to
     * collate all the preferences that need to be removed when attached to the
     * hierarchy, then purge them all when loading is complete. The Graveyard keeps track
     * of this, and will reap the dead when onAttached is called.
     */
    private static class Graveyard {

        private final Set<String> mDeathRow = new ArraySet<>();

        private static Graveyard sInstance;

        private final Context mContext;

        private Graveyard(Context context) {
            mContext = context;
        }

        public synchronized static Graveyard get(Context context) {
            if (sInstance == null) {
                sInstance = new Graveyard(context);
            }
            return sInstance;
        }

        public void addTombstone(String pref) {
            synchronized (mDeathRow) {
                mDeathRow.add(pref);
            }
        }

        private PreferenceGroup getParent(Preference p1, Preference p2) {
            return getParent(p1.getPreferenceManager().getPreferenceScreen(), p2);
        }

        private PreferenceGroup getParent(PreferenceGroup root, Preference preference) {
            for (int i = 0; i < root.getPreferenceCount(); i++) {
                Preference p = root.getPreference(i);
                if (p == preference)
                    return root;
                if (PreferenceGroup.class.isInstance(p)) {
                    PreferenceGroup parent = getParent((PreferenceGroup) p, preference);
                    if (parent != null)
                        return parent;
                }
            }
            return null;
        }

        public void summonReaper(PreferenceManager mgr) {
            synchronized (mDeathRow) {
                for (String dead : mDeathRow) {
                    Preference deadPref = mgr.findPreference(dead);
                    if (deadPref != null) {
                        deadPref.setVisible(false);
                    }
                }
                mDeathRow.clear();
            }
        }
    }
}
