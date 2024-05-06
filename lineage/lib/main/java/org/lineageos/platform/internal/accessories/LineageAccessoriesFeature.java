/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories;

import android.content.Context;
import android.os.Handler;

import org.lineageos.platform.internal.LineageBaseFeature;

public abstract class LineageAccessoriesFeature extends LineageBaseFeature {
    public LineageAccessoriesFeature(Context context, Handler handler) {
        super(context, handler);
    }

    public abstract boolean isSupported();
}
