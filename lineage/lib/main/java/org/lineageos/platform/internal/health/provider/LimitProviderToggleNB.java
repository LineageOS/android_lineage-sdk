/*
 * Copyright (C) 2023 The LineageOS Project
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

import org.lineageos.platform.internal.R;

import vendor.lineage.health.ChargingControlSupportedMode;
import vendor.lineage.health.IChargingControl;

public class LimitProviderToggleNB extends LimitProviderToggle {
    public LimitProviderToggleNB(IChargingControl chargingControl, Context context) {
        super(chargingControl, context);
        mChargingLimitMargin = mContext.getResources().getInteger(
                R.integer.config_chargingControlBatteryRechargeMargin);
    }

    @Override
    public boolean isSupported() {
        return isModeSupported(ChargingControlSupportedMode.TOGGLE);
    }

    @Override
    public boolean shouldAlwaysMonitorBattery() {
        return true;
    }
}
