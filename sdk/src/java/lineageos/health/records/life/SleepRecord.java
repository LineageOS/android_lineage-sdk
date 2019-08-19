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

import android.os.Parcel;

import java.util.Objects;

import lineageos.health.Record;

/**
 * Wasted time in minutes (/s).
 *
 * <a href="https://en.wikipedia.org/wiki/Sleep">More info</a>
 */
public class SleepRecord extends Record {
    public static final int CATEGORY = 301;

    private boolean inBed;

    public SleepRecord(long begin, long end, boolean inBed) {
        this(UNSET_UID, begin, end, inBed);
    }

    public SleepRecord(long uid, long begin, long end, boolean inBed) {
        super(uid, begin, end - begin, CATEGORY);
    }

    /** @hide */
    public SleepRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    protected void writeValueTo(Parcel dest) {
        dest.writeInt(inBed ? 1 : 0);
    }

    @Override
    protected void readValueFrom(Parcel parcel) {
        inBed = parcel.readInt() == 1;
    }

    @Override
    public String getSymbol() {
        return "min";
    }

    public boolean isInBed() {
        return inBed;
    }

    public void setInBed(boolean inBed) {
        this.inBed = inBed;
    }

    @Override
    public String toString() {
        return String.format(
            "Record { uid: %1$d, time: %2$d; duration: %3$d; inBed: %4$s }",
            uid, time, duration, String.valueOf(inBed)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SleepRecord that = (SleepRecord) o;
        return inBed == that.inBed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), inBed);
    }
}
