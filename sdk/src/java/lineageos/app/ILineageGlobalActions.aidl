/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.app;

/** @hide */
interface ILineageGlobalActions {

    void updateUserConfig(boolean enabled, String action);

    List<String> getLocalUserConfig();

    String[] getUserActionsArray();

    boolean userConfigContains(String preference);
}
