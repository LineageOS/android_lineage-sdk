/*
**
** Copyright (C) 2018 The LineageOS Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package lineageos.style;

import android.os.Parcel;
import android.os.Parcelable;

import lineageos.os.Build;
import lineageos.os.Concierge;
import lineageos.os.Concierge.ParcelInfo;

public class Suggestion implements Parcelable {
    public final int mGlobalStyle;
    public final int mColorPosition;

    /**
     * Default constructor
     *
     * @see lineageos.style.StyleInterface#getSuggestion
     *
     * @param globalStyle one of {@link #STYLE_GLOBAL_LIGHT} or {@link #STYLE_GLOBAL_DARK}
     * @param colorPosition position of selected color in the input array
     */
    public Suggestion(int globalStyle, int colorPosition) {
        mGlobalStyle = globalStyle;
        mColorPosition = colorPosition;
    }

    private Suggestion(Parcel parcel) {
        ParcelInfo parcelInfo = Concierge.receiveParcel(parcel);
        int parcelableVersion = parcelInfo.getParcelVersion();

        if (parcelableVersion >= Build.LINEAGE_VERSION_CODES.HACKBERRY) {
            mGlobalStyle = parcel.readInt();
            mColorPosition = parcel.readInt();
        } else {
            mGlobalStyle = 0;
            mColorPosition = 0;
        }

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }

    /** @hide */
    public static final Parcelable.Creator<Suggestion> CREATOR =
            new Parcelable.Creator<Suggestion>() {

                @Override
                public Suggestion createFromParcel(Parcel source) {
                    return new Suggestion(source);
                }

                @Override
                public Suggestion[] newArray(int size) {
                    return new Suggestion[size];
                }
            };

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mGlobalStyle);
        out.writeInt(mColorPosition);
    }
}
