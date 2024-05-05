/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.input

import java.lang.ref.WeakReference
import lineageos.input.IAccessoryCallback
import vendor.lineage.input.IAccessory

class Accessory private constructor(
    private val accessoryInterface: WeakReference<IAccessory>,
) {
    // Accessory callback
    private val accessoryCallbacks: WeakReference<IAccessoryCallback>? = null
    companion object {
        fun from(accessoryInterface: IAccessory) = Accessory(WeakReference(accessoryInterface))
    }
}
