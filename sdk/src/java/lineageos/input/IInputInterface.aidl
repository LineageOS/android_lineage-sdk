/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.input;

import lineageos.input.IAccessory;
import lineageos.input.IAccessoriesCallback;

/** @hide */
interface IInputInterface {
    // Accessories

    /**
     * Get the list of persistent accessories.
     *
     * @return An array of {@link lineageos.input.IAccessory}
     */
    IAccessory[] getAccessories();

    void registerCallback(IAccessoriesCallback callback);
}
