/**
 * Copyright (c) 2015-2016, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.tests.hardware;

import android.os.Bundle;

import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

import lineageos.hardware.LineageHardwareManager;
import lineageos.hardware.DisplayMode;

import org.lineageos.tests.TestActivity;

/**
 * Created by adnan on 8/31/15.
 */
public class LineageHardwareTest extends TestActivity {
    private LineageHardwareManager mHardwareManager;

    private static final List<Integer> FEATURES = Arrays.asList(
            LineageHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT,
            LineageHardwareManager.FEATURE_COLOR_ENHANCEMENT,
            LineageHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION,
            LineageHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY,
            LineageHardwareManager.FEATURE_KEY_DISABLE,
            LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT,
            LineageHardwareManager.FEATURE_TOUCH_HOVERING,
            LineageHardwareManager.FEATURE_AUTO_CONTRAST,
            LineageHardwareManager.FEATURE_DISPLAY_MODES,
    );

    private static final List<String> FEATURE_STRINGS = Arrays.asList(
            "FEATURE_ADAPTIVE_BACKLIGHT",
            "FEATURE_COLOR_ENHANCEMENT",
            "FEATURE_DISPLAY_COLOR_CALIBRATION",
            "FEATURE_HIGH_TOUCH_SENSITIVITY",
            "FEATURE_KEY_DISABLE",
            "FEATURE_SUNLIGHT_ENHANCEMENT",
            "FEATURE_TOUCH_HOVERING",
            "FEATURE_AUTO_CONTRAST",
            "FEATURE_DISPLAY_MODES",
    );

    private static final List<Integer> BOOLEAN_FEATURES = Arrays.asList(
            LineageHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT,
            LineageHardwareManager.FEATURE_COLOR_ENHANCEMENT,
            LineageHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY,
            LineageHardwareManager.FEATURE_KEY_DISABLE,
            LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT,
            LineageHardwareManager.FEATURE_TOUCH_HOVERING,
            LineageHardwareManager.FEATURE_AUTO_CONTRAST
    );

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mHardwareManager = LineageHardwareManager.getInstance(this);
    }

    @Override
    protected String tag() {
        return null;
    }

    @Override
    protected Test[] tests() {
        return mTests;
    }

    private boolean vibratorSupported() {
        if (mHardwareManager.isSupported(LineageHardwareManager.FEATURE_VIBRATOR)) {
            return true;
        } else {
            Toast.makeText(LineageHardwareTest.this, "Vibrator not supported",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean displayColorCalibrationSupported() {
        if (mHardwareManager.isSupported(LineageHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            return true;
        } else {
            Toast.makeText(LineageHardwareTest.this, "Display Color Calibration not supported",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean displayModesSupported() {
        if (mHardwareManager.isSupported(LineageHardwareManager.FEATURE_DISPLAY_MODES)) {
            return true;
        } else {
            Toast.makeText(LineageHardwareTest.this, "Display modes not supported",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private Test[] mTests = new Test[] {
            new Test("Test get supported features") {
                public void run() {
                    Toast.makeText(LineageHardwareTest.this, "Supported features " +
                                    mHardwareManager.getSupportedFeatures(),
                            Toast.LENGTH_SHORT).show();
                }
            },
            new Test("Test features supported") {
                @Override
                protected void run() {
                    StringBuilder builder = new StringBuilder();
                    int i = 0;
                    for (int feature : FEATURES) {
                        boolean supported = mHardwareManager.isSupported(feature);
                        if (mHardwareManager.isSupported(FEATURE_STRINGS.get(i)) != supported) {
                            throw new RuntimeException("Internal error, feature string lookup failed");
                        }
                        i++;
                        builder.append("Feature " + feature + "\n")
                                .append("is supported " + supported + "\n");
                    }
                    Toast.makeText(LineageHardwareTest.this, "Supported features " +
                                    builder.toString(),
                            Toast.LENGTH_SHORT).show();
                }
            },
            new Test("Test boolean features enabled") {
                @Override
                protected void run() {
                    StringBuilder builder = new StringBuilder();
                    for (int feature : BOOLEAN_FEATURES) {
                        builder.append("Feature " + feature + "\n")
                                .append("is enabled " + mHardwareManager.isSupported(feature)
                                        + "\n");
                    }
                    Toast.makeText(LineageHardwareTest.this, "Features " +
                                    builder.toString(),
                            Toast.LENGTH_SHORT).show();
                }
            },
            new Test("Test get vibrator intensity") {
                @Override
                protected void run() {
                    if (vibratorSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Vibrator intensity " +
                                        mHardwareManager.getVibratorIntensity(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test get vibrator default intensity") {
                @Override
                protected void run() {
                    if (vibratorSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Vibrator default intensity " +
                                        mHardwareManager.getVibratorDefaultIntensity(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test get vibrator max intensity") {
                @Override
                protected void run() {
                    if (vibratorSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Vibrator max intensity " +
                                        mHardwareManager.getVibratorMaxIntensity(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test get vibrator min intensity") {
                @Override
                protected void run() {
                    if (vibratorSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Vibrator min intensity " +
                                        mHardwareManager.getVibratorMinIntensity(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test get vibrator min intensity") {
                @Override
                protected void run() {
                    if (vibratorSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Vibrator min intensity " +
                                        mHardwareManager.getVibratorWarningIntensity(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test Display Color Calibration") {
                @Override
                protected void run() {
                    if (displayColorCalibrationSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Display Color Calibration " +
                                        mHardwareManager.getDisplayColorCalibration(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test Default Display Color Calibration") {
                @Override
                protected void run() {
                    if (displayColorCalibrationSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Default Display Color Calibration " +
                                        mHardwareManager.getDisplayColorCalibrationDefault(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test Display Color Calibration Max") {
                @Override
                protected void run() {
                    if (displayColorCalibrationSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Display Color Calibration Max " +
                                        mHardwareManager.getDisplayColorCalibrationMax(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test Display Color Calibration Min") {
                @Override
                protected void run() {
                    if (displayColorCalibrationSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Display Color Calibration Min " +
                                        mHardwareManager.getDisplayColorCalibrationMin(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test Set Display Color Calibration") {
                @Override
                protected void run() {
                    if (displayColorCalibrationSupported()) {
                        mHardwareManager.setDisplayColorCalibration(new int[] {0,0,0});
                    }
                }
            },
            new Test("Test Get Display Modes") {
                @Override
                protected void run() {
                    if (displayModesSupported()) {
                        StringBuilder builder = new StringBuilder();
                        for (DisplayMode displayMode : mHardwareManager.getDisplayModes()) {
                            builder.append("Display mode " + displayMode.name + "\n");
                        }
                        Toast.makeText(LineageHardwareTest.this, "Display modes: \n"
                                        + builder.toString(), Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test Get Current Display Mode") {
                @Override
                protected void run() {
                    if (displayModesSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Default Display Mode " +
                                        mHardwareManager.getCurrentDisplayMode(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
            new Test("Test Get Default Display Mode") {
                @Override
                protected void run() {
                    if (displayModesSupported()) {
                        Toast.makeText(LineageHardwareTest.this, "Default Display Mode " +
                                        mHardwareManager.getCurrentDisplayMode(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            },
    };
}
