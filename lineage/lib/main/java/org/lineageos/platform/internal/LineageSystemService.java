/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal;

import android.content.Context;

import com.android.server.SystemService;

public abstract class LineageSystemService extends SystemService {
    public LineageSystemService(Context context) {
        super(context);
    }

    public abstract String getFeatureDeclaration();


    /**
     * Override and return true if the service should be started
     * before the device is decrypted.
     */
    public boolean isCoreService() {
        return true;
    }
}
