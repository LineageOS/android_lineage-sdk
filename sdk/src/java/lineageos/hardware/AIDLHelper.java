/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.hardware;

import java.util.ArrayList;

class AIDLHelper {
    static TouchscreenGesture[] fromAIDLGestures(
            vendor.lineage.touch.Gesture[] gestures) {
        int size = gestures.length;
        TouchscreenGesture[] r = new TouchscreenGesture[size];
        for (int i = 0; i < size; i++) {
            vendor.lineage.touch.Gesture g = gestures[i];
            r[i] = new TouchscreenGesture(g.id, g.name, g.keycode);
        }
        return r;
    }

    static vendor.lineage.touch.Gesture toAIDLGesture(TouchscreenGesture gesture) {
        vendor.lineage.touch.Gesture g = new vendor.lineage.touch.Gesture();
        g.id = gesture.id;
        g.name = gesture.name;
        g.keycode = gesture.keycode;
        return g;
    }
}
