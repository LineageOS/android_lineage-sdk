/*
 * Copyright (c) 2019, The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lineageos.health;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import lineageos.os.Build;
import lineageos.os.Concierge;
import lineageos.os.Concierge.ParcelInfo;

/**
 * General clinical information about a human.
 * This is returned when calling {@link #lineageos.health.HealthStoreInterface#getHumanInfo}
 */
public class HumanInfo implements Parcelable {
    public static final int BLOOD_TYPE_UNSET = 0;
    public static final int BLOOD_TYPE_0_NEG = 1;
    public static final int BLOOD_TYPE_0_POS = 2;
    public static final int BLOOD_TYPE_A_NEG = 3;
    public static final int BLOOD_TYPE_A_POS = 4;
    public static final int BLOOD_TYPE_B_NEG = 5;
    public static final int BLOOD_TYPE_B_POS = 6;
    public static final int BLOOD_TYPE_AB_NEG = 7;
    public static final int BLOOD_TYPE_AB_POS = 8;

    public static final int BIOLOGICAL_SEX_UNSET = 0;
    public static final int BIOLOGICAL_SEX_FEMALE = 1;
    public static final int BIOLOGICAL_SEX_MALE = 2;

    public String name;
    public long birthDate;

    public int biologicalSex;

    public float weight;
    public float height;

    public int bloodType;

    public String allergies;
    public String medications;

    public boolean organDonor;

    /** @hide */
    public HumanInfo() {
        name = "";
        birthDate = 0L;
        biologicalSex = BIOLOGICAL_SEX_UNSET;
        weight = 0f;
        height = 0f;
        allergies = "";
        medications = "";
        organDonor = false;
    }

    /**
     * Default constructor
     *
     * @see {@link lineageos.health.HealthStoreInterface#getHumanInfo}
     *
     * @param name Full name of the user
     * @param birthDate Birth day of the user in millisec
     * @param biologicalSex Biological sex of the user. One of
     *                  {@link #lineageos.health.HumanInfo#BIOLOGICAL_SEX_UNSET},
     *                  {@link #lineageos.health.HumanInfo#BIOLOGICAL_SEX_FEMALE},
     *                  {@link #lineageos.health.HumanInfo#BIOLOGICAL_SEX_MALE}
     * @param weight Weight of the user in Kg
     * @param height Height of the user in cm
     * @param bloodType Bood type of the user. One of
     *                  {@link #lineageos.health.HumanInfo#BLOOD_TYPE_UNSET},
     *                  {@link #lineageos.health.HumanInfo#BLOOD_TYPE_0_NEG},
     *                  {@link #lineageos.health.HumanInfo#BLOOD_TYPE_0_POS},
     *                  {@link #lineageos.health.HumanInfo#BLOOD_TYPE_A_NEG},
     *                  {@link #lineageos.health.HumanInfo#BLOOD_TYPE_A_POS},
     *                  {@link #lineageos.health.HumanInfo#BLOOD_TYPE_B_NEG},
     *                  {@link #lineageos.health.HumanInfo#BLOOD_TYPE_B_POS},
     *                  {@link #lineageos.health.HumanInfo#BLOOD_TYPE_AB_NEG},
     *                  {@link #lineageos.health.HumanInfo#BLOOD_TYPE_AB_POS}
     * @param allergies Names of allergies of the user
     * @param medications Names of medications of the user
     * @param organDonor Whether the user is an organ donor
     */
    public HumanInfo(String name, long birthDate, int biologicalSex,
            float weight, float height,
            int bloodType, String allergies, String medications,
            boolean organDonor) {
        this.name = name;
        this.biologicalSex = biologicalSex;
        this.birthDate = birthDate;
        this.weight = weight;
        this.height = height;
        this.bloodType = bloodType;
        this.allergies = allergies;
        this.medications = medications;
        this.organDonor = organDonor;
    }

    private HumanInfo(Parcel parcel) {
        ParcelInfo parcelInfo = Concierge.receiveParcel(parcel);
        int parcelableVersion = parcelInfo.getParcelVersion();

        if (parcelableVersion >= Build.LINEAGE_VERSION_CODES.JACKFRUIT) {
            name = parcel.readString();
            birthDate = parcel.readLong();
            biologicalSex = parcel.readInt();
            weight = parcel.readFloat();
            height = parcel.readFloat();
            bloodType = parcel.readInt();
            allergies = parcel.readString();
            medications = parcel.readString();
            organDonor = parcel.readInt() == 1;
        } else {
            name = "";
            birthDate = 0L;
            biologicalSex = BIOLOGICAL_SEX_UNSET;
            weight = 0f;
            height = 0f;
            bloodType = BLOOD_TYPE_UNSET;
            allergies = "";
            medications = "";
            organDonor = false;
        }

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }

    /** @hide */
    public static final Parcelable.Creator<HumanInfo> CREATOR =
            new Parcelable.Creator<HumanInfo>() {

                @Override
                public HumanInfo createFromParcel(Parcel source) {
                    return new HumanInfo(source);
                }

                @Override
                public HumanInfo[] newArray(int size) {
                    return new HumanInfo[size];
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

        // ==== JACKFRUIT ====
        dest.writeString(name);
        dest.writeLong(birthDate);
        dest.writeInt(biologicalSex);
        dest.writeFloat(weight);
        dest.writeFloat(height);
        dest.writeInt(bloodType);
        dest.writeString(allergies);
        dest.writeString(medications);
        dest.writeInt(organDonor ? 1 : 0);

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(long birthDate) {
        this.birthDate = birthDate;
    }

    public int getBiologicalSex() {
        return biologicalSex;
    }

    public void setBiologicalSex(int biologicalSex) {
        this.biologicalSex = biologicalSex;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public int getBloodType() {
        return bloodType;
    }

    public void setBloodType(int bloodType) {
        this.bloodType = bloodType;
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public String getMedications() {
        return medications;
    }

    public void setMedications(String medications) {
        this.medications = medications;
    }

    public boolean isOrganDonor() {
        return organDonor;
    }

    public void setOrganDonor(boolean organDonor) {
        this.organDonor = organDonor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HumanInfo humanInfo = (HumanInfo) o;
        return birthDate == humanInfo.birthDate &&
                biologicalSex == humanInfo.biologicalSex &&
                Float.compare(humanInfo.weight, weight) == 0 &&
                Float.compare(humanInfo.height, height) == 0 &&
                bloodType == humanInfo.bloodType &&
                organDonor == humanInfo.organDonor &&
                Objects.equals(name, humanInfo.name) &&
                Objects.equals(allergies, humanInfo.allergies) &&
                Objects.equals(medications, humanInfo.medications);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, birthDate, biologicalSex, weight, height,
                bloodType, allergies, medications, organDonor);
    }
}
