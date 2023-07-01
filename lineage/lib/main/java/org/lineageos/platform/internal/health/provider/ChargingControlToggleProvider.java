/*
 * Copyright (C) 2023 The Android Open Source Project
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

package org.lineageos.platform.internal.health.provider;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import vendor.lineage.health.IChargingControl;

public abstract class ChargingControlToggleProvider extends ChargingControlProvider {

    ChargingControlToggleProvider(IChargingControl chargingControl,
            Context context) {
        super(chargingControl, context);
    }

    protected boolean setChargingEnabled(boolean enabled) {
        final boolean isChargingEnabled;

        if (mChargingControl == null) {
            return false;
        }

        try {
            isChargingEnabled = mChargingControl.getChargingEnabled();
        } catch (IllegalStateException | RemoteException | UnsupportedOperationException e) {
            Log.e(TAG, "Failed to get charging enabled status!");
            return false;
        }

        if (isChargingEnabled != enabled) {
            try {
                mChargingControl.setChargingEnabled(!isChargingEnabled);
            } catch (IllegalStateException | RemoteException | UnsupportedOperationException e) {
                Log.e(TAG, "Failed to set charging status");
                return false;
            }
        }

        return true;
    }
}
