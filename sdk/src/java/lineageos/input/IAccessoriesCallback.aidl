/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.input;

import lineageos.input.IAccessory;

interface IAccessoriesCallback {
    void onAccessoriesChanged(in IAccessory[] accessories);
}
