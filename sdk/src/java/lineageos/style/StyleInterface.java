/**
 * Copyright (c) 2015, The CyanogenMod Project
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

package lineageos.style;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import lineageos.app.LineageContextConstants;

public class StyleInterface {

    /**
     * Global style: automatic (based on wallpaper) mode
     *
     * @see #setGlobalStyle
     */
    public static final int STYLE_GLOBAL_AUTO_WALLPAPER = 0;

    /**
     * Global style: automatic (based on day time) mode
     *
     * @see #setGlobalStyle
     */
    public static final int STYLE_GLOBAL_AUTO_DAYTIME = 1;

    /**
     * Global style: light
     *
     * @see #setGlobalStyle
     */
    public static final int STYLE_GLOBAL_LIGHT = 2;

    /**
     * Global style: dark
     *
     * @see #setGlobalStyle
     */
    public static final int STYLE_GLOBAL_DARK = 3;

    /**
     * Default accent
     * Used to remove any active accent and use default one
     *
     * @see #setAccent
     */
    public static final String ACCENT_DEFAULT = "lineageos";

    /**
     * Allows an application to change system style.
     * This is a dangerous permission, your app must request
     * it at runtime as any other dangerous permission
     */
    public static final String CHANGE_STYLE_SETTINGS_PERMISSION =
            "lineageos.permission.CHANGE_STYLE";

    private static final String TAG = "StyleInterface";

    private static IStyleInterface sService;
    private static StyleInterface sInstance;

    private Context mContext;

    private StyleInterface(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }
        sService = getService();
        if (context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.STYLES) && sService == null) {
            throw new RuntimeException("Unable to get StyleInterfaceService. The service" +
                    " either crashed, was not started, or the interface has been called to early" +
                    " in SystemServer init");
            }
    }

    /**
     * Get or create an instance of the {@link lineageos.app.StyleInterface}
     * @param context
     * @return {@link StyleInterface}
     */
    public static StyleInterface getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new StyleInterface(context);
        }
        return sInstance;
    }

    /** @hide **/
    public static IStyleInterface getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_STYLE_INTERFACE);
        sService = IStyleInterface.Stub.asInterface(b);

        if (b != null) {
            sService = IStyleInterface.Stub.asInterface(b);
            return sService;
        } else {
            Log.e(TAG, "null service. SAD!");
            return null;
        }
    }

    /**
     * Set global style.
     *
     * You will need {@link #CHANGE_STYLE_SETTINGS_PERMISSION}
     * to utilize this functionality.
     *
     * @see #STYLE_GLOBAL_AUTO_WALLPAPER
     * @see #STYLE_GLOBAL_AUTO_DAYTIME
     * @see #STYLE_GLOBAL_LIGHT
     * @see #STYLE_GLOBAL_DARK
     * @param style The style mode to set the device to.
     *             One of {@link #STYLE_GLOBAL_AUTO_WALLPAPER},
     *             {@link #STYLE_GLOBAL_AUTO_DAYTIME},
     *             {@link #STYLE_GLOBAL_LIGHT} or
     *             {@link #STYLE_GLOBAL_DARK}
     */
    public boolean setGlobalStyle(int style) {
        if (sService == null) {
            return false;
        }
        try {
            return sService.setGlobalStyle(style);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return false;
    }

    /**
     * Set color accent package.
     *
     * You will need {@link #CHANGE_STYLE_SETTINGS_PERMISSION}
     * to utilize this functionality.
     *
     * @param pkgName The package name of the accent
     */
    public boolean setAccent(String pkgName) {
        if (sService == null) {
            return false;
        }
        try {
            return sService.setAccent(pkgName);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return false;
    }

    /**
     * Get the best color that suites a bitmap object and the appropriate global style
     *
     * @param source The object you want the suggested color to be matched with
     * @param colors A list of colors that the selection will be made from
     *
     * @return suggestion for global style + accent combination
     */
    public Suggestion getSuggestion(Bitmap source, int[] colors) {
        if (colors.length == 0) {
            throw new IllegalArgumentException(
                    "The colors array argument must contain at least one element");
        }
        Suggestion fallback = new Suggestion(STYLE_GLOBAL_LIGHT, colors[0]);

        if (sService == null) {
            return fallback;
        }
        try {
            return sService.getSuggestion(source, colors);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return fallback;
    }
}