/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.services.telecom;

import android.os.Parcel;
import android.os.Parcelable;

public final class Item implements Parcelable {
    public static final Creator<Item> CREATOR = new Creator<Item>() {
        @Override
        public Item createFromParcel(Parcel in) {
            return new Item(in);
        }

        @Override
        public Item[] newArray(int size) {
            return new Item[size];
        }
    };

    public final String number;
    public final String name;
    public final String categories;
    public final String languages;
    public final String organization;
    public final String website;

    public Item(String number, String name, String categories, String languages,
            String organization, String website) {
        this.number = number;
        this.name = name;
        this.categories = categories;
        this.languages = languages;
        this.organization = organization;
        this.website = website;
    }

    private Item(Parcel in) {
        number = in.readString();
        name = in.readString();
        categories = in.readString();
        languages = in.readString();
        organization = in.readString();
        website = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(number);
        dest.writeString(name);
        dest.writeString(categories);
        dest.writeString(languages);
        dest.writeString(organization);
        dest.writeString(website);
    }
}
