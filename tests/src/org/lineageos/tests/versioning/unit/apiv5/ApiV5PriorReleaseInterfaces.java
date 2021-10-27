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

package org.lineageos.tests.versioning.unit.apiv5;

import java.util.HashMap;
import java.util.Map;

public class ApiV5PriorReleaseInterfaces {
    private static Map<String, Map<String, Integer>> mApiMethodsAndValues =
            new HashMap<String, Map<String, Integer>>();

    //ExternalViewProviderFactory Aidl (IExternalViewProviderFactory)
    static {
        Map<String, Integer> extProviderMap =
                getInternalInterfaceMap("IExternalViewProviderFactory");
        // DRAGONFRUIT TO 1
        // ELDERBERRY BEGIN
    }

    //ExternalViewProvider Aidl (IExternalViewProvider)
    static {
        Map<String, Integer> extViewProviderMap =
                getInternalInterfaceMap("IExternalViewProvider");
        // DRAGONFRUIT TO 7
        // ELDERBERRY BEGIN
    }

    //LineageAudioManager Aidl (ILineageAudioService)
    static {
        Map<String, Integer> lineageAudioService =
                getInternalInterfaceMap("ILineageAudioService");
        //ELDERBERRY BEGIN
        lineageAudioService.put("listAudioSessions", 1);
    }

    //RequestInfoListener Aidl (IRequestInfoListener)
    static {
        Map<String, Integer> requestInfoListener =
                getInternalInterfaceMap("IRequestInfoListener");
        //ELDERBERRY BEGIN
        requestInfoListener.put("onLookupCityRequestCompleted ", 1);
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
