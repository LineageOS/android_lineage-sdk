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

package lineageos.health.records.activity;

import android.annotation.NonNull;
import android.os.Parcel;

import lineageos.health.Record;

import java.util.Date;
import java.util.Objects;

/**
 * Running activity.
 *
 * <ul>
 * <li>Distance in km</li>
 * <li>Average speed in km/h</li>
 * </ul>
 *
 * <a href="https://en.wikipedia.org/wiki/Running">More info</a>
 */
public class RunningRecord extends Record {
    public static final int CATEGORY = 401;

    private float distance;
    private float avgSpeed;

    public RunningRecord(Date begin, Date end,
            float distance, float avgSpeed) {
        this(UNSET_UID, begin, end, distance, avgSpeed);
    }

    public RunningRecord(long uid, Date begin, Date end,
            float distance, float avgSpeed) {
        super(uid, begin, end, CATEGORY);
        this.distance = distance;
        this.avgSpeed = avgSpeed;
    }

    /** @hide */
    public RunningRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    protected void writeValueTo(Parcel dest) {
        dest.writeFloat(distance);
        dest.writeFloat(avgSpeed);
    }

    @Override
    protected void readValueFrom(Parcel parcel) {
        distance = parcel.readFloat();
        avgSpeed = parcel.readFloat();
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public float getAverageSpeed() {
        return avgSpeed;
    }

    public void setAverageSpeed(float avgSpeed) {
        this.avgSpeed = avgSpeed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RunningRecord that = (RunningRecord) o;
        return Float.compare(that.distance, distance) == 0 &&
            Float.compare(that.avgSpeed, avgSpeed) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), distance, avgSpeed);
    }

    @Override
    public String toString() {
        return String.format(
            "Record { uid: %1$d, begin: %2$d; end: %3$d; distance: %4$.2f; avgSpeed: %5$.2f }",
            uid, time.getTime(), end.getTime(), distance, avgSpeed
        );
    }
}
