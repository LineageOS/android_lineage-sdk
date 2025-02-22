/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.health;

/** @hide */
interface IHealthInterface {
    boolean isChargingControlSupported();

    boolean getChargingControlEnabled();
    boolean setChargingControlEnabled(boolean enabled);

    int getChargingControlMode();
    boolean setChargingControlMode(int mode);

    int getChargingControlStartTime();
    boolean setChargingControlStartTime(int time);

    int getChargingControlTargetTime();
    boolean setChargingControlTargetTime(int time);

    int getChargingControlLimit();
    boolean setChargingControlLimit(int limit);

    boolean resetChargingControl();
    boolean allowFineGrainedSettings();

    int getChargingControlStatus();
}
