/*
 * SPDX-FileCopyrightText: 2019-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.hardware;

import android.util.Range;

import java.util.ArrayList;

class HIDLHelper {

    static DisplayMode[] fromHIDLModes(
            ArrayList<vendor.lineage.livedisplay.V2_0.DisplayMode> modes) {
        int size = modes.size();
        DisplayMode[] r = new DisplayMode[size];
        for (int i = 0; i < size; i++) {
            vendor.lineage.livedisplay.V2_0.DisplayMode m = modes.get(i);
            r[i] = new DisplayMode(m.id, m.name);
        }
        return r;
    }

    static DisplayMode fromHIDLMode(
            vendor.lineage.livedisplay.V2_0.DisplayMode mode) {
        return new DisplayMode(mode.id, mode.name);
    }

    static HSIC fromHIDLHSIC(vendor.lineage.livedisplay.V2_0.HSIC hsic) {
        return new HSIC(hsic.hue, hsic.saturation, hsic.intensity,
                hsic.contrast, hsic.saturationThreshold);
    }

    static vendor.lineage.livedisplay.V2_0.HSIC toHIDLHSIC(HSIC hsic) {
        vendor.lineage.livedisplay.V2_0.HSIC h = new vendor.lineage.livedisplay.V2_0.HSIC();
        h.hue = hsic.getHue();
        h.saturation = hsic.getSaturation();
        h.intensity = hsic.getIntensity();
        h.contrast = hsic.getContrast();
        h.saturationThreshold = hsic.getSaturationThreshold();
        return h;
    }

    static Range<Integer> fromHIDLRange(vendor.lineage.livedisplay.V2_0.Range range) {
        return new Range(range.min, range.max);
    }

    static Range<Float> fromHIDLRange(vendor.lineage.livedisplay.V2_0.FloatRange range) {
        return new Range(range.min, range.max);
    }
}
