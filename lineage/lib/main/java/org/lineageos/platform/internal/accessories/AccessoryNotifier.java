/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserHandle;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import lineageos.accessories.AccessoryInfo;
import lineageos.accessories.AccessoryType;
import lineageos.accessories.BatteryInfo;

import org.lineageos.platform.internal.R;

/**
 * Shows a short notification when an accessory gets connected, or attached to the device to be
 * charged, e.g. an active pen.
 * The state reported when an accessory is added isn't notified, it might have been connected
 * for a while already.
 * All the methods must be called from the handler thread.
 */
public class AccessoryNotifier implements Dumpable {
    private static final String CHANNEL_ID = "LineageAccessories";

    // The accessory ID is used as the tag
    private static final int NOTIFICATION_ID = 3000;
    private static final long NOTIFICATION_TIMEOUT_MS = 5_000L;

    private static final String ACTION_ACCESSORIES_SETTINGS =
            "org.lineageos.lineageparts.ACCESSORIES_SETTINGS";

    private final Context mContext;
    private final NotificationManager mNotificationManager;

    private static final class State {
        final boolean connected;
        final boolean charging;

        State(boolean connected, boolean charging) {
            this.connected = connected;
            this.charging = charging;
        }

        @Override
        public String toString() {
            return "connected=" + connected + " charging=" + charging;
        }
    }

    // Accessory ID -> last known state
    private final Map<String, State> mStates = new HashMap<>();

    // Accessory ID -> when its notification got posted, in elapsed realtime
    private final Map<String, Long> mPostTimes = new HashMap<>();

    public AccessoryNotifier(Context context) {
        mContext = context;
        mNotificationManager = context.getSystemService(NotificationManager.class);
    }

    public void onAccessoryUpdated(String id, Accessory accessory) {
        AccessoryInfo info = accessory.getAccessoryInfo();
        if (info == null) {
            return;
        }

        State state = new State(accessory.isConnected(), info.isCharging);
        State oldState = mStates.put(id, state);
        if (oldState == null) {
            return;
        }

        if (!state.connected || (oldState.charging && !state.charging)) {
            cancel(id);
        } else if ((!oldState.connected && state.connected)
                || (!oldState.charging && state.charging)) {
            mPostTimes.put(id, SystemClock.elapsedRealtime());
            post(id, accessory, NOTIFICATION_TIMEOUT_MS);
        } else if (mPostTimes.containsKey(id)) {
            // Refresh the battery level while the notification is still shown
            long elapsed = SystemClock.elapsedRealtime() - mPostTimes.get(id);
            if (elapsed < NOTIFICATION_TIMEOUT_MS) {
                post(id, accessory, NOTIFICATION_TIMEOUT_MS - elapsed);
            } else {
                mPostTimes.remove(id);
            }
        }
    }

    public void onAccessoryRemoved(String id) {
        mStates.remove(id);
        cancel(id);
    }

    public void onAllAccessoriesRemoved() {
        new ArrayList<>(mPostTimes.keySet()).forEach(this::cancel);
        mStates.clear();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("AccessoryNotifier:");
        pw.println("  States: " + mStates);
        pw.println("  Notifications posted: " + mPostTimes.keySet());
    }

    private void post(String id, Accessory accessory, long timeoutMs) {
        AccessoryInfo info = accessory.getAccessoryInfo();

        Notification.Builder builder = new Notification.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(info.type == AccessoryType.KEYBOARD
                        ? R.drawable.ic_accessory_keyboard
                        : R.drawable.ic_accessory_pen)
                .setContentTitle(mContext.getString(
                        R.string.accessories_notification_connected, info.displayName))
                .setContentIntent(PendingIntent.getActivityAsUser(mContext, 0,
                        new Intent(ACTION_ACCESSORIES_SETTINGS)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE, null, UserHandle.CURRENT))
                .setCategory(Notification.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setTimeoutAfter(timeoutMs);

        BatteryInfo batteryInfo = accessory.getBatteryInfo();
        if (batteryInfo != null
                && batteryInfo.levelPercentage != BatteryInfo.BATTERY_LEVEL_UNKNOWN) {
            builder.setContentText(mContext.getString(info.isCharging
                            ? R.string.accessories_notification_charging
                            : R.string.accessories_notification_battery,
                    batteryInfo.levelPercentage));
        }

        createNotificationChannelIfNeeded();
        mNotificationManager.notifyAsUser(id, NOTIFICATION_ID, builder.build(),
                UserHandle.CURRENT);
    }

    private void cancel(String id) {
        if (mPostTimes.remove(id) != null) {
            mNotificationManager.cancelAsUser(id, NOTIFICATION_ID, UserHandle.CURRENT);
        }
    }

    private void createNotificationChannelIfNeeded() {
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }

        // Pop up like a heads up notification, but silently
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                mContext.getString(R.string.accessories_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        mNotificationManager.createNotificationChannel(channel);
    }
}
