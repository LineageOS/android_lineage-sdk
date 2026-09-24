/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.util;

import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;

public final class ControllerUtils {
    private static final String TAG = "ControllerUtils";

    private ControllerUtils() {
        // This class is not supposed to be instantiated
    }

    /**
     * Launches GameControllerFragment for the device matching the given descriptor.
     *
     * @param context the current context, used to retrieve the package manager.
     * @param descriptor the SHA-1 descriptor of the target input device.
     */
    public static void launchControllerRemapping(Context context, String descriptor) {
        InputManager inputManager = context.getSystemService(InputManager.class);
        if (inputManager == null) {
            Log.e(TAG, "InputManager service not available");
            return;
        }

        InputDevice device = inputManager.getInputDeviceByDescriptor(descriptor);
        if (device == null) {
            Log.w(TAG, "No device found for descriptor: " + descriptor);
            return;
        }

        Bundle args = new Bundle();
        args.putParcelable("input_device_identifier", device.getIdentifier());

        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.SubSettings");
        intent.putExtra(
                ":settings:show_fragment",
                "com.android.settings.input.gamecontroller.GameControllerFragment");
        intent.putExtra(":settings:show_fragment_args", args);

        context.startActivity(intent);
    }
}
