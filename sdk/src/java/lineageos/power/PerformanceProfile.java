/*
 * Copyright (C) 2016 The CyanogenMod Project
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
package lineageos.power;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import lineageos.os.Build;
import lineageos.os.Concierge;

/**
 * Encapsulates information about an available system power/peformance profile, managed
 * by the PerformanceManager.
 */
public class PerformanceProfile implements Parcelable, Comparable<PerformanceProfile> {

    private final int mId;

    private final float mWeight;

    private final String mName;

    private final String mDescription;

    public PerformanceProfile(int id, float weight, String name, String description) {
        mId = id;
        mWeight = weight;
        mName = name;
        mDescription = description;
    }

    private PerformanceProfile(Parcel in) {
        Concierge.ParcelInfo parcelInfo = Concierge.receiveParcel(in);
        int parcelableVersion = parcelInfo.getParcelVersion();

        mId = in.readInt();
        mWeight = in.readFloat();
        mName = in.readString();
        mDescription = in.readString();

        if (parcelableVersion >= Build.LINEAGE_VERSION_CODES.GUAVA) {
            // nothing yet
        }

        parcelInfo.complete();
    }

    /**
     * Unique identifier for this profile. Must match values used by the PowerHAL.
     *
     * @return the id
     */
    public int getId() {
        return mId;
    }

    /**
     * The profile's weight, from 0 to 1, with 0 being lowest (power save), 1 being
     * highest (performance), and 0.5 as the balanced default profile. Other
     * values may be seen, depending on the device. This value can be used for
     * sorting.
     *
     * @return weight
     */
    public float getWeight() {
        return mWeight;
    }

    /**
     * A localized name for the profile, suitable for display.
     *
     * @return name
     */
    public String getName() {
        return mName;
    }

    /**
     * A localized description of the profile, suitable for display.
     *
     * @return description
     */
    public String getDescription() {
        return mDescription;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Concierge.ParcelInfo parcelInfo = Concierge.prepareParcel(dest);

        dest.writeInt(mId);
        dest.writeFloat(mWeight);
        dest.writeString(mName);
        dest.writeString(mDescription);

        parcelInfo.complete();
    }

    public static final Creator<PerformanceProfile> CREATOR = new Creator<PerformanceProfile>() {
        @Override
        public PerformanceProfile createFromParcel(Parcel in) {
            return new PerformanceProfile(in);
        }

        @Override
        public PerformanceProfile[] newArray(int size) {
            return new PerformanceProfile[size];
        }
    };

    @Override
    public int compareTo(PerformanceProfile other) {
        return Float.compare(mWeight, other.mWeight);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (!getClass().equals(other.getClass())) {
            return false;
        }

        PerformanceProfile o = (PerformanceProfile) other;
        return Objects.equals(mId, o.mId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId);
    }

    @Override
    public String toString() {
        return String.format("PerformanceProfile[id=%d, weight=%f, name=%s desc=%s]",
                mId, mWeight, mName, mDescription);
    }
}
