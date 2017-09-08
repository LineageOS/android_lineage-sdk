/**
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.platform.internal.common;

import android.content.Context;
import org.lineageos.platform.internal.LineageSystemService;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Helper methods for fetching a LineageSystemService from a class declaration
 */
public class LineageSystemServiceHelper {
    private Context mContext;

    public LineageSystemServiceHelper(Context context) {
        mContext = context;
    }

    public LineageSystemService getServiceFor(String className) {
        final Class<LineageSystemService> serviceClass;
        try {
            serviceClass = (Class<LineageSystemService>)Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Failed to create service " + className
                    + ": service class not found", ex);
        }

        return getServiceFromClass(serviceClass);
    }

    public <T extends LineageSystemService> T getServiceFromClass(Class<T> serviceClass) {
        final T service;
        try {
            Constructor<T> constructor = serviceClass.getConstructor(Context.class);
            service = constructor.newInstance(mContext);
        } catch (InstantiationException ex) {
            throw new RuntimeException("Failed to create service " + serviceClass
                    + ": service could not be instantiated", ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Failed to create service " + serviceClass
                    + ": service must have a public constructor with a Context argument", ex);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException("Failed to create service " + serviceClass
                    + ": service must have a public constructor with a Context argument", ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Failed to create service " + serviceClass
                    + ": service constructor threw an exception", ex);
        }
        return service;
    }
}
