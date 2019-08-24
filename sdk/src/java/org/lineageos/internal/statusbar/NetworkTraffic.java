/**
 * Copyright (C) 2017-2019 The LineageOS project
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

package org.lineageos.internal.statusbar;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import lineageos.providers.LineageSettings;

import org.lineageos.platform.internal.R;

import java.util.HashMap;
import java.util.Map;

public class NetworkTraffic extends TextView {
    private static final String TAG = "NetworkTraffic";

    private static final int MODE_DISABLED = 0;
    private static final int MODE_UPSTREAM_ONLY = 1;
    private static final int MODE_DOWNSTREAM_ONLY = 2;
    private static final int MODE_UPSTREAM_AND_DOWNSTREAM = 3;

    private static final int MESSAGE_TYPE_PERIODIC_REFRESH = 0;
    private static final int MESSAGE_TYPE_UPDATE_VIEW = 1;

    private static final int REFRESH_INTERVAL = 2000;

    private static final int UNITS_KILOBITS = 0;
    private static final int UNITS_MEGABITS = 1;
    private static final int UNITS_KILOBYTES = 2;
    private static final int UNITS_MEGABYTES = 3;

    // Thresholds themselves are always defined in kbps
    private static final long AUTOHIDE_THRESHOLD_KILOBITS  = 10;
    private static final long AUTOHIDE_THRESHOLD_MEGABITS  = 100;
    private static final long AUTOHIDE_THRESHOLD_KILOBYTES = 8;
    private static final long AUTOHIDE_THRESHOLD_MEGABYTES = 80;

    private int mMode = MODE_DISABLED;
    private boolean mNetworkTrafficIsVisible;
    private long mTxKbps;
    private long mRxKbps;
    private long mLastTxBytes;
    private long mLastRxBytes;
    private long mLastUpdateTime;
    private int mTextSizeSingle;
    private int mTextSizeMulti;
    private boolean mAutoHide;
    private long mAutoHideThreshold;
    private int mUnits;
    private boolean mShowUnits;
    private int mDarkModeFillColor;
    private int mLightModeFillColor;
    private int mIconTint = Color.WHITE;
    private SettingsObserver mObserver;
    private Drawable mDrawable;

    // Network tracking related variables
    final private ConnectivityManager mConnectivityManager;
    final private HashMap<Network, NetworkState> mNetworkMap = new HashMap<>();
    private boolean mNetworksChanged = true;

    public class NetworkState {
        public NetworkCapabilities mNetworkCapabilities;
        public LinkProperties mLinkProperties;

        public NetworkState(NetworkCapabilities networkCapabilities,
                LinkProperties linkProperties) {
            mNetworkCapabilities = networkCapabilities;
            mLinkProperties = linkProperties;
        }
    };

    public NetworkTraffic(Context context) {
        this(context, null);
    }

    public NetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources resources = getResources();
        mTextSizeSingle = resources.getDimensionPixelSize(R.dimen.net_traffic_single_text_size);
        mTextSizeMulti = resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);

        mNetworkTrafficIsVisible = false;

        mObserver = new SettingsObserver(mTrafficHandler);

        mConnectivityManager = getContext().getSystemService(ConnectivityManager.class);
        final NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);
    }

    private LineageStatusBarItem.DarkReceiver mDarkReceiver =
            new LineageStatusBarItem.DarkReceiver() {
        public void onDarkChanged(Rect area, float darkIntensity, int tint) {
            mIconTint = tint;
            setTextColor(mIconTint);
            updateTrafficDrawableColor();
        }
        public void setFillColors(int darkColor, int lightColor) {
            mDarkModeFillColor = darkColor;
            mLightModeFillColor = lightColor;
        }
    };

    private LineageStatusBarItem.VisibilityReceiver mVisibilityReceiver =
            new LineageStatusBarItem.VisibilityReceiver() {
        public void onVisibilityChanged(boolean isVisible) {
            if (mNetworkTrafficIsVisible != isVisible) {
                mNetworkTrafficIsVisible = isVisible;
                updateViewState();
            }
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        LineageStatusBarItem.Manager manager =
                LineageStatusBarItem.findManager((View) this);
        manager.addDarkReceiver(mDarkReceiver);
        manager.addVisibilityReceiver(mVisibilityReceiver);

        mContext.registerReceiver(mIntentReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        mObserver.observe();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContext.unregisterReceiver(mIntentReceiver);
        mObserver.unobserve();
    }

    private Handler mTrafficHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            final long now = SystemClock.elapsedRealtime();
            final long timeDelta = now - mLastUpdateTime; /* ms */
            if (msg.what == MESSAGE_TYPE_PERIODIC_REFRESH
                    && timeDelta >= REFRESH_INTERVAL * 0.95f) {
                // Update counters
                long txBytes = 0;
                long rxBytes = 0;
                // Sum stats from interfaces of interest
                for (Map.Entry<Network, NetworkState> entry : mNetworkMap.entrySet()) {
                    final Network network = entry.getKey();
                    final NetworkState state = entry.getValue();
                    Log.d(TAG, "network = " + network.toString()
                            + " capabilities = " + state.mNetworkCapabilities.toString()
                            + " linkproperties = " + state.mLinkProperties.toString());
                    final String iface = state.mLinkProperties.getInterfaceName();
                    if (iface == null) {
                        continue;
                    }
                    Log.d(TAG, "adding stats for " + iface);
                    txBytes += TrafficStats.getTxBytes(iface);
                    rxBytes += TrafficStats.getRxBytes(iface);
                }

                final long txBytesDelta = txBytes - mLastTxBytes;
                final long rxBytesDelta = rxBytes - mLastRxBytes;

                if (!mNetworksChanged && timeDelta > 0 && txBytesDelta >= 0 && rxBytesDelta >= 0) {
                    mTxKbps = (long) (txBytesDelta * 8f / 1000f / (timeDelta / 1000f));
                    mRxKbps = (long) (rxBytesDelta * 8f / 1000f / (timeDelta / 1000f));

                    Log.d(TAG, "TX Bytes " + mLastTxBytes + " Rate = " + mTxKbps);
                    Log.d(TAG, "RX Bytes " + mLastRxBytes + " Rate = " + mRxKbps);
                } else if (mNetworksChanged) {
                    mNetworksChanged = false;
                }
                mLastTxBytes = txBytes;
                mLastRxBytes = rxBytes;
                mLastUpdateTime = now;
            }

            final boolean enabled = mMode != MODE_DISABLED && isConnectionAvailable();
            final boolean showUpstream =
                    mMode == MODE_UPSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
            final boolean showDownstream =
                    mMode == MODE_DOWNSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
            final boolean shouldHide = mAutoHide && (!showUpstream || mTxKbps < mAutoHideThreshold)
                    && (!showDownstream || mRxKbps < mAutoHideThreshold);

            if (!enabled || shouldHide) {
                setText("");
                setVisibility(GONE);
            } else {
                // Get information for uplink ready so the line return can be added
                StringBuilder output = new StringBuilder();
                if (showUpstream) {
                    output.append(formatOutput(mTxKbps));
                }

                // Ensure text size is where it needs to be
                int textSize;
                if (showUpstream && showDownstream) {
                    output.append("\n");
                    textSize = mTextSizeMulti;
                } else {
                    textSize = mTextSizeSingle;
                }

                // Add information for downlink if it's called for
                if (showDownstream) {
                    output.append(formatOutput(mRxKbps));
                }

                // Update view if there's anything new to show
                if (!output.toString().contentEquals(getText())) {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) textSize);
                    setText(output.toString());
                }
                setVisibility(VISIBLE);
            }

            // Schedule periodic refresh
            mTrafficHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
            if (enabled && mNetworkTrafficIsVisible) {
                mTrafficHandler.sendEmptyMessageDelayed(MESSAGE_TYPE_PERIODIC_REFRESH,
                        REFRESH_INTERVAL);
            }
        }

        private String formatOutput(long kbps) {
            final String value;
            final String unit;
            switch (mUnits) {
                case UNITS_KILOBITS:
                    value = String.format("%d", kbps);
                    unit = mContext.getString(R.string.kilobitspersecond_short);
                    break;
                case UNITS_MEGABITS:
                    value = String.format("%.1f", (float) kbps / 1000);
                    unit = mContext.getString(R.string.megabitspersecond_short);
                    break;
                case UNITS_KILOBYTES:
                    value = String.format("%d", kbps / 8);
                    unit = mContext.getString(R.string.kilobytespersecond_short);
                    break;
                case UNITS_MEGABYTES:
                    value = String.format("%.2f", (float) kbps / 8000);
                    unit = mContext.getString(R.string.megabytespersecond_short);
                    break;
                default:
                    value = "unknown";
                    unit = "unknown";
                    break;
            }

            if (mShowUnits) {
                return value + " " + unit;
            } else {
                return value;
            }
        }
    };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                updateViewState();
            }
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_MODE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_UNITS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS),
                    false, this, UserHandle.USER_ALL);
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private boolean isConnectionAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null;
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mMode = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 0, UserHandle.USER_CURRENT);
        mAutoHide = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0, UserHandle.USER_CURRENT) == 1;
        mUnits = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_UNITS, /* Mbps */ 1,
                UserHandle.USER_CURRENT);

        switch (mUnits) {
            case UNITS_KILOBITS:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_KILOBITS;
                break;
            case UNITS_MEGABITS:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_MEGABITS;
                break;
            case UNITS_KILOBYTES:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_KILOBYTES;
                break;
            case UNITS_MEGABYTES:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_MEGABYTES;
                break;
            default:
                mAutoHideThreshold = 0;
                break;
        }

        mShowUnits = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, 1,
                UserHandle.USER_CURRENT) == 1;

        if (mMode != MODE_DISABLED) {
            updateTrafficDrawable();
        }
        updateViewState();
    }

    private void updateViewState() {
        mTrafficHandler.sendEmptyMessage(MESSAGE_TYPE_UPDATE_VIEW);
    }

    private void clearHandlerCallbacks() {
        mTrafficHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
        mTrafficHandler.removeMessages(MESSAGE_TYPE_UPDATE_VIEW);
    }

    private void updateTrafficDrawable() {
        final int drawableResId;
        if (mMode == MODE_UPSTREAM_AND_DOWNSTREAM) {
            drawableResId = R.drawable.stat_sys_network_traffic_updown;
        } else if (mMode == MODE_UPSTREAM_ONLY) {
            drawableResId = R.drawable.stat_sys_network_traffic_up;
        } else if (mMode == MODE_DOWNSTREAM_ONLY) {
            drawableResId = R.drawable.stat_sys_network_traffic_down;
        } else {
            drawableResId = 0;
        }
        mDrawable = drawableResId != 0 ? getResources().getDrawable(drawableResId) : null;
        setCompoundDrawablesWithIntrinsicBounds(null, null, mDrawable, null);
        updateTrafficDrawableColor();
    }

    private void updateTrafficDrawableColor() {
        if (mDrawable != null) {
            mDrawable.setColorFilter(mIconTint, PorterDuff.Mode.MULTIPLY);
        }
    }

    private ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            mNetworkMap.put(network,
                    new NetworkState(mConnectivityManager.getNetworkCapabilities(network),
                    mConnectivityManager.getLinkProperties(network)));
            mNetworksChanged = true;
        }

        @Override
        public void onCapabilitiesChanged(Network network,
                NetworkCapabilities networkCapabilities) {
            if (mNetworkMap.containsKey(network)) {
                mNetworkMap.put(network, new NetworkState(networkCapabilities,
                        mConnectivityManager.getLinkProperties(network)));
                mNetworksChanged = true;
            }
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            if (mNetworkMap.containsKey(network)) {
                mNetworkMap.put(network,
                        new NetworkState(mConnectivityManager.getNetworkCapabilities(network),
                        linkProperties));
                mNetworksChanged = true;
            }
        }

        @Override
        public void onLost(Network network) {
            mNetworkMap.remove(network);
            mNetworksChanged = true;
        }
    };
}
