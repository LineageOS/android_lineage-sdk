/*
 * SPDX-FileCopyrightText: 2018-2026 The LineageOS project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.statusbar;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;

import java.util.ArrayList;

public class LineageStatusBarItem {

    public interface Manager {
        void addDarkReceiver(DarkReceiver darkReceiver);
        void removeDarkReceiver(DarkReceiver darkReceiver);
        void addVisibilityReceiver(VisibilityReceiver visibilityReceiver);
        void removeVisibilityReceiver(VisibilityReceiver visibilityReceiver);
    }

    public interface DarkReceiver {
        void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint);
        void setFillColors(int darkColor, int lightColor);
    }

    public interface VisibilityReceiver {
        void onVisibilityChanged(boolean isVisible);
    }

    // Locate parent LineageStatusBarItem.Manager
    public static Manager findManager(View v) {
        ViewParent parent = v.getParent();
        if (parent == null) {
            return null;
        } else if (parent instanceof Manager) {
            return (Manager) parent;
        } else if (parent instanceof View) {
            return findManager((View) parent);
        } else {
            return null;
        }
    }
}
