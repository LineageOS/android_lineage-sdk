/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
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
    private final Context mContext;

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
        } catch (IllegalAccessException | NoSuchMethodException ex) {
            throw new RuntimeException("Failed to create service " + serviceClass
                    + ": service must have a public constructor with a Context argument", ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Failed to create service " + serviceClass
                    + ": service constructor threw an exception", ex);
        }
        return service;
    }
}
