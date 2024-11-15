/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.platform.internal.common;

import android.app.ActivityManagerNative;
import android.app.IUserSwitchObserver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.util.Log;

/**
 * Simple extension of ContentObserver that also listens for user switch events to call update
 */
public abstract class UserContentObserver extends ContentObserver {
    private static final String TAG = "UserContentObserver";

    private final Runnable mUpdateRunnable;

    private final IUserSwitchObserver mUserSwitchObserver = new IUserSwitchObserver.Stub() {
        @Override
        public void onBeforeUserSwitching(int newUserId) throws RemoteException {
        }
        @Override
        public void onUserSwitching(int newUserId, IRemoteCallback reply) {
        }
        @Override
        public void onUserSwitchComplete(int newUserId) throws RemoteException {
            mHandler.post(mUpdateRunnable);
        }
        @Override
        public void onForegroundProfileSwitch(int newProfileId) {
        }
        @Override
        public void onLockedBootComplete(int newUserId) {
        }
    };

    private final Handler mHandler;

    /**
     * Content observer that tracks user switches
     * to allow clients to re-load settings for current user
     */
    public UserContentObserver(Handler handler) {
        super(handler);
        mHandler = handler;
        mUpdateRunnable = this::update;
    }

    protected void observe() {
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(mUserSwitchObserver, TAG);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to register user switch observer!", e);
        }
    }

    protected void unobserve() {
        try {
            mHandler.removeCallbacks(mUpdateRunnable);
            ActivityManagerNative.getDefault().unregisterUserSwitchObserver(mUserSwitchObserver);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to unregister user switch observer!", e);
        }
    }

    /**
     *  Called to notify of registered uri changes and user switches.
     *  Always invoked on the handler passed in at construction
     */
    protected abstract void update();

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        update();
    }
}
