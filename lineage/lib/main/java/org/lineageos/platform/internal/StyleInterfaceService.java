/*
 * Copyright (c) 2018 The LineageOS Project
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

package org.lineageos.platform.internal;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;

import lineageos.app.LineageContextConstants;
import lineageos.providers.LineageSettings;
import lineageos.style.IStyleInterface;
import lineageos.style.StyleInterface;


/** @hide */
public class StyleInterfaceService extends LineageSystemService {
    private static final String TAG = "LineageStyleInterfaceService";
    private static final String ACCENT_METADATA_COLOR = "lineage_berry_accent_preview";

    private Context mContext;
    private IOverlayManager mOverlayService;
    private PackageManager mPackageManager;

    public StyleInterfaceService(Context context) {
        super(context);
        mContext = context;
        if (context.getPackageManager().hasSystemFeature(LineageContextConstants.Features.STYLES)) {
            publishBinderService(LineageContextConstants.LINEAGE_STYLE_INTERFACE, mService);
        } else {
            Log.wtf(TAG, "Lineage profile service started by system server but feature xml not" +
                    " declared. Not publishing binder service!");
        }
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.STYLES;
    }

    @Override
    public void onStart() {
        mPackageManager = mContext.getPackageManager();
        mOverlayService = IOverlayManager.Stub.asInterface(ServiceManager.getService("overlay"));
    }

    private void enforceChangeStylePermission() {
        mContext.enforceCallingOrSelfPermission(StyleInterface.CHANGE_STYLE_SETTINGS_PERMISSION,
                "You do not have permissions to change system style");
    }

    private boolean setGlobalStyleInternal(int mode) {
        return LineageSettings.System.putInt(mContext.getContentResolver(),
                LineageSettings.System.BERRY_GLOBAL_STYLE, mode);
    }

    private boolean setAccentInternal(String pkgName) {
        if (pkgName != StyleInterface.ACCENT_DEFAULT && !isChangeableOverlay(pkgName)) {
            Log.e(TAG, pkgName + ": is not a valid overlay package");
            return false;
        }

        int userId = UserHandle.myUserId();

        // Disable current accent
        String currentAccent = LineageSettings.System.getString(mContext.getContentResolver(),
                LineageSettings.System.BERRY_CURRENT_ACCENT);
        try {
            mOverlayService.setEnabled(currentAccent, false, userId);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to disable current accent", e);
        }

        // Enable new one
        try {
            mOverlayService.setEnabled(pkgName, true, userId);
            return LineageSettings.System.putString(mContext.getContentResolver(),
                    LineageSettings.System.BERRY_CURRENT_ACCENT, pkgName);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to enable new accent", e);
        }
        return false;
    }

    private boolean isChangeableOverlay(String pkgName) {
        if (pkgName == null) {
            return false;
        }

        try {
            PackageInfo pi = mPackageManager.getPackageInfo(pkgName, 0);
            return pi != null && !pi.isStaticOverlay && isValidAccent(pkgName);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean isValidAccent(String pkgName) {
        try {
            ApplicationInfo ai = mPackageManager.getApplicationInfo(pkgName,
                    PackageManager.GET_META_DATA);
            int color = ai.metaData == null ? -1 :
                    ai.metaData.getInt(ACCENT_METADATA_COLOR, -1);
            return color == -1;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private final IBinder mService = new IStyleInterface.Stub() {
        @Override
        public boolean setGlobalStyle(int style) {
            enforceChangeStylePermission();
            /*
             * We need to clear the caller's identity in order to
             *   allow this method call to modify settings
             *   not allowed by the caller's permissions.
             */
            long token = clearCallingIdentity();
            boolean success = setGlobalStyleInternal(style);
            restoreCallingIdentity(token);
            return success;
        }

        @Override
        public boolean setAccent(String pkgName) {
            enforceChangeStylePermission();
            /*
             * We need to clear the caller's identity in order to
             *   allow this method call to modify settings
             *   not allowed by the caller's permissions.
             */
            long token = clearCallingIdentity();
            boolean success = setAccentInternal(pkgName);
            restoreCallingIdentity(token);
            return success;
        }
    };
}