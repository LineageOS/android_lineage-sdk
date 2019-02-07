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

package lineageos.hardware;

import java.util.HashMap;

import com.android.server.LocalServices;
import com.android.server.display.DisplayTransformManager;

import static com.android.server.display.DisplayTransformManager.LEVEL_COLOR_MATRIX_NIGHT_DISPLAY;

import static lineageos.hardware.LineageHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION;
import static lineageos.hardware.LineageHardwareManager.FEATURE_READING_ENHANCEMENT;

class HWC2Helper {

    private static final int LEVEL_COLOR_MATRIX_LIVEDISPLAY = LEVEL_COLOR_MATRIX_NIGHT_DISPLAY + 1;
    private static final int LEVEL_COLOR_MATRIX_READING = LEVEL_COLOR_MATRIX_GRAYSCALE + 1;

    private static final int MIN = 0;
    private static final int MAX = 255;

    private static final int[] sCurColors = new int[] { MAX, MAX, MAX };

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

    private static HashMap<Integer, Boolean> sBooleanFeatureMap = new HashMap<Integer, Boolean>();

    private static DisplayTransformManager sDTMService =
            LocalServices.getService(DisplayTransformManager.class);

    static int getSupportedFeature() {
        if (sDTMService == null) {
            return 0;
        }

        return FEATURE_DISPLAY_COLOR_CALIBRATION | FEATURE_READING_ENHANCEMENT;
    }

    static boolean get(int feature) {
        if (sBooleanFeatureMap.contains(feature)) {
            return sBooleanFeatureMap.get(feature);
        }
    }

    static boolean set(int feature, boolean enable) {
        sBooleanFeatureMap.put(feature, enable);

        switch (feature) {
            case FEATURE_READING_ENHANCEMENT:
                sDTMService.setColorMatrix(LEVEL_COLOR_MATRIX_READING,
                        enable ? MATRIX_GRAYSCALE : MATRIX_NORMAL);
                return true;
            }
        }

        return false;
    }

    static int[] getDisplayColorCalibration() {
        return sCurColors;
    }

    static int getDisplayColorCalibrationMin() {
        return MIN;
    }

    static int getDisplayColorCalibrationMax() {
        return MAX;
    }

    static boolean setDisplayColorCalibration(int[] rgb) {
        sCurColors = rgb;

        sDTMService.setColorMatrix(LEVEL_COLOR_MATRIX_LIVEDISPLAY, toColorMatrix(rgb));
        return true;
    }

    private static float[] toColorMatrix(String rgbString) {
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

}
