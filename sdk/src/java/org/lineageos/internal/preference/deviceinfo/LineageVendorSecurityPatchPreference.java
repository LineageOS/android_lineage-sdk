/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2017-2018 The LineageOS Project
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

package org.lineageos.internal.preference.deviceinfo;

import android.content.Context;
import android.os.SystemProperties;
import android.text.format.DateFormat;
import android.util.AttributeSet;

import lineageos.preference.SelfRemovingPreference;

import org.lineageos.platform.internal.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LineageVendorSecurityPatchPreference extends SelfRemovingPreference {
    private static final String KEY_LINEAGE_VENDOR_SECURITY_PATCH =
            "ro.lineage.build.vendor_security_patch";

    public LineageVendorSecurityPatchPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    public LineageVendorSecurityPatchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LineageVendorSecurityPatchPreference(Context context) {
        super(context);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        setTitle(R.string.lineage_vendor_security_patch);
        setSummary(getVendorSecurityPatchLevel());
    }

    private String getVendorSecurityPatchLevel() {
        String na = getContext().getResources().getString(R.string.not_available);
        String patch = SystemProperties.get(KEY_LINEAGE_VENDOR_SECURITY_PATCH, na);
        if (!na.equals(patch)) {
            try {
                SimpleDateFormat template = new SimpleDateFormat("yyyy-MM-dd");
                Date patchDate = template.parse(patch);
                String format = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                        "dMMMMyyyy");
                patch = DateFormat.format(format, patchDate).toString();
            } catch (ParseException e) {
                // parsing failed, use raw string
            }
        }
        return patch;
    }
}
