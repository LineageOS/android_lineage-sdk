/*
 * SPDX-FileCopyrightText: 2017 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.services.telecom;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber;

import org.lineageos.lib.phone.spn.SensitivePN;
import org.lineageos.lib.phone.spn.XmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.datatype.DatatypeConfigurationException;

class SensitivePhoneNumbers {
    private final String LOG_TAG = getClass().getSimpleName();

    private static final String SENSITIVE_PHONE_NUMBERS_FILE_PATH =
            "/product/etc/sensitive_pn.xml";

    private static SensitivePhoneNumbers sInstance = null;
    private static boolean sNumbersLoaded;

    private final HashMap<String, ArrayList<org.lineageos.lib.phone.spn.Item>>
            mSensitiveNumbersMap = new HashMap<>();

    private SensitivePhoneNumbers() { }

    public static SensitivePhoneNumbers getInstance() {
        if (sInstance == null) {
            sInstance = new SensitivePhoneNumbers();
        }
        return sInstance;
    }

    private void loadSensitivePhoneNumbers() {
        if (sNumbersLoaded) {
            return;
        }

        File sensitivePhoneNumbersFile = new File(SENSITIVE_PHONE_NUMBERS_FILE_PATH);
        FileInputStream sensitivePhoneNumbersInputStream;

        try {
            sensitivePhoneNumbersInputStream = new FileInputStream(sensitivePhoneNumbersFile);
        } catch (FileNotFoundException e) {
            Log.w(LOG_TAG, "Can not open " + sensitivePhoneNumbersFile.getAbsolutePath());
            return;
        }

        try {
            for (SensitivePN sensitivePN : new XmlParser()
                    .read(sensitivePhoneNumbersInputStream).getSensitivePN()) {
                String[] mccs = sensitivePN.getNetwork().split(",");
                for (String mcc : mccs) {
                    mSensitiveNumbersMap.put(mcc, new ArrayList<>(sensitivePN.getItem()));
                }
            }
        } catch (DatatypeConfigurationException | IOException | XmlPullParserException e) {
            Log.w(LOG_TAG, "Exception in spn-conf parser", e);
        }

        sNumbersLoaded = true;
    }

    public ArrayList<Item> getSensitivePnInfosForMcc(String mcc) {
        loadSensitivePhoneNumbers();
        ArrayList<Item> result = new ArrayList<>();
        for (org.lineageos.lib.phone.spn.Item item : mSensitiveNumbersMap.getOrDefault(mcc,
                new ArrayList<org.lineageos.lib.phone.spn.Item>())) {
            result.add(new Item(item.getNumber(), item.getName(), item.getCategories(),
                    item.getLanguages(), item.getOrganization(), item.getWebsite()));
        }
        return result;
    }

    public boolean isSensitiveNumber(Context context, String numberToCheck, int subId) {
        String nationalNumber = formatNumberToNational(context, numberToCheck);
        if (TextUtils.isEmpty(nationalNumber)) {
            return false;
        }
        loadSensitivePhoneNumbers();

        SubscriptionManager subManager = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> list = subManager.getActiveSubscriptionInfoList();
        if (list != null) {
            // Test all subscriptions so an accidential use of a wrong sim also hides the number
            for (SubscriptionInfo subInfo : list) {
                String mcc = String.valueOf(subInfo.getMcc());
                if (isSensitiveNumber(nationalNumber, mcc)) {
                    return true;
                }
            }
        }

        // Fall back to check with the passed subId
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            subId = SubscriptionManager.getDefaultSubscriptionId();
        }
        telephonyManager = telephonyManager.createForSubscriptionId(subId);
        String networkUsed = telephonyManager.getNetworkOperator();
        if (!TextUtils.isEmpty(networkUsed)) {
            String networkMCC = networkUsed.substring(0, 3);
            if (isSensitiveNumber(nationalNumber, networkMCC)) {
                return true;
            }
        }

        // Also try the sim's operator
        if (telephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY) {
            String simOperator = telephonyManager.getSimOperator();
            if (!TextUtils.isEmpty(simOperator)) {
                String networkMCC = simOperator.substring(0, 3);
                if (isSensitiveNumber(nationalNumber, networkMCC)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isSensitiveNumber(String numberToCheck, String mcc) {
        if (mSensitiveNumbersMap.containsKey(mcc)) {
            for (org.lineageos.lib.phone.spn.Item item : mSensitiveNumbersMap.get(mcc)) {
                if (PhoneNumberUtils.compare(numberToCheck, item.getNumber())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String formatNumberToNational(Context context, String number) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        String countryIso = context.getResources().getConfiguration().locale.getCountry();

        Phonenumber.PhoneNumber pn = null;
        try {
            pn = util.parse(number, countryIso);
        } catch (NumberParseException e) {
        }

        if (pn != null) {
            return util.format(pn, PhoneNumberFormat.NATIONAL);
        } else {
            return number;
        }
    }
}
