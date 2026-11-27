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
 * Provides the name known to the Bluetooth stack to the accessories that have a Bluetooth data
 * link, since it's more specific than the generic name the HAL can provide.
 * All the methods must be called from the handler thread.
 */
public class AccessoryBluetoothName implements Dumpable {
    private final Context mContext;
    private final Handler mHandler;

    // Accessory ID -> accessory, for the accessories with a Bluetooth data link
    private final Map<String, Accessory> mAccessories = new HashMap<>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            if (device == null) {
                return;
            }

            for (Accessory accessory : mAccessories.values()) {
                if (device.getAddress().equalsIgnoreCase(accessory.getBluetoothAddress())) {
                    accessory.setBluetoothName(device.getAlias());
                }
            }
        }
    };

    public AccessoryBluetoothName(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    public void start() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ALIAS_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        mContext.registerReceiver(mReceiver, filter, null, mHandler, Context.RECEIVER_EXPORTED);
    }

    public void onAccessoryUpdated(String id, Accessory accessory) {
        String address = accessory.getBluetoothAddress();
        if (address == null) {
            onAccessoryRemoved(id);
            accessory.setBluetoothName(null);
            return;
        }

        mAccessories.put(id, accessory);
        accessory.setBluetoothName(getAlias(address));
    }

    public void onAccessoryRemoved(String id) {
        mAccessories.remove(id);
    }

    public void onAllAccessoriesRemoved() {
        mAccessories.clear();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("AccessoryBluetoothName:");
        pw.println("  Accessories: " + mAccessories.keySet());
    }

    private String getAlias(String address) {
        BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        String upperCaseAddress = address.toUpperCase();
        if (adapter == null || !BluetoothAdapter.checkBluetoothAddress(upperCaseAddress)) {
            return null;
        }
        return adapter.getRemoteDevice(upperCaseAddress).getAlias();
    }
}
