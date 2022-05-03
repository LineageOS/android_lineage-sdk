/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2020 The LineageOS Project
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

package lineageos.profiles;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.os.Parcel;
import android.os.Parcelable;

import lineageos.os.Build;
import lineageos.os.Concierge;
import lineageos.os.Concierge.ParcelInfo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;


import java.io.IOException;

/**
 * The {@link AutobrightnessSettings} class allows for overriding and setting the autobrightness
 * of the display.
 *
 * <p>Example for setting the autobrightness to enabled:
 * <pre class="prettyprint">
 * AutobrightnessSettings autobrightness = new AutobrightnessSettings(BooleanState.STATE_ENABLED, true)
 * profile.setAutobrightness(autobrightness);
 * </pre>
 */
public final class AutobrightnessSettings implements Parcelable {

    private int mValue;
    private boolean mOverride;
    private boolean mDirty;

    /** @hide */
    public static final Parcelable.Creator<AutobrightnessSettings> CREATOR =
            new Parcelable.Creator<AutobrightnessSettings>() {
        public AutobrightnessSettings createFromParcel(Parcel in) {
            return new AutobrightnessSettings(in);
        }

        @Override
        public AutobrightnessSettings[] newArray(int size) {
            return new AutobrightnessSettings[size];
        }
    };

    /**
     * BooleanStates for specific {@link AutobrightnessSettings}
     */
    public static class BooleanState {
        /** Disabled state */
        public static final int STATE_DISABLED = 0;
        /** Enabled state */
        public static final int STATE_ENABLED = 1;
    }

    /**
     * Unwrap {@link AutobrightnessSettings} from a parcel.
     * @param parcel
     */
    public AutobrightnessSettings(Parcel parcel) {
        readFromParcel(parcel);
    }

    /**
     * Construct a {@link AutobrightnessSettings} with a default value of
     * {@link BooleanState#STATE_DISABLED}.
     */
    public AutobrightnessSettings() {
        this(BooleanState.STATE_DISABLED, false);
    }

    /**
     * Construct a {@link AutobrightnessSettings} with a default value and whether or not it should
     * override user settings.
     * @param value ex: {@link BooleanState#STATE_DISABLED}
     * @param override whether or not the setting should override user settings
     */
    public AutobrightnessSettings(int value, boolean override) {
        mValue = value;
        mOverride = override;
        mDirty = false;
    }

    /**
     * Get the default value for the {@link AutobrightnessSettings}
     * @return integer value corresponding with its brightness value
     */
    public int getValue() {
        return mValue;
    }

    /**
     * Set the default value for the {@link AutobrightnessSettings}
     * @param value {@link BooleanState#STATE_DISABLED}
     */
    public void setValue(int value) {
        mValue = value;
        mDirty = true;
    }

    /**
     * Set whether or not the {@link AutobrightnessSettings} should override default user values
     * @param override boolean override
     */
    public void setOverride(boolean override) {
        mOverride = override;
        mDirty = true;
    }

    /**
     * Check whether or not the {@link AutobrightnessSettings} overrides user settings.
     * @return true if override
     */
    public boolean isOverride() {
        return mOverride;
    }

    /** @hide */
    public boolean isDirty() {
        return mDirty;
    }

    /** @hide */
    public void processOverride(Context context) {
        if (isOverride()) {
            int current = Settings.System.getInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            if (current != mValue) {
                Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    mValue);
            }
        }
    }

    /** @hide */
    public static AutobrightnessSettings fromXml(XmlPullParser xpp, Context context)
            throws XmlPullParserException, IOException {
        int event = xpp.next();
        AutobrightnessSettings autobrightnessDescriptor = new AutobrightnessSettings();
        while ((event != XmlPullParser.END_TAG && event != XmlPullParser.END_DOCUMENT) ||
                !xpp.getName().equals("autobrightnessDescriptor")) {
            if (event == XmlPullParser.START_TAG) {
                String name = xpp.getName();
                if (name.equals("value")) {
                    autobrightnessDescriptor.mValue = Integer.parseInt(xpp.nextText());
                } else if (name.equals("override")) {
                    autobrightnessDescriptor.mOverride = Boolean.parseBoolean(xpp.nextText());
                }
            } else if (event == XmlPullParser.END_DOCUMENT) {
                throw new IOException("Premature end of file while parsing autobrightness settings");
            }
            event = xpp.next();
        }
        return autobrightnessDescriptor;
    }

    /** @hide */
    public void getXmlString(StringBuilder builder, Context context) {
        builder.append("<autobrightnessDescriptor>\n<value>");
        builder.append(mValue);
        builder.append("</value>\n<override>");
        builder.append(mOverride);
        builder.append("</override>\n</autobrightnessDescriptor>\n");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Tell the concierge to prepare the parcel
        ParcelInfo parcelInfo = Concierge.prepareParcel(dest);

        // === BOYSENBERRY ===
        dest.writeInt(mOverride ? 1 : 0);
        dest.writeInt(mValue);
        dest.writeInt(mDirty ? 1 : 0);

        // Complete the parcel info for the concierge
        parcelInfo.complete();
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        // Read parcelable version via the Concierge
        ParcelInfo parcelInfo = Concierge.receiveParcel(in);
        int parcelableVersion = parcelInfo.getParcelVersion();

        // Pattern here is that all new members should be added to the end of
        // the writeToParcel method. Then we step through each version, until the latest
        // API release to help unravel this parcel
        if (parcelableVersion >= Build.LINEAGE_VERSION_CODES.BOYSENBERRY) {
            mOverride = in.readInt() != 0;
            mValue = in.readInt();
            mDirty = in.readInt() != 0;
        }

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }
}
