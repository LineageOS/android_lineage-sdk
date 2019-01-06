/**
 * Copyright (c) 2016, The CyanogenMod Project
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

package org.lineageos.tests.versioning.unit.apiv4;

import java.util.HashMap;
import java.util.Map;

public class ApiV4PriorReleaseInterfaces {
    private static Map<String, Map<String, Integer>> mApiMethodsAndValues =
            new HashMap<String, Map<String, Integer>>();

    //Profiles Aidl (IProfileManager)
    static {
        Map<String, Integer> profilesMap = getInternalInterfaceMap("IProfileManager");
        // APRICOT + BOYSENBERRY + CANTALOUPE to 19
        // DRAGONFRUIT BEGIN
        profilesMap.put("isEnabled", 20);
    }

    //LineageHardwareManager Aidl (ILineageHardwareService)
    static {
        Map<String, Integer> hardwareMap = getInternalInterfaceMap("ILineageHardwareService");
        // APRICOT + BOYSENBERRY + CANTALOUPE to 24
        // DRAGONFRUIT BEGIN
        hardwareMap.put("isSunlightEnhancementSelfManaged", 25);
        hardwareMap.put("getUniqueDeviceId", 26);
    }

    //LineageStatusBarManager Aidl (ILineageStatusBarManager)
    static {
        // APRICOT + BOYSENBERRY + CANTALOUPE to 5
        // DRAGONFRUIT BEGIN
    }

    //AppSuggestManager Aidl (IAppSuggestManager)
    static {
        // APRICOT + BOYSENBERRY + CANTALOUPE to 2
        // DRAGONFRUIT BEGIN
    }

    //LineageTelephonyManager Aidl (ILineageTelephonyManager)
    static {
        // APRICOT + BOYSENBERRY + CANTALOUPE to 9
        // DRAGONFRUIT BEGIN
    }

    //PerformanceManager Aidl (IPerformanceManager)
    static {
        Map<String, Integer> perfMap = getInternalInterfaceMap("IPerformanceManager");
        // DRAGONFRUIT BEGIN
        perfMap.put("cpuBoost", 1);
        perfMap.put("setPowerProfile", 2);
        perfMap.put("getPowerProfile", 3);
        perfMap.put("getNumberOfProfiles", 4);
        perfMap.put("getProfileHasAppProfiles", 5);
    }

    //ExternalViewProviderFactory Aidl (IExternalViewProviderFactory)
    static {
        Map<String, Integer> extProviderMap =
                getInternalInterfaceMap("IExternalViewProviderFactory");
        // DRAGONFRUIT BEGIN
        extProviderMap.put("createExternalView", 1);
    }

    //ExternalViewProvider Aidl (IExternalViewProvider)
    static {
        Map<String, Integer> extViewProviderMap =
                getInternalInterfaceMap("IExternalViewProvider");
        // DRAGONFRUIT BEGIN
        extViewProviderMap.put("onAttach", 1);
        extViewProviderMap.put("onStart", 2);
        extViewProviderMap.put("onResume", 3);
        extViewProviderMap.put("onPause", 4);
        extViewProviderMap.put("onStop", 5);
        extViewProviderMap.put("onDetach", 6);
        extViewProviderMap.put("alterWindow", 7);
    }

    protected static Map<String, Integer> getInternalInterfaceMap(String targetInterface) {
        Map<String, Integer> internalMap = mApiMethodsAndValues.get(targetInterface);
        if (internalMap == null) {
            internalMap = new HashMap<String, Integer>();
            mApiMethodsAndValues.put(targetInterface, internalMap);
            return internalMap;
        }
        return internalMap;
    }

    public static Map<String, Map<String, Integer>> getInterfaces() {
        return mApiMethodsAndValues;
    }
}
