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

package lineageos.health.records;

import android.os.Parcel;

import java.util.Objects;

import lineageos.health.Record;

/**
 * Abstract class that defines a {@link lineageos.health.Record} that holds
 * a single float value
 */
public abstract class SimpleFloatRecord extends Record {
    protected float value;

    public SimpleFloatRecord(long uid, long time, int category, float value) {
        super(uid, time, category);
        this.value = value;
    }

    public SimpleFloatRecord(long uid, long time, long duration,
            int category, float value) {
        super(uid, time, duration, category);
        this.value = value;
    }

    /** @hide */
    public SimpleFloatRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    public boolean validate() {
        return value > 0f && super.validate();
    }

    @Override
    protected void writeValueTo(Parcel dest) {
        dest.writeFloat(value);
    }

    @Override
    protected void readValueFrom(Parcel parcel) {
        value = parcel.readFloat();
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleFloatRecord)) return false;
        if (!super.equals(o)) return false;
        SimpleFloatRecord that = (SimpleFloatRecord) o;
        return Float.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value);
    }

    @Override
    public String toString() {
        return String.format(
            "Record { uid: %1$d, time: %2$d; duration: %3$d; category: %4$d; value: %5$.2f }",
            uid, time, duration, getCategory(), value
        );
    }
}
