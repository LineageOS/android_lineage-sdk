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

import lineageos.health.records.SimpleIntRecord;

/**
 * Respiratory rate measured in breaths per minute.
 *
 * <a href="https://en.wikipedia.org/wiki/Respiratory_rate">More info</a>
 */
public class RespiratoryRateRecord extends SimpleIntRecord {
    public static final int CATEGORY = 203;

    public RespiratoryRateRecord(long time, int value) {
        this(UNSET_UID, time, value);
    }

    public RespiratoryRateRecord(long uid, long time, int value) {
        super(uid, time, CATEGORY, value);
    }

    /** @hide */
    public RespiratoryRateRecord(byte[] bytes) {
        super(bytes);
    }
}
