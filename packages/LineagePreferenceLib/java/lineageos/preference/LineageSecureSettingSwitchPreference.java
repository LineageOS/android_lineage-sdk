/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.preference;

import android.content.Context;
import android.util.AttributeSet;

import lineageos.providers.LineageSettings;

public class LineageSecureSettingSwitchPreference extends SelfRemovingSwitchPreference {

    public LineageSecureSettingSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public LineageSecureSettingSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LineageSecureSettingSwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected boolean isPersisted() {
        return LineageSettings.Secure.getString(getContext().getContentResolver(), getKey()) != null;
    }

    @Override
    protected void putBoolean(String key, boolean value) {
        LineageSettings.Secure.putInt(getContext().getContentResolver(), key, value ? 1 : 0);
    }

    @Override
    protected boolean getBoolean(String key, boolean defaultValue) {
        return LineageSettings.Secure.getInt(getContext().getContentResolver(),
                key, defaultValue ? 1 : 0) != 0;
    }
}
