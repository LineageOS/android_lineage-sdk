/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package lineageos.hardware;

import android.content.Context;
import android.hidl.base.V1_0.IBase;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Range;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;

import lineageos.app.LineageContextConstants;

import vendor.lineage.touch.IGloveMode;
import vendor.lineage.touch.IHighTouchPollingRate;
import vendor.lineage.touch.IKeyDisabler;
import vendor.lineage.touch.IKeySwapper;
import vendor.lineage.touch.IStylusMode;
import vendor.lineage.touch.ITouchscreenGesture;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
     * High Touch Polling Rate
     */
    @VisibleForTesting
    public static final int FEATURE_HIGH_TOUCH_POLLING_RATE = 0x8;

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
     * Hardware navigation key swapping
     */
    @VisibleForTesting
    public static final int FEATURE_KEY_SWAP = 0x40;

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

    /**
     * Anti flicker mode
     */
    @VisibleForTesting
    public static final int FEATURE_ANTI_FLICKER = 0x200000;

    private static final List<Integer> BOOLEAN_FEATURES = Arrays.asList(
        FEATURE_ADAPTIVE_BACKLIGHT,
        FEATURE_ANTI_FLICKER,
        FEATURE_AUTO_CONTRAST,
        FEATURE_COLOR_ENHANCEMENT,
        FEATURE_HIGH_TOUCH_POLLING_RATE,
        FEATURE_HIGH_TOUCH_SENSITIVITY,
        FEATURE_KEY_DISABLE,
        FEATURE_KEY_SWAP,
        FEATURE_SUNLIGHT_ENHANCEMENT,
        FEATURE_TOUCH_HOVERING,
        FEATURE_READING_ENHANCEMENT
    );

    private static ILineageHardwareService sService;
    private static LineageHardwareManager sLineageHardwareManagerInstance;

    private Context mContext;

    private final ArrayMap<String, String> mDisplayModeMappings = new ArrayMap<String, String>();
    private final boolean mFilterDisplayModes;

    // AIDL hals
    private HashMap<Integer, IBinder> mAIDLMap = new HashMap<Integer, IBinder>();
    // HIDL hals
    private HashMap<Integer, IBase> mHIDLMap = new HashMap<Integer, IBase>();

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

        final String[] mappings = mContext.getResources().getStringArray(
                org.lineageos.platform.internal.R.array.config_displayModeMappings);
        if (mappings != null && mappings.length > 0) {
            for (String mapping : mappings) {
                String[] split = mapping.split(":");
                if (split.length == 2) {
                    mDisplayModeMappings.put(split[0], split[1]);
                }
            }
        }
        mFilterDisplayModes = mContext.getResources().getBoolean(
                org.lineageos.platform.internal.R.bool.config_filterDisplayModes);
    }

    /**
     * Get or create an instance of the {@link lineageos.hardware.LineageHardwareManager}
     * @param context
     * @return {@link LineageHardwareManager}
     */
    public static LineageHardwareManager getInstance(Context context) {
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
     * Determine if a Lineage Hardware feature is supported on this device
     *
     * @param feature The Lineage Hardware feature to query
     *
     * @return true if the feature is supported, false otherwise.
     */
    public boolean isSupported(int feature) {
        return isSupportedAIDL(feature) || isSupportedHIDL(feature) || isSupportedHWC2(feature);
    }

    private boolean isSupportedAIDL(int feature) {
        if (!mAIDLMap.containsKey(feature)) {
            mAIDLMap.put(feature, getAIDLService(feature));
        }
        return mAIDLMap.get(feature) != null;
    }

    private boolean isSupportedHIDL(int feature) {
        if (!mHIDLMap.containsKey(feature)) {
            mHIDLMap.put(feature, getHIDLService(feature));
        }
        return mHIDLMap.get(feature) != null;
    }

    private boolean isSupportedHWC2(int feature) {
        try {
            if (checkService()) {
                return feature == (sService.getSupportedFeatures() & feature);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    private IBinder getAIDLService(int feature) {
        switch (feature) {
            case FEATURE_ADAPTIVE_BACKLIGHT:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.IAdaptiveBacklight.DESCRIPTOR + "/default");
            case FEATURE_ANTI_FLICKER:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.IAntiFlicker.DESCRIPTOR + "/default");
            case FEATURE_AUTO_CONTRAST:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.IAutoContrast.DESCRIPTOR + "/default");
            case FEATURE_COLOR_BALANCE:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.IColorBalance.DESCRIPTOR + "/default");
            case FEATURE_COLOR_ENHANCEMENT:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.IColorEnhancement.DESCRIPTOR + "/default");
            case FEATURE_DISPLAY_COLOR_CALIBRATION:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.IDisplayColorCalibration.DESCRIPTOR + "/default");
            case FEATURE_DISPLAY_MODES:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.IDisplayModes.DESCRIPTOR + "/default");
            case FEATURE_HIGH_TOUCH_POLLING_RATE:
                return ServiceManager.waitForDeclaredService(
                        IHighTouchPollingRate.DESCRIPTOR + "/default");
            case FEATURE_HIGH_TOUCH_SENSITIVITY:
                return ServiceManager.waitForDeclaredService(
                        IGloveMode.DESCRIPTOR + "/default");
            case FEATURE_KEY_DISABLE:
                return ServiceManager.waitForDeclaredService(
                        IKeyDisabler.DESCRIPTOR + "/default");
            case FEATURE_KEY_SWAP:
                return ServiceManager.waitForDeclaredService(
                        IKeySwapper.DESCRIPTOR + "/default");
            case FEATURE_PICTURE_ADJUSTMENT:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.IPictureAdjustment.DESCRIPTOR + "/default");
            case FEATURE_READING_ENHANCEMENT:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.IReadingEnhancement.DESCRIPTOR + "/default");
            case FEATURE_SUNLIGHT_ENHANCEMENT:
                return ServiceManager.waitForDeclaredService(
                        vendor.lineage.livedisplay.ISunlightEnhancement.DESCRIPTOR + "/default");
            case FEATURE_TOUCH_HOVERING:
                return ServiceManager.waitForDeclaredService(
                        IStylusMode.DESCRIPTOR + "/default");
            case FEATURE_TOUCHSCREEN_GESTURES:
                return ServiceManager.waitForDeclaredService(
                        ITouchscreenGesture.DESCRIPTOR + "/default");
        }
        return null;
    }

    private IBase getHIDLService(int feature) {
        try {
            switch (feature) {
                case FEATURE_ADAPTIVE_BACKLIGHT:
                    return vendor.lineage.livedisplay.V2_0.IAdaptiveBacklight.getService(true);
                case FEATURE_ANTI_FLICKER:
                    return vendor.lineage.livedisplay.V2_1.IAntiFlicker.getService(true);
                case FEATURE_AUTO_CONTRAST:
                    return vendor.lineage.livedisplay.V2_0.IAutoContrast.getService(true);
                case FEATURE_COLOR_BALANCE:
                    return vendor.lineage.livedisplay.V2_0.IColorBalance.getService(true);
                case FEATURE_COLOR_ENHANCEMENT:
                    return vendor.lineage.livedisplay.V2_0.IColorEnhancement.getService(true);
                case FEATURE_DISPLAY_COLOR_CALIBRATION:
                    return vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration.getService(true);
                case FEATURE_DISPLAY_MODES:
                    return vendor.lineage.livedisplay.V2_0.IDisplayModes.getService(true);
                case FEATURE_PICTURE_ADJUSTMENT:
                    return vendor.lineage.livedisplay.V2_0.IPictureAdjustment.getService(true);
                case FEATURE_READING_ENHANCEMENT:
                    return vendor.lineage.livedisplay.V2_0.IReadingEnhancement.getService(true);
                case FEATURE_SUNLIGHT_ENHANCEMENT:
                    return vendor.lineage.livedisplay.V2_0.ISunlightEnhancement.getService(true);
            }
        } catch (NoSuchElementException | RemoteException e) {
        }
        return null;
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

        try {
            if (isSupportedAIDL(feature)) {
                IBinder b = mAIDLMap.get(feature);
                switch (feature) {
                    case FEATURE_ADAPTIVE_BACKLIGHT:
                        vendor.lineage.livedisplay.IAdaptiveBacklight adaptiveBacklight =
                                vendor.lineage.livedisplay.IAdaptiveBacklight.Stub.asInterface(b);
                        return adaptiveBacklight.getEnabled();
                    case FEATURE_ANTI_FLICKER:
                        vendor.lineage.livedisplay.IAntiFlicker antiFlicker =
                                vendor.lineage.livedisplay.IAntiFlicker.Stub.asInterface(b);
                        return antiFlicker.getEnabled();
                    case FEATURE_AUTO_CONTRAST:
                        vendor.lineage.livedisplay.IAutoContrast autoContrast =
                                vendor.lineage.livedisplay.IAutoContrast.Stub.asInterface(b);
                        return autoContrast.getEnabled();
                    case FEATURE_COLOR_ENHANCEMENT:
                        vendor.lineage.livedisplay.IColorEnhancement colorEnhancement =
                                vendor.lineage.livedisplay.IColorEnhancement.Stub.asInterface(b);
                        return colorEnhancement.getEnabled();
                    case FEATURE_HIGH_TOUCH_POLLING_RATE:
                        return IHighTouchPollingRate.Stub.asInterface(b).getEnabled();
                    case FEATURE_HIGH_TOUCH_SENSITIVITY:
                        return IGloveMode.Stub.asInterface(b).getEnabled();
                    case FEATURE_KEY_DISABLE:
                        return IKeyDisabler.Stub.asInterface(b).getEnabled();
                    case FEATURE_KEY_SWAP:
                        return IKeySwapper.Stub.asInterface(b).getEnabled();
                    case FEATURE_READING_ENHANCEMENT:
                        vendor.lineage.livedisplay.IReadingEnhancement readingEnhancement =
                                vendor.lineage.livedisplay.IReadingEnhancement.Stub.asInterface(b);
                        return readingEnhancement.getEnabled();
                    case FEATURE_SUNLIGHT_ENHANCEMENT:
                        vendor.lineage.livedisplay.ISunlightEnhancement sunlightEnhancement =
                                vendor.lineage.livedisplay.ISunlightEnhancement.Stub.asInterface(b);
                        return sunlightEnhancement.getEnabled();
                    case FEATURE_TOUCH_HOVERING:
                        return IStylusMode.Stub.asInterface(b).getEnabled();
                }
            } else if (isSupportedHIDL(feature)) {
                IBase obj = mHIDLMap.get(feature);
                switch (feature) {
                    case FEATURE_ADAPTIVE_BACKLIGHT:
                        vendor.lineage.livedisplay.V2_0.IAdaptiveBacklight adaptiveBacklight =
                                (vendor.lineage.livedisplay.V2_0.IAdaptiveBacklight) obj;
                        return adaptiveBacklight.isEnabled();
                    case FEATURE_ANTI_FLICKER:
                        vendor.lineage.livedisplay.V2_1.IAntiFlicker antiFlicker =
                                (vendor.lineage.livedisplay.V2_1.IAntiFlicker) obj;
                        return antiFlicker.isEnabled();
                    case FEATURE_AUTO_CONTRAST:
                        vendor.lineage.livedisplay.V2_0.IAutoContrast autoContrast =
                                (vendor.lineage.livedisplay.V2_0.IAutoContrast) obj;
                        return autoContrast.isEnabled();
                    case FEATURE_COLOR_ENHANCEMENT:
                        vendor.lineage.livedisplay.V2_0.IColorEnhancement colorEnhancement =
                                (vendor.lineage.livedisplay.V2_0.IColorEnhancement) obj;
                        return colorEnhancement.isEnabled();
                    case FEATURE_SUNLIGHT_ENHANCEMENT:
                        vendor.lineage.livedisplay.V2_0.ISunlightEnhancement sunlightEnhancement =
                                (vendor.lineage.livedisplay.V2_0.ISunlightEnhancement) obj;
                        return sunlightEnhancement.isEnabled();
                    case FEATURE_READING_ENHANCEMENT:
                        vendor.lineage.livedisplay.V2_0.IReadingEnhancement readingEnhancement =
                                (vendor.lineage.livedisplay.V2_0.IReadingEnhancement) obj;
                        return readingEnhancement.isEnabled();
                }
            } else if (checkService()) {
                return sService.get(feature);
            }
        } catch (Exception e) {
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

        try {
            if (isSupportedAIDL(feature)) {
                IBinder b = mAIDLMap.get(feature);
                switch (feature) {
                    case FEATURE_ADAPTIVE_BACKLIGHT:
                        vendor.lineage.livedisplay.IAdaptiveBacklight adaptiveBacklight =
                                vendor.lineage.livedisplay.IAdaptiveBacklight.Stub.asInterface(b);
                        adaptiveBacklight.setEnabled(enable);
                        break;
                    case FEATURE_ANTI_FLICKER:
                        vendor.lineage.livedisplay.IAntiFlicker antiFlicker =
                                vendor.lineage.livedisplay.IAntiFlicker.Stub.asInterface(b);
                        antiFlicker.setEnabled(enable);
                        break;
                    case FEATURE_AUTO_CONTRAST:
                        vendor.lineage.livedisplay.IAutoContrast autoContrast =
                                vendor.lineage.livedisplay.IAutoContrast.Stub.asInterface(b);
                        autoContrast.setEnabled(enable);
                        break;
                    case FEATURE_COLOR_ENHANCEMENT:
                        vendor.lineage.livedisplay.IColorEnhancement colorEnhancement =
                                vendor.lineage.livedisplay.IColorEnhancement.Stub.asInterface(b);
                        colorEnhancement.setEnabled(enable);
                        break;
                    case FEATURE_HIGH_TOUCH_POLLING_RATE:
                        IHighTouchPollingRate.Stub.asInterface(b).setEnabled(enable);
                        break;
                    case FEATURE_HIGH_TOUCH_SENSITIVITY:
                        IGloveMode.Stub.asInterface(b).setEnabled(enable);
                        break;
                    case FEATURE_KEY_DISABLE:
                        IKeyDisabler.Stub.asInterface(b).setEnabled(enable);
                        break;
                    case FEATURE_KEY_SWAP:
                        IKeySwapper.Stub.asInterface(b).setEnabled(enable);
                        break;
                    case FEATURE_READING_ENHANCEMENT:
                        vendor.lineage.livedisplay.IReadingEnhancement readingEnhancement =
                                vendor.lineage.livedisplay.IReadingEnhancement.Stub.asInterface(b);
                        readingEnhancement.setEnabled(enable);
                        break;
                    case FEATURE_SUNLIGHT_ENHANCEMENT:
                        vendor.lineage.livedisplay.ISunlightEnhancement sunlightEnhancement =
                                vendor.lineage.livedisplay.ISunlightEnhancement.Stub.asInterface(b);
                        sunlightEnhancement.setEnabled(enable);
                        break;
                    case FEATURE_TOUCH_HOVERING:
                        IStylusMode.Stub.asInterface(b).setEnabled(enable);
                        break;
                }
                return enable;
            }
            if (isSupportedHIDL(feature)) {
                IBase obj = mHIDLMap.get(feature);
                switch (feature) {
                    case FEATURE_ADAPTIVE_BACKLIGHT:
                        vendor.lineage.livedisplay.V2_0.IAdaptiveBacklight adaptiveBacklight =
                                (vendor.lineage.livedisplay.V2_0.IAdaptiveBacklight) obj;
                        return adaptiveBacklight.setEnabled(enable);
                    case FEATURE_ANTI_FLICKER:
                        vendor.lineage.livedisplay.V2_1.IAntiFlicker antiFlicker =
                                (vendor.lineage.livedisplay.V2_1.IAntiFlicker) obj;
                        return antiFlicker.setEnabled(enable);
                    case FEATURE_AUTO_CONTRAST:
                        vendor.lineage.livedisplay.V2_0.IAutoContrast autoContrast =
                                (vendor.lineage.livedisplay.V2_0.IAutoContrast) obj;
                        return autoContrast.setEnabled(enable);
                    case FEATURE_COLOR_ENHANCEMENT:
                        vendor.lineage.livedisplay.V2_0.IColorEnhancement colorEnhancement =
                                (vendor.lineage.livedisplay.V2_0.IColorEnhancement) obj;
                        return colorEnhancement.setEnabled(enable);
                    case FEATURE_READING_ENHANCEMENT:
                        vendor.lineage.livedisplay.V2_0.IReadingEnhancement readingEnhancement =
                                (vendor.lineage.livedisplay.V2_0.IReadingEnhancement) obj;
                        return readingEnhancement.setEnabled(enable);
                    case FEATURE_SUNLIGHT_ENHANCEMENT:
                        vendor.lineage.livedisplay.V2_0.ISunlightEnhancement sunlightEnhancement =
                                (vendor.lineage.livedisplay.V2_0.ISunlightEnhancement) obj;
                        return sunlightEnhancement.setEnabled(enable);
                }
            } else if (checkService()) {
                return sService.set(feature, enable);
            }
        } catch (Exception e) {
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
        try {
            if (isSupportedAIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
                vendor.lineage.livedisplay.IDisplayColorCalibration displayColorCalibration =
                        vendor.lineage.livedisplay.IDisplayColorCalibration.Stub.asInterface(
                                mAIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION));
                return displayColorCalibration.getCalibration();
            }
            if (isSupportedHIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
                vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration displayColorCalibration =
                        (vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration)
                                mHIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION);
                return ArrayUtils.convertToIntArray(displayColorCalibration.getCalibration());
            }
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
        if (isSupportedAIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            vendor.lineage.livedisplay.IDisplayColorCalibration displayColorCalibration =
                    vendor.lineage.livedisplay.IDisplayColorCalibration.Stub.asInterface(
                            mAIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION));
            try {
                return displayColorCalibration.getMinValue();
            } catch (RemoteException e) {
                return 0;
            }
        }
        if (isSupportedHIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration displayColorCalibration =
                        (vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration)
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
        if (isSupportedAIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            vendor.lineage.livedisplay.IDisplayColorCalibration displayColorCalibration =
                    vendor.lineage.livedisplay.IDisplayColorCalibration.Stub.asInterface(
                            mAIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION));
            try {
                return displayColorCalibration.getMaxValue();
            } catch (RemoteException e) {
                return 0;
            }
        }
        if (isSupportedHIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration displayColorCalibration =
                        (vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration)
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
        try {
            if (isSupportedAIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
                vendor.lineage.livedisplay.IDisplayColorCalibration displayColorCalibration =
                        vendor.lineage.livedisplay.IDisplayColorCalibration.Stub.asInterface(
                                mAIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION));
                displayColorCalibration.setCalibration(rgb);
                return true;
            }
            if (isSupportedHIDL(FEATURE_DISPLAY_COLOR_CALIBRATION)) {
                vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration displayColorCalibration =
                        (vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration)
                                mHIDLMap.get(FEATURE_DISPLAY_COLOR_CALIBRATION);
                return displayColorCalibration.setCalibration(
                       new ArrayList<Integer>(Arrays.asList(rgb[0], rgb[1], rgb[2])));
            }
            if (checkService()) {
                return sService.setDisplayColorCalibration(rgb);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return a list of available display modes on the devices
     */
    public DisplayMode[] getDisplayModes() {
        DisplayMode[] modes = null;
        try {
            if (isSupportedAIDL(FEATURE_DISPLAY_MODES)) {
                vendor.lineage.livedisplay.IDisplayModes displayModes =
                        vendor.lineage.livedisplay.IDisplayModes.Stub.asInterface(
                                mAIDLMap.get(FEATURE_DISPLAY_MODES));
                modes = AIDLHelper.fromAIDLModes(displayModes.getDisplayModes());
            } else if (isSupportedHIDL(FEATURE_DISPLAY_MODES)) {
                vendor.lineage.livedisplay.V2_0.IDisplayModes displayModes =
                        (vendor.lineage.livedisplay.V2_0.IDisplayModes)
                                mHIDLMap.get(FEATURE_DISPLAY_MODES);
                modes = HIDLHelper.fromHIDLModes(displayModes.getDisplayModes());
            }
        } catch (RemoteException e) {
        } finally {
            if (modes == null) {
                return null;
            }
            final ArrayList<DisplayMode> remapped = new ArrayList<DisplayMode>();
            for (DisplayMode mode : modes) {
                DisplayMode r = remapDisplayMode(mode);
                if (r != null) {
                    remapped.add(r);
                }
            }
            return remapped.toArray(new DisplayMode[0]);
        }
    }

    /**
     * @return the currently active display mode
     */
    public DisplayMode getCurrentDisplayMode() {
        DisplayMode mode = null;
        try {
            if (isSupportedAIDL(FEATURE_DISPLAY_MODES)) {
                vendor.lineage.livedisplay.IDisplayModes displayModes =
                        vendor.lineage.livedisplay.IDisplayModes.Stub.asInterface(
                                mAIDLMap.get(FEATURE_DISPLAY_MODES));
                mode = AIDLHelper.fromAIDLMode(displayModes.getCurrentDisplayMode());
            } else if (isSupportedHIDL(FEATURE_DISPLAY_MODES)) {
                vendor.lineage.livedisplay.V2_0.IDisplayModes displayModes =
                        (vendor.lineage.livedisplay.V2_0.IDisplayModes)
                                mHIDLMap.get(FEATURE_DISPLAY_MODES);
                mode = HIDLHelper.fromHIDLMode(displayModes.getCurrentDisplayMode());
            }
        } catch (RemoteException e) {
        } finally {
            return mode != null ? remapDisplayMode(mode) : null;
        }
    }

    /**
     * @return the default display mode to be set on boot
     */
    public DisplayMode getDefaultDisplayMode() {
        DisplayMode mode = null;
        try {
            if (isSupportedAIDL(FEATURE_DISPLAY_MODES)) {
                vendor.lineage.livedisplay.IDisplayModes displayModes =
                        vendor.lineage.livedisplay.IDisplayModes.Stub.asInterface(
                                mAIDLMap.get(FEATURE_DISPLAY_MODES));
                mode = AIDLHelper.fromAIDLMode(displayModes.getDefaultDisplayMode());
            } else if (isSupportedHIDL(FEATURE_DISPLAY_MODES)) {
                vendor.lineage.livedisplay.V2_0.IDisplayModes displayModes =
                        (vendor.lineage.livedisplay.V2_0.IDisplayModes)
                                mHIDLMap.get(FEATURE_DISPLAY_MODES);
                mode = HIDLHelper.fromHIDLMode(displayModes.getDefaultDisplayMode());
            }
        } catch (RemoteException e) {
        } finally {
            return mode != null ? remapDisplayMode(mode) : null;
        }
    }

    /**
     * @return true if setting the mode was successful
     */
    public boolean setDisplayMode(DisplayMode mode, boolean makeDefault) {
        try {
            if (isSupportedAIDL(FEATURE_DISPLAY_MODES)) {
                vendor.lineage.livedisplay.IDisplayModes displayModes =
                        vendor.lineage.livedisplay.IDisplayModes.Stub.asInterface(
                                mAIDLMap.get(FEATURE_DISPLAY_MODES));
                displayModes.setDisplayMode(mode.id, makeDefault);
                return true;
            }
            if (isSupportedHIDL(FEATURE_DISPLAY_MODES)) {
                vendor.lineage.livedisplay.V2_0.IDisplayModes displayModes =
                        (vendor.lineage.livedisplay.V2_0.IDisplayModes)
                                mHIDLMap.get(FEATURE_DISPLAY_MODES);
                return displayModes.setDisplayMode(mode.id, makeDefault);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    private DisplayMode remapDisplayMode(DisplayMode in) {
        if (in == null) {
            return null;
        }
        if (mDisplayModeMappings.containsKey(in.name)) {
            return new DisplayMode(in.id, mDisplayModeMappings.get(in.name));
        }
        if (!mFilterDisplayModes) {
            return in;
        }
        return null;
    }

    /**
     * @return the available range for color temperature adjustments
     */
    public Range<Integer> getColorBalanceRange() {
        try {
            if (isSupportedAIDL(FEATURE_COLOR_BALANCE)) {
                vendor.lineage.livedisplay.IColorBalance colorBalance =
                        vendor.lineage.livedisplay.IColorBalance.Stub.asInterface(
                                mAIDLMap.get(FEATURE_COLOR_BALANCE));
                return AIDLHelper.fromAIDLRange(colorBalance.getColorBalanceRange());
            }
            if (isSupportedHIDL(FEATURE_COLOR_BALANCE)) {
                vendor.lineage.livedisplay.V2_0.IColorBalance colorBalance =
                        (vendor.lineage.livedisplay.V2_0.IColorBalance)
                                mHIDLMap.get(FEATURE_COLOR_BALANCE);
                return HIDLHelper.fromHIDLRange(colorBalance.getColorBalanceRange());
            }
        } catch (RemoteException e) {
        }
        return new Range<Integer>(0, 0);
    }

    /**
     * @return the current color balance value
     */
    public int getColorBalance() {
        try {
            if (isSupportedAIDL(FEATURE_COLOR_BALANCE)) {
                vendor.lineage.livedisplay.IColorBalance colorBalance =
                        vendor.lineage.livedisplay.IColorBalance.Stub.asInterface(
                                mAIDLMap.get(FEATURE_COLOR_BALANCE));
                return colorBalance.getColorBalance();
            }
            if (isSupportedHIDL(FEATURE_COLOR_BALANCE)) {
                vendor.lineage.livedisplay.V2_0.IColorBalance colorBalance =
                        (vendor.lineage.livedisplay.V2_0.IColorBalance)
                                mHIDLMap.get(FEATURE_COLOR_BALANCE);
                return colorBalance.getColorBalance();
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
        try {
            if (isSupportedAIDL(FEATURE_COLOR_BALANCE)) {
                vendor.lineage.livedisplay.IColorBalance colorBalance =
                        vendor.lineage.livedisplay.IColorBalance.Stub.asInterface(
                                mAIDLMap.get(FEATURE_COLOR_BALANCE));
                colorBalance.setColorBalance(value);
                return true;
            }
            if (isSupportedHIDL(FEATURE_COLOR_BALANCE)) {
                vendor.lineage.livedisplay.V2_0.IColorBalance colorBalance =
                        (vendor.lineage.livedisplay.V2_0.IColorBalance)
                                mHIDLMap.get(FEATURE_COLOR_BALANCE);
                return colorBalance.setColorBalance(value);
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
        try {
            if (isSupportedAIDL(FEATURE_PICTURE_ADJUSTMENT)) {
                vendor.lineage.livedisplay.IPictureAdjustment pictureAdjustment =
                        vendor.lineage.livedisplay.IPictureAdjustment.Stub.asInterface(
                                mAIDLMap.get(FEATURE_PICTURE_ADJUSTMENT));
                return AIDLHelper.fromAIDLHSIC(pictureAdjustment.getPictureAdjustment());
            }
            if (isSupportedHIDL(FEATURE_PICTURE_ADJUSTMENT)) {
                vendor.lineage.livedisplay.V2_0.IPictureAdjustment pictureAdjustment =
                        (vendor.lineage.livedisplay.V2_0.IPictureAdjustment)
                                mHIDLMap.get(FEATURE_PICTURE_ADJUSTMENT);
                return HIDLHelper.fromHIDLHSIC(pictureAdjustment.getPictureAdjustment());
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
        try {
            if (isSupportedAIDL(FEATURE_PICTURE_ADJUSTMENT)) {
                vendor.lineage.livedisplay.IPictureAdjustment pictureAdjustment =
                        vendor.lineage.livedisplay.IPictureAdjustment.Stub.asInterface(
                                mAIDLMap.get(FEATURE_PICTURE_ADJUSTMENT));
                return AIDLHelper.fromAIDLHSIC(pictureAdjustment.getDefaultPictureAdjustment());
            }
            if (isSupportedHIDL(FEATURE_PICTURE_ADJUSTMENT)) {
                vendor.lineage.livedisplay.V2_0.IPictureAdjustment pictureAdjustment =
                        (vendor.lineage.livedisplay.V2_0.IPictureAdjustment)
                                mHIDLMap.get(FEATURE_PICTURE_ADJUSTMENT);
                return HIDLHelper.fromHIDLHSIC(pictureAdjustment.getDefaultPictureAdjustment());
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
        try {
            if (isSupportedAIDL(FEATURE_PICTURE_ADJUSTMENT)) {
                vendor.lineage.livedisplay.IPictureAdjustment pictureAdjustment =
                        vendor.lineage.livedisplay.IPictureAdjustment.Stub.asInterface(
                                mAIDLMap.get(FEATURE_PICTURE_ADJUSTMENT));
                pictureAdjustment.setPictureAdjustment(AIDLHelper.toAIDLHSIC(hsic));
                return true;
            }
            if (isSupportedHIDL(FEATURE_PICTURE_ADJUSTMENT)) {
                vendor.lineage.livedisplay.V2_0.IPictureAdjustment pictureAdjustment =
                        (vendor.lineage.livedisplay.V2_0.IPictureAdjustment)
                                mHIDLMap.get(FEATURE_PICTURE_ADJUSTMENT);
                return pictureAdjustment.setPictureAdjustment(HIDLHelper.toHIDLHSIC(hsic));
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
        try {
            if (isSupportedAIDL(FEATURE_PICTURE_ADJUSTMENT)) {
                vendor.lineage.livedisplay.IPictureAdjustment pictureAdjustment =
                        vendor.lineage.livedisplay.IPictureAdjustment.Stub.asInterface(
                                mAIDLMap.get(FEATURE_PICTURE_ADJUSTMENT));
                return Arrays.asList(
                        AIDLHelper.fromAIDLRange(pictureAdjustment.getHueRange()),
                        AIDLHelper.fromAIDLRange(pictureAdjustment.getSaturationRange()),
                        AIDLHelper.fromAIDLRange(pictureAdjustment.getIntensityRange()),
                        AIDLHelper.fromAIDLRange(pictureAdjustment.getContrastRange()),
                        AIDLHelper.fromAIDLRange(pictureAdjustment.getSaturationThresholdRange()));
            }
            if (isSupportedHIDL(FEATURE_PICTURE_ADJUSTMENT)) {
                vendor.lineage.livedisplay.V2_0.IPictureAdjustment pictureAdjustment =
                        (vendor.lineage.livedisplay.V2_0.IPictureAdjustment)
                                mHIDLMap.get(FEATURE_PICTURE_ADJUSTMENT);
                return Arrays.asList(
                        HIDLHelper.fromHIDLRange(pictureAdjustment.getHueRange()),
                        HIDLHelper.fromHIDLRange(pictureAdjustment.getSaturationRange()),
                        HIDLHelper.fromHIDLRange(pictureAdjustment.getIntensityRange()),
                        HIDLHelper.fromHIDLRange(pictureAdjustment.getContrastRange()),
                        HIDLHelper.fromHIDLRange(pictureAdjustment.getSaturationThresholdRange()));
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return a list of available touchscreen gestures on the devices
     */
    public TouchscreenGesture[] getTouchscreenGestures() {
        try {
            if (isSupportedAIDL(FEATURE_TOUCHSCREEN_GESTURES)) {
                ITouchscreenGesture touchscreenGesture = ITouchscreenGesture.Stub.asInterface(
                        mAIDLMap.get(FEATURE_TOUCHSCREEN_GESTURES));
                return AIDLHelper.fromAIDLGestures(touchscreenGesture.getSupportedGestures());
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * @return true if setting the activation status was successful
     */
    public boolean setTouchscreenGestureEnabled(
            TouchscreenGesture gesture, boolean state) {
        try {
            if (isSupportedAIDL(FEATURE_TOUCHSCREEN_GESTURES)) {
                ITouchscreenGesture touchscreenGesture = ITouchscreenGesture.Stub.asInterface(
                        mAIDLMap.get(FEATURE_TOUCHSCREEN_GESTURES));
                touchscreenGesture.setGestureEnabled(AIDLHelper.toAIDLGesture(gesture), state);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * @return true if service is valid
     */
    private boolean checkService() {
        sService = getService();
        if (sService == null) {
            Log.w(TAG, "not connected to LineageHardwareManagerService");
            return false;
        }
        return true;
    }

}
