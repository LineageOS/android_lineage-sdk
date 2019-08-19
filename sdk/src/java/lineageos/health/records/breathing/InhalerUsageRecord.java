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
package lineageos.health.records.breathing;

import android.annotation.NonNull;
import android.os.Parcel;

import java.util.Objects;

import lineageos.health.Record;

/**
 * Record an inhaler usage with some annotation regarding
 * the inhaled medicinal / the motivation of its usage.
 *
 * <a href="https://en.wikipedia.org/wiki/Inhaler">More info</a>
 */
public class InhalerUsageRecord extends Record {
    public static final int CATEGORY = 203;

    @NonNull
    private String notes = "";

    public InhalerUsageRecord(long time) {
        super(time);
    }

    public InhalerUsageRecord(long uid, long time) {
        super(uid, time);
    }

    public InhalerUsageRecord(long time, @NonNull String notes) {
        super(time);
        this.notes = notes;
    }

    public InhalerUsageRecord(long uid, long time, @NonNull String notes) {
        super(uid, time);
        this.notes = notes;
    }

    @Override
    public int getCategory() {
        return CATEGORY;
    }

    @Override
    protected void writeValueTo(Parcel dest) {
        dest.writeString(notes);
    }

    @Override
    protected void readValueFrom(Parcel parcel) {
        notes = parcel.readString();
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
        InhalerUsageRecord that = (InhalerUsageRecord) o;
        return notes.equals(that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), notes);
    }
}
