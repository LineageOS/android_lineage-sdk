/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.profiles;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.policy.IKeyguardService;

import lineageos.app.Profile;

/**
 * The {@link LockSettings} class allows for overriding and setting the
 * current Lock screen state/security level. Value should be one a constant from
 * of {@link Profile.LockMode}
 *
 * <p>Example for disabling lockscreen security:
 * <pre class="prettyprint">
 * LockSettings lock = new LockSettings(Profile.LockMode.INSECURE);
 * profile.setScreenLockMode(lock);
 * </pre>
 */
public final class LockSettings implements Parcelable {

    private static final String TAG = LockSettings.class.getSimpleName();

    private int mValue;
    private boolean mDirty;

    /** @hide */
    public static final Creator<LockSettings> CREATOR
            = new Creator<LockSettings>() {
        public LockSettings createFromParcel(Parcel in) {
            return new LockSettings(in);
        }

        @Override
        public LockSettings[] newArray(int size) {
            return new LockSettings[size];
        }
    };

    /**
     * Unwrap {@link LockSettings} from a parcel.
     * @param parcel
     */
    public LockSettings(Parcel parcel) {
        readFromParcel(parcel);
    }

    /**
     * Construct a {@link LockSettings} with a default value of {@link Profile.LockMode.DEFAULT}.
     */
    public LockSettings() {
        this(Profile.LockMode.DEFAULT);
    }

    /**
     * Construct a {@link LockSettings} with a default value.
     */
    public LockSettings(int value) {
        mValue = value;
        mDirty = false;
    }

    /**
     * Get the value for the {@link LockSettings}
     * @return a constant from {@link Profile.LockMode}
     */
    public int getValue() {
        return mValue;
    }

    /**
     * Set the value for the {@link LockSettings}
     *
     * see {@link Profile.LockMode}
     */
    public void setValue(int value) {
        mValue = value;
        mDirty = true;
    }

    /** @hide */
    public boolean isDirty() {
        return mDirty;
    }

    /** @hide */
    public void processOverride(Context context, IKeyguardService keyguard) {
        boolean enable;
        final DevicePolicyManager devicePolicyManager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (devicePolicyManager != null && devicePolicyManager.requireSecureKeyguard()) {
            enable = true;
        } else {
            switch (mValue) {
                default:
                case Profile.LockMode.DEFAULT:
                case Profile.LockMode.INSECURE:
                    enable = true;
                    break;
                case Profile.LockMode.DISABLE:
                    enable = false;
                    break;
            }
        }

        try {
            keyguard.setKeyguardEnabled(enable);
        } catch (RemoteException e) {
            Log.w(TAG, "unable to set keyguard enabled state to: " + enable, e);
        }
    }

    /** @hide */
    public void writeXmlString(StringBuilder builder, Context context) {
        builder.append(mValue);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mValue);
        dest.writeInt(mDirty ? 1 : 0);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mValue = in.readInt();
        mDirty = in.readInt() != 0;
    }
}
