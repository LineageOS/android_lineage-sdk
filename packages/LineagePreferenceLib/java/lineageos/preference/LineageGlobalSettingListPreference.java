/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.preference;

import android.content.Context;
import android.util.AttributeSet;

import lineageos.providers.LineageSettings;

public class LineageGlobalSettingListPreference extends SelfRemovingListPreference {

    public LineageGlobalSettingListPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public LineageGlobalSettingListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public int getIntValue(int defValue) {
        return getValue() == null ? defValue : Integer.valueOf(getValue());
    }

    @Override
    protected boolean isPersisted() {
        return LineageSettings.Global.getString(getContext().getContentResolver(),
                getKey()) != null;
    }

    @Override
    protected void putString(String key, String value) {
        LineageSettings.Global.putString(getContext().getContentResolver(), key, value);
    }

    @Override
    protected String getString(String key, String defaultValue) {
        return LineageSettings.Global.getString(getContext().getContentResolver(),
                key, defaultValue);
    }
}
