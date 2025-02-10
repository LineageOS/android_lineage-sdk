/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package lineageos.hardware;

import static lineageos.hardware.LiveDisplayManager.FEATURE_COLOR_BALANCE;
import static lineageos.hardware.LiveDisplayManager.FEATURE_PICTURE_ADJUSTMENT;
import static lineageos.hardware.LiveDisplayManager.FEATURE_FIRST;
import static lineageos.hardware.LiveDisplayManager.FEATURE_LAST;
import static lineageos.hardware.LiveDisplayManager.MODE_FIRST;
import static lineageos.hardware.LiveDisplayManager.MODE_LAST;
import static lineageos.hardware.LiveDisplayManager.MODE_OFF;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Range;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Holder class for LiveDisplay static configuration.
 *
 * This class holds various defaults and hardware capabilities
 * which are involved with LiveDisplay.
 */
public class LiveDisplayConfig implements Parcelable {

    private final BitSet mCapabilities;
    private final BitSet mAllModes = new BitSet();

    private final int mDefaultDayTemperature;
    private final int mDefaultNightTemperature;
    private final int mDefaultMode;

    private final boolean mDefaultAutoContrast;
    private final boolean mDefaultAutoOutdoorMode;
    private final boolean mDefaultCABC;
    private final boolean mDefaultColorEnhancement;

    private final Range<Integer> mColorTemperatureRange;
    private final Range<Integer> mColorBalanceRange;
    private final Range<Float> mHueRange;
    private final Range<Float> mSaturationRange;
    private final Range<Float> mIntensityRange;
    private final Range<Float> mContrastRange;
    private final Range<Float> mSaturationThresholdRange;

    public LiveDisplayConfig(BitSet capabilities, int defaultMode,
            int defaultDayTemperature, int defaultNightTemperature,
            boolean defaultAutoOutdoorMode, boolean defaultAutoContrast,
            boolean defaultCABC, boolean defaultColorEnhancement,
            Range<Integer> colorTemperatureRange,
            Range<Integer> colorBalanceRange,
            Range<Float> hueRange,
            Range<Float> saturationRange,
            Range<Float> intensityRange,
            Range<Float> contrastRange,
            Range<Float> saturationThresholdRange) {
        super();
        mCapabilities = (BitSet) capabilities.clone();
        mAllModes.set(MODE_FIRST, MODE_LAST);
        mDefaultMode = defaultMode;
        mDefaultDayTemperature = defaultDayTemperature;
        mDefaultNightTemperature = defaultNightTemperature;
        mDefaultAutoContrast = defaultAutoContrast;
        mDefaultAutoOutdoorMode = defaultAutoOutdoorMode;
        mDefaultCABC = defaultCABC;
        mDefaultColorEnhancement = defaultColorEnhancement;
        mColorTemperatureRange = colorTemperatureRange;
        mColorBalanceRange = colorBalanceRange;
        mHueRange = hueRange;
        mSaturationRange = saturationRange;
        mIntensityRange = intensityRange;
        mContrastRange = contrastRange;
        mSaturationThresholdRange = saturationThresholdRange;
    }

    private LiveDisplayConfig(Parcel parcel) {
        float[] paRanges = new float[10];

        mCapabilities = BitSet.valueOf(new long[] { parcel.readLong() });
        mAllModes.set(MODE_FIRST, MODE_LAST);
        mDefaultMode = parcel.readInt();
        mDefaultDayTemperature = parcel.readInt();
        mDefaultNightTemperature = parcel.readInt();
        mDefaultAutoContrast = parcel.readInt() == 1;
        mDefaultAutoOutdoorMode = parcel.readInt() == 1;
        mDefaultCABC = parcel.readInt() == 1;
        mDefaultColorEnhancement = parcel.readInt() == 1;
        mColorTemperatureRange = Range.create(parcel.readInt(), parcel.readInt());
        mColorBalanceRange = Range.create(parcel.readInt(), parcel.readInt());
        parcel.readFloatArray(paRanges);
        mHueRange = Range.create(paRanges[0], paRanges[1]);
        mSaturationRange = Range.create(paRanges[2], paRanges[3]);
        mIntensityRange = Range.create(paRanges[4], paRanges[5]);
        mContrastRange = Range.create(paRanges[6], paRanges[7]);
        mSaturationThresholdRange = Range.create(paRanges[8], paRanges[9]);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("capabilities=").append(mCapabilities.toString());
        sb.append(" defaultMode=").append(mDefaultMode);
        sb.append(" defaultDayTemperature=").append(mDefaultDayTemperature);
        sb.append(" defaultNightTemperature=").append(mDefaultNightTemperature);
        sb.append(" defaultAutoOutdoorMode=").append(mDefaultAutoOutdoorMode);
        sb.append(" defaultAutoContrast=").append(mDefaultAutoContrast);
        sb.append(" defaultCABC=").append(mDefaultCABC);
        sb.append(" defaultColorEnhancement=").append(mDefaultColorEnhancement);
        sb.append(" colorTemperatureRange=").append(mColorTemperatureRange);
        if (mCapabilities.get(FEATURE_COLOR_BALANCE)) {
            sb.append(" colorBalanceRange=").append(mColorBalanceRange);
        }
        if (mCapabilities.get(FEATURE_PICTURE_ADJUSTMENT)) {
            sb.append(" hueRange=").append(mHueRange);
            sb.append(" saturationRange=").append(mSaturationRange);
            sb.append(" intensityRange=").append(mIntensityRange);
            sb.append(" contrastRange=").append(mContrastRange);
            sb.append(" saturationThresholdRange=").append(mSaturationThresholdRange);
        }
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        long[] caps = mCapabilities.toLongArray();
        out.writeLong(caps != null && caps.length > 0 ? caps[0] : 0L);
        out.writeInt(mDefaultMode);
        out.writeInt(mDefaultDayTemperature);
        out.writeInt(mDefaultNightTemperature);
        out.writeInt(mDefaultAutoContrast ? 1 : 0);
        out.writeInt(mDefaultAutoOutdoorMode ? 1 : 0);
        out.writeInt(mDefaultCABC ? 1 : 0);
        out.writeInt(mDefaultColorEnhancement ? 1 : 0);
        out.writeInt(mColorTemperatureRange.getLower());
        out.writeInt(mColorTemperatureRange.getUpper());
        out.writeInt(mColorBalanceRange.getLower());
        out.writeInt(mColorBalanceRange.getUpper());
        out.writeFloatArray(new float[] {
                mHueRange.getLower(), mHueRange.getUpper(),
                mSaturationRange.getLower(), mSaturationRange.getUpper(),
                mIntensityRange.getLower(), mIntensityRange.getUpper(),
                mContrastRange.getLower(), mContrastRange.getUpper(),
                mSaturationThresholdRange.getLower(), mSaturationThresholdRange.getUpper() } );
    }

    /**
     * Checks if a particular feature or mode is supported by the system.
     *
     * @param feature
     * @return true if capable
     */
    public boolean hasFeature(int feature) {
        return ((feature >= MODE_FIRST && feature <= MODE_LAST) ||
                (feature >= FEATURE_FIRST && feature <= FEATURE_LAST)) &&
                (feature == MODE_OFF || mCapabilities.get(feature));
    }

    /**
     * Checks if LiveDisplay is available for use on this device.
     *
     * @return true if any feature is enabled
     */
    public boolean isAvailable() {
        return !mCapabilities.isEmpty();
    }

    /**
     * Checks if LiveDisplay has support for adaptive modes.
     *
     * @return true if adaptive modes are available
     */
    public boolean hasModeSupport() {
        return isAvailable() && mCapabilities.intersects(mAllModes);
    }

    /**
     * Gets the default color temperature to use in the daytime. This is typically
     * set to 6500K, however this may not be entirely accurate. Use this value for
     * resetting controls to the default.
     *
     * @return the default day temperature in K
     */
    public int getDefaultDayTemperature() {
        return mDefaultDayTemperature;
    }

    /**
     * Gets the default color temperature to use at night. This is typically set
     * to 4500K, but this may not be entirely accurate. Use this value for resetting
     * controls to defaults.
     *
     * @return the default night color temperature
     */
    public int getDefaultNightTemperature() {
        return mDefaultNightTemperature;
    }

    /**
     * Get the default adaptive mode.
     *
     * @return the default mode
     */
    public int getDefaultMode() {
        return mDefaultMode;
    }

    /**
     * Get the default value for auto contrast
     *
     * @return true if enabled
     */
    public boolean getDefaultAutoContrast() {
        return mDefaultAutoContrast;
    }

    /**
     * Get the default value for automatic outdoor mode
     *
     * @return true if enabled
     */
    public boolean getDefaultAutoOutdoorMode() {
        return mDefaultAutoOutdoorMode;
    }

    /**
     * Get the default value for CABC
     *
     * @return true if enabled
     */
    public boolean getDefaultCABC() {
        return mDefaultCABC;
    }

    /**
     * Get the default value for color enhancement
     *
     * @return true if enabled
     */
    public boolean getDefaultColorEnhancement() {
        return mDefaultColorEnhancement;
    }

    /**
     * Get the range of supported color temperatures
     *
     * @return range in Kelvin
     */
    public Range<Integer> getColorTemperatureRange() {
        return mColorTemperatureRange;
    }

    /**
     * Get the range of supported color balance
     *
     * @return linear range which maps into the temperature range curve
     */
    public Range<Integer> getColorBalanceRange() {
        return mColorBalanceRange;
    }

    /**
     * Get the supported range for hue adjustment
     *
     * @return float range
     */
    public Range<Float> getHueRange() { return mHueRange; }

    /**
     * Get the supported range for saturation adjustment
     *
     * @return float range
     */
    public Range<Float> getSaturationRange() { return mSaturationRange; }

    /**
     * Get the supported range for intensity adjustment
     *
     * @return float range
     */
    public Range<Float> getIntensityRange() { return mIntensityRange; }

    /**
     * Get the supported range for contrast adjustment
     *
     * @return float range
     */
    public Range<Float> getContrastRange() { return mContrastRange; }

    /**
     * Get the supported range for saturation threshold adjustment
     *
     * @return float range
     */
    public Range<Float> getSaturationThresholdRange() {
        return mSaturationThresholdRange;
    }


    /**
     * Convenience method to get a list of all picture adjustment ranges
     * with a single call.
     *
     * @return List of float ranges
     */
    public List<Range<Float>> getPictureAdjustmentRanges() {
        return Arrays.asList(mHueRange, mSaturationRange, mIntensityRange,
                             mContrastRange, mSaturationThresholdRange);
    }

    /** @hide */
    public static final Parcelable.Creator<LiveDisplayConfig> CREATOR =
            new Parcelable.Creator<LiveDisplayConfig>() {
        public LiveDisplayConfig createFromParcel(Parcel in) {
            return new LiveDisplayConfig(in);
        }

        @Override
        public LiveDisplayConfig[] newArray(int size) {
            return new LiveDisplayConfig[size];
        }
    };
}
