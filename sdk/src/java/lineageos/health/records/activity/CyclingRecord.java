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

import lineageos.health.records.SimpleFloatRecord;

/**
 * Cycling distance record in km.
 *
 * <a href="https://en.wikipedia.org/wiki/Cycling">More info</a>
 */
public class CyclingRecord extends SimpleFloatRecord {
    public static final int CATEGORY = 400;

    public CyclingRecord(long time, long duration, float value) {
        this(UNSET_UID, time, duration, value);
    }

    public CyclingRecord(long uid, long time, long duration, float value) {
        super(uid, time, duration, CATEGORY, value);
    }

    /** @hide */
    public CyclingRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    public String getSymbol() {
        return "km";
    }
}
