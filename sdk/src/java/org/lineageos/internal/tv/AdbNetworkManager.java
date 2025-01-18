/*
 * SPDX-FileCopyrightText: 2025 The LineageOS project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.debug.AdbNotifications;
import android.debug.AdbTransportType;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.notification.SystemNotificationChannels;

import org.lineageos.platform.internal.R;

import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

public class AdbNetworkManager {
    public static final String ADB_NETWORK_PORT = "5555";

    private static final String ADB_PORT_PROP = "service.adb.tcp.port";
    private static final String TAG = "AdbNetworkManager";
    private static final String ADB_NOTIFICATION_CHANNEL_ID_TV = "usbdevicemanager.adb.tv";

    private Context mContext;
    private String mHostAddress = null;
    private NotificationManager mNotificationManager;

    private static AdbNetworkManager sInstance;

    private AdbNetworkManager(Context context) {
        mContext = context;
        mNotificationManager = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    public static AdbNetworkManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AdbNetworkManager(context);
        }
        return sInstance;
    }

    public void setEnabled(boolean enable) {
        if (enable) {
            start();
            return;
        }

        stop();
    }

    public void start() {
        if (isRunning()) return;

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
                    }
                }
            }
        } catch (SocketException se) {
            Log.e(TAG, se.toString());
            return;
        };

        Log.d(TAG, "ADB over network enabled");
        notifyAdbNetworkStarted();
    }

    public void stop() {
        if (!isRunning()) return;

        SystemProperties.set(ADB_PORT_PROP, "-1");
        cancelNotification();
        Log.d(TAG, "ADB over network disabled");
    }

    public boolean getEnabled() {
        return isRunning();
    }

    public String getHostAddress() {
        return mHostAddress;
    }

    private boolean isRunning() {
        return SystemProperties.getInt(ADB_PORT_PROP, -1) > 0;
    }

    private void notifyAdbNetworkStarted() {
        Notification notification = createNotification();
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
                        com.android.internal.R.string.adb_debugging_notification_channel_tv),
                NotificationManager.IMPORTANCE_HIGH));
    }

    private Notification createNotification() {
        String title = mContext.getString(R.string.adbwifi_enabled_notification_title);
        String message = mContext.getString(R.string.adbwifi_enabled_notification_message,
                mHostAddress, ADB_NETWORK_PORT);

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ResolveInfo resolveInfo = mContext.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_SYSTEM_ONLY);

        // Settings app may not be available (e.g. device policy manager removes it)
        PendingIntent pIntent = null;
        if (resolveInfo != null) {
            intent.setPackage(resolveInfo.activityInfo.packageName);
            pIntent = PendingIntent.getActivityAsUser(mContext, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE, null, UserHandle.CURRENT);
        }

        return new Notification.Builder(mContext, SystemNotificationChannels.DEVELOPER_IMPORTANT)
                .setSmallIcon(com.android.internal.R.drawable.stat_sys_adb)
                .setWhen(0)
                .setOngoing(true)
                .setTicker(title)
                .setDefaults(0)  // please be quiet
                .setColor(mContext.getColor(
                            com.android.internal.R.color.system_notification_accent_color))
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .extend(new Notification.TvExtender()
                        .setChannelId(ADB_NOTIFICATION_CHANNEL_ID_TV))
                .build();
    }
}
