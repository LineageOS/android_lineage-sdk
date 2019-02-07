/*
 * Copyright (C) 2019 The LineageOS Project
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

package org.lineageos.platform.internal.display;

import android.content.Context;
import android.os.IBinder;

import lineageos.app.LineageContextConstants;
import lineageos.hardware.ILineageHWC2Service;
import lineageos.hardware.LineageHardwareManager;

import java.util.HashMap;

import com.android.server.LocalServices;
import com.android.server.display.DisplayTransformManager;

import org.lineageos.platform.internal.LineageSystemService;

import static com.android.server.display.DisplayTransformManager.LEVEL_COLOR_MATRIX_NIGHT_DISPLAY;
import static com.android.server.display.DisplayTransformManager.LEVEL_COLOR_MATRIX_GRAYSCALE;

import static lineageos.hardware.LineageHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION;
import static lineageos.hardware.LineageHardwareManager.FEATURE_READING_ENHANCEMENT;

/** @hide */
public class LineageHWC2Service extends LineageSystemService {

    private static final int LEVEL_COLOR_MATRIX_LIVEDISPLAY = LEVEL_COLOR_MATRIX_NIGHT_DISPLAY + 1;
    private static final int LEVEL_COLOR_MATRIX_READING = LEVEL_COLOR_MATRIX_GRAYSCALE + 1;

    private static final int MIN = 0;
    private static final int MAX = 255;

    /**
     * Matrix and offset used for converting color to grayscale.
     * Copied from com.android.server.accessibility.DisplayAdjustmentUtils.MATRIX_GRAYSCALE
     */
    private static final float[] MATRIX_GRAYSCALE = new float[] {
        .2126f, .2126f, .2126f, 0,
        .7152f, .7152f, .7152f, 0,
        .0722f, .0722f, .0722f, 0,
             0,      0,      0, 1
    };

    /** Full color matrix and offset */
    private static final float[] MATRIX_NORMAL = new float[] {
        1, 0, 0, 0,
        0, 1, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1
    };

    private final int[] mCurColors = new int[] { MAX, MAX, MAX };

    private HashMap<Integer, Boolean> mBooleanFeatureMap;

    private DisplayTransformManager mDTMService;

    private final IBinder mService = new ILineageHWC2Service.Stub() {

        public int getSupportedFeatures() {
            if (mDTMService == null) {
                return 0;
            }

            return FEATURE_DISPLAY_COLOR_CALIBRATION | FEATURE_READING_ENHANCEMENT;
        }

        public boolean get(int feature) {
            if (mBooleanFeatureMap.containsKey(feature)) {
                return mBooleanFeatureMap.get(feature);
            }

            return false;
        }

        public boolean set(int feature, boolean enable) {
            if (feature == (getSupportedFeatures() & feature)) {
                mBooleanFeatureMap.put(feature, enable);
            }

            switch (feature) {
                case FEATURE_READING_ENHANCEMENT:
                    mDTMService.setColorMatrix(LEVEL_COLOR_MATRIX_READING,
                            enable ? MATRIX_GRAYSCALE : MATRIX_NORMAL);
                    return true;
            }

            return false;
        }

        public int[] getDisplayColorCalibration() {
            return mCurColors;
        }

        public int getDisplayColorCalibrationMin() {
            return MIN;
        }

        public int getDisplayColorCalibrationMax() {
            return MAX;
        }

        public boolean setDisplayColorCalibration(int[] rgb) {
            for (int i = 0; i < rgb.length; i++) {
                mCurColors[i] = rgb[i];
            }

            mDTMService.setColorMatrix(LEVEL_COLOR_MATRIX_LIVEDISPLAY, toColorMatrix(rgb));
            return true;
        }

    };

    private static float[] toColorMatrix(int[] rgb) {
        float[] mat = new float[16];

        // sanity check
        for (int i = 0; i < 3; i++) {
            if (rgb[i] >= MAX) {
                rgb[i] = MAX;
            } else if (rgb[i] < MIN) {
                rgb[i] = MIN;
            }

            mat[i * 5] = (float)rgb[i] / (float)MAX;
        }

        mat[15] = 1.0f;
        return mat;
    }

    public LineageHWC2Service(Context context) {
        super(context);
        mBooleanFeatureMap = new HashMap<Integer, Boolean>();
        mDTMService = LocalServices.getService(DisplayTransformManager.class);
        publishBinderService(LineageContextConstants.LINEAGE_HWC2_SERVICE, mService);
    }


    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.HWC2_HELPER;
    }

    @Override
    public void onBootPhase(int phase) {
    }

    @Override
    public void onStart() {
    }

}
