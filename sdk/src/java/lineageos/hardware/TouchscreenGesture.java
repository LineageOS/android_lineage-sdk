/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.hardware;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Touchscreen gestures API
 *
 * A device may implement several touchscreen gestures for use while
 * the display is turned off, such as drawing alphabets and shapes.
 * These gestures can be interpreted by userspace to activate certain
 * actions and launch certain apps, such as to skip music tracks,
 * to turn on the flashlight, or to launch the camera app.
 *
 * This *should always* be supported by the hardware directly.
 * A lot of recent touch controllers have a firmware option for this.
 *
 * This API provides support for enumerating the gestures
 * supported by the touchscreen.
 *
 * A TouchscreenGesture is referenced by it's identifier and carries an
 * associated name (up to the user to translate this value).
 */
public class TouchscreenGesture implements Parcelable {

    public final int id;
    public final String name;
    public final int keycode;

    public TouchscreenGesture(int id, String name, int keycode) {
        this.id = id;
        this.name = name;
        this.keycode = keycode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(id);
        parcel.writeString(name);
        parcel.writeInt(keycode);
    }

    /** @hide */
    public static final Parcelable.Creator<TouchscreenGesture> CREATOR =
            new Parcelable.Creator<TouchscreenGesture>() {

        public TouchscreenGesture createFromParcel(Parcel in) {
            return new TouchscreenGesture(in.readInt(), in.readString(), in.readInt());
        }

        @Override
        public TouchscreenGesture[] newArray(int size) {
            return new TouchscreenGesture[size];
        }
    };
}
