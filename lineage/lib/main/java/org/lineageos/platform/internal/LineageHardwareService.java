/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
 *               2017 The LineageOS Project
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
package org.lineageos.platform.internal;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Range;

import com.android.server.SystemService;

import lineageos.app.LineageContextConstants;
import lineageos.hardware.ILineageHardwareService;
import lineageos.hardware.LineageHardwareManager;
import lineageos.hardware.DisplayMode;
import lineageos.hardware.IThermalListenerCallback;
import lineageos.hardware.ThermalListenerCallback;
import lineageos.hardware.HSIC;
import lineageos.hardware.TouchscreenGesture;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lineageos.hardware.AdaptiveBacklight;
import org.lineageos.hardware.AutoContrast;
import org.lineageos.hardware.ColorBalance;
import org.lineageos.hardware.ColorEnhancement;
import org.lineageos.hardware.DisplayColorCalibration;
import org.lineageos.hardware.DisplayGammaCalibration;
import org.lineageos.hardware.DisplayModeControl;
import org.lineageos.hardware.HighTouchSensitivity;
import org.lineageos.hardware.KeyDisabler;
import org.lineageos.hardware.LongTermOrbits;
import org.lineageos.hardware.PersistentStorage;
import org.lineageos.hardware.PictureAdjustment;
import org.lineageos.hardware.SerialNumber;
import org.lineageos.hardware.SunlightEnhancement;
import org.lineageos.hardware.ThermalMonitor;
import org.lineageos.hardware.ThermalUpdateCallback;
import org.lineageos.hardware.TouchscreenGestures;
import org.lineageos.hardware.TouchscreenHovering;
import org.lineageos.hardware.UniqueDeviceId;
import org.lineageos.hardware.VibratorHW;

/** @hide */
public class LineageHardwareService extends LineageSystemService implements ThermalUpdateCallback {

    private static final boolean DEBUG = true;
    private static final String TAG = LineageHardwareService.class.getSimpleName();

    private final Context mContext;
    private final LineageHardwareInterface mLineageHwImpl;
    private int mCurrentThermalState = ThermalListenerCallback.State.STATE_UNKNOWN;
    private RemoteCallbackList<IThermalListenerCallback> mRemoteCallbackList;

    private final ArrayMap<String, String> mDisplayModeMappings =
            new ArrayMap<String, String>();
    private final boolean mFilterDisplayModes;

    private interface LineageHardwareInterface {
        public int getSupportedFeatures();
        public boolean get(int feature);
        public boolean set(int feature, boolean enable);

        public int[] getDisplayColorCalibration();
        public boolean setDisplayColorCalibration(int[] rgb);

        public int getNumGammaControls();
        public int[] getDisplayGammaCalibration(int idx);
        public boolean setDisplayGammaCalibration(int idx, int[] rgb);

        public int[] getVibratorIntensity();
        public boolean setVibratorIntensity(int intensity);

        public String getLtoSource();
        public String getLtoDestination();
        public long getLtoDownloadInterval();

        public String getSerialNumber();
        public String getUniqueDeviceId();

        public boolean requireAdaptiveBacklightForSunlightEnhancement();
        public boolean isSunlightEnhancementSelfManaged();

        public DisplayMode[] getDisplayModes();
        public DisplayMode getCurrentDisplayMode();
        public DisplayMode getDefaultDisplayMode();
        public boolean setDisplayMode(DisplayMode mode, boolean makeDefault);

        public boolean writePersistentBytes(String key, byte[] value);
        public byte[] readPersistentBytes(String key);

        public int getColorBalanceMin();
        public int getColorBalanceMax();
        public int getColorBalance();
        public boolean setColorBalance(int value);

        public HSIC getPictureAdjustment();
        public HSIC getDefaultPictureAdjustment();
        public boolean setPictureAdjustment(HSIC hsic);
        public List<Range<Float>> getPictureAdjustmentRanges();

        public TouchscreenGesture[] getTouchscreenGestures();
        public boolean setTouchscreenGestureEnabled(TouchscreenGesture gesture, boolean state);
    }

    private class LegacyLineageHardware implements LineageHardwareInterface {

        private int mSupportedFeatures = 0;

        public LegacyLineageHardware() {
            if (AdaptiveBacklight.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT;
            if (ColorEnhancement.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_COLOR_ENHANCEMENT;
            if (DisplayColorCalibration.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION;
            if (DisplayGammaCalibration.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_DISPLAY_GAMMA_CALIBRATION;
            if (HighTouchSensitivity.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY;
            if (KeyDisabler.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_KEY_DISABLE;
            if (LongTermOrbits.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_LONG_TERM_ORBITS;
            if (SerialNumber.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_SERIAL_NUMBER;
            if (SunlightEnhancement.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT;
            if (VibratorHW.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_VIBRATOR;
            if (TouchscreenHovering.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_TOUCH_HOVERING;
            if (AutoContrast.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_AUTO_CONTRAST;
            if (DisplayModeControl.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_DISPLAY_MODES;
            if (PersistentStorage.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_PERSISTENT_STORAGE;
            if (ThermalMonitor.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_THERMAL_MONITOR;
            if (UniqueDeviceId.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_UNIQUE_DEVICE_ID;
            if (ColorBalance.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_COLOR_BALANCE;
            if (PictureAdjustment.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_PICTURE_ADJUSTMENT;
            if (TouchscreenGestures.isSupported())
                mSupportedFeatures |= LineageHardwareManager.FEATURE_TOUCHSCREEN_GESTURES;
        }

        public int getSupportedFeatures() {
            return mSupportedFeatures;
        }

        public boolean get(int feature) {
            switch(feature) {
                case LineageHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT:
                    return AdaptiveBacklight.isEnabled();
                case LineageHardwareManager.FEATURE_COLOR_ENHANCEMENT:
                    return ColorEnhancement.isEnabled();
                case LineageHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY:
                    return HighTouchSensitivity.isEnabled();
                case LineageHardwareManager.FEATURE_KEY_DISABLE:
                    return KeyDisabler.isActive();
                case LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT:
                    return SunlightEnhancement.isEnabled();
                case LineageHardwareManager.FEATURE_TOUCH_HOVERING:
                    return TouchscreenHovering.isEnabled();
                case LineageHardwareManager.FEATURE_AUTO_CONTRAST:
                    return AutoContrast.isEnabled();
                case LineageHardwareManager.FEATURE_THERMAL_MONITOR:
                    return ThermalMonitor.isEnabled();
                default:
                    Log.e(TAG, "feature " + feature + " is not a boolean feature");
                    return false;
            }
        }

        public boolean set(int feature, boolean enable) {
            switch(feature) {
                case LineageHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT:
                    return AdaptiveBacklight.setEnabled(enable);
                case LineageHardwareManager.FEATURE_COLOR_ENHANCEMENT:
                    return ColorEnhancement.setEnabled(enable);
                case LineageHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY:
                    return HighTouchSensitivity.setEnabled(enable);
                case LineageHardwareManager.FEATURE_KEY_DISABLE:
                    return KeyDisabler.setActive(enable);
                case LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT:
                    return SunlightEnhancement.setEnabled(enable);
                case LineageHardwareManager.FEATURE_TOUCH_HOVERING:
                    return TouchscreenHovering.setEnabled(enable);
                case LineageHardwareManager.FEATURE_AUTO_CONTRAST:
                    return AutoContrast.setEnabled(enable);
                default:
                    Log.e(TAG, "feature " + feature + " is not a boolean feature");
                    return false;
            }
        }

        private int[] splitStringToInt(String input, String delimiter) {
            if (input == null || delimiter == null) {
                return null;
            }
            String strArray[] = input.split(delimiter);
            try {
                int intArray[] = new int[strArray.length];
                for(int i = 0; i < strArray.length; i++) {
                    intArray[i] = Integer.parseInt(strArray[i]);
                }
                return intArray;
            } catch (NumberFormatException e) {
                /* ignore */
            }
            return null;
        }

        private String rgbToString(int[] rgb) {
            StringBuilder builder = new StringBuilder();
            builder.append(rgb[LineageHardwareManager.COLOR_CALIBRATION_RED_INDEX]);
            builder.append(" ");
            builder.append(rgb[LineageHardwareManager.COLOR_CALIBRATION_GREEN_INDEX]);
            builder.append(" ");
            builder.append(rgb[LineageHardwareManager.COLOR_CALIBRATION_BLUE_INDEX]);
            return builder.toString();
        }

        public int[] getDisplayColorCalibration() {
            int[] rgb = splitStringToInt(DisplayColorCalibration.getCurColors(), " ");
            if (rgb == null || rgb.length != 3) {
                Log.e(TAG, "Invalid color calibration string");
                return null;
            }
            int[] currentCalibration = new int[6];
            currentCalibration[LineageHardwareManager.COLOR_CALIBRATION_RED_INDEX] = rgb[0];
            currentCalibration[LineageHardwareManager.COLOR_CALIBRATION_GREEN_INDEX] = rgb[1];
            currentCalibration[LineageHardwareManager.COLOR_CALIBRATION_BLUE_INDEX] = rgb[2];
            currentCalibration[LineageHardwareManager.COLOR_CALIBRATION_DEFAULT_INDEX] =
                DisplayColorCalibration.getDefValue();
            currentCalibration[LineageHardwareManager.COLOR_CALIBRATION_MIN_INDEX] =
                DisplayColorCalibration.getMinValue();
            currentCalibration[LineageHardwareManager.COLOR_CALIBRATION_MAX_INDEX] =
                DisplayColorCalibration.getMaxValue();
            return currentCalibration;
        }

        public boolean setDisplayColorCalibration(int[] rgb) {
            return DisplayColorCalibration.setColors(rgbToString(rgb));
        }

        public int getNumGammaControls() {
            return DisplayGammaCalibration.getNumberOfControls();
        }

        public int[] getDisplayGammaCalibration(int idx) {
            int[] rgb = splitStringToInt(DisplayGammaCalibration.getCurGamma(idx), " ");
            if (rgb == null || rgb.length != 3) {
                Log.e(TAG, "Invalid gamma calibration string");
                return null;
            }
            int[] currentCalibration = new int[5];
            currentCalibration[LineageHardwareManager.GAMMA_CALIBRATION_RED_INDEX] = rgb[0];
            currentCalibration[LineageHardwareManager.GAMMA_CALIBRATION_GREEN_INDEX] = rgb[1];
            currentCalibration[LineageHardwareManager.GAMMA_CALIBRATION_BLUE_INDEX] = rgb[2];
            currentCalibration[LineageHardwareManager.GAMMA_CALIBRATION_MIN_INDEX] =
                DisplayGammaCalibration.getMinValue(idx);
            currentCalibration[LineageHardwareManager.GAMMA_CALIBRATION_MAX_INDEX] =
                DisplayGammaCalibration.getMaxValue(idx);
            return currentCalibration;
        }

        public boolean setDisplayGammaCalibration(int idx, int[] rgb) {
            return DisplayGammaCalibration.setGamma(idx, rgbToString(rgb));
        }

        public int[] getVibratorIntensity() {
            int[] vibrator = new int[5];
            vibrator[LineageHardwareManager.VIBRATOR_INTENSITY_INDEX] = VibratorHW.getCurIntensity();
            vibrator[LineageHardwareManager.VIBRATOR_DEFAULT_INDEX] = VibratorHW.getDefaultIntensity();
            vibrator[LineageHardwareManager.VIBRATOR_MIN_INDEX] = VibratorHW.getMinIntensity();
            vibrator[LineageHardwareManager.VIBRATOR_MAX_INDEX] = VibratorHW.getMaxIntensity();
            vibrator[LineageHardwareManager.VIBRATOR_WARNING_INDEX] = VibratorHW.getWarningThreshold();
            return vibrator;
        }

        public boolean setVibratorIntensity(int intensity) {
            return VibratorHW.setIntensity(intensity);
        }

        public String getLtoSource() {
            return LongTermOrbits.getSourceLocation();
        }

        public String getLtoDestination() {
            File file = LongTermOrbits.getDestinationLocation();
            return file.getAbsolutePath();
        }

        public long getLtoDownloadInterval() {
            return LongTermOrbits.getDownloadInterval();
        }

        public String getSerialNumber() {
            return SerialNumber.getSerialNumber();
        }

        public String getUniqueDeviceId() {
            return UniqueDeviceId.getUniqueDeviceId();
        }

        public boolean requireAdaptiveBacklightForSunlightEnhancement() {
            return SunlightEnhancement.isAdaptiveBacklightRequired();
        }

        public boolean isSunlightEnhancementSelfManaged() {
            return SunlightEnhancement.isSelfManaged();
        }

        public DisplayMode[] getDisplayModes() {
            return DisplayModeControl.getAvailableModes();
        }

        public DisplayMode getCurrentDisplayMode() {
            return DisplayModeControl.getCurrentMode();
        }

        public DisplayMode getDefaultDisplayMode() {
            return DisplayModeControl.getDefaultMode();
        }

        public boolean setDisplayMode(DisplayMode mode, boolean makeDefault) {
            return DisplayModeControl.setMode(mode, makeDefault);
        }

        public boolean writePersistentBytes(String key, byte[] value) {
            return PersistentStorage.set(key, value);
        }

        public byte[] readPersistentBytes(String key) {
            return PersistentStorage.get(key);
        }

        public int getColorBalanceMin() {
            return ColorBalance.getMinValue();
        }

        public int getColorBalanceMax() {
            return ColorBalance.getMaxValue();
        }

        public int getColorBalance() {
            return ColorBalance.getValue();
        }

        public boolean setColorBalance(int value) {
            return ColorBalance.setValue(value);
        }

        public HSIC getPictureAdjustment() { return PictureAdjustment.getHSIC(); }

        public HSIC getDefaultPictureAdjustment() { return PictureAdjustment.getDefaultHSIC(); }

        public boolean setPictureAdjustment(HSIC hsic) { return PictureAdjustment.setHSIC(hsic); }

        public List<Range<Float>> getPictureAdjustmentRanges() {
            return Arrays.asList(
                    PictureAdjustment.getHueRange(),
                    PictureAdjustment.getSaturationRange(),
                    PictureAdjustment.getIntensityRange(),
                    PictureAdjustment.getContrastRange(),
                    PictureAdjustment.getSaturationThresholdRange());
        }

        public TouchscreenGesture[] getTouchscreenGestures() {
            return TouchscreenGestures.getAvailableGestures();
        }

        public boolean setTouchscreenGestureEnabled(TouchscreenGesture gesture, boolean state) {
            return TouchscreenGestures.setGestureEnabled(gesture, state);
        }
    }

    private LineageHardwareInterface getImpl(Context context) {
        return new LegacyLineageHardware();
    }

    public LineageHardwareService(Context context) {
        super(context);
        mContext = context;
        mLineageHwImpl = getImpl(context);
        publishBinderService(LineageContextConstants.LINEAGE_HARDWARE_SERVICE, mService);

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

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.HARDWARE_ABSTRACTION;
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            Intent intent = new Intent(lineageos.content.Intent.ACTION_INITIALIZE_LINEAGE_HARDWARE);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL,
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS);
        }
    }

    @Override
    public void onStart() {
        if (ThermalMonitor.isSupported()) {
            ThermalMonitor.initialize(this);
            mRemoteCallbackList = new RemoteCallbackList<IThermalListenerCallback>();
        }
    }

    @Override
    public void setThermalState(int state) {
        mCurrentThermalState = state;
        int i = mRemoteCallbackList.beginBroadcast();
        while (i > 0) {
            i--;
            try {
                mRemoteCallbackList.getBroadcastItem(i).onThermalChanged(state);
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mRemoteCallbackList.finishBroadcast();
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

    private final IBinder mService = new ILineageHardwareService.Stub() {

        private boolean isSupported(int feature) {
            return (getSupportedFeatures() & feature) == feature;
        }

        @Override
        public int getSupportedFeatures() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            return mLineageHwImpl.getSupportedFeatures();
        }

        @Override
        public boolean get(int feature) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(feature)) {
                Log.e(TAG, "feature " + feature + " is not supported");
                return false;
            }
            return mLineageHwImpl.get(feature);
        }

        @Override
        public boolean set(int feature, boolean enable) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(feature)) {
                Log.e(TAG, "feature " + feature + " is not supported");
                return false;
            }
            return mLineageHwImpl.set(feature, enable);
        }

        @Override
        public int[] getDisplayColorCalibration() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION)) {
                Log.e(TAG, "Display color calibration is not supported");
                return null;
            }
            return mLineageHwImpl.getDisplayColorCalibration();
        }

        @Override
        public boolean setDisplayColorCalibration(int[] rgb) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION)) {
                Log.e(TAG, "Display color calibration is not supported");
                return false;
            }
            if (rgb.length < 3) {
                Log.e(TAG, "Invalid color calibration");
                return false;
            }
            return mLineageHwImpl.setDisplayColorCalibration(rgb);
        }

        @Override
        public int getNumGammaControls() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_DISPLAY_GAMMA_CALIBRATION)) {
                Log.e(TAG, "Display gamma calibration is not supported");
                return 0;
            }
            return mLineageHwImpl.getNumGammaControls();
        }

        @Override
        public int[] getDisplayGammaCalibration(int idx) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_DISPLAY_GAMMA_CALIBRATION)) {
                Log.e(TAG, "Display gamma calibration is not supported");
                return null;
            }
            return mLineageHwImpl.getDisplayGammaCalibration(idx);
        }

        @Override
        public boolean setDisplayGammaCalibration(int idx, int[] rgb) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_DISPLAY_GAMMA_CALIBRATION)) {
                Log.e(TAG, "Display gamma calibration is not supported");
                return false;
            }
            return mLineageHwImpl.setDisplayGammaCalibration(idx, rgb);
        }

        @Override
        public int[] getVibratorIntensity() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_VIBRATOR)) {
                Log.e(TAG, "Vibrator is not supported");
                return null;
            }
            return mLineageHwImpl.getVibratorIntensity();
        }

        @Override
        public boolean setVibratorIntensity(int intensity) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_VIBRATOR)) {
                Log.e(TAG, "Vibrator is not supported");
                return false;
            }
            return mLineageHwImpl.setVibratorIntensity(intensity);
        }

        @Override
        public String getLtoSource() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_LONG_TERM_ORBITS)) {
                Log.e(TAG, "Long term orbits is not supported");
                return null;
            }
            return mLineageHwImpl.getLtoSource();
        }

        @Override
        public String getLtoDestination() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_LONG_TERM_ORBITS)) {
                Log.e(TAG, "Long term orbits is not supported");
                return null;
            }
            return mLineageHwImpl.getLtoDestination();
        }

        @Override
        public long getLtoDownloadInterval() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_LONG_TERM_ORBITS)) {
                Log.e(TAG, "Long term orbits is not supported");
                return 0;
            }
            return mLineageHwImpl.getLtoDownloadInterval();
        }

        @Override
        public String getSerialNumber() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_SERIAL_NUMBER)) {
                Log.e(TAG, "Serial number is not supported");
                return null;
            }
            return mLineageHwImpl.getSerialNumber();
        }

        @Override
        public String getUniqueDeviceId() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_UNIQUE_DEVICE_ID)) {
                Log.e(TAG, "Unique device ID is not supported");
                return null;
            }
            return mLineageHwImpl.getUniqueDeviceId();
        }

        @Override
        public boolean requireAdaptiveBacklightForSunlightEnhancement() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT)) {
                Log.e(TAG, "Sunlight enhancement is not supported");
                return false;
            }
            return mLineageHwImpl.requireAdaptiveBacklightForSunlightEnhancement();
        }

        @Override
        public boolean isSunlightEnhancementSelfManaged() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT)) {
                Log.e(TAG, "Sunlight enhancement is not supported");
                return false;
            }
            return mLineageHwImpl.isSunlightEnhancementSelfManaged();
        }

        @Override
        public DisplayMode[] getDisplayModes() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_DISPLAY_MODES)) {
                Log.e(TAG, "Display modes are not supported");
                return null;
            }
            final DisplayMode[] modes = mLineageHwImpl.getDisplayModes();
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
            return remapped.toArray(new DisplayMode[remapped.size()]);
        }

        @Override
        public DisplayMode getCurrentDisplayMode() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_DISPLAY_MODES)) {
                Log.e(TAG, "Display modes are not supported");
                return null;
            }
            return remapDisplayMode(mLineageHwImpl.getCurrentDisplayMode());
        }

        @Override
        public DisplayMode getDefaultDisplayMode() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_DISPLAY_MODES)) {
                Log.e(TAG, "Display modes are not supported");
                return null;
            }
            return remapDisplayMode(mLineageHwImpl.getDefaultDisplayMode());
        }

        @Override
        public boolean setDisplayMode(DisplayMode mode, boolean makeDefault) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_DISPLAY_MODES)) {
                Log.e(TAG, "Display modes are not supported");
                return false;
            }
            return mLineageHwImpl.setDisplayMode(mode, makeDefault);
        }

        @Override
        public boolean writePersistentBytes(String key, byte[] value) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.MANAGE_PERSISTENT_STORAGE, null);
            if (key == null || key.length() == 0 || key.length() > 64) {
                Log.e(TAG, "Invalid key: " + key);
                return false;
            }
            // A null value is delete
            if (value != null && (value.length > 4096 || value.length == 0)) {
                Log.e(TAG, "Invalid value: " + (value != null ? Arrays.toString(value) : null));
                return false;
            }
            if (!isSupported(LineageHardwareManager.FEATURE_PERSISTENT_STORAGE)) {
                Log.e(TAG, "Persistent storage is not supported");
                return false;
            }
            return mLineageHwImpl.writePersistentBytes(key, value);
        }

        @Override
        public byte[] readPersistentBytes(String key) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.MANAGE_PERSISTENT_STORAGE, null);
            if (key == null || key.length() == 0 || key.length() > 64) {
                Log.e(TAG, "Invalid key: " + key);
                return null;
            }
            if (!isSupported(LineageHardwareManager.FEATURE_PERSISTENT_STORAGE)) {
                Log.e(TAG, "Persistent storage is not supported");
                return null;
            }
            return mLineageHwImpl.readPersistentBytes(key);
        }

        @Override
        public int getThermalState() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_THERMAL_MONITOR)) {
                return mCurrentThermalState;
            }
            return ThermalListenerCallback.State.STATE_UNKNOWN;
        }

        @Override
        public boolean registerThermalListener(IThermalListenerCallback callback) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_THERMAL_MONITOR)) {
                return mRemoteCallbackList.register(callback);
            }
            return false;
        }

        @Override
        public boolean unRegisterThermalListener(IThermalListenerCallback callback) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_THERMAL_MONITOR)) {
                return mRemoteCallbackList.unregister(callback);
            }
            return false;
        }

        @Override
        public int getColorBalanceMin() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_COLOR_BALANCE)) {
                return mLineageHwImpl.getColorBalanceMin();
            }
            return 0;
        }

        @Override
        public int getColorBalanceMax() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_COLOR_BALANCE)) {
                return mLineageHwImpl.getColorBalanceMax();
            }
            return 0;
        }

        @Override
        public int getColorBalance() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_COLOR_BALANCE)) {
                return mLineageHwImpl.getColorBalance();
            }
            return 0;
        }

        @Override
        public boolean setColorBalance(int value) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_COLOR_BALANCE)) {
                return mLineageHwImpl.setColorBalance(value);
            }
            return false;
        }

        @Override
        public HSIC getPictureAdjustment() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_PICTURE_ADJUSTMENT)) {
                return mLineageHwImpl.getPictureAdjustment();
            }
            return new HSIC(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        @Override
        public HSIC getDefaultPictureAdjustment() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_PICTURE_ADJUSTMENT)) {
                return mLineageHwImpl.getDefaultPictureAdjustment();
            }
            return new HSIC(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        @Override
        public boolean setPictureAdjustment(HSIC hsic) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_PICTURE_ADJUSTMENT) && hsic != null) {
                return mLineageHwImpl.setPictureAdjustment(hsic);
            }
            return false;
        }

        @Override
        public float[] getPictureAdjustmentRanges() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (isSupported(LineageHardwareManager.FEATURE_PICTURE_ADJUSTMENT)) {
                final List<Range<Float>> r = mLineageHwImpl.getPictureAdjustmentRanges();
                return new float[] {
                        r.get(0).getLower(), r.get(0).getUpper(),
                        r.get(1).getLower(), r.get(1).getUpper(),
                        r.get(2).getLower(), r.get(2).getUpper(),
                        r.get(3).getLower(), r.get(3).getUpper(),
                        r.get(4).getUpper(), r.get(4).getUpper() };
            }
            return new float[10];
        }

        @Override
        public TouchscreenGesture[] getTouchscreenGestures() {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_TOUCHSCREEN_GESTURES)) {
                Log.e(TAG, "Touchscreen gestures are not supported");
                return null;
            }
            return mLineageHwImpl.getTouchscreenGestures();
        }

        @Override
        public boolean setTouchscreenGestureEnabled(TouchscreenGesture gesture, boolean state) {
            mContext.enforceCallingOrSelfPermission(
                    lineageos.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(LineageHardwareManager.FEATURE_TOUCHSCREEN_GESTURES)) {
                Log.e(TAG, "Touchscreen gestures are not supported");
                return false;
            }
            return mLineageHwImpl.setTouchscreenGestureEnabled(gesture, state);
        }
    };
}
