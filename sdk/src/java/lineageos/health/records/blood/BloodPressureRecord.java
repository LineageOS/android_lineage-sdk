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

package lineageos.health.records.blood;

import android.os.Parcel;

import lineageos.health.Record;

import java.util.Objects;

/**
 * Blood pressure (BP) measured in mmHg.
 *
 * <a href="https://en.wikipedia.org/wiki/Blood_pressure">More info</a>
 */
public class BloodPressureRecord extends Record {
    public static final int CATEGORY = 1; // 0-01

    private int systolic;
    private int diastolic;

    public BloodPressureRecord(int systolic, int diastolic, long time) {
        super(time);
        this.systolic = systolic;
        this.diastolic = diastolic;
    }

    public BloodPressureRecord(long uid, int systolic, int diastolic, long time) {
        super(uid, time);
        this.systolic = systolic;
        this.diastolic = diastolic;
    }

    @Override
    public int getCategory() {
        return CATEGORY;
    }

    @Override
    protected void writeValueTo(Parcel dest) {
        dest.writeInt(systolic);
        dest.writeInt(diastolic);
    }

    @Override
    protected void readValueFrom(Parcel parcel) {
        systolic = parcel.readInt();
        diastolic = parcel.readInt();
    }

    @Override
    public String getSymbol() {
        return "mmHg";
    }

    public int getSystolic() {
        return systolic;
    }

    public void setSystolic(int systolic) {
        this.systolic = systolic;
    }

    public int getDiastolic() {
        return diastolic;
    }

    public void setDiastolic(int diastolic) {
        this.diastolic = diastolic;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BloodPressureRecord other = (BloodPressureRecord) obj;
        return other.systolic == systolic &&
                other.diastolic == diastolic &&
                other.time == time;
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, systolic, diastolic);
    }
}
