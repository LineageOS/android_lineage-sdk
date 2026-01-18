/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-License-Identifier: Apache-2.0
 */
package lineageos.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * A Preference which can automatically remove itself from the hierarchy
 * based on constraints set in XML.
 */
public class SelfRemovingPreference extends Preference {

    private final ConstraintsHelper mConstraints;

    public SelfRemovingPreference(Context context, AttributeSet attrs,
                                  int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        mConstraints = new ConstraintsHelper(context, attrs, this);
    }

    public SelfRemovingPreference(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public SelfRemovingPreference(Context context, AttributeSet attrs) {
        this(context, attrs, ConstraintsHelper.getAttr(context,
                androidx.preference.R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    public SelfRemovingPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mConstraints.onAttached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mConstraints.onBindViewHolder(holder);
    }

    public void setAvailable(boolean available) {
        mConstraints.setAvailable(available);
    }

    public boolean isAvailable() {
        return mConstraints.isAvailable();
    }
}
