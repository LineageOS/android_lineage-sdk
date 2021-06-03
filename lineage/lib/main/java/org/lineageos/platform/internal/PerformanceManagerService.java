/*
 * Copyright (C) 2014 The CyanogenMod Project
 *               2018-2021 The LineageOS Project
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

package org.lineageos.platform.internal;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager.ServiceType;
import android.os.PowerManagerInternal;
import android.os.PowerSaveState;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.server.ServiceThread;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import lineageos.app.LineageContextConstants;
import lineageos.power.IPerformanceManager;
import lineageos.power.PerformanceProfile;

import static lineageos.power.PerformanceManager.PROFILE_BALANCED;
import static lineageos.power.PerformanceManager.PROFILE_POWER_SAVE;
import static lineageos.providers.LineageSettings.Secure.PERFORMANCE_PROFILE;
import static lineageos.providers.LineageSettings.Secure.getInt;
import static lineageos.providers.LineageSettings.Secure.getUriFor;
import static lineageos.providers.LineageSettings.Secure.putInt;

/**
 * @hide
 */
public class PerformanceManagerService extends LineageSystemService {

    private static final String TAG = "PerformanceManager";

    private static final boolean DEBUG = false;

    private final Context mContext;

    private final ArrayMap<Integer, PerformanceProfile> mProfiles = new ArrayMap<>();

    private int mNumProfiles = 0;

    private final ServiceThread mHandlerThread;
    private final HintHandler mHandler;
    private final Thread mWaitMpctlThread;

    // keep in sync with hardware/libhardware/include/hardware/power.h
    private final int POWER_HINT_SET_PROFILE  = 0x00000111;

    private final int POWER_FEATURE_SUPPORTED_PROFILES = 0x00001000;

    private PowerManagerInternal mPm;

    // Observes user-controlled settings
    private PerformanceSettingsObserver mObserver;

    // Take lock when accessing mProfiles
    private final Object mLock = new Object();

    // Manipulate state variables under lock
    private boolean mLowPowerModeEnabled = false;
    private boolean mMpctlReady          = true;
    private boolean mSystemReady         = false;
    private int     mUserProfile         = -1;
    private int     mActiveProfile       = -1;

    // Dumpable circular buffer for boost logging
    private final PerformanceLog mPerformanceLog = new PerformanceLog();

    // Events on the handler
    private static final int MSG_SET_PROFILE  = 1;

    // PowerManager ServiceType to use when we're only
    // interested in gleaning global battery saver state.
    private static final int SERVICE_TYPE_DUMMY = ServiceType.LOCATION;

    public PerformanceManagerService(Context context) {
        super(context);

        mContext = context;

        // We need a higher priority thread to handle these requests in front of
        // everything else asynchronously
        mHandlerThread = new ServiceThread(TAG,
                Process.THREAD_PRIORITY_DISPLAY, false /*allowIo*/);
        mHandlerThread.start();

        mHandler = new HintHandler(mHandlerThread.getLooper());

        if (mContext.getResources().getBoolean(R.bool.config_waitForMpctlOnBoot)) {
            mMpctlReady = false;
            mWaitMpctlThread = new Thread(() -> {
                int retries = 20;
                while (retries-- > 0) {
                    if (!SystemProperties.getBoolean("sys.post_boot.parsed", false) &&
                            !SystemProperties.getBoolean("vendor.post_boot.parsed", false)) {
                        continue;
                    }

                    if (SystemProperties.get("init.svc.perfd").equals("running") ||
                            SystemProperties.get("init.svc.vendor.perfd").equals("running") ||
                            SystemProperties.get("init.svc.perf-hal-1-0").equals("running") ||
                            SystemProperties.get("init.svc.perf-hal-2-0").equals("running") ||
                            SystemProperties.get("init.svc.perf-hal-2-1").equals("running") ||
                            SystemProperties.get("init.svc.perf-hal-2-2").equals("running") ||
                            SystemProperties.get("init.svc.mpdecision").equals("running")) {
                        break;
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Slog.w(TAG, "Interrupted:", e);
                    }
                }

                // Give mp-ctl enough time to initialize
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Slog.w(TAG, "Interrupted:", e);
                }

                synchronized (mLock) {
                    mMpctlReady = true;
                    applyProfileLocked();
                }
            });
            mWaitMpctlThread.setDaemon(true);
        } else {
            mWaitMpctlThread = null;
        }
    }

    private class PerformanceSettingsObserver extends ContentObserver {

        private final Uri PERFORMANCE_PROFILE_URI =
                getUriFor(PERFORMANCE_PROFILE);

        private final ContentResolver mCR;

        public PerformanceSettingsObserver(Context context, Handler handler) {
            super(handler);
            mCR = context.getContentResolver();
        }

        public void observe(boolean enabled) {
            if (enabled) {
                mCR.registerContentObserver(PERFORMANCE_PROFILE_URI, false, this);
                onChange(false);
            } else {
                mCR.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            int profile = getInt(mCR, PERFORMANCE_PROFILE, PROFILE_BALANCED);

            synchronized (mLock) {
                if (mUserProfile < 0) {
                    mUserProfile = profile;
                    setPowerProfileLocked(mUserProfile, false);
                }
            }
        }
    };

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.PERFORMANCE;
    }

    @Override
    public void onStart() {
        publishBinderService(LineageContextConstants.LINEAGE_PERFORMANCE_SERVICE, mBinder);
    }

    private void populateProfilesLocked() {
        mProfiles.clear();

        Resources res = mContext.getResources();
        String[] profileNames = res.getStringArray(R.array.perf_profile_entries);
        int[] profileIds = res.getIntArray(R.array.perf_profile_values);
        String[] profileWeights = res.getStringArray(R.array.perf_profile_weights);
        String[] profileDescs = res.getStringArray(R.array.perf_profile_summaries);

        for (int i = 0; i < profileIds.length; i++) {
            if (profileIds[i] >= mNumProfiles) {
                continue;
            }
            float weight = Float.valueOf(profileWeights[i]);
            mProfiles.put(profileIds[i], new PerformanceProfile(profileIds[i],
                    weight, profileNames[i], profileDescs[i]));
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY && !mSystemReady) {
            synchronized (mLock) {
                mPm = getLocalService(PowerManagerInternal.class);
                mNumProfiles = mPm.getFeature(POWER_FEATURE_SUPPORTED_PROFILES);

                if (hasProfiles()) {
                    populateProfilesLocked();

                    mObserver = new PerformanceSettingsObserver(mContext, mHandler);
                    mObserver.observe(true);
                }

                mSystemReady = true;

                if (hasProfiles()) {
                    setPowerProfileLocked(mUserProfile, false);
                    mPm.registerLowPowerModeObserver(mLowPowerModeListener);
                    mContext.registerReceiver(mLocaleChangedReceiver,
                            new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
                }
            }
        } else if (phase == PHASE_BOOT_COMPLETED && !mMpctlReady) {
            mWaitMpctlThread.start();
        }
    }

    private boolean hasProfiles() {
        return mNumProfiles > 0;
    }

    /**
     * Apply a power profile and persist if fromUser = true
     * <p>
     * Must call with lock held.
     *
     * @param profile  power profile
     * @param fromUser true to persist the profile
     * @return true if the active profile changed
     */
    private boolean setPowerProfileLocked(int profile, boolean fromUser) {
        if (DEBUG) {
            Slog.v(TAG, String.format(Locale.US,"setPowerProfileL(%d, fromUser=%b)", profile, fromUser));
        }

        if (!mSystemReady || !mMpctlReady) {
            Slog.e(TAG, "System is not ready, dropping profile request");
            return false;
        }

        if (!mProfiles.containsKey(profile)) {
            Slog.e(TAG, "Invalid profile: " + profile);
            return false;
        }

        boolean isProfileSame = profile == mActiveProfile;

        if (!isProfileSame && profile != PROFILE_POWER_SAVE &&
                mActiveProfile == PROFILE_POWER_SAVE) {
            long token = Binder.clearCallingIdentity();
            mPm.setPowerSaveMode(false);
            Binder.restoreCallingIdentity(token);
        }

        /**
         * It's possible that mCurrrentProfile != getUserProfile() because of a
         * per-app profile. Store the user's profile preference and then bail
         * early if there is no work to be done.
         */
        if (fromUser) {
            putInt(mContext.getContentResolver(), PERFORMANCE_PROFILE, profile);
            mUserProfile = profile;
        }

        if (isProfileSame) {
            return false;
        }

        // Enforce the performance access permission declared by lineage's res package
        mContext.enforceCallingOrSelfPermission(
                lineageos.platform.Manifest.permission.PERFORMANCE_ACCESS, null);

        long token = Binder.clearCallingIdentity();

        mActiveProfile = profile;

        mHandler.obtainMessage(MSG_SET_PROFILE, profile,
                (fromUser ? 1 : 0)).sendToTarget();

        Binder.restoreCallingIdentity(token);

        return true;
    }

    private void applyProfileLocked() {
        if (!hasProfiles()) {
            // don't have profiles, bail.
            return;
        }

        final int profile;
        if (mLowPowerModeEnabled) {
            // LPM always wins
            profile = PROFILE_POWER_SAVE;
        } else {
            profile = mUserProfile;
        }

        setPowerProfileLocked(profile, false);
    }

    private final IBinder mBinder = new IPerformanceManager.Stub() {

        @Override
        public boolean setPowerProfile(int profile) {
            synchronized (mLock) {
                return setPowerProfileLocked(profile, true);
            }
        }

        @Override
        public int getPowerProfile() {
            synchronized (mLock) {
                return mUserProfile;
            }
        }

        @Override
        public PerformanceProfile getPowerProfileById(int profile) {
            synchronized (mLock) {
                return mProfiles.get(profile);
            }
        }

        @Override
        public PerformanceProfile getActivePowerProfile() {
            synchronized (mLock) {
                return mProfiles.get(mUserProfile);
            }
        }

        @Override
        public int getNumberOfProfiles() {
            return mNumProfiles;
        }

        @Override
        public PerformanceProfile[] getPowerProfiles() throws RemoteException {
            synchronized (mLock) {
                return mProfiles.values().toArray(new PerformanceProfile[0]);
            }
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, TAG);

            synchronized (mLock) {
                pw.println();
                pw.println("PerformanceManager Service State:");
                pw.println();

                if (!hasProfiles()) {
                    pw.println(" No profiles available.");
                } else {
                    pw.println(" User-selected profile: " +
                            Objects.toString(mProfiles.get(mUserProfile)));
                    if (mUserProfile != mActiveProfile) {
                        pw.println(" System-selected profile: " +
                                Objects.toString(mProfiles.get(mActiveProfile)));
                    }
                    pw.println();
                    pw.println(" Supported profiles:");
                    for (Map.Entry<Integer, PerformanceProfile> profile : mProfiles.entrySet()) {
                        pw.println("  " + profile.getKey() + ": " + profile.getValue().toString());
                    }
                    pw.println();
                    mPerformanceLog.dump(pw);
                }
            }
        }
    };

    private static class PerformanceLog {
        static final int USER_PROFILE = 2;

        static final String[] EVENTS = new String[] { "USER_PROFILE" };

        private static final int LOG_BUF_SIZE = 25;

        static class Entry {
            private final long timestamp;
            private final int event;
            private final String info;

            Entry(long timestamp_, int event_, String info_) {
                timestamp = timestamp_;
                event = event_;
                info = info_;
            }
        }

        private final ArrayDeque<Entry> mBuffer = new ArrayDeque<>(LOG_BUF_SIZE);

        void log(int event, String info) {
            synchronized (mBuffer) {
                mBuffer.add(new Entry(System.currentTimeMillis(), event, info));
                if (mBuffer.size() >= LOG_BUF_SIZE) {
                    mBuffer.poll();
                }
            }
        }

        void dump(PrintWriter pw) {
            synchronized (mBuffer) {
                pw.println("Performance log:");
                for (Entry entry : mBuffer) {
                    pw.println(String.format("  %1$tH:%1$tM:%1$tS.%1$tL: %2$14s  %3$s",
                            new Date(entry.timestamp), EVENTS[entry.event], entry.info));
                }
                pw.println();
            }
        }
    }

    /**
     * Handler for asynchronous operations performed by the performance manager.
     */
    private final class HintHandler extends Handler {

        public HintHandler(Looper looper) {
            super(looper, null, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SET_PROFILE:
                    mPm.powerHint(POWER_HINT_SET_PROFILE, msg.arg1);
                    mPerformanceLog.log(PerformanceLog.USER_PROFILE, "profile=" + msg.arg1);
                    break;
            }
        }
    }

    private final PowerManagerInternal.LowPowerModeListener mLowPowerModeListener = new
            PowerManagerInternal.LowPowerModeListener() {

                @Override
                public void onLowPowerModeChanged(PowerSaveState state) {
                    final boolean enabled = state.globalBatterySaverEnabled;
                    synchronized (mLock) {
                        if (enabled == mLowPowerModeEnabled) {
                            return;
                        }
                        if (DEBUG) {
                            Slog.d(TAG, "low power mode enabled: " + enabled);
                        }
                        mLowPowerModeEnabled = enabled;
                        applyProfileLocked();
                    }
                }

                @Override
                public int getServiceType() {
                    return SERVICE_TYPE_DUMMY;
                }
            };

    private final BroadcastReceiver mLocaleChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                populateProfilesLocked();
            }
        }
    };
}
