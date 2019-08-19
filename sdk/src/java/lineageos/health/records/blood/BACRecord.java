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

import lineageos.health.records.SimpleFloatRecord;

/**
 * Blood alcohol content (BAC) measured in g/dL (% by vol).
 *
 * <a href="https://en.wikipedia.org/wiki/Blood_alcohol_content">More info</a>
 */
public class BACRecord extends SimpleFloatRecord {
    public static final int CATEGORY = 3; // 0-03

    public BACRecord(long time, float value) {
        super(UNSET_UID, time, value);
    }

    public BACRecord(long uid, long time, float value) {
        super(uid, time, value);
    }

    @Override
    public String getSymbol() {
        return "%";
    }
}
