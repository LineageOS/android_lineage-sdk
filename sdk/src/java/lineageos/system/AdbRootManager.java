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

import android.os.RemoteException;
import android.os.ServiceManager;

import lineageos.app.LineageContextConstants;
import lineageos.system.IAdbRootService;

/** @hide */
public class AdbRootManager {
    private IAdbRootService mService;

    private IAdbRootService getService() {
        synchronized (this) {
            if (mService != null) {
                return mService;
            }
            mService = IAdbRootService.Stub.asInterface(
                    ServiceManager.getService(LineageContextConstants.LINEAGE_ADBROOT_SERVICE));
            return mService;
        }
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
