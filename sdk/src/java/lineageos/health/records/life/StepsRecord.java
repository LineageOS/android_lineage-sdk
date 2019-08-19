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

package lineageos.health.records.life;

import lineageos.health.records.SimpleIntRecord;

/**
 * Steps count record.
 *
 * <a href="https://en.wikipedia.org/wiki/Walking">More info</a>
 */
public class StepsRecord extends SimpleIntRecord {
    public static final int CATEGORY = 302;

    public StepsRecord(long time, long duration, int value) {
        this(UNSET_UID, time, duration, value);
    }

    public StepsRecord(long uid, long time, long duration, int value) {
        super(uid, time, duration, CATEGORY, value);
    }

    /** @hide */
    public StepsRecord(byte[] bytes) {
        super(bytes);
    }
}
