/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.applications;

import android.content.Context;

public class LineageActivityManager {
    private Context mContext;

    // Long screen related activity settings
    private LongScreen mLongScreen;

    public LineageActivityManager(Context context) {
        mContext = context;

        mLongScreen = new LongScreen(context);
    }

    public boolean shouldForceLongScreen(String packageName) {
        return mLongScreen.shouldForceLongScreen(packageName);
    }
}
