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
 * Body temperature in °C.
 *
 * <a href="https://en.wikipedia.org/wiki/Thermoregulation">More info</a>
 */
public class BodyTemperatureRecord extends SimpleFloatRecord {
    public static int CATEGORY = 103;

    public BodyTemperatureRecord(long time, float value) {
        super(time, value);
    }

    public BodyTemperatureRecord(long uid, long time, float value) {
        super(uid, time, value);
    }

    @Override
    public int getCategory() {
        return CATEGORY;
    }

    @Override
    public String getSymbol() {
        return "\u00B0C";
    }

    /**
     * Convert from °C to °F.
     *
     * <a href="https://en.wikipedia.org/wiki/Fahrenheit#Definition_and_conversion">More info</a>
     */
    public static float convertToF(float c) {
        return (c * 1.8f) + 32;
    }

    /**
     * Convert from °F to °C.
     *
     * <a href="https://en.wikipedia.org/wiki/Fahrenheit#Definition_and_conversion">More info</a>
     */
    public static float convertToC(float f) {
        return (f - 32) * 5 / 9;
    }
}
