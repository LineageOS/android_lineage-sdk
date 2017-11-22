/**
 * Copyright (C) 2017 The LineageOS Project
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

package org.lineageos.internal.notification;

public class LedValues {
    public static final int LIGHT_BRIGHTNESS_MAXIMUM = 255;

    private boolean mEnabled;
    private int mColor;
    private int mOnMs;
    private int mOffMs;
    private int mBrightness;

    public LedValues(int color, int onMs, int offMs) {
        this(color, onMs, offMs, LIGHT_BRIGHTNESS_MAXIMUM);
    }

    public LedValues(int color, int onMs, int offMs, int brightness) {
        this(true, color, onMs, offMs, brightness);
    }

    public LedValues(boolean enabled, int color, int onMs, int offMs, int brightness) {
        mEnabled = enabled;
        mColor = color;
        mOnMs = onMs;
        mOffMs = offMs;
        mBrightness = brightness;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enable) {
        mEnabled = enable;
    }

    public int getColor() {
        return mColor;
    }

    public void setColor(int color) {
        mColor = color;
    }

    public int getOnMs() {
        return mOnMs;
    }

    public void setOnMs(int onMs) {
        mOnMs = onMs;
    }

    public int getOffMs() {
        return mOffMs;
    }

    public void setOffMs(int offMs) {
        mOffMs = offMs;
    }

    public int getBrightness() {
        return mBrightness;
    }

    public void setBrightness(int brightness) {
        mBrightness = brightness;
    }

    public void setSolid() {
        mOnMs = 0;
        mOffMs = 0;
    }

    public boolean isPulsed() {
        return mOnMs != 0 && mOffMs != 0;
    }

    public void setPulsed(int onMs, int offMs) {
        mOnMs = onMs;
        mOffMs = offMs;
    }

    public void applyAlphaToBrightness() {
        final int alpha = (mColor >> 24) & 0xFF;
        if (alpha > 0 && alpha < 255) {
            mBrightness = mBrightness * alpha / 255;
        }
        mColor |= 0xFF000000;
    }

    public void applyBrightnessToColor() {
        if (mBrightness > 0 && mBrightness < 255) {
            int red   = ((mColor >> 16) & 0xFF) * mBrightness / 255;
            int green = ((mColor >>  8) & 0xFF) * mBrightness / 255;
            int blue  = (mColor & 0xFF) * mBrightness / 255;
            mColor = (red << 16) | (green << 8) | blue;
            mBrightness = 255;
        }
    }

    @Override
    public String toString() {
        return "enabled=" + mEnabled + " color=#" + String.format("%08X", mColor)
                + " onMs=" + mOnMs + " offMs=" + mOffMs + " brightness=" + mBrightness;
    }
}
