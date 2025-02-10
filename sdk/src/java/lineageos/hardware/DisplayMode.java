/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.hardware;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Display Modes API
 *
 * A device may implement a list of preset display modes for different
 * viewing intents, such as movies, photos, or extra vibrance. These
 * modes may have multiple components such as gamma correction, white
 * point adjustment, etc, but are activated by a single control point.
 *
 * This API provides support for enumerating and selecting the
 * modes supported by the hardware.
 *
 * A DisplayMode is referenced by it's identifier and carries an
 * associated name (up to the user to translate this value).
 */
public class DisplayMode implements Parcelable {
    public final int id;
    public final String name;

    public DisplayMode(int id, String name) {
        this.id = id;
        this.name = name;
    }

    private DisplayMode(Parcel parcel) {
        this.id = parcel.readInt();
        this.name = parcel.readInt() != 0 ? parcel.readString() : null;
    }

    @Override
    public int describeContents() {
        return 0;
    }   

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(id);
        if (name != null) {
            out.writeInt(1);
            out.writeString(name);
        } else {
            out.writeInt(0);
        }
    }
    
    /** @hide */
    public static final Parcelable.Creator<DisplayMode> CREATOR =
            new Parcelable.Creator<DisplayMode>() {
        public DisplayMode createFromParcel(Parcel in) {
            return new DisplayMode(in);
        }

        @Override
        public DisplayMode[] newArray(int size) {
            return new DisplayMode[size];
        }
    };

}
