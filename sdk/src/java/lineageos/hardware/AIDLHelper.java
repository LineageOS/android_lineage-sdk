/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.hardware;

import android.util.Range;

import java.util.ArrayList;

class AIDLHelper {
    static DisplayMode[] fromAIDLModes(vendor.lineage.livedisplay.DisplayMode[] modes) {
        int size = modes.length;
        DisplayMode[] r = new DisplayMode[size];
        for (int i = 0; i < size; i++) {
            vendor.lineage.livedisplay.DisplayMode m = modes[i];
            r[i] = new DisplayMode(m.id, m.name);
        }
        return r;
    }

    static DisplayMode fromAIDLMode(vendor.lineage.livedisplay.DisplayMode mode) {
        return new DisplayMode(mode.id, mode.name);
    }

    static HSIC fromAIDLHSIC(vendor.lineage.livedisplay.HSIC hsic) {
        return new HSIC(hsic.hue, hsic.saturation, hsic.intensity,
                hsic.contrast, hsic.saturationThreshold);
    }

    static vendor.lineage.livedisplay.HSIC toAIDLHSIC(HSIC hsic) {
        vendor.lineage.livedisplay.HSIC h = new vendor.lineage.livedisplay.HSIC();
        h.hue = hsic.getHue();
        h.saturation = hsic.getSaturation();
        h.intensity = hsic.getIntensity();
        h.contrast = hsic.getContrast();
        h.saturationThreshold = hsic.getSaturationThreshold();
        return h;
    }

    static Range<Integer> fromAIDLRange(vendor.lineage.livedisplay.Range range) {
        return new Range(range.min, range.max);
    }

    static Range<Float> fromAIDLRange(vendor.lineage.livedisplay.FloatRange range) {
        return new Range(range.min, range.max);
    }

    static TouchscreenGesture[] fromAIDLGestures(
            vendor.lineage.touch.Gesture[] gestures) {
        int size = gestures.length;
        TouchscreenGesture[] r = new TouchscreenGesture[size];
        for (int i = 0; i < size; i++) {
            vendor.lineage.touch.Gesture g = gestures[i];
            r[i] = new TouchscreenGesture(g.id, g.name, g.keycode);
        }
        return r;
    }

    static vendor.lineage.touch.Gesture toAIDLGesture(TouchscreenGesture gesture) {
        vendor.lineage.touch.Gesture g = new vendor.lineage.touch.Gesture();
        g.id = gesture.id;
        g.name = gesture.name;
        g.keycode = gesture.keycode;
        return g;
    }
}
