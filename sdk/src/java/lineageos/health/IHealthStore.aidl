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

package lineageos.health;

import lineageos.health.MedicalProfile;
import lineageos.health.Record;

import java.util.List;

/** {@hide} */
interface IHealthStore {
    boolean writeRecord(in Record record);
    boolean deleteRecord(in Record record);
    Record getRecord(long uid);
    List<Record> getRecords(int category);
    List<Record> getRecordsInTimeFrame(int category, long begin, long end);

    void setMedicalProfile(in MedicalProfile profile);
    MedicalProfile getMedicalProfile();

    boolean deleteAllInCategory(int category);
    boolean deleteAllData();

    boolean blackListApp(String pkgName, int category);
    boolean whiteListApp(String pkgName, int category);
    boolean isBlackListed(String pkgName, int category);
}
