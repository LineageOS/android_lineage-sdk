/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.health;

import android.content.Context;
import android.os.Handler;

import org.lineageos.platform.internal.LineageBaseFeature;

public abstract class LineageHealthFeature extends LineageBaseFeature {
    protected static final String TAG = "LineageHealth";

    public LineageHealthFeature(Context context, Handler handler) {
        super(context, handler);
    }

    public abstract boolean isSupported();
}
