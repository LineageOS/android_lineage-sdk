/*
 * Copyright (C) 2019 The LineageOS Project
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

package lineageos.system;

import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import lineageos.app.LineageContextConstants;
import lineageos.system.IAdbRootService;

/** @hide */
public class AdbRootManager {
    private static final String TAG = "AdbRootManager";
    private IAdbRootService mService;
    private final IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            if (mService != null) {
                mService.asBinder().unlinkToDeath(this, 0);
            }
            mService = null;
        }
    };

    private synchronized IAdbRootService getService()
            throws RemoteException {
        if (mService != null) {
            return mService;
        }

        final IBinder service = ServiceManager.getService(
                LineageContextConstants.LINEAGE_ADBROOT_SERVICE);
        if (service != null) {
            service.linkToDeath(mDeathRecipient, 0);
            mService = IAdbRootService.Stub.asInterface(service);
            return mService;
        }

        Slog.e(TAG, "Unable to acquire AdbRootService");
        return null;
    }

    public void setEnabled(boolean enabled) {
        try {
            final IAdbRootService svc = getService();
            if (svc != null) {
                svc.setEnabled(enabled);
            }
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    public boolean getEnabled() {
        try {
            final IAdbRootService svc = getService();
            if (svc != null) {
                return svc.getEnabled();
            }
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
        return false;
    }
}
