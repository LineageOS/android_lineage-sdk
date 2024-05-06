/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories

import android.content.Context
import android.os.Handler
import org.lineageos.platform.internal.LineageBaseFeature

abstract class LineageAccessoriesFeature(
    protected val context: Context,
    protected val handler: Handler
) : LineageBaseFeature(context, handler) {
    abstract fun isSupported(): Boolean
}
