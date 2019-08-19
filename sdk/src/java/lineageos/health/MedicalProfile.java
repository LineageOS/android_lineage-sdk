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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import lineageos.os.Build;
import lineageos.os.Concierge;
import lineageos.os.Concierge.ParcelInfo;

/**
 * General clinical information about a human.
 * This is returned when calling {@link #lineageos.health.HealthStoreInterface#getMedicalProfile}
 */
public class MedicalProfile implements Parcelable {
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

    public static final int ORGAN_DONOR_UNSET = 0;
    public static final int ORGAN_DONOR_YES = 1;
    public static final int ORGAN_DONOR_NO = 2;

    private String name = "";
    private long birthDate;

    private int biologicalSex;

    private float weight;
    private float height;

    private int bloodType;
    public int organDonor;

    @NonNull
    private String allergies = "";
    @NonNull
    private String medications = "";
    @NonNull
    private String conditions = "";
    @NonNull
    private String notes = "";


    /** @hide */
    public MedicalProfile() {
        name = "";
        birthDate = 0L;
        biologicalSex = BIOLOGICAL_SEX_UNSET;
        weight = 0f;
        height = 0f;
        bloodType = BLOOD_TYPE_UNSET;
        organDonor = ORGAN_DONOR_UNSET;
        allergies = "";
        medications = "";
        conditions = "";
        notes = "";
    }

    /**
     * Default constructor
     *
     * @see {@link lineageos.health.HealthStoreInterface#getMedicalProfile}
     *
     * @param name Full name of the user
     * @param birthDate Birth day of the user in millisec
     * @param biologicalSex Biological sex of the user. One of
     *                  {@link #lineageos.health.MedicalProfile#BIOLOGICAL_SEX_UNSET},
     *                  {@link #lineageos.health.MedicalProfile#BIOLOGICAL_SEX_FEMALE},
     *                  {@link #lineageos.health.MedicalProfile#BIOLOGICAL_SEX_MALE}
     * @param weight Weight of the user in Kg
     * @param height Height of the user in cm
     * @param bloodType Bood type of the user. One of
     *                  {@link #lineageos.health.MedicalProfile#BLOOD_TYPE_UNSET},
     *                  {@link #lineageos.health.MedicalProfile#BLOOD_TYPE_0_NEG},
     *                  {@link #lineageos.health.MedicalProfile#BLOOD_TYPE_0_POS},
     *                  {@link #lineageos.health.MedicalProfile#BLOOD_TYPE_A_NEG},
     *                  {@link #lineageos.health.MedicalProfile#BLOOD_TYPE_A_POS},
     *                  {@link #lineageos.health.MedicalProfile#BLOOD_TYPE_B_NEG},
     *                  {@link #lineageos.health.MedicalProfile#BLOOD_TYPE_B_POS},
     *                  {@link #lineageos.health.MedicalProfile#BLOOD_TYPE_AB_NEG},
     *                  {@link #lineageos.health.MedicalProfile#BLOOD_TYPE_AB_POS}
     * @param organDonor Whether the user is an organ donor
     * @param allergies Names of allergies of the user
     * @param medications Names of medications of the user
     * @param conditions Medical conditions of the user
     * @param notes Additional notes
     */
    public MedicalProfile(
            @NonNull String name, long birthDate, int biologicalSex,
            float weight, float height,
            int bloodType, int organDonor,
            @NonNull String allergies, @NonNull String medications,
            @NonNull String conditions, @NonNull String notes) {
        this.name = name;
        this.biologicalSex = biologicalSex;
        this.birthDate = birthDate;
        this.weight = weight;
        this.height = height;
        this.bloodType = bloodType;
        this.allergies = allergies;
        this.medications = medications;
        this.conditions = conditions;
        this.notes = notes;
        this.organDonor = organDonor;
    }

    private MedicalProfile(@NonNull Parcel parcel) {
        ParcelInfo parcelInfo = Concierge.receiveParcel(parcel);
        int parcelableVersion = parcelInfo.getParcelVersion();

        if (parcelableVersion >= Build.LINEAGE_VERSION_CODES.JACKFRUIT) {
            name = parcel.readString();
            birthDate = parcel.readLong();
            biologicalSex = parcel.readInt();
            weight = parcel.readFloat();
            height = parcel.readFloat();
            bloodType = parcel.readInt();
            organDonor = parcel.readInt();
            allergies = parcel.readString();
            medications = parcel.readString();
            conditions = parcel.readString();
            notes = parcel.readString();
        } else {
            name = "";
            birthDate = 0L;
            biologicalSex = BIOLOGICAL_SEX_UNSET;
            weight = 0f;
            height = 0f;
            bloodType = BLOOD_TYPE_UNSET;
            organDonor = ORGAN_DONOR_UNSET;
            allergies = "";
            medications = "";
            conditions = "";
            notes = "";
        }

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }

    /** @hide */
    public static final Parcelable.Creator<MedicalProfile> CREATOR =
            new Parcelable.Creator<MedicalProfile>() {

        @Override
        public MedicalProfile createFromParcel(Parcel source) {
            return new MedicalProfile(source);
        }

        @Override
        public MedicalProfile[] newArray(int size) {
            return new MedicalProfile[size];
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
        dest.writeInt(organDonor);
        dest.writeString(allergies);
        dest.writeString(medications);
        dest.writeString(conditions);
        dest.writeString(notes);

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
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

    public int getOrganDonor() {
        return organDonor;
    }

    public void setOrganDonor(int organDonor) {
        this.organDonor = organDonor;
    }

    @NonNull
    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(@NonNull String allergies) {
        this.allergies = allergies;
    }

    @NonNull
    public String getMedications() {
        return medications;
    }

    public void setMedications(@NonNull String medications) {
        this.medications = medications;
    }

    @NonNull
    public String getConditions() {
        return conditions;
    }

    public void setConditions(@NonNull String conditions) {
        this.conditions = conditions;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(@NonNull String notes) {
        this.notes = notes;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MedicalProfile that = (MedicalProfile) o;
        return birthDate == that.birthDate &&
                biologicalSex == that.biologicalSex &&
                Float.compare(that.weight, weight) == 0 &&
                Float.compare(that.height, height) == 0 &&
                that.bloodType == bloodType &&
                that.organDonor == organDonor &&
                Objects.equals(that.name, name) &&
                Objects.equals(that.allergies, allergies) &&
                Objects.equals(that.medications, medications) &&
                Objects.equals(that.conditions, conditions) &&
                Objects.equals(that.notes, notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, birthDate, biologicalSex, weight,
                height, bloodType, organDonor, allergies, medications,
                conditions, notes);
    }
}
