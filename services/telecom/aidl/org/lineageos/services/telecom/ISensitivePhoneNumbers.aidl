/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.services.telecom;

import org.lineageos.services.telecom.Item;

interface ISensitivePhoneNumbers {
    const String SERVICE_NAME = "lineagesensitivephone";

    boolean isSensitiveNumber(String number, int subId);

    List<Item> getSensitivePnInfosForMcc(String mcc);
}
