/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.util;

import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.text.TextUtils;
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
     * @param title optional custom title to show in GameControllerFragment.
     */
    public static void launchControllerRemapping(Context context, String descriptor, String title) {
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

        String fragmentTitle = TextUtils.isEmpty(title) ? device.getName() : title;

        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.SubSettings");
        intent.putExtra(
                ":settings:show_fragment",
                "com.android.settings.input.gamecontroller.GameControllerFragment");
        intent.putExtra(":settings:show_fragment_args", args);
        intent.putExtra(":settings:show_fragment_title", fragmentTitle);

        context.startActivity(intent);
    }
}
