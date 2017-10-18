/*
 * Copyright (C) 2017 The LineageOS Project
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
        boolean isPrimaryUser = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;

        return advancedRestartEnabled && !keyguardLocked && isPrimaryUser;
    }
}
