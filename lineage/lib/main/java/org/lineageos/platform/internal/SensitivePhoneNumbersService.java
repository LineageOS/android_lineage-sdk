/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;

import lineageos.app.LineageContextConstants;
import com.android.server.telecom.ISensitivePhoneNumbers;

import org.lineageos.lib.phone.SensitivePhoneNumbers;

/** @hide */
public class SensitivePhoneNumbersService extends LineageSystemService {
    private final Context mContext;

    public SensitivePhoneNumbersService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public String getFeatureDeclaration() {
        return null;
    }

    @Override
    public void onStart() {
        publishBinderService(LineageContextConstants.LINEAGE_SENSITIVE_PHONE_NUMBERS_SERVICE, mService);
    }

    private final IBinder mService = new ISensitivePhoneNumbers.Stub() {
        @Override
        public boolean isSensitiveNumber(String number, int subId) {
            final long token = Binder.clearCallingIdentity();
            try {
                return SensitivePhoneNumbers.getInstance()
                        .isSensitiveNumber(mContext, number, subId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };
}

