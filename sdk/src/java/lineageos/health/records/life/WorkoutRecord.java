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

package lineageos.health.records.life;

import android.annotation.NonNull;
import android.os.Parcel;

import lineageos.health.Record;

import java.util.Objects;

/**
 * Workout exercise record.
 *
 * Keeps track of burned calories and eventual notes
 * regarding the session.
 *
 * <a href="https://en.wikipedia.org/wiki/Exercise">More info</a>
 */
public class WorkoutRecord extends Record {
    public static final int CATEGORY = 304;

    private int calories;
    @NonNull
    private String notes = "";

    public WorkoutRecord(long begin, long end, int calories) {
        this(UNSET_UID, begin, end, calories, "");
    }

    public WorkoutRecord(long begin, long end,
            int calories, @NonNull String notes) {
        this(UNSET_UID, begin, end, calories, notes);
    }

    public WorkoutRecord(long uid, long begin, long end, int calories) {
        this(uid, begin, end, calories, "");
    }

    public WorkoutRecord(long uid, long begin, long end,
            int calories, @NonNull String notes) {
        super(uid, begin, end - begin, CATEGORY);
        this.calories = calories;
        this.notes = notes;
    }

    /** @hide */
    public WorkoutRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    protected void writeValueTo(Parcel dest) {
        dest.writeInt(calories);
        dest.writeString(notes);
    }

    @Override
    protected void readValueFrom(Parcel parcel) {
        calories = parcel.readInt();
        notes = parcel.readString();
    }

    @Override
    public String getSymbol() {
        return "Cal";
    }

    public int getCalories() {
        return calories;
    }

    public void setCalories(int calories) {
        this.calories = calories;
    }

    @NonNull
    public String getNotes() {
        return notes;
    }

    public void setNotes(@NonNull String notes) {
        this.notes = notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        WorkoutRecord that = (WorkoutRecord) o;
        return calories == that.calories &&
            notes.equals(that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), calories, notes);
    }

    @Override
    public String toString() {
        return String.format(
            "Record { uid: %1$d, time: %2$d; duration: %3$d; calories: %4$d; notes: %5$s }",
            uid, time, duration, calories, notes
        );
    }
}
