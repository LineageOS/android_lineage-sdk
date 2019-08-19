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

import lineageos.health.records.SimpleIntRecord;

import java.util.Date;
import java.util.Objects;

/**
 * Heart rate measured in Beats Per Minute (BPM).
 *
 * <a href="https://en.wikipedia.org/wiki/Heart_rate">More info</a>
 */
public final class HeartRateRecord extends SimpleIntRecord {
    public static final int CATEGORY = 3; // 0-03

    public HeartRateRecord(Date time, int value) {
        this(UNSET_UID, time, value);
    }

    public HeartRateRecord(long uid, Date time, int value) {
        super(uid, time, CATEGORY, value);
    }

    /** @hide */
    public HeartRateRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    public String getSymbol() {
        return "BPM";
    }
}
