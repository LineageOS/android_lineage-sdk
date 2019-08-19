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

import lineageos.os.Build;
import lineageos.os.Concierge;
import lineageos.os.Concierge.ParcelInfo;

import java.util.Objects;

/**
 * Generic base Record class.
 * This is returned when calling {@link #lineageos.health.HealthStoreInterface#getHumanInfo}
 */
public class Record implements Parcelable {
    /** @hide */
    public final static long UNSET_UID = -1;

    private final long uid;

    protected long time;
    protected long duration;

    /**
     * Constructor for instant records with an uid
     *
     * @see {@link lineageos.health.HealthStoreInterface#getRecords}
     * @see {@link lineageos.health.HealthStoreInterface#getRecord}
     * @see {@link lineageos.health.HealthStoreInterface#writeRecord}
     *
     * @param uid The unique id number of the record
     * @param time The timestamp of when the record was made
     */
    public Record(long time) {
        this(UNSET_UID, time, 0L);
    }

    /**
     * Constructor for instant records with an uid
     *
     * @see {@link lineageos.health.HealthStoreInterface#getRecords}
     * @see {@link lineageos.health.HealthStoreInterface#getRecord}
     * @see {@link lineageos.health.HealthStoreInterface#writeRecord}
     *
     * @param uid The unique id number of the record
     * @param time The timestamp of when the record was made
     */
    public Record(long uid, long time) {
        this(uid, time, 0L);
    }

    /**
     * Constructor for records with a time length and an uid
     *
     * @see {@link lineageos.health.HealthStoreInterface#getRecords}
     * @see {@link lineageos.health.HealthStoreInterface#getRecord}
     * @see {@link lineageos.health.HealthStoreInterface#writeRecord}
     *
     * @param uid The unique id number of the record
     * @param time The timestamp of when the record was made
     * @param duration The duration in millisec of the record
     */
    public Record(long uid, long time, long duration) {
        this.uid = uid;
        this.time = time;
        this.duration = duration;
    }

    /** @hide */
    public Record(byte[] bytes) {
        this(fromBytes(bytes));
    }

    private Record(Parcel parcel) {
        ParcelInfo parcelInfo = Concierge.receiveParcel(parcel);
        int parcelableVersion = parcelInfo.getParcelVersion();

        if (parcelableVersion >= Build.LINEAGE_VERSION_CODES.JACKFRUIT) {
            uid = parcel.readLong();
            time = parcel.readLong();
            duration = parcel.readLong();
            readValueFrom(parcel);
        } else {
            uid = -1L;
            time = 0L;
            duration = 0L;
            readValueFrom(null);
        }

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }

    /**
     * Get the category uid for this record.
     *
     * Reference values:
     * <b>System categories</b>
     * <ul>
     * <li>000 - 099: Hearth & Blood [API 10]</li>
     * <li>100 - 199: Sleep & Mindfulness [API 10]</li>
     * <li>200 - 299: Body [API 10]</li>
     * <li>300 - 399: Breathing [API 10]</li>
     * </ul>
     *
     * <p>Custom values:<br>
     * Apps may define their own custom category, which will not be shown
     * In the system management app. While it's possible for developers to
     * use a custom category, we will not safeguard against category uid
     * conflicts between 3rd party apps. We encourage you to submit your
     * custom Record implementation to our <a href="https://review.lineageos.org">gerrit</a>
     * so that it will be added to the official sdk so that everyone will be able
     * to use a standard implementation that will never run into uid or implementation
     * conflicts.</p>
     *
     * <p>We recommend adding a public constant to your implementation so
     * that you need no instance of a Record to query the database for all
     * the other records of the same category</p>
     */
    public int getCategory() {
        return -1;
    }

    /**
     * Write the value object to a {@link android.os.Parcel}
     *
     * @see {@link lineageos.health.Record#writeToParcel(Parcel, int)}
     *
     * @param dest The parcel on which to write the value
     */
    protected void writeValueTo(Parcel dest) {
        // Do nothing
    }

    /**
     * Read the value object from a {@link android.os.Parcel}
     *
     * @see {@link lineageos.health.Record#Record(Parcel)}
     *
     * @param parcel The parcel from which the value will be read.
     *             <b>May be null</b>
     */
    protected void readValueFrom(Parcel parcel) {
        // Do nothing
    }

    /**
     * Get symbol used for the measurement unit.
     * Will return <code>null</code> if no symbol is available
     */
    public String getSymbol() {
        return null;
    }

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
        dest.writeLong(uid);
        dest.writeLong(time);
        dest.writeLong(duration);
        writeValueTo(dest);

        // Complete parcel info for the concierge
        parcelInfo.complete();
    }

    public long getUid() {
        return uid;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    /** @hide */
    public byte[] asByteArray() {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);

        // The category is exposed in the db for indexing
        parcel.writeInt(getCategory());
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Record)) return false;
        Record that = (Record) o;
        return that.time == time && that.duration == duration;
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, duration);
    }

    private static Parcel fromBytes(byte[] bytes) {
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        return parcel;
    }

    /** @hide */
    public static final Parcelable.Creator<Record> CREATOR =
            new Parcelable.Creator<Record>() {

                @Override
                public Record createFromParcel(Parcel source) {
                    return new Record(source);
                }

                @Override
                public Record[] newArray(int size) {
                    return new Record[size];
                }
            };

}
