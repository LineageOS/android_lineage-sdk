/*
 * Copyright (C) 2018-2021 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.internal.util;

import android.app.ActivityManager;
import android.app.ActivityManager.StackInfo;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import org.lineageos.platform.internal.R;

import java.util.List;

public class ActionUtils {
    private static final boolean DEBUG = false;
    private static final String TAG = ActionUtils.class.getSimpleName();
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    /**
     * Kills the top most / most recent user application, but leaves out the launcher.
     *
     * @param context the current context, used to retrieve the package manager.
     * @param userId the ID of the currently active user
     * @return {@code true} when a user application was found and closed.
     */
    public static boolean killForegroundApp(Context context, int userId) {
        try {
            return killForegroundAppInternal(context, userId);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not kill foreground app");
        }
        return false;
    }

    private static boolean killForegroundAppInternal(Context context, int userId)
            throws RemoteException {
        final IActivityTaskManager atm = ActivityTaskManager.getService();

        if (atm.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_PINNED) {
            return false;
        }

        final String packageName = getForegroundTaskPackageName(context, userId);

        if (packageName == null) {
            return false;
        }

        final IActivityManager am = ActivityManagerNative.getDefault();
        am.forceStopPackage(packageName, UserHandle.USER_CURRENT);

        Toast.makeText(context, R.string.app_killed_message, Toast.LENGTH_SHORT).show();

        return true;
    }

    /**
     * Attempt to bring up the last activity in the stack before the current active one.
     *
     * @param context
     * @return whether an activity was found to switch to
     */
    public static boolean switchToLastApp(Context context, int userId) {
        try {
            return switchToLastAppInternal(context, userId);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not switch to last app");
        }
        return false;
    }

    private static boolean switchToLastAppInternal(Context context, int userId)
            throws RemoteException {
        ActivityManager.RecentTaskInfo lastTask = getLastTask(context, userId);

        if (lastTask == null || lastTask.id < 0) {
            return false;
        }

        final String packageName = lastTask.baseIntent.getComponent().getPackageName();
        final IActivityManager am = ActivityManagerNative.getDefault();
        final ActivityOptions opts = ActivityOptions.makeCustomAnimation(context,
                org.lineageos.platform.internal.R.anim.last_app_in,
                org.lineageos.platform.internal.R.anim.last_app_out);

        if (DEBUG) Log.d(TAG, "switching to " + packageName);
        am.moveTaskToFront(null, null, lastTask.id,
                ActivityManager.MOVE_TASK_NO_USER_ACTION, opts.toBundle());

        return true;
    }

    private static ActivityManager.RecentTaskInfo getLastTask(Context context, int userId)
            throws RemoteException {
        final String defaultHomePackage = resolveCurrentLauncherPackage(context, userId);
        final IActivityManager am = ActivityManager.getService();
        final List<ActivityManager.RecentTaskInfo> tasks = am.getRecentTasks(5,
                ActivityManager.RECENT_IGNORE_UNAVAILABLE, userId).getList();

        for (int i = 1; i < tasks.size(); i++) {
            ActivityManager.RecentTaskInfo task = tasks.get(i);
            if (task.origActivity != null) {
                task.baseIntent.setComponent(task.origActivity);
            }
            String packageName = task.baseIntent.getComponent().getPackageName();
            if (!packageName.equals(defaultHomePackage)
                    && !packageName.equals(SYSTEMUI_PACKAGE)) {
                return task;
            }
        }

        return null;
    }

    private static String getForegroundTaskPackageName(Context context, int userId)
            throws RemoteException {
        final String defaultHomePackage = resolveCurrentLauncherPackage(context, userId);
        final IActivityManager am = ActivityManager.getService();
        final StackInfo focusedStack = am.getFocusedStackInfo();

        if (focusedStack == null || focusedStack.topActivity == null) {
            return null;
        }

        final String packageName = focusedStack.topActivity.getPackageName();
        if (!packageName.equals(defaultHomePackage)
                && !packageName.equals(SYSTEMUI_PACKAGE)) {
            return packageName;
        }

        return null;
    }

    private static String resolveCurrentLauncherPackage(Context context, int userId) {
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        final PackageManager pm = context.getPackageManager();
        final ResolveInfo launcherInfo = pm.resolveActivityAsUser(launcherIntent, 0, userId);

        if (launcherInfo.activityInfo != null &&
                !launcherInfo.activityInfo.packageName.equals("android")) {
            return launcherInfo.activityInfo.packageName;
        }

        return null;
    }
}
