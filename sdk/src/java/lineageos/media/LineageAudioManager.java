/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package lineageos.media;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.Collections;
import java.util.List;

import lineageos.app.LineageContextConstants;

/**
 * Manager for extended audio system capabilities provided by
 * LineageOS.
 *
 * Currently, this API provides an application the ability to
 * query active global audio sessions, and receive broadcasts
 * when new audio sessions are created and destroyed.
 *
 * Applications wishing to receive audio session information
 * should register for the {@link ACTION_AUDIO_SESSIONS_CHANGED}
 * broadcast. This broadcast requires an application to hold the
 * {@link lineageos.permission.OBSERVE_AUDIO_SESSIONS}
 * permission. When receiving the broadcast, {@link EXTRA_SESSION_INFO}
 * will hold the {@link AudioSessionInfo} object associated
 * with the session. {@link EXTRA_SESSION_ADDED} will hold
 * a boolean value, true if the session is added, false if it
 * is being removed.
 *
 * It is important for applications to be cautious about which
 * audio streams effects are attached to when using this API as
 * it may interfere with their normal operation. An equalizer
 * application for example would only want to react to streams
 * with the type of {@link android.media.AudioManager.STREAM_MUSIC}.
 *
 * @see android.media.AudioManager
 *
 * @hide
 */
public final class LineageAudioManager {

    private static final String TAG = "LineageAudioManager";

    /**
     * Broadcast sent when audio session are added and removed.
     */
    public static final String ACTION_AUDIO_SESSIONS_CHANGED =
            "lineageos.intent.action.ACTION_AUDIO_SESSIONS_CHANGED";

    /**
     * Extra containing {@link AudioSessionInfo}
     */
    public static final String EXTRA_SESSION_INFO = "session_info";

    /**
     * Boolean extra, true if session is being added.
     */
    public static final String EXTRA_SESSION_ADDED = "added";

    private Context mContext;

    private static LineageAudioManager sInstance;
    private static ILineageAudioService sService;

    /**
     * @hide to prevent subclassing from outside of the framework
     */
    private LineageAudioManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;

        }
        sService = getService();

        if (!context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.AUDIO) || !checkService()) {
            Log.wtf(TAG, "Unable to get LineageAudioService. The service either" +
                    " crashed, was not started, or the interface has been called to early in" +
                    " SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link lineageos.media.LineageAudioManager}
     * @param context
     * @return {@link LineageAudioManager}
     */
    public synchronized static LineageAudioManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LineageAudioManager(context);
        }
        return sInstance;
    }

    /** @hide */
    public static ILineageAudioService getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_AUDIO_SERVICE);
        if (b != null) {
            sService = ILineageAudioService.Stub.asInterface(b);
            return sService;
        }
        return null;
    }

    /**
     * @return true if service is valid
     */
    private boolean checkService() {
        if (sService == null) {
            Log.w(TAG, "not connected to LineageAudioService");
            return false;
        }
        return true;
    }

    /**
     * List audio sessions for the given stream type defined in
     * {@link android.media.AudioManager}, for example,
     * {@link android.media.AudioManager#STREAM_MUSIC}.
     *
     * @param streamType from {@link android.media.AudioManager}
     * @return list of {@link AudioSessionInfo}, or empty list if none found
     * @see android.media.AudioManager
     */
    public List<AudioSessionInfo> listAudioSessions(int streamType) {
        if (checkService()) {
            try {
                final List<AudioSessionInfo> sessions = sService.listAudioSessions(streamType);
                if (sessions != null) {
                    return Collections.unmodifiableList(sessions);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to list audio sessions!", e);
            }
        }
        return Collections.emptyList();
    }

    /**
     * List all audio sessions.
     *
     * @return list of {@link AudioSessionInfo}, or empty list if none found
     * @see android.media.AudioManager
     */
    public List<AudioSessionInfo> listAudioSessions() {
        return listAudioSessions(-1);
    }
}
