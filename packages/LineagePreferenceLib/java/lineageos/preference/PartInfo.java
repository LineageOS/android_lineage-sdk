/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.preference;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public class PartInfo implements Parcelable {

    private static final String TAG = PartInfo.class.getSimpleName();

    private final String mName;

    private String mTitle;

    private String mSummary;

    private String mFragmentClass;

    private int mIconRes;

    private boolean mAvailable = true;

    /* for search provider */
    private int mXmlRes = 0;

    public PartInfo(String name, String title, String summary) {
        mName = name;
        mTitle = title;
        mSummary = summary;
    }

    public PartInfo(String name) {
        this(name, null, null);
    }

    public PartInfo(Parcel parcel) {
        mName = parcel.readString();
        mTitle = parcel.readString();
        mSummary = parcel.readString();
        mFragmentClass = parcel.readString();
        mIconRes = parcel.readInt();
        mAvailable = parcel.readInt() == 1;
        mXmlRes = parcel.readInt();
    }

    public String getName() {
        return mName;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setSummary(String summary) {
        mSummary = summary;
    }

    public String getSummary() {
        return mSummary;
    }

    public String getFragmentClass() {
        return mFragmentClass;
    }

    public void setFragmentClass(String fragmentClass) {
        mFragmentClass = fragmentClass;
    }

    public int getIconRes() {
        return mIconRes;
    }

    public void setIconRes(int iconRes) {
        mIconRes = iconRes;
    }

    public boolean isAvailable() {
        return mAvailable;
    }

    public void setAvailable(boolean available) {
        mAvailable = available;
    }

    public int getXmlRes() {
        return mXmlRes;
    }

    public void setXmlRes(int xmlRes) {
        mXmlRes = xmlRes;
    }

    public boolean updateFrom(PartInfo other) {
        if (other == null) {
            return false;
        }
        if (other.equals(this)) {
            return false;
        }
        setTitle(other.getTitle());
        setSummary(other.getSummary());
        setFragmentClass(other.getFragmentClass());
        setIconRes(other.getIconRes());
        setAvailable(other.isAvailable());
        setXmlRes(other.getXmlRes());
        return true;
    }

    @Override
    public String toString() {
        return String.format("PartInfo=[ name=%s title=%s summary=%s fragment=%s xmlRes=%x ]",
                mName, mTitle, mSummary, mFragmentClass, mXmlRes);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int i) {
        out.writeString(mName);
        out.writeString(mTitle);
        out.writeString(mSummary);
        out.writeString(mFragmentClass);
        out.writeInt(mIconRes);
        out.writeInt(mAvailable ? 1 : 0);
        out.writeInt(mXmlRes);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (getClass() != other.getClass()) {
            return false;
        }
        PartInfo o = (PartInfo) other;
        return Objects.equals(mName, o.mName) && Objects.equals(mTitle, o.mTitle) &&
                Objects.equals(mSummary, o.mSummary) && Objects.equals(mFragmentClass, o.mFragmentClass) &&
                Objects.equals(mIconRes, o.mIconRes) && Objects.equals(mAvailable, o.mAvailable) &&
                Objects.equals(mXmlRes, o.mXmlRes);
    }

    public String getAction() {
        return PartsList.PARTS_ACTION_PREFIX + "." + mName;
    }

    public Intent getIntentForActivity() {
        Intent i = new Intent(getAction());
        i.setComponent(PartsList.LINEAGEPARTS_ACTIVITY);
        return i;
    }

    public static final Parcelable.Creator<PartInfo> CREATOR = new Parcelable.Creator<PartInfo>() {
        @Override
        public PartInfo createFromParcel(Parcel in) {
            return new PartInfo(in);
        }

        @Override
        public PartInfo[] newArray(int size) {
            return new PartInfo[size];
        }
    };
}

