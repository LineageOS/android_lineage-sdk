/*
 * Copyright (C) 2018 The LineageOS Project
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
import android.content.Intent;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.lineageos.platform.internal.R;

public class LineageVersionTextView extends TextView implements View.OnClickListener {
    private static final String TAG = "LineageVersionTextView";

    private static final String KEY_LINEAGE_VERSION_PROP = "ro.lineage.version";

    private static final String PLATLOGO_PACKAGE_NAME = "org.lineageos.lineageparts";
    private static final String PLATLOGO_ACTIVITY_CLASS =
            PLATLOGO_PACKAGE_NAME + ".logo.PlatLogoActivity";

    private long[] mHits = new long[3];

    public LineageVersionTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setText(SystemProperties.get(KEY_LINEAGE_VERSION_PROP,
                getContext().getResources().getString(R.string.unknown)));
        setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
        mHits[mHits.length - 1] = SystemClock.uptimeMillis();
        if (mHits[0] >= (SystemClock.uptimeMillis() - 500)) {
            launchLogoActivity();
        }
    }

    private void launchLogoActivity() {
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClassName(PLATLOGO_PACKAGE_NAME, PLATLOGO_ACTIVITY_CLASS);
        try {
            getContext().startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Unable to start activity " + intent.toString());
        }
    }

}
