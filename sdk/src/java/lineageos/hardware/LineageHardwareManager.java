/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
 *               2017-2018 The LineageOS Project
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

import android.content.Context;
import android.hidl.base.V1_0.IBase;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Range;

import com.android.internal.annotations.VisibleForTesting;

import lineageos.app.LineageContextConstants;
import lineageos.hardware.DisplayMode;
import lineageos.hardware.HIDLHelper;
import lineageos.hardware.HSIC;
import lineageos.hardware.TouchscreenGesture;

import vendor.lineage.livedisplay.V2_0.IAdaptiveBacklight;
import vendor.lineage.livedisplay.V2_0.IAutoContrast;
import vendor.lineage.livedisplay.V2_0.IColorBalance;
import vendor.lineage.livedisplay.V2_0.IColorEnhancement;
import vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration;
import vendor.lineage.livedisplay.V2_0.IDisplayModes;
import vendor.lineage.livedisplay.V2_0.IPictureAdjustment;
import vendor.lineage.livedisplay.V2_0.IReadingEnhancement;
import vendor.lineage.livedisplay.V2_0.ISunlightEnhancement;
import vendor.lineage.touch.V1_0.IGloveMode;
import vendor.lineage.touch.V1_0.IKeyDisabler;
import vendor.lineage.touch.V1_0.IStylusMode;
import vendor.lineage.touch.V1_0.ITouchscreenGesture;

import java.io.UnsupportedEncodingException;
import java.lang.IllegalArgumentException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Manages access to LineageOS hardware extensions
 *
 *  <p>
 *  This manager requires the HARDWARE_ABSTRACTION_ACCESS permission.
 *  <p>
 *  To get the instance of this class, utilize LineageHardwareManager#getInstance(Context context)
 */
public final class LineageHardwareManager {
    private static final String TAG = "LineageHardwareManager";

    // The VisibleForTesting annotation is to ensure Proguard doesn't remove these
    // fields, as they might be used via reflection. When the @Keep annotation in
    // the support library is properly handled in the platform, we should change this.

    /**
     * Adaptive backlight support (this refers to technologies like NVIDIA SmartDimmer,
     * QCOM CABL or Samsung CABC)
     */
    @VisibleForTesting
    public static final int FEATURE_ADAPTIVE_BACKLIGHT = 0x1;

    /**
     * Color enhancement support
     */
    @VisibleForTesting
    public static final int FEATURE_COLOR_ENHANCEMENT = 0x2;

    /**
     * Display RGB color calibration
     */
    @VisibleForTesting
    public static final int FEATURE_DISPLAY_COLOR_CALIBRATION = 0x4;

    /**
     * High touch sensitivity for touch panels
     */
    @VisibleForTesting
    public static final int FEATURE_HIGH_TOUCH_SENSITIVITY = 0x10;

    /**
     * Hardware navigation key disablement
     */
    @VisibleForTesting
    public static final int FEATURE_KEY_DISABLE = 0x20;

    /**
     * Increased display readability in bright light
     */
    @VisibleForTesting
    public static final int FEATURE_SUNLIGHT_ENHANCEMENT = 0x100;

    /**
     * Variable vibrator intensity
     */
    @VisibleForTesting
    public static final int FEATURE_VIBRATOR = 0x400;

    /**
     * Touchscreen hovering
     */
    @VisibleForTesting
    public static final int FEATURE_TOUCH_HOVERING = 0x800;

    /**
     * Auto contrast
     */
    @VisibleForTesting
    public static final int FEATURE_AUTO_CONTRAST = 0x1000;

    /**
     * Display modes
     */
    @VisibleForTesting
    public static final int FEATURE_DISPLAY_MODES = 0x2000;

    /**
     * Reading mode
     */
    @VisibleForTesting
    public static final int FEATURE_READING_ENHANCEMENT = 0x4000;

    /**
     * Color balance
     */
    @VisibleForTesting
    public static final int FEATURE_COLOR_BALANCE = 0x20000;

    /**
     * HSIC picture adjustment
     */
    @VisibleForTesting
    public static final int FEATURE_PICTURE_ADJUSTMENT = 0x40000;

    /**
     * Touchscreen gesture
     */
    @VisibleForTesting
    public static final int FEATURE_TOUCHSCREEN_GESTURES = 0x80000;

    private static final List<Integer> BOOLEAN_FEATURES = Arrays.asList(
        FEATURE_ADAPTIVE_BACKLIGHT,
        FEATURE_AUTO_CONTRAST,
        FEATURE_COLOR_ENHANCEMENT,
        FEATURE_HIGH_TOUCH_SENSITIVITY,
        FEATURE_KEY_DISABLE,
        FEATURE_SUNLIGHT_ENHANCEMENT,
        FEATURE_TOUCH_HOVERING,
        FEATURE_READING_ENHANCEMENT
    );

    private static ILineageHardwareService sService;
    private static LineageHardwareManager sLineageHardwareManagerInstance;

    private Context mContext;

    // HIDL hals
    private HashMap<int, IBase> mHIDLMap;

    /**
     * @hide to prevent subclassing from outside of the framework
     */
    private LineageHardwareManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }
        sService = getService();

        if (context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.HARDWARE_ABSTRACTION) && !checkService()) {
            Log.wtf(TAG, "Unable to get LineageHardwareService. The service either" +
                    " crashed, was not started, or the interface has been called to early in" +
                    " SystemServer init");
        }

        mHIDLMap = new HashMap<int, IBase>();

        try {
            IAdaptiveBacklight adaptiveBacklight = IAdaptiveBacklight.getService(true);
            mHIDLMap.put(FEATURE_ADAPTIVE_BACKLIGHT, adaptiveBacklight);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IAutoContrast autoContrast = IAutoContrast.getService(true);
            mHIDLMap.put(FEATURE_AUTO_CONTRAST, autoContrast);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IColorBalance colorBalance = IColorBalance.getService(true);
            mHIDLMap.put(FEATURE_COLOR_BALANCE, colorBalance);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IColorEnhancement colorEnhancement = IColorEnhancement.getService(true);
            mHIDLMap.put(FEATURE_COLOR_ENHANCEMENT, colorEnhancement);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IDisplayColorCalibration displayColorCalibration = IDisplayColorCalibration.getService(true);
            mHIDLMap.put(FEATURE_DISPLAY_COLOR_CALIBRATION, displayColorCalibration);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IDisplayModes displayModes = IDisplayModes.getService(true);
            mHIDLMap.put(FEATURE_DISPLAY_MODES, displayModes);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IPictureAdjustment pictureAdjustment = IPictureAdjustment.getService(true);
            mHIDLMap.put(FEATURE_DISPLAY_MODES, pictureAdjustment);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IReadingEnhancement readingEnhancement = IReadingEnhancement.getService(true);
            mHIDLMap.put(FEATURE_READING_ENHANCEMENT, readingEnhancement);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            ISunlightEnhancement sunlightEnhancement = ISunlightEnhancement.getService(true);
            mHIDLMap.put(FEATURE_SUNLIGHT_ENHANCEMENT, sunlightEnhancement);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IGloveMode gloveMode = IGloveMode.getService(true);
            mHIDLMap.put(FEATURE_HIGH_TOUCH_SENSITIVITY, gloveMode);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IKeyDisabler keyDisabler = IKeyDisabler.getService(true);
            mHIDLMap.put(FEATURE_KEY_DISABLE, keyDisabler);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            IStylusMode stylusMode = IStylusMode.getService(true);
            mHIDLMap.put(FEATURE_TOUCH_HOVERING, stylusMode);
        } catch (NoSuchElementException | RemoteException e) {
        }
        try {
            ITouchscreenGesture touchscreenGesture = ITouchscreenGesture.getService(true);
            mHIDLMap.put(FEATURE_TOUCHSCREEN_GESTURES, touchscreenGesture);
        } catch (NoSuchElementException | RemoteException e) {
        }
    }

    /**
     * Get or create an instance of the {@link lineageos.hardware.LineageHardwareManager}
     * @param context
     * @return {@link LineageHardwareManager}
     */
    public static LineageHardwareManager getInstance(Context context) {
        context.enforceCallingOrSelfPermission(
                lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (sLineageHardwareManagerInstance == null) {
            sLineageHardwareManagerInstance = new LineageHardwareManager(context);
        }
        return sLineageHardwareManagerInstance;
    }

    /** @hide */
    public static ILineageHardwareService getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_HARDWARE_SERVICE);
        if (b != null) {
            sService = ILineageHardwareService.Stub.asInterface(b);
            return sService;
        }
        return null;
    }

    /**
     * @return the supported features bitmask
     * @hide
     */
    public int getSupportedFeatures() {
        try {
            if (checkService()) {
                return sService.getSupportedFeatures();
            }
        } catch (RemoteException e) {
        }
        return 0;
    }

    /**
     * Determine if a Lineage Hardware feature is supported on this device
     *
     * @param feature The Lineage Hardware feature to query
     *
     * @return true if the feature is supported, false otherwise.
     */
    public boolean isSupported(int feature) {
        return isSupportedHIDL(feature) || isSupportedLegacy(feature);
    }

    private boolean isSupportedHIDL(int feature) {
        return mHIDLMap.containsKey(feature);
    }

    private boolean isSupportedLegacy(int feature) {
        return feature == (getSupportedFeatures() & feature);
    }

    /**
     * String version for preference constraints
     *
     * @hide
     */
    public boolean isSupported(String feature) {
        if (!feature.startsWith("FEATURE_")) {
            return false;
        }
        try {
            Field f = getClass().getField(feature);
            if (f != null) {
                return isSupported((int) f.get(null));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.d(TAG, e.getMessage(), e);
        }

        return false;
    }
    /**
     * Determine if the given feature is enabled or disabled.
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the Lineage Hardware feature to query
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean get(int feature) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        if (isSupportedHIDL(feature)) {
            IBase obj = mHIDLMap.get(feature);
            switch (feature) {
                case FEATURE_ADAPTIVE_BACKLIGHT:
                    IAdaptiveBacklight adaptiveBacklight = (IAdaptiveBacklight) obj;
                    try {
                        return adaptiveBacklight.isEnabled();
                    } catch (RemoteException e) {
                        return false;
                    }
                case FEATURE_AUTO_CONTRAST:
                    IAutoContrast autoContrast = (IAutoContrast) obj;
                    try {
                        return autoContrast.isEnabled();
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_COLOR_ENHANCEMENT:
                    IColorEnhancement colorEnhancement = (IColorEnhancement) obj;
                    try {
                        return colorEnhancement.isEnabled();
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_HIGH_TOUCH_SENSITIVITY:
                    IGloveMode gloveMode = (IGloveMode) obj;
                    try {
                        return gloveMode.isEnabled();
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_KEY_DISABLE:
                    IKeyDisabler keyDisabler = (IKeyDisabler) obj;
                    try {
                        return keyDisabler.isEnabled();
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_SUNLIGHT_ENHANCEMENT:
                    ISunlightEnhancement sunlightEnhancement = (ISunlightEnhancement) obj;
                    try {
                        return sunlightEnhancement.isEnabled();
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_TOUCH_HOVERING:
                    IStylusMode stylusMode = (IStylusMode) obj;
                    try {
                        return stylusMode.isEnabled();
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_READING_ENHANCEMENT:
                    IReadingEnhancement readingEnhancement = (IReadingEnhancement) obj;
                    try {
                        return readingEnhancement.isEnabled();
                    } catch (RemoteException e) {
                        return false;
                    }
            }
        }

        try {
            if (checkService()) {
                return sService.get(feature);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * Enable or disable the given feature
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the Lineage Hardware feature to set
     * @param enable true to enable, false to disale
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean set(int feature, boolean enable) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        if (isSupportedHIDL(feature)) {
            IBase obj = mHIDLMap.get(feature);
            switch (feature) {
                case FEATURE_ADAPTIVE_BACKLIGHT:
                    IAdaptiveBacklight adaptiveBacklight = (IAdaptiveBacklight) obj;
                    try {
                        return adaptiveBacklight.setEnabled(enable);
                    } catch (RemoteException e) {
                        return false;
                    }
                case FEATURE_AUTO_CONTRAST:
                    IAutoContrast autoContrast = (IAutoContrast) obj;
                    try {
                        return autoContrast.setEnabled(enable);
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_COLOR_ENHANCEMENT:
                    IColorEnhancement colorEnhancement = (IColorEnhancement) obj;
                    try {
                        return colorEnhancement.setEnabled(enable);
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_HIGH_TOUCH_SENSITIVITY:
                    IGloveMode gloveMode = (IGloveMode) obj;
                    try {
                        return gloveMode.setEnabled(enable);
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_KEY_DISABLE:
                    IKeyDisabler keyDisabler = (IKeyDisabler) obj;
                    try {
                        return keyDisabler.setEnabled(enable);
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_SUNLIGHT_ENHANCEMENT:
                    ISunlightEnhancement sunlightEnhancement = (ISunlightEnhancement) obj;
                    try {
                        return sunlightEnhancement.setEnabled(enable);
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_TOUCH_HOVERING:
                    IStylusMode stylusMode = (IStylusMode) obj;
                    try {
                        return stylusMode.setEnabled(enable);
                    } catch (RemoteException e) {
                        return false;
                    }
               case FEATURE_READING_ENHANCEMENT:
                    IReadingEnhancement readingEnhancement = (IReadingEnhancement) obj;
                    try {
                        return readingEnhancement.setEnabled(enable);
                    } catch (RemoteException e) {
                        return false;
                    }
            }
        }

        try {
            if (checkService()) {
                return sService.set(feature, enable);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    private int getArrayValue(int[] arr, int idx, int defaultValue) {
        if (arr == null || arr.length <= idx) {
            return defaultValue;
        }

        return arr[idx];
    }

    /**
     * {@hide}
     */
    public static final int VIBRATOR_INTENSITY_INDEX = 0;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_DEFAULT_INDEX = 1;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_MIN_INDEX = 2;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_MAX_INDEX = 3;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_WARNING_INDEX = 4;

    private int[] getVibratorIntensityArray() {
        try {
            if (checkService()) {
                return sService.getVibratorIntensity();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return The current vibrator intensity.
     */
    public int getVibratorIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_INTENSITY_INDEX, 0);
    }

    /**
     * @return The default vibrator intensity.
     */
    public int getVibratorDefaultIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_DEFAULT_INDEX, 0);
    }

    /**
     * @return The minimum vibrator intensity.
     */
    public int getVibratorMinIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_MIN_INDEX, 0);
    }

    /**
     * @return The maximum vibrator intensity.
     */
    public int getVibratorMaxIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_MAX_INDEX, 0);
    }

    /**
     * @return The warning threshold vibrator intensity.
     */
    public int getVibratorWarningIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_WARNING_INDEX, 0);
    }

    /**
     * Set the current vibrator intensity
     *
     * @param intensity the intensity to set, between {@link #getVibratorMinIntensity()} and
     * {@link #getVibratorMaxIntensity()} inclusive.
     *
     * @return true on success, false otherwise.
     */
    public boolean setVibratorIntensity(int intensity) {
        try {
            if (checkService()) {
                return sService.setVibratorIntensity(intensity);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_RED_INDEX = 0;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_GREEN_INDEX = 1;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_BLUE_INDEX = 2;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_MIN_INDEX = 3;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_MAX_INDEX = 4;

    private int[] getDisplayColorCalibrationArray() {
        if (isSupportedHIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            IDisplayColorCalibration displayColorCalibration = (IDisplayColorCalibration)
                    mHIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION);
            try {
                return displayColorCalibration.getCalibration().toArray();
            } catch (RemoteException e) {
                return null;
            }
        }

        try {
            if (checkService()) {
                return sService.getDisplayColorCalibration();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the current RGB calibration, where int[0] = R, int[1] = G, int[2] = B.
     */
    public int[] getDisplayColorCalibration() {
        int[] arr = getDisplayColorCalibrationArray();
        if (arr == null || arr.length < 3) {
            return null;
        }
        return Arrays.copyOf(arr, 3);
    }

    /**
     * @return The minimum value for all colors
     */
    public int getDisplayColorCalibrationMin() {
        if (isSupportedHIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            IDisplayColorCalibration displayColorCalibration = (IDisplayColorCalibration)
                    mHIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION);
            try {
                return displayColorCalibration.getMinValue();
            } catch (RemoteException e) {
                return 0;
            }
        }

        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_MIN_INDEX, 0);
    }

    /**
     * @return The maximum value for all colors
     */
    public int getDisplayColorCalibrationMax() {
        if (isSupportedHIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            IDisplayColorCalibration displayColorCalibration = (IDisplayColorCalibration)
                    mHIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION);
            try {
                return displayColorCalibration.getMaxValue();
            } catch (RemoteException e) {
                return 0;
            }
        }

        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_MAX_INDEX, 0);
    }

    /**
     * Set the display color calibration to the given rgb triplet
     *
     * @param rgb RGB color calibration.  Each value must be between
     * {@link #getDisplayColorCalibrationMin()} and {@link #getDisplayColorCalibrationMax()},
     * inclusive.
     *
     * @return true on success, false otherwise.
     */
    public boolean setDisplayColorCalibration(int[] rgb) {
        if (isSupportedHIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            IDisplayColorCalibration displayColorCalibration = (IDisplayColorCalibration)
                    mHIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION);
            try {
                return displayColorCalibration.setCalibration(Arrays.asList(rgb));
            } catch (RemoteException e) {
                return false;
            }
        }

        try {
            if (checkService()) {
                return sService.setDisplayColorCalibration(rgb);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return true if adaptive backlight should be enabled when sunlight enhancement
     * is enabled.
     */
    public boolean requireAdaptiveBacklightForSunlightEnhancement() {
        if (isSupportedHIDL(FEATURE_SUNLIGHT_ENHANCEMENT)) {
            return false;
        }

        try {
            if (checkService()) {
                return sService.requireAdaptiveBacklightForSunlightEnhancement();
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return true if this implementation does it's own lux metering
     */
    public boolean isSunlightEnhancementSelfManaged() {
        if (isSupportedHIDL(FEATURE_SUNLIGHT_ENHANCEMENT)) {
            return false;
        }

        try {
            if (checkService()) {
                return sService.isSunlightEnhancementSelfManaged();
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return a list of available display modes on the devices
     */
    public DisplayMode[] getDisplayModes() {
        if (isSupportedHIDL(FEATURE_DISPLAY_MODES)) {
            IDisplayModes displayModes = (IDisplayModes) mHIDLMap.get(FEATURE_DISPLAY_MODES);
            try {
                return HIDLHelper.fromHIDLModes(displayModes.getDisplayModes());
            } catch (RemoteException e) {
                return null;
            }
        }

        try {
            if (checkService()) {
                return sService.getDisplayModes();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the currently active display mode
     */
    public DisplayMode getCurrentDisplayMode() {
        if (isSupportedHIDL(FEATURE_DISPLAY_MODES)) {
            IDisplayModes displayModes = (IDisplayModes) mHIDLMap.get(FEATURE_DISPLAY_MODES);
            try {
                return HIDLHelper.fromHIDLMode(displayModes.getCurrentDisplayMode());
            } catch (RemoteException e) {
                return null;
            }
        }

        try {
            if (checkService()) {
                return sService.getCurrentDisplayMode();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the default display mode to be set on boot
     */
    public DisplayMode getDefaultDisplayMode() {
        if (isSupportedHIDL(FEATURE_DISPLAY_MODES)) {
            IDisplayModes displayModes = (IDisplayModes) mHIDLMap.get(FEATURE_DISPLAY_MODES);
            try {
                return HIDLHelper.fromHIDLMode(displayModes.getDefaultDisplayMode());
            } catch (RemoteException e) {
                return null;
            }
        }

        try {
            if (checkService()) {
                return sService.getDefaultDisplayMode();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return true if setting the mode was successful
     */
    public boolean setDisplayMode(DisplayMode mode, boolean makeDefault) {
        if (isSupportedHIDL(FEATURE_DISPLAY_MODES)) {
            IDisplayModes displayModes = (IDisplayModes) mHIDLMap.get(FEATURE_DISPLAY_MODES);
            try {
                return displayModes.setDisplayMode(mode.id, makeDefault);
            } catch (RemoteException e) {
                return false;
            }
        }

        try {
            if (checkService()) {
                return sService.setDisplayMode(mode, makeDefault);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return the available range for color temperature adjustments
     */
    public Range<Integer> getColorBalanceRange() {
        if (isSupportedHIDL(FEATURE_COLOR_BALANCE)) {
            IColorBalance colorBalance = (IColorBalance) mHIDLMap.get(FEATURE_COLOR_BALANCE);
            try {
                return HIDLHelper.fromHIDLRange(colorBalance.getColorBalanceRange());
            } catch (RemoteException e) {
                return new Range<Integer>(0, 0);
            }
        }

        int min = 0;
        int max = 0;
        try {
            if (checkService()) {
                min = sService.getColorBalanceMin();
                max = sService.getColorBalanceMax();
            }
        } catch (RemoteException e) {
        }
        return new Range<Integer>(min, max);
    }

    /**
     * @return the current color balance value
     */
    public int getColorBalance() {
        if (isSupportedHIDL(FEATURE_COLOR_BALANCE)) {
            IColorBalance colorBalance = (IColorBalance) mHIDLMap.get(FEATURE_COLOR_BALANCE);
            try {
                return colorBalance.getColorBalance();
            } catch (RemoteException e) {
                return 0;
            }
        }

        try {
            if (checkService()) {
                return sService.getColorBalance();
            }
        } catch (RemoteException e) {
        }
        return 0;
    }

    /**
     * Sets the desired color balance. Must fall within the range obtained from
     * getColorBalanceRange()
     *
     * @param value
     * @return true if success
     */
    public boolean setColorBalance(int value) {
        if (isSupportedHIDL(FEATURE_COLOR_BALANCE)) {
            IColorBalance colorBalance = (IColorBalance) mHIDLMap.get(FEATURE_COLOR_BALANCE);
            try {
                return colorBalance.setColorBalance(value);
            } catch (RemoteException e) {
                return false;
            }
        }

        try {
            if (checkService()) {
                return sService.setColorBalance(value);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * Gets the current picture adjustment values
     *
     * @return HSIC object with current settings
     */
    public HSIC getPictureAdjustment() {
        if (isSupportedHIDL(FEATURE_PICTURE_ADJUSTMENT)) {
            IPictureAdjustment pictureAdjustment = (IPictureAdjustment)
                    mHIDLMap.get(FEATURE_PICTURE_ADJUSTMENT);
            try {
                return HIDLHelper.fromHIDLHSIC(pictureAdjustment.getPictureAdjustment());
            } catch (RemoteException e) {
                return null;
            }
        }

        try {
            if (checkService()) {
                return sService.getPictureAdjustment();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * Gets the default picture adjustment for the current mode
     *
     * @return HSIC object with default settings
     */
    public HSIC getDefaultPictureAdjustment() {
        if (isSupportedHIDL(FEATURE_PICTURE_ADJUSTMENT)) {
            IPictureAdjustment pictureAdjustment = (IPictureAdjustment)
                    mHIDLMap.get(FEATURE_PICTURE_ADJUSTMENT);
            try {
                return HIDLHelper.fromHIDLHSIC(pictureAdjustment.getDefaultPictureAdjustment());
            } catch (RemoteException e) {
                return null;
            }
        }

        try {
            if (checkService()) {
                return sService.getDefaultPictureAdjustment();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * Sets the desired hue/saturation/intensity/contrast
     *
     * @param hsic
     * @return true if success
     */
    public boolean setPictureAdjustment(final HSIC hsic) {
        if (isSupportedHIDL(FEATURE_PICTURE_ADJUSTMENT)) {
            IPictureAdjustment pictureAdjustment = (IPictureAdjustment)
                    mHIDLMap.get(FEATURE_PICTURE_ADJUSTMENT);
            try {
                return pictureAdjustment.setPictureAdjustment(HIDLHelper.toHIDLHSIC(hsic));
            } catch (RemoteException e) {
                return false;
            }
        }

        try {
            if (checkService()) {
                return sService.setPictureAdjustment(hsic);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * Get a list of ranges valid for picture adjustment.
     *
     * @return range list
     */
    public List<Range<Float>> getPictureAdjustmentRanges() {
        if (isSupportedHIDL(FEATURE_PICTURE_ADJUSTMENT)) {
            IPictureAdjustment pictureAdjustment = (IPictureAdjustment)
                    mHIDLMap.get(FEATURE_PICTURE_ADJUSTMENT);
            try {
                return Arrays.asList(
                        HIDLHelper.fromHIDLIntRange(pictureAdjustment.getHueRange()),
                        HIDLHelper.fromHIDLRange(pictureAdjustment.getSaturationRange()),
                        HIDLHelper.fromHIDLRange(pictureAdjustment.getIntensityRange()),
                        HIDLHelper.fromHIDLRange(pictureAdjustment.getContrastRange()),
                        HIDLHelper.fromHIDLRange(pictureAdjustment.getSaturationThresholdRange()));
            } catch (RemoteException e) {
                return null;
            }
        }

        try {
            if (checkService()) {
                float[] ranges = sService.getPictureAdjustmentRanges();
                if (ranges.length > 7) {
                    return Arrays.asList(new Range<Float>(ranges[0], ranges[1]),
                            new Range<Float>(ranges[2], ranges[3]),
                            new Range<Float>(ranges[4], ranges[5]),
                            new Range<Float>(ranges[6], ranges[7]),
                            (ranges.length > 9 ?
                                    new Range<Float>(ranges[8], ranges[9]) :
                                    new Range<Float>(0.0f, 0.0f)));
                }
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return a list of available touchscreen gestures on the devices
     */
    public TouchscreenGesture[] getTouchscreenGestures() {
        if (isSupportedHIDL(FEATURE_TOUCHSCREEN_GESTURES)) {
            ITouchscreenGesture touchscreenGesture = (ITouchscreenGesture)
                    mHIDLMap.get(FEATURE_TOUCHSCREEN_GESTURES);
            try {
                return HIDLHelper.fromHIDLGestures(touchscreenGesture.getSupportedGestures());
            } catch (RemoteException e){
                return null;
            }
        }

        try {
            if (checkService()) {
                return sService.getTouchscreenGestures();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return true if setting the activation status was successful
     */
    public boolean setTouchscreenGestureEnabled(
            TouchscreenGesture gesture, boolean state) {
        if (isSupportedHIDL(FEATURE_TOUCHSCREEN_GESTURES)) {
            ITouchscreenGesture touchscreenGesture = (ITouchscreenGesture)
                    mHIDLMap.get(FEATURE_TOUCHSCREEN_GESTURES);
            try {
                return touchscreenGesture.setGestureEnabled(HIDLHelper.toHIDLGesture(gesture), state);
            } catch (RemoteException e){
                return false;
            }
        }

        try {
            if (checkService()) {
                return sService.setTouchscreenGestureEnabled(gesture, state);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return true if service is valid
     */
    private boolean checkService() {
        if (sService == null) {
            Log.w(TAG, "not connected to LineageHardwareManagerService");
            return false;
        }
        return true;
    }

}
