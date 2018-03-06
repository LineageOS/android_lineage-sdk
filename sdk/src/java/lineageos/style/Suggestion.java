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

/**
 * Style suggestion holder class.
 * This is returned when calling {@link #lineageos.style.StyleInterface#getSuggestion}
 */
public class Suggestion implements Parcelable {
    public final int globalStyle;
    public final int selectedAccent;

    /**
     * Default constructor
     *
     * @see {@link lineageos.style.StyleInterface#getSuggestion}
     *
     * @param globalStyle One of {@link #lineageos.style.StyleInterface#STYLE_GLOBAL_LIGHT} or
     *                           {@link #lineageos.style.StyleInterface#STYLE_GLOBAL_DARK}
     * @param selectedAccent The position of the selected color in the input array
     */
    public Suggestion(int globalStyle, int selectedAccent) {
        this.globalStyle = globalStyle;
        this.selectedAccent = selectedAccent;
    }

    private Suggestion(Parcel parcel) {
        ParcelInfo parcelInfo = Concierge.receiveParcel(parcel);
        int parcelableVersion = parcelInfo.getParcelVersion();

        if (parcelableVersion >= Build.LINEAGE_VERSION_CODES.HACKBERRY) {
            globalStyle = parcel.readInt();
            selectedAccent = parcel.readInt();
        } else {
            globalStyle = 0;
            selectedAccent = 0;
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
    public void writeToParcel(Parcel dest, int flags) {
        // Tell the concierge to prepare the parcel
        ParcelInfo parcelInfo = Concierge.prepareParcel(dest);

        // ==== HACKBERRY ====
        dest.writeInt(globalStyle);
        dest.writeInt(selectedAccent);

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }
}
