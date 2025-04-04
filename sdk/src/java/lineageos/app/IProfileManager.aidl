/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2025 LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.app;

import android.os.ParcelUuid;

import lineageos.app.NotificationGroup;
import lineageos.app.Profile;

/** {@hide} */
interface IProfileManager
{
    boolean setActiveProfile(in ParcelUuid profileParcelUuid);
    boolean setActiveProfileByName(String profileName);
    Profile getActiveProfile();

    boolean addProfile(in Profile profile);
    boolean removeProfile(in Profile profile);
    void updateProfile(in Profile profile);

    Profile getProfile(in ParcelUuid profileParcelUuid);
    Profile getProfileByName(String profileName);
    Profile[] getProfiles();
    boolean profileExists(in ParcelUuid profileUuid);
    boolean profileExistsByName(String profileName);
    boolean notificationGroupExistsByName(String notificationGroupName);

    NotificationGroup[] getNotificationGroups();
    void addNotificationGroup(in NotificationGroup group);
    void removeNotificationGroup(in NotificationGroup group);
    void updateNotificationGroup(in NotificationGroup group);
    NotificationGroup getNotificationGroupForPackage(in String pkg);
    NotificationGroup getNotificationGroup(in ParcelUuid groupParcelUuid);

    void resetAll();
    boolean isEnabled();
}
