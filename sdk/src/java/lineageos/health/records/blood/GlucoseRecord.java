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

import java.util.Objects;

import lineageos.health.Record;

/**
 * Glucose / Blood sugar level measured in mmol/L.
 *
 * Also supports inserting insulin & basal dosages measured in insulin units.
 *
 * <a href="https://en.wikipedia.org/wiki/Blood_sugar_level">More info</a>
 */
public final class GlucoseRecord extends Record {
    public static final int CATEGORY = 2; // 0-02

    private float value;
    private boolean beforeMeal;
    private float insulin;
    private float basalInsulin;

    public GlucoseRecord(long time, float value, boolean beforeMeal) {
        this(UNSET_UID, time, beforeMeal, -1f, -1f);
    }

    public GlucoseRecord(long time, float value, boolean beforeMeal,
                         float insulin) {
        this(UNSET_UID, time, value, beforeMeal, insulin, -1f);
    }

    public GlucoseRecord(long time, float value, boolean beforeMeal,
                         float insulin, float basalInsulin) {
        this(UNSET_UID, time, value, beforeMeal, insulin, basalInsulin);
    }

    public GlucoseRecord(long uid, long time, float value, boolean beforeMeal,
                         float insulin, float basalInsulin) {
        super(uid, time, CATEGORY);
        this.value = value;
        this.beforeMeal = beforeMeal;
        this.insulin = insulin;
        this.basalInsulin = basalInsulin;
    }

    /** @hide */
    public GlucoseRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    protected void writeValueTo(Parcel dest) {
        dest.writeFloat(value);
        dest.writeInt(beforeMeal ? 1 : 0);
        dest.writeFloat(insulin);
        dest.writeFloat(basalInsulin);
    }

    @Override
    protected void readValueFrom(Parcel parcel) {
        value = parcel.readFloat();
        beforeMeal = parcel.readInt() == 1;
        insulin = parcel.readFloat();
        basalInsulin = parcel.readFloat();
    }

    @Override
    public String getSymbol() {
        return "mmol/L";
    }

    @Override
    public boolean validate() {
        return value > 0f && super.validate();
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public boolean isBeforeMeal() {
        return beforeMeal;
    }

    public void setBeforeMeal(boolean beforeMeal) {
        this.beforeMeal = beforeMeal;
    }

    public float getInsulin() {
        return insulin;
    }

    public void setInsulin(float insulin) {
        this.insulin = insulin;
    }

    public float getBasalInsulin() {
        return basalInsulin;
    }

    public void setBasalInsulin(float basalInsulin) {
        this.basalInsulin = basalInsulin;
    }

    /**
     * Convert a mmol/L value to a mg/dL value.
     *
     * <a href="https://en.wikipedia.org/wiki/Blood_sugar_level#Units">More info</a>
     */
    public static int convertTomgdL(float mmolL) {
        return (int) (mmolL * 18);
    }

    /**
     * Convert a mg/dL value to a mmol/dL value.
     *
     * <a href="https://en.wikipedia.org/wiki/Blood_sugar_level#Units">More info</a>
     */
    public static float convertTommolL(int mgdL) {
        return mgdL / 18f;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GlucoseRecord that = (GlucoseRecord) o;
        return Float.compare(that.value, value) == 0 &&
                that.beforeMeal == beforeMeal &&
                Float.compare(that.insulin, insulin) == 0 &&
                Float.compare(that.basalInsulin, basalInsulin) == 0 &&
                that.time == time;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, time, beforeMeal, insulin, basalInsulin);
    }

    @Override
    public String toString() {
        return String.format(
            "Record { uid: %1$d, time: %2$d; duration: %3$d; value: %4$.2f; " +
            "beforeMeal: %5$s; insulin: %6$.1f; basalInsulin: %7$.1f }",
            uid, time, duration, value, String.valueOf(beforeMeal),
            insulin, basalInsulin
        );
    }

}
