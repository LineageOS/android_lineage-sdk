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

package lineageos.health.records.mindfulness;

import android.annotation.NonNull;
import android.os.Parcel;

import lineageos.health.Record;

import java.util.Objects;

/**
 * Mood journal.
 *
 * <ul>
 * <li>moodLevel: value in range 0 (happy) to 5 (sad)</li>
 * <li>notes: eventual notes</li>
 * </ul>
 *
 * <a href="https://en.wikipedia.org/wiki/Mood_(psychology)">More info</a>
 */
public class MoodRecord extends Record {
    public static final int CATEGORY = 301;

    private int moodLevel;
    @NonNull
    private String notes = "";

    public MoodRecord(long time, int moodLevel) {
        this(UNSET_UID, time, moodLevel, "");
    }

    public MoodRecord(long uid, long time, int moodLevel) {
        this(uid, time, moodLevel, "");
    }

    public MoodRecord(long time, int moodLevel, @NonNull String notes) {
        this(UNSET_UID, time, moodLevel, notes);
    }

    public MoodRecord(long uid, long time, int value, @NonNull String notes) {
        super(uid, time, CATEGORY);
        this.moodLevel = moodLevel;
        this.notes = notes;
    }

    /** @hide */
    public MoodRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    protected void writeValueTo(Parcel dest) {
        dest.writeInt(moodLevel);
        dest.writeString(notes);
    }

    @Override
    protected void readValueFrom(Parcel parcel) {
        moodLevel = parcel.readInt();
        notes = parcel.readString();
    }

    @Override
    public boolean validate() {
        return moodLevel >= 1 && moodLevel <= 5 && super.validate();
    }

    public int getMoodLevel() {
        return moodLevel;
    }

    public void setMoodLevel(int moodLevel) {
        this.moodLevel = moodLevel;
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
        MoodRecord that = (MoodRecord) o;
        return moodLevel == that.moodLevel &&
            notes.equals(that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), moodLevel, notes);
    }

    @Override
    public String toString() {
        return String.format(
            "Record { uid: %1$d, time: %2$d; duration: %3$d; moodLevel: %4$d; notes: %5$s }",
            uid, time, duration, moodLevel, notes
        );
    }
}
