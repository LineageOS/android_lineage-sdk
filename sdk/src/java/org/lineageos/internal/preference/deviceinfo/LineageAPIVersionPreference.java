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

package org.lineageos.internal.preference.deviceinfo;

import android.content.Context;
import android.os.SystemProperties;
import android.util.AttributeSet;

import lineageos.preference.SelfRemovingPreference;

import org.lineageos.platform.internal.R;

public class LineageAPIVersionPreference extends SelfRemovingPreference {
    private static final String TAG = "LineageAPIVersionPreference";

    private static final String KEY_BUILD_DATE_PROP = "ro.build.date";

    public LineageAPIVersionPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public LineageAPIVersionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LineageAPIVersionPreference(Context context) {
        super(context);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        setTitle(R.string.lineage_api_level);
        final int sdk = lineageos.os.Build.LINEAGE_VERSION.SDK_INT;
        StringBuilder builder = new StringBuilder();
        builder.append(lineageos.os.Build.getNameForSDKInt(sdk))
                .append(" (" + sdk + ")");
        setSummary(builder.toString());
    }
}
