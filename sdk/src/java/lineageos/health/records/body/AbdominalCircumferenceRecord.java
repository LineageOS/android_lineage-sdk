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

import lineageos.health.records.SimpleFloatRecord;

/**
 * Abdominal circumference in cm.
 *
 * <a href="https://en.wikipedia.org/wiki/Abdominal_obesity#diagnosis">More info</a>
 */
public final class AbdominalCircumferenceRecord extends SimpleFloatRecord {
    public static final int CATEGORY = 100;

    public AbdominalCircumferenceRecord(long time, float value) {
        this(UNSET_UID, time, value);
    }

    public AbdominalCircumferenceRecord(long uid, long time, float value) {
        super(uid, time, CATEGORY, value);
    }

    /** @hide */
    public AbdominalCircumferenceRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    public String getSymbol() {
        return "cm";
    }
}
