/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal;

import android.content.Context;

import lineageos.app.LineageContextConstants;

/** @hide */
public class LineageSettingsService extends LineageSystemService {

    private static final String TAG = LineageSettingsService.class.getSimpleName();

    private final Context mContext;

    public LineageSettingsService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.SETTINGS;
    }

    @Override
    public void onBootPhase(int phase) {
    }

    @Override
    public void onStart() {
    }
}
