/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.services.telecom;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.ServiceManager;

import java.util.List;

public class SensitivePhoneNumbersService extends Service {
    private final IBinder mBinder = new ISensitivePhoneNumbers.Stub() {
        @Override
        public boolean isSensitiveNumber(String number, int subId) {
            final long token = Binder.clearCallingIdentity();
            try {
                return SensitivePhoneNumbers.getInstance()
                        .isSensitiveNumber(SensitivePhoneNumbersService.this, number, subId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public List<Item> getSensitivePnInfosForMcc(String mcc) {
            final long token = Binder.clearCallingIdentity();
            try {
                return SensitivePhoneNumbers.getInstance().getSensitivePnInfosForMcc(mcc);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ServiceManager.addService(ISensitivePhoneNumbers.SERVICE_NAME, mBinder);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
