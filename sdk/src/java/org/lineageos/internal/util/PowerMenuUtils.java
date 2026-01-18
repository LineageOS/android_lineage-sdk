/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.util;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.UserHandle;

import lineageos.providers.LineageSettings;

public final class PowerMenuUtils {
    public static boolean isAdvancedRestartPossible(final Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean keyguardLocked = km.inKeyguardRestrictedInputMode() && km.isKeyguardSecure();
        boolean advancedRestartEnabled = LineageSettings.Secure.getInt(context.getContentResolver(),
                LineageSettings.Secure.ADVANCED_REBOOT, 0) == 1;
        boolean isPrimaryUser = UserHandle.getCallingUserId() == UserHandle.USER_SYSTEM;

        return advancedRestartEnabled && !keyguardLocked && isPrimaryUser;
    }
}
