/*
 * Copyright (c) 2019 The LineageOS Project
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

package org.lineageos.platform.internal.health;

import android.annotation.NonNull;

import lineageos.health.Record;
import lineageos.health.records.activity.*;
import lineageos.health.records.blood.*;
import lineageos.health.records.body.*;
import lineageos.health.records.breathing.*;
import lineageos.health.records.mindfulness.*;

/** @hide */
final class RecordFactory {
    private static final String CATEGORY_ERROR_MSG =
        "%1$d is not a valid category, please refer to " +
        "https://wiki.lineageos.org/sdk/api/health/record.html " +
        "for a list of valid categories.";

    private RecordFactory() {
    }

    /**
     * Now, this _looks_ bad, but it is way more performant
     * than using reflection passing the class as a parameter instead of
     * the category integer.
     * Performance is more important in this situation as
     * we are reading data from storage which is already a slow
     * operation: we don't want additional overhead.
     * It also helps protecting against unknown / invalid data.
     */
    static Record build(int category, @NonNull byte[] source) {
        switch (category) {
            // Hearth & blood
            case BACRecord.CATEGORY:
                return new BACRecord(source);
            case BloodPressureRecord.CATEGORY:
                return new BloodPressureRecord(source);
            case GlucoseRecord.CATEGORY:
                return new GlucoseRecord(source);
            case HeartRateRecord.CATEGORY:
                return new HeartRateRecord(source);
            case PerfusionIndexRecord.CATEGORY:
                return new PerfusionIndexRecord(source);
            // Body
            case AbdominalCircumferenceRecord.CATEGORY:
                return new AbdominalCircumferenceRecord(source);
            case BodyMassIndexRecord.CATEGORY:
                return new BodyMassIndexRecord(source);
            case LeanBodyMassRecord.CATEGORY:
                return new LeanBodyMassRecord(source);
            case MenstrualCycleRecord.CATEGORY:
                return new MenstrualCycleRecord(source);
            case UvIndexRecord.CATEGORY:
                return new UvIndexRecord(source);
            case WaterIntakeRecord.CATEGORY:
                return new WaterIntakeRecord(source);
            case WeightRecord.CATEGORY:
                return new WeightRecord(source);
            // Breathing
            case InhalerUsageRecord.CATEGORY:
                return new InhalerUsageRecord(source);
            case OxygenSaturationRecord.CATEGORY:
                return new OxygenSaturationRecord(source);
            case PeakExpiratoryFlowRecord.CATEGORY:
                return new PeakExpiratoryFlowRecord(source);
            case RespiratoryRateRecord.CATEGORY:
                return new RespiratoryRateRecord(source);
            case VitalCapacityRecord.CATEGORY:
                return new VitalCapacityRecord(source);
            // Mindfulness
            case MeditationRecord.CATEGORY:
                return new MeditationRecord(source);
            case MoodRecord.CATEGORY:
                return new MoodRecord(source);
            case SleepRecord.CATEGORY:
                return new SleepRecord(source);
            // Activity
            case CyclingRecord.CATEGORY:
                return new CyclingRecord(source);
            case RunningRecord.CATEGORY:
                return new RunningRecord(source);
            case StepsRecord.CATEGORY:
                return new StepsRecord(source);
            case WorkoutRecord.CATEGORY:
                return new WorkoutRecord(source);
            default:
                throw new IllegalArgumentException(
                    String.format(CATEGORY_ERROR_MSG, category));
        }
    }
}
