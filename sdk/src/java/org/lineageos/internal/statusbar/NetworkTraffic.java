/**
 * Copyright (C) 2017-2023 The LineageOS project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import lineageos.providers.LineageSettings;

import org.lineageos.platform.internal.R;

import java.util.ArrayList;
import java.util.HashMap;

public class NetworkTraffic extends TextView {
    private static final String TAG = "NetworkTraffic";

    private static final boolean DEBUG = false;

    private static final int MODE_DISABLED = 0;
    private static final int MODE_UPSTREAM_ONLY = 1;
    private static final int MODE_DOWNSTREAM_ONLY = 2;
    private static final int MODE_UPSTREAM_AND_DOWNSTREAM = 3;

    private static final int POSITION_START = 0;
    private static final int POSITION_CENTER = 1;
    private static final int POSITION_END = 2;

    private static final int MESSAGE_TYPE_PERIODIC_REFRESH = 0;
    private static final int MESSAGE_TYPE_UPDATE_VIEW = 1;
    private static final int MESSAGE_TYPE_ADD_NETWORK = 2;
    private static final int MESSAGE_TYPE_REMOVE_NETWORK = 3;

    private static final int REFRESH_INTERVAL = 2000;

    private static final int UNITS_KILOBITS = 0;
    private static final int UNITS_MEGABITS = 1;
    private static final int UNITS_KILOBYTES = 2;
    private static final int UNITS_MEGABYTES = 3;
    private static final int UNITS_AUTOBYTES = 4;

    private static final int SHOW_UNITS_OFF = 0;
    private static final int SHOW_UNITS_ON = 1;
    private static final int SHOW_UNITS_COMPACT = 2;

    // Thresholds themselves are always defined in kbps
    private static final long AUTOHIDE_THRESHOLD_KILOBITS  = 10;
    private static final long AUTOHIDE_THRESHOLD_MEGABITS  = 100;
    private static final long AUTOHIDE_THRESHOLD_KILOBYTES = 8;
    private static final long AUTOHIDE_THRESHOLD_MEGABYTES = 80;

    private final int mTextSizeSingle;
    private final int mTextSizeMulti;
    private final Handler mTrafficHandler;
    private final SettingsObserver mObserver;

    private int mMode = MODE_DISABLED;
    private int mPosition = POSITION_CENTER;
    private int mViewPosition = -1;
    private boolean mNetworkTrafficIsVisible;
    private long mTxKbps;
    private long mRxKbps;
    private long mLastTxBytes;
    private long mLastRxBytes;
    private long mLastUpdateTime;
    private boolean mAutoHide;
    private long mAutoHideThreshold;
    private int mUnits;
    private int mShowUnits;
    private int mIconTint = Color.WHITE;
    private Drawable mDrawable;

    private final HashMap<Network, LinkProperties> mLinkPropertiesMap = new HashMap<>();
    // Used to indicate that the set of sources contributing
    // to current stats have changed.
    private boolean mNetworksChanged = true;

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

        mTrafficHandler = new Handler(mContext.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_TYPE_PERIODIC_REFRESH:
                        recalculateStats();
                        displayStatsAndReschedule();
                        break;

                    case MESSAGE_TYPE_UPDATE_VIEW:
                        displayStatsAndReschedule();
                        break;

                    case MESSAGE_TYPE_ADD_NETWORK:
                        final LinkPropertiesHolder lph = (LinkPropertiesHolder) msg.obj;
                        mLinkPropertiesMap.put(lph.getNetwork(), lph.getLinkProperties());
                        mNetworksChanged = true;
                        break;

                    case MESSAGE_TYPE_REMOVE_NETWORK:
                        mLinkPropertiesMap.remove((Network) msg.obj);
                        mNetworksChanged = true;
                        break;
                }
            }

            private void recalculateStats() {
                final long now = SystemClock.elapsedRealtime();
                final long timeDelta = now - mLastUpdateTime; /* ms */
                if (timeDelta < REFRESH_INTERVAL * 0.95f) {
                    return;
                }
                // Sum tx and rx bytes from all sources of interest
                long txBytes = 0;
                long rxBytes = 0;
                // Add interface stats
                for (LinkProperties linkProperties : mLinkPropertiesMap.values()) {
                    final String iface = linkProperties.getInterfaceName();
                    if (iface == null) {
                        continue;
                    }
                    final long ifaceTxBytes = TrafficStats.getTxBytes(iface);
                    final long ifaceRxBytes = TrafficStats.getRxBytes(iface);
                    if (DEBUG) {
                        Log.d(TAG, "adding stats from interface " + iface
                                + " txbytes " + ifaceTxBytes + " rxbytes " + ifaceRxBytes);
                    }
                    txBytes += ifaceTxBytes;
                    rxBytes += ifaceRxBytes;
                }

                final long txBytesDelta = txBytes - mLastTxBytes;
                final long rxBytesDelta = rxBytes - mLastRxBytes;

                if (!mNetworksChanged && timeDelta > 0 && txBytesDelta >= 0 && rxBytesDelta >= 0) {
                    mTxKbps = (long) (txBytesDelta * 8f / 1000f / (timeDelta / 1000f));
                    mRxKbps = (long) (rxBytesDelta * 8f / 1000f / (timeDelta / 1000f));
                } else if (mNetworksChanged) {
                    mTxKbps = 0;
                    mRxKbps = 0;
                    mNetworksChanged = false;
                }
                mLastTxBytes = txBytes;
                mLastRxBytes = rxBytes;
                mLastUpdateTime = now;
            }

            private void displayStatsAndReschedule() {
                final boolean enabled = mMode != MODE_DISABLED && mPosition == mViewPosition
                        && isConnectionAvailable();
                final boolean showUpstream =
                        mMode == MODE_UPSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
                final boolean showDownstream =
                        mMode == MODE_DOWNSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM;
                final boolean shouldHide = mAutoHide
                        && (!showUpstream || mTxKbps < mAutoHideThreshold)
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
                int unitid = 0;
                switch (mUnits) {
                    case UNITS_KILOBITS:
                        value = String.format("%d", kbps);
                        unitid = R.string.kilobitspersecond_short;
                        break;
                    case UNITS_MEGABITS:
                        value = String.format("%.1f", (float) kbps / 1000);
                        unitid = R.string.megabitspersecond_short;
                        break;
                    case UNITS_KILOBYTES:
                    case UNITS_AUTOBYTES:
                        if (kbps < 8000 || mUnits == UNITS_KILOBYTES) {
                            value = String.format("%.0f", (float) kbps / 8 );
                            unitid = mShowUnits == SHOW_UNITS_COMPACT
                                ? R.string.kilobytespersecond_compact
                                : R.string.kilobytespersecond_short;
                            break;
                        }
                    case UNITS_MEGABYTES:
                        {
                            final String format;
                            if (kbps < 80000) {
                                format = "%.2f";
                            } else if (kbps < 800000) {
                                format = "%.1f";
                            } else {
                                format = "%.0f";
                            }
                            value = String.format(format, (float) kbps / 8000 );
                        }
                        unitid = mShowUnits == SHOW_UNITS_COMPACT
                            ? R.string.megabytespersecond_compact
                            : R.string.megabytespersecond_short;
                        break;
                    default:
                        value = "unknown";
                        break;
                }

                if (mShowUnits > SHOW_UNITS_OFF && unitid != 0) {
                    unit = mContext.getString(unitid);
                    return value + " " + unit;
                } else {
                    return value;
                }
            }
        };
        mObserver = new SettingsObserver(mTrafficHandler);

        // Network tracking related variables
        final NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        ConnectivityManager.NetworkCallback networkCallback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onLinkPropertiesChanged(Network network,
                            LinkProperties linkProperties) {
                        Message msg = new Message();
                        msg.what = MESSAGE_TYPE_ADD_NETWORK;
                        msg.obj = new LinkPropertiesHolder(network, linkProperties);
                        mTrafficHandler.sendMessage(msg);
                    }

                    @Override
                    public void onLost(Network network) {
                        Message msg = new Message();
                        msg.what = MESSAGE_TYPE_REMOVE_NETWORK;
                        msg.obj = network;
                        mTrafficHandler.sendMessage(msg);
                    }
                };
        ConnectivityManager.NetworkCallback defaultNetworkCallback =
                new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                updateViewState();
            }

            @Override
            public void onLost(Network network) {
                updateViewState();
            }
        };
        context.getSystemService(ConnectivityManager.class)
                .registerNetworkCallback(request, networkCallback);
        context.getSystemService(ConnectivityManager.class)
                .registerDefaultNetworkCallback(defaultNetworkCallback);
    }

    public void setViewPosition(int vpos) {
        mViewPosition = vpos;
    }

    private final LineageStatusBarItem.DarkReceiver mDarkReceiver =
            new LineageStatusBarItem.DarkReceiver() {
        public void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint) {
            mIconTint = tint;
            setTextColor(mIconTint);
            updateTrafficDrawableColor();
        }
        public void setFillColors(int darkColor, int lightColor) {
        }
    };

    private final LineageStatusBarItem.VisibilityReceiver mVisibilityReceiver =
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

        mObserver.observe();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mObserver.unobserve();
    }

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
                    LineageSettings.Secure.NETWORK_TRAFFIC_POSITION),
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
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        return cm.getActiveNetwork() != null;
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mMode = LineageSettings.Secure.getInt(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 0);
        mPosition = LineageSettings.Secure.getInt(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_POSITION, POSITION_CENTER);
        mAutoHide = LineageSettings.Secure.getInt(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0) == 1;
        mUnits = LineageSettings.Secure.getInt(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_UNITS, UNITS_KILOBYTES);
        mShowUnits = LineageSettings.Secure.getInt(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, SHOW_UNITS_ON);

        switch (mUnits) {
            case UNITS_KILOBITS:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_KILOBITS;
                break;
            case UNITS_MEGABITS:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_MEGABITS;
                break;
            case UNITS_KILOBYTES:
            case UNITS_AUTOBYTES:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_KILOBYTES;
                break;
            case UNITS_MEGABYTES:
                mAutoHideThreshold = AUTOHIDE_THRESHOLD_MEGABYTES;
                break;
            default:
                mAutoHideThreshold = 0;
                break;
        }

        if (mMode != MODE_DISABLED) {
            updateTrafficDrawable();
        }
        updateViewState();
    }

    private void updateViewState() {
        mTrafficHandler.sendEmptyMessage(MESSAGE_TYPE_UPDATE_VIEW);
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
            mDrawable.setColorFilter(
                    new PorterDuffColorFilter(mIconTint, PorterDuff.Mode.MULTIPLY));
        }
    }

    private static class LinkPropertiesHolder {
        private final Network mNetwork;
        private final LinkProperties mLinkProperties;

        public LinkPropertiesHolder(Network network, LinkProperties linkProperties) {
            mNetwork = network;
            mLinkProperties = linkProperties;
        }

        public Network getNetwork() {
            return mNetwork;
        }

        public LinkProperties getLinkProperties() {
            return mLinkProperties;
        }
    }
}
