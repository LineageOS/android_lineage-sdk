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
package lineageos.health.records.body;

import lineageos.health.Record;

/**
 * Record an intake of a glass of water.
 *
 * <a href="https://en.wikipedia.org/wiki/Drinking_water">More info</a>
 */
public class WaterIntakeRecord extends Record {
    public static final int CATEGORY = 106;

    public WaterIntakeRecord(long time) {
        this(UNSET_UID, time);
    }

    public WaterIntakeRecord(long uid, long time) {
        super(uid, time, CATEGORY);
    }

    /** @hide */
    public WaterIntakeRecord(byte[] bytes) {
        super(bytes);
    }
}
