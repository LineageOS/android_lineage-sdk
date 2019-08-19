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

import java.util.Date;

import lineageos.health.records.SimpleFloatRecord;

/**
 * Oxygen saturation in percentage (0 - 100).
 *
 * <a href="https://en.wikipedia.org/wiki/Oxygen_saturation_(medicine)">More info</a>
 */
public class OxygenSaturationRecord extends SimpleFloatRecord {
    public static final int CATEGORY = 201;

    public OxygenSaturationRecord(Date time, float value) {
        this(UNSET_UID, time, value);
    }

    public OxygenSaturationRecord(long uid, Date time, float value) {
        super(uid, time, CATEGORY, value);
    }

    /** @hide */
    public OxygenSaturationRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    public String getSymbol() {
        return "%";
    }

    @Override
    public boolean validate() {
        return value >= 0f && value <= 100f && super.validate();
    }
}
