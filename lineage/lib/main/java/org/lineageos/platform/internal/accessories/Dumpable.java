/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories;

import java.io.PrintWriter;

public interface Dumpable {
    void dump(PrintWriter pw);
}
