/*
 * SPDX-FileCopyrightText: SHIFT GmbH
 * SPDX-License-Identifier: Apache-2.0
 */

package shiftos.preference;

import android.content.Context;
import android.util.AttributeSet;
import lineageos.preference.SelfRemovingSwitchPreference;
import shiftos.providers.ShiftOsSettings;

public class SecureSettingSwitchPreference extends SelfRemovingSwitchPreference {

    public SecureSettingSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SecureSettingSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SecureSettingSwitchPreference(Context context) {
        super(context, null);
    }

    @Override
    protected boolean isPersisted() {
        return ShiftOsSettings.Secure.getString(getContext().getContentResolver(),
                getKey()) != null;
    }

    @Override
    protected void putBoolean(String key, boolean value) {
        ShiftOsSettings.Secure.putInt(getContext().getContentResolver(), key, value ? 1 : 0);
    }

    @Override
    protected boolean getBoolean(String key, boolean defaultValue) {
        return ShiftOsSettings.Secure.getInt(getContext().getContentResolver(),
                key, defaultValue ? 1 : 0) != 0;
    }
}
