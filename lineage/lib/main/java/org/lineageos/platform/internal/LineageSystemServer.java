/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.SystemServiceManager;

import org.lineageos.platform.internal.common.LineageSystemServiceHelper;

/**
 * Base Lineage System Server which handles the starting and states of various Lineage
 * specific system services. Since its part of the main looper provided by the system
 * server, it will be available indefinitely (until all the things die).
 */
public class LineageSystemServer {
    private static final String TAG = "LineageSystemServer";
    private final Context mSystemContext;
    private final LineageSystemServiceHelper mSystemServiceHelper;

    private static final String ENCRYPTING_STATE = "trigger_restart_min_framework";
    private static final String ENCRYPTED_STATE = "1";

    public LineageSystemServer(Context systemContext) {
        mSystemContext = systemContext;
        mSystemServiceHelper = new LineageSystemServiceHelper(mSystemContext);
    }

    public static boolean coreAppsOnly() {
        // Only run "core" apps+services if we're encrypting the device.
        final String cryptState = SystemProperties.get("vold.decrypt");
        final boolean isAlarmBoot = SystemProperties.getBoolean("ro.alarm_boot", false);
        return ENCRYPTING_STATE.equals(cryptState) ||
               ENCRYPTED_STATE.equals(cryptState) ||
               isAlarmBoot;
    }

    /**
     * Invoked via reflection by the SystemServer
     */
    private void run() {
        // Start services.
        try {
            startServices();
        } catch (Throwable ex) {
            Slog.e("System", "******************************************");
            Slog.e("System", "************ Failure starting lineage system services", ex);
            throw ex;
        }
    }

    private void startServices() {
        final Context context = mSystemContext;
        final SystemServiceManager ssm = LocalServices.getService(SystemServiceManager.class);
        String[] externalServices = context.getResources().getStringArray(
                org.lineageos.platform.internal.R.array.config_externalLineageServices);

        for (String service : externalServices) {
            try {
                Slog.i(TAG, "Attempting to start service " + service);
                LineageSystemService lineageSystemService =  mSystemServiceHelper.getServiceFor(service);
                if (context.getPackageManager().hasSystemFeature(
                        lineageSystemService.getFeatureDeclaration())) {
                    if (coreAppsOnly() && !lineageSystemService.isCoreService()) {
                        Slog.d(TAG, "Not starting " + service +
                                " - only parsing core apps");
                    } else {
                        Slog.i(TAG, "Starting service " + service);
                        ssm.startService(lineageSystemService.getClass());
                    }
                } else {
                    Slog.i(TAG, "Not starting service " + service +
                            " due to feature not declared on device");
                }
            } catch (Throwable e) {
                reportWtf("starting " + service , e);
            }
        }
    }

    private void reportWtf(String msg, Throwable e) {
        Slog.w(TAG, "***********************************************");
        Slog.wtf(TAG, "BOOT FAILURE " + msg, e);
    }
}
