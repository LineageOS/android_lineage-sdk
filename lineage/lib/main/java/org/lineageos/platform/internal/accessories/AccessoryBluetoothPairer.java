/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pairs the accessories which report their Bluetooth address once they're charging and enabled,
 * e.g. an active pen attached to the device's charger.
 * Already paired accessories get their HID profile connected instead.
 * Bluetooth is never enabled by this class, pairing is deferred until the user enables it.
 * All the methods must be called from the handler thread.
 */
public class AccessoryBluetoothPairer implements Dumpable {
    private static final String TAG = "AccessoryBluetoothPairer";

    private static final long SCAN_TIMEOUT_MS = 30_000L;

    private static final int MAX_PAIRING_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1_000L;

    private final Context mContext;
    private final Handler mHandler;

    private BluetoothHidHost mHidHost;

    // Accessory ID -> Bluetooth address, for the accessories currently charging and enabled
    private final Map<String, String> mChargingAccessories = new HashMap<>();

    // Bluetooth address -> state
    private final Map<String, Scan> mScans = new HashMap<>();
    private final Map<String, Runnable> mRetries = new HashMap<>();
    private final Map<String, Integer> mPairingAttempts = new HashMap<>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            switch (action) {
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    onBluetoothStateChanged(
                            intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
                    break;
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    BluetoothDevice device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    if (device == null) {
                        return;
                    }
                    onBondStateChanged(device.getAddress(),
                            intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1),
                            intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1));
                    break;
            }
        }
    };

    private final BluetoothProfile.ServiceListener mProfileListener =
            new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mHandler.post(() -> {
                mHidHost = (BluetoothHidHost) proxy;
                getChargingAddresses().forEach(address -> pairIfNeeded(address));
            });
        }

        @Override
        public void onServiceDisconnected(int profile) {
            mHandler.post(() -> mHidHost = null);
        }
    };

    private final class Scan extends ScanCallback {
        final String mAddress;
        final Runnable mTimeout;

        Scan(String address) {
            mAddress = address;
            mTimeout = () -> {
                Log.w(TAG, mAddress + " not found");
                stopScan(mAddress);
                retryPairing(mAddress);
            };
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            mHandler.post(() -> {
                if (mScans.get(mAddress) != this) {
                    return;
                }
                stopScan(mAddress);

                BluetoothDevice device = result.getDevice();
                if (device.getBondState() != BluetoothDevice.BOND_NONE) {
                    return;
                }
                Log.i(TAG, "Bonding with " + mAddress + ", attempt "
                        + mPairingAttempts.getOrDefault(mAddress, 0));
                if (!device.createBond(BluetoothDevice.TRANSPORT_LE)) {
                    Log.e(TAG, "createBond failed for " + mAddress);
                    retryPairing(mAddress);
                }
            });
        }

        @Override
        public void onScanFailed(int errorCode) {
            mHandler.post(() -> {
                if (mScans.get(mAddress) != this) {
                    return;
                }
                Log.e(TAG, "Scan for " + mAddress + " failed: " + errorCode);
                stopScan(mAddress);
                retryPairing(mAddress);
            });
        }
    }

    public AccessoryBluetoothPairer(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    public void start() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, filter, null, mHandler, Context.RECEIVER_EXPORTED);

        BluetoothAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.HID_HOST);
        }
    }

    public void onAccessoryUpdated(String id, String bluetoothAddress, boolean isCharging,
            boolean isEnabled) {
        String address = null;
        if (isCharging && isEnabled && bluetoothAddress != null) {
            String upperCaseAddress = bluetoothAddress.toUpperCase();
            if (BluetoothAdapter.checkBluetoothAddress(upperCaseAddress)) {
                address = upperCaseAddress;
            } else {
                Log.e(TAG, id + " reported an invalid address: " + bluetoothAddress);
            }
        }

        String oldAddress = mChargingAccessories.get(id);
        if (address == null ? oldAddress == null : address.equals(oldAddress)) {
            return;
        }
        if (oldAddress != null) {
            cancelPairing(oldAddress);
        }

        if (address == null) {
            mChargingAccessories.remove(id);
            return;
        }

        Log.i(TAG, id + " (" + address + ") is charging");
        mChargingAccessories.put(id, address);
        mPairingAttempts.put(address, 0);
        pairIfNeeded(address);
    }

    public void onAccessoryRemoved(String id) {
        String address = mChargingAccessories.remove(id);
        if (address != null) {
            cancelPairing(address);
        }
    }

    public void onAllAccessoriesRemoved() {
        getChargingAddresses().forEach(this::cancelPairing);
        mChargingAccessories.clear();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("AccessoryBluetoothPairer:");
        pw.println("  Charging accessories: " + mChargingAccessories);
        pw.println("  Scanning for: " + mScans.keySet());
        pw.println("  Pairing attempts: " + mPairingAttempts);
        pw.println("  HID host connected: " + (mHidHost != null));
    }

    private void onBluetoothStateChanged(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_ON:
                for (String address : getChargingAddresses()) {
                    mPairingAttempts.put(address, 0);
                    pairIfNeeded(address);
                }
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                getChargingAddresses().forEach(this::cancelPairing);
                break;
        }
    }

    private void onBondStateChanged(String address, int previousState, int state) {
        if (!mChargingAccessories.containsValue(address)) {
            return;
        }

        if (previousState == BluetoothDevice.BOND_BONDING
                && state == BluetoothDevice.BOND_NONE) {
            Log.w(TAG, "Bonding with " + address + " failed");
            retryPairing(address);
        }
    }

    private void pairIfNeeded(String address) {
        if (!mChargingAccessories.containsValue(address) || mScans.containsKey(address)) {
            return;
        }

        BluetoothAdapter adapter = getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is disabled, not pairing " + address);
            return;
        }

        BluetoothDevice device = adapter.getRemoteDevice(address);
        switch (device.getBondState()) {
            case BluetoothDevice.BOND_BONDED:
                connect(device);
                return;
            case BluetoothDevice.BOND_BONDING:
                return;
        }

        int attempts = mPairingAttempts.getOrDefault(address, 0);
        if (attempts >= MAX_PAIRING_ATTEMPTS) {
            Log.e(TAG, "Giving up pairing " + address);
            return;
        }
        mPairingAttempts.put(address, attempts + 1);

        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.e(TAG, "LE scanner unavailable");
            return;
        }

        Scan scan = new Scan(address);
        mScans.put(address, scan);

        Log.d(TAG, "Scanning for " + address);
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setDeviceAddress(address).build());
        scanner.startScan(filters,
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0L)
                        .build(),
                scan);
        mHandler.postDelayed(scan.mTimeout, SCAN_TIMEOUT_MS);
    }

    // Make sure that the HID profile of an already paired accessory gets connected
    private void connect(BluetoothDevice device) {
        if (mHidHost == null) {
            return;
        }
        if (mHidHost.getConnectionState(device) != BluetoothProfile.STATE_DISCONNECTED) {
            return;
        }

        Log.i(TAG, "Connecting " + device.getAddress());
        if (!mHidHost.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED)) {
            Log.e(TAG, "Failed to connect " + device.getAddress());
        }
    }

    private void retryPairing(String address) {
        Runnable retry = mRetries.remove(address);
        if (retry != null) {
            mHandler.removeCallbacks(retry);
        }

        retry = () -> {
            mRetries.remove(address);
            pairIfNeeded(address);
        };
        mRetries.put(address, retry);
        mHandler.postDelayed(retry, RETRY_DELAY_MS);
    }

    private void cancelPairing(String address) {
        Runnable retry = mRetries.remove(address);
        if (retry != null) {
            mHandler.removeCallbacks(retry);
        }
        stopScan(address);
    }

    private void stopScan(String address) {
        Scan scan = mScans.remove(address);
        if (scan == null) {
            return;
        }
        mHandler.removeCallbacks(scan.mTimeout);

        BluetoothAdapter adapter = getAdapter();
        BluetoothLeScanner scanner = adapter != null ? adapter.getBluetoothLeScanner() : null;
        if (scanner != null) {
            scanner.stopScan(scan);
        }
    }

    private List<String> getChargingAddresses() {
        return new ArrayList<>(mChargingAccessories.values());
    }

    private BluetoothAdapter getAdapter() {
        BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
        return bluetoothManager != null ? bluetoothManager.getAdapter() : null;
    }
}
