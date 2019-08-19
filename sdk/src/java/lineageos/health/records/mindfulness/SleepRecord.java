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
import android.annotation.Nullable;
import android.os.Parcel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lineageos.health.Record;

/**
 * Sleep time.
 *
 * It is possible to optionally add sleep stages
 * for more detailed information about user's sleep.
 *
 * <a href="https://en.wikipedia.org/wiki/Sleep">More info</a>
 */
public class SleepRecord extends Record {
    public static final int CATEGORY = 302;

    @NonNull
    private final List<SleepStage> sleepStages = new ArrayList<>();

    public SleepRecord(Date begin, Date end) {
        this(UNSET_UID, begin, end);
    }

    public SleepRecord(long uid, Date begin, Date end) {
        super(uid, begin, end, CATEGORY);
    }

    /** @hide */
    public SleepRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    protected void writeValueTo(@NonNull Parcel dest) {
        // Number of stages
        dest.writeInt(sleepStages.size());

        for (SleepStage stage : sleepStages) {
            dest.writeString(stage.toString());
        }
    }

    @Override
    protected void readValueFrom(Parcel parcel) {
        int size = parcel.readInt();
        if (size <= 0) {
            return;
        }

        for (int i = 0; i < size; i++) {
            final SleepStage stage = SleepStage.fromString(parcel.readString());
            if (stage != null) {
                sleepStages.add(stage);
            }
        }
    }

    @NonNull
    public List<SleepStage> getSleepStages() {
        return sleepStages;
    }

    public void addSleepStage(@NonNull SleepStage stage) {
        sleepStages.add(stage);
    }

    public void removeSleepStage(@NonNull SleepStage stage) {
        sleepStages.remove(stage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SleepRecord that = (SleepRecord) o;
        return sleepStages.equals(that.sleepStages);
    }

    /**
     * Represent a sleep stage.
     *
     * Multiple sleep stages may be added to a record.
     */
    public static class SleepStage {
        public static final int SLEEP_LIGHT = 0;
        public static final int SLEEP_DEEP = 1;
        public static final int SLEEP_REM = 2;
        public static final int SLEEP_AWAKE = 3;

        private final int category;
        @NonNull
        private final Date from;
        @NonNull
        private final Date to;

        public SleepStage(int category, @NonNull Date from, @NonNull Date to) {
            this.category = category;
            this.from = from;
            this.to = to;
        }

        /**
         * Build from a string (for serialization)
         *
         * @param str Expected format: <pre>id,fromMillis,toMillis</pre>.
         * @see #toString()
         */
        @Nullable
        public static SleepStage fromString(String str) {
            final String[] split = str.split(",");
            if (split.length != 3) {
                return null;
            }

            int category = Integer.parseInt(split[0]);
            long from = Long.parseLong(split[1]);
            long to = Long.parseLong(split[2]);

            return new SleepStage(category, new Date(from), new Date(to));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SleepStage other = (SleepStage) o;
            return other.category == category &&
                other.from.getTime() == from.getTime() &&
                other.to.getTime() == to.getTime();
        }

        @Override
        public String toString() {
            return String.format("%1$d,%2$d,%3$d", category,
                from.getTime(), to.getTime());
        }
    }
}
