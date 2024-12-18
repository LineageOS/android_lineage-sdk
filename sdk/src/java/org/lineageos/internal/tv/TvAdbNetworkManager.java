/*
 * SPDX-FileCopyrightText: 2024 The LineageOS project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.debug.AdbNotifications;
import android.debug.AdbTransportType;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;

import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.SocketException;

import java.util.Collections;
import java.util.List;

public class TvAdbNetworkManager {
    public static final String ADB_NETWORK_PORT = "5555";

    private static final String ADB_PORT_PROP = "service.adb.tcp.port";
    private static final String TAG = "TvAdbNetworkManager";
    private static final String ADB_NOTIFICATION_CHANNEL_ID_TV = "usbdevicemanager.adb.tv";

    private Context mContext;
    private boolean mRunning = false;
    private String mHostAddress = null;
    private NotificationManager mNotificationManager;

    public TvAdbNetworkManager(Context context) {
        mContext = context;
        mNotificationManager = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public boolean setEnabled(boolean enable) {
        if (enable) {
            return start();
        }

        return stop();
    }

    public boolean start() {
        SystemProperties.set(ADB_PORT_PROP, ADB_NETWORK_PORT);

        mHostAddress = null;

        try {
            List<NetworkInterface> interfaces = Collections.list(
                    NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() &&
                        addr.getHostAddress().indexOf(':') < 0) {
                        mHostAddress = addr.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException se) {
            Log.e(TAG, se.toString());
            return false;
        };

        createNotification();
        mRunning = true;
        return mHostAddress != null;
    }

    public boolean stop() {
        SystemProperties.set(ADB_PORT_PROP, "-1");
        cancelNotification();
        mRunning = false;
        return true;
    }

    public boolean getEnabled() {
        return mRunning;
    }

    public String getHostAddress() {
        return mHostAddress;
    }

    private void createNotification() {
        Notification notification = AdbNotifications.createNotification(mContext,
                AdbTransportType.WIFI);
        mNotificationManager.notifyAsUser(null, SystemMessage.NOTE_ADB_WIFI_ACTIVE,
                notification, UserHandle.ALL);
    }

    private void cancelNotification() {
        mNotificationManager.cancelAsUser(null, SystemMessage.NOTE_ADB_WIFI_ACTIVE,
                UserHandle.ALL);
    }

    private void createNotificationChannel() {
        mNotificationManager.createNotificationChannel(
        new NotificationChannel(ADB_NOTIFICATION_CHANNEL_ID_TV,
                mContext.getString(
                        com.android.internal.R.string
                                .adb_debugging_notification_channel_tv),
                NotificationManager.IMPORTANCE_HIGH));
    }
}
