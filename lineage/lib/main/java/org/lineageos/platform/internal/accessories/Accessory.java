/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories;

import android.os.RemoteException;
import android.text.TextUtils;

import java.io.PrintWriter;
import java.util.function.Consumer;

import lineageos.accessories.AccessoryInfo;
import lineageos.accessories.AccessoryStatus;
import lineageos.accessories.AccessoryType;
import lineageos.accessories.BatteryInfo;
import lineageos.accessories.BatteryStatus;
import lineageos.accessories.DataLinkInfo;
import lineageos.accessories.DataLinkType;
import lineageos.accessories.IAccessory;
import lineageos.accessories.IAccessoryCallback;

/**
 * vendor.lineage.accessories.IAccessory <-> lineageos.accessories.IAccessory
 */
public class Accessory implements Dumpable {
    private final vendor.lineage.accessories.IAccessory mAccessoryInterface;
    private final Consumer<Accessory> mOnUpdated;

    // Vendor data
    private volatile AccessoryInfo mAccessoryInfo;
    private volatile byte mAccessoryStatus = AccessoryStatus.DISCONNECTED;
    private volatile BatteryInfo mBatteryInfo;

    // Callbacks
    private final CallbacksManager<IAccessoryCallback> mCallbacks = new CallbacksManager<>();

    // Vendor accessory callback
    private final vendor.lineage.accessories.IAccessoryCallback mAccessoryCallback =
            new vendor.lineage.accessories.IAccessoryCallback.Stub() {
        @Override
        public void onAccessoryInfoUpdated(
                vendor.lineage.accessories.AccessoryInfo accessoryInfo) {
            if (accessoryInfo == null) {
                throw new IllegalArgumentException(
                        "Received null AccessoryInfo, this shouldn't happen");
            }

            AccessoryInfo info = toFwk(accessoryInfo);

            // Update the accessory info
            mAccessoryInfo = info;

            // Notify the callbacks
            mCallbacks.forEachCallback(callback -> callback.onAccessoryInfoUpdated(info));

            mOnUpdated.accept(Accessory.this);
        }

        @Override
        public void onAccessoryStatusUpdated(byte accessoryStatus) {
            byte status = accessoryStatusToFwk(accessoryStatus);

            // Update the accessory status
            mAccessoryStatus = status;

            // Notify the callbacks
            mCallbacks.forEachCallback(callback -> callback.onAccessoryStatusUpdated(status));

            mOnUpdated.accept(Accessory.this);
        }

        @Override
        public void onBatteryInfoUpdated(vendor.lineage.accessories.BatteryInfo batteryInfo) {
            BatteryInfo info = toFwk(batteryInfo);

            // Update the battery info
            mBatteryInfo = info;

            // Notify the callbacks
            mCallbacks.forEachCallback(callback -> callback.onBatteryInfoUpdated(info));
        }

        @Override
        public int getInterfaceVersion() {
            return VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return HASH;
        }
    };

    // Framework service implementation
    // RemoteExceptions can't be sent to the clients, if the HAL died fall back to the last known
    // data, so that clients can still identify the accessory when it gets removed
    private final IAccessory.Stub mService = new IAccessory.Stub() {
        @Override
        public AccessoryInfo getAccessoryInfo() {
            try {
                return toFwk(mAccessoryInterface.getAccessoryInfo());
            } catch (RemoteException e) {
                AccessoryInfo info = mAccessoryInfo;
                if (info == null) {
                    throw new IllegalStateException("Accessory is not available", e);
                }
                return info;
            }
        }

        @Override
        public byte getAccessoryStatus() {
            try {
                return accessoryStatusToFwk(mAccessoryInterface.getAccessoryStatus());
            } catch (RemoteException e) {
                return AccessoryStatus.DISCONNECTED;
            }
        }

        @Override
        public void registerCallback(IAccessoryCallback callback) throws RemoteException {
            mCallbacks.registerCallback(callback);

            // Pass the current accessory info
            callback.onAccessoryInfoUpdated(mAccessoryInfo);
            callback.onAccessoryStatusUpdated(mAccessoryStatus);
            callback.onBatteryInfoUpdated(mBatteryInfo);
        }

        @Override
        public void unregisterCallback(IAccessoryCallback callback) {
            mCallbacks.unregisterCallback(callback);
        }

        @Override
        public void setEnabled(boolean enabled) {
            try {
                mAccessoryInterface.setEnabled(enabled);
            } catch (RemoteException e) {
                throw new IllegalStateException("Accessory is not available", e);
            }
        }

        @Override
        public BatteryInfo getBatteryInfo() {
            try {
                return toFwk(mAccessoryInterface.getBatteryInfo());
            } catch (RemoteException e) {
                return null;
            }
        }
    };

    private Accessory(vendor.lineage.accessories.IAccessory accessoryInterface,
            Consumer<Accessory> onUpdated) throws RemoteException {
        mAccessoryInterface = accessoryInterface;
        mOnUpdated = onUpdated;

        // Set the callback, this will also populate the accessory data
        mAccessoryInterface.setCallback(mAccessoryCallback);
    }

    /**
     * @param accessoryInterface The vendor accessory
     * @param onUpdated Called on the binder thread when the info or the status changes
     */
    public static Accessory from(vendor.lineage.accessories.IAccessory accessoryInterface,
            Consumer<Accessory> onUpdated) throws RemoteException {
        return new Accessory(accessoryInterface, onUpdated);
    }

    public IAccessory asInterface() {
        return mService;
    }

    /**
     * @return The Bluetooth address of the accessory, null if it doesn't have a known Bluetooth
     *         data link
     */
    public String getBluetoothAddress() {
        AccessoryInfo info = mAccessoryInfo;
        if (info == null || info.dataLink == null
                || info.dataLink.type != DataLinkType.BLUETOOTH
                || TextUtils.isEmpty(info.dataLink.id)) {
            return null;
        }
        return info.dataLink.id;
    }

    public boolean isCharging() {
        AccessoryInfo info = mAccessoryInfo;
        return info != null && info.isCharging;
    }

    public boolean isEnabled() {
        return mAccessoryStatus == AccessoryStatus.ENABLED;
    }

    @Override
    public void dump(PrintWriter pw) {
        AccessoryInfo info = mAccessoryInfo;
        if (info == null) {
            pw.println("Accessory (no info received yet):");
        } else {
            pw.println("Accessory " + info.id + ":");
            pw.println("  Type: " + info.type);
            pw.println("  Display name: " + info.displayName);
            pw.println("  Charging: " + info.isCharging);
            DataLinkInfo dataLink = info.dataLink;
            pw.println("  Data link: " + (dataLink != null
                    ? dataLinkTypeToString(dataLink.type) + " " + dataLink.id : "none"));
        }
        pw.println("  Status: " + mAccessoryStatus);
        BatteryInfo batteryInfo = mBatteryInfo;
        if (batteryInfo != null) {
            pw.println("  Battery: status " + batteryInfo.status
                    + ", level " + batteryInfo.levelPercentage);
        }
    }

    private static byte accessoryTypeToFwk(byte type) {
        switch (type) {
            case vendor.lineage.accessories.AccessoryType.KEYBOARD:
                return AccessoryType.KEYBOARD;
            case vendor.lineage.accessories.AccessoryType.ACTIVE_PEN:
                return AccessoryType.ACTIVE_PEN;
            default:
                throw new IllegalArgumentException("Unknown AccessoryType: " + type);
        }
    }

    private static byte accessoryStatusToFwk(byte status) {
        switch (status) {
            case vendor.lineage.accessories.AccessoryStatus.DISCONNECTED:
                return AccessoryStatus.DISCONNECTED;
            case vendor.lineage.accessories.AccessoryStatus.DISABLED:
                return AccessoryStatus.DISABLED;
            case vendor.lineage.accessories.AccessoryStatus.ENABLED:
                return AccessoryStatus.ENABLED;
            default:
                throw new IllegalArgumentException("Unknown AccessoryStatus: " + status);
        }
    }

    private static byte dataLinkTypeToFwk(byte type) {
        switch (type) {
            case vendor.lineage.accessories.DataLinkType.BLUETOOTH:
                return DataLinkType.BLUETOOTH;
            case vendor.lineage.accessories.DataLinkType.USB:
                return DataLinkType.USB;
            default:
                throw new IllegalArgumentException("Unknown DataLinkType: " + type);
        }
    }

    private static String dataLinkTypeToString(byte type) {
        switch (type) {
            case DataLinkType.BLUETOOTH:
                return "bluetooth";
            case DataLinkType.USB:
                return "usb";
            default:
                return "unknown (" + type + ")";
        }
    }

    private static byte batteryStatusToFwk(byte status) {
        switch (status) {
            case vendor.lineage.accessories.BatteryStatus.UNKNOWN:
                return BatteryStatus.UNKNOWN;
            case vendor.lineage.accessories.BatteryStatus.CHARGING:
                return BatteryStatus.CHARGING;
            case vendor.lineage.accessories.BatteryStatus.DISCHARGING:
                return BatteryStatus.DISCHARGING;
            case vendor.lineage.accessories.BatteryStatus.NOT_CHARGING:
                return BatteryStatus.NOT_CHARGING;
            default:
                throw new IllegalArgumentException("Unknown BatteryStatus: " + status);
        }
    }

    private static AccessoryInfo toFwk(vendor.lineage.accessories.AccessoryInfo info) {
        AccessoryInfo fwk = new AccessoryInfo();
        fwk.id = info.id;
        fwk.type = accessoryTypeToFwk(info.type);
        fwk.isPersistent = info.isPersistent;
        fwk.displayName = info.displayName;
        fwk.displayId = info.displayId;
        fwk.supportsToggling = info.supportsToggling;
        fwk.supportsBatteryQuerying = info.supportsBatteryQuerying;
        fwk.isCharging = info.isCharging;
        fwk.dataLink = toFwk(info.dataLink);
        return fwk;
    }

    private static DataLinkInfo toFwk(vendor.lineage.accessories.DataLinkInfo info) {
        if (info == null) {
            return null;
        }

        DataLinkInfo fwk = new DataLinkInfo();
        fwk.type = dataLinkTypeToFwk(info.type);
        fwk.id = info.id;
        return fwk;
    }

    private static BatteryInfo toFwk(vendor.lineage.accessories.BatteryInfo info) {
        if (info == null) {
            return null;
        }

        BatteryInfo fwk = new BatteryInfo();
        fwk.status = batteryStatusToFwk(info.status);
        fwk.levelPercentage = info.levelPercentage;
        return fwk;
    }
}
