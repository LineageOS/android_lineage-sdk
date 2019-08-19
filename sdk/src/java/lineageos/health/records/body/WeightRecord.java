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
 * Weight in N.
 *
 * <b>Not to be confused with mass (kg)</b>.
 * Use {@link #kgToN(float)} in the constructor to
 * compute the mass from mass on Earth.
 *
 * Support converting to mass in kg on the following celestial bodies:
 * <ul>
 * <li>Earth</li>
 * <li>Moon</li>
 * <li>Mars</li>
 * </ul>
 *
 * If you are using this API on an unsupported celestial body,
 * please do your math.
 *
 * <a href="https://en.wikipedia.org/wiki/Weight">More info</a>
 */
public class WeightRecord extends SimpleFloatRecord {
    public static final int CATEGORY = 106;
    private static final float CONST_G_EARTH = 9.80665f;
    private static final float CONST_G_MOON = 1.62452f;
    private static final float CONST_G_MARS = 3.71198f;

    public WeightRecord(long time, float value) {
        this(UNSET_UID, time, value);
    }

    public WeightRecord(long uid, long time, float value) {
        super(uid, time, CATEGORY, value);
    }

    /** @hide */
    public WeightRecord(byte[] bytes) {
        super(bytes);
    }

    @Override
    public String getSymbol() {
        return "N";
    }

    public static float kgToN(float kg) {
        return kg * CONST_G_EARTH;
    }

    public float getMass() {
        return value / CONST_G_EARTH;
    }

    /*
     * These will become handy to all of those who will
     * be using this API in their space adventures.
     *
     * "Future-proofing"
     */

    public float getMassOnMoon() {
        return value / CONST_G_EARTH * CONST_G_MOON;
    }

    public float getMassOnMars() {
        return value / CONST_G_EARTH * CONST_G_MARS;
    }
}
