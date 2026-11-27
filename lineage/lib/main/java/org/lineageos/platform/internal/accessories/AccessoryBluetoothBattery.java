/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides the battery level reported by the Bluetooth stack to the accessories that have a
 * Bluetooth data link but whose HAL can't query their battery.
 * All the methods must be called from the handler thread.
 */
public class AccessoryBluetoothBattery implements Dumpable {
    private final Context mContext;
    private final Handler mHandler;

    // Accessory ID -> accessory, for the accessories relying on the Bluetooth battery level
    private final Map<String, Accessory> mAccessories = new HashMap<>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            if (device == null) {
                return;
            }

            int level = intent.getIntExtra(BluetoothDevice.EXTRA_BATTERY_LEVEL,
                    BluetoothDevice.BATTERY_LEVEL_UNKNOWN);
            for (Accessory accessory : mAccessories.values()) {
                if (device.getAddress().equalsIgnoreCase(accessory.getBluetoothAddress())) {
                    accessory.setBluetoothBatteryLevel(level);
                }
            }
        }
    };

    public AccessoryBluetoothBattery(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    public void start() {
        mContext.registerReceiver(mReceiver,
                new IntentFilter(BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED), null, mHandler,
                Context.RECEIVER_EXPORTED);
    }

    public void onAccessoryUpdated(String id, Accessory accessory) {
        String address = accessory.getBluetoothAddress();
        if (accessory.supportsBatteryQuerying() || address == null) {
            onAccessoryRemoved(id);
            return;
        }

        mAccessories.put(id, accessory);
        accessory.setBluetoothBatteryLevel(getBatteryLevel(address));
    }

    public void onAccessoryRemoved(String id) {
        mAccessories.remove(id);
    }

    public void onAllAccessoriesRemoved() {
        mAccessories.clear();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("AccessoryBluetoothBattery:");
        pw.println("  Accessories: " + mAccessories.keySet());
    }

    private int getBatteryLevel(String address) {
        BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        String upperCaseAddress = address.toUpperCase();
        if (adapter == null || !BluetoothAdapter.checkBluetoothAddress(upperCaseAddress)) {
            return BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        }
        return adapter.getRemoteDevice(upperCaseAddress).getBatteryLevel();
    }
}
