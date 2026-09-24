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

    private static final String INPUT_DEVICE_ID = "input_device_identifier";
    private static final String SETTINGS = "com.android.settings";
    private static final String SUB_SETTINGS = "com.android.settings.SubSettings";
    private static final String EXTRA_SHOW_FRAGMENT = ":settings:show_fragment";
    private static final String GAME_CONTROLLER_FRAGMENT =
            "com.android.settings.input.gamecontroller.GameControllerFragment";
    private static final String EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args";

    private ControllerUtils() {
        // This class is not supposed to be instantiated
    }

    /**
     * Launches GameControllerFragment for the device matching the given descriptor.
     *
     * @param context the current context, used to launch the activity.
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
        args.putParcelable(INPUT_DEVICE_ID, device.getIdentifier());

        Intent intent = new Intent()
                .setClassName(SETTINGS, SUB_SETTINGS)
                .putExtra(EXTRA_SHOW_FRAGMENT, GAME_CONTROLLER_FRAGMENT)
                .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, args);

        context.startActivity(intent);
    }
}
