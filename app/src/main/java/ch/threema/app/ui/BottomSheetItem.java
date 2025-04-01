/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.ui;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.DrawableRes;

public class BottomSheetItem implements Parcelable {

    private final Bitmap bitmap;
    private final String title;
    private final String tag;
    private final @DrawableRes int resource;
    private final String data;

    public BottomSheetItem(Bitmap bitmap, String title, String tag, String data) {
        this.bitmap = bitmap;
        this.title = title;
        this.tag = tag;
        this.resource = 0;
        this.data = data;
    }

    public BottomSheetItem(@DrawableRes int resource, String title, String tag, String data) {
        this.bitmap = null;
        this.title = title;
        this.tag = tag;
        this.resource = resource;
        this.data = data;
    }

    public BottomSheetItem(Bitmap bitmap, String title, String tag) {
        this.bitmap = bitmap;
        this.title = title;
        this.tag = tag;
        this.resource = 0;
        this.data = null;
    }

    public BottomSheetItem(@DrawableRes int resource, String title, String tag) {
        this.bitmap = null;
        this.title = title;
        this.tag = tag;
        this.resource = resource;
        this.data = null;
    }

    public Bitmap getBitmap() {
        return this.bitmap;
    }

    public String getTitle() {
        return this.title;
    }

    public String getTag() {
        return this.tag;
    }

    public @DrawableRes int getResource() {
        return this.resource;
    }

    public String getData() {
        return this.data;
    }

    protected BottomSheetItem(Parcel in) {
        bitmap = (Bitmap) in.readValue(Bitmap.class.getClassLoader());
        title = in.readString();
        tag = in.readString();
        resource = in.readInt();
        data = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        try {
            dest.writeValue(bitmap);
        } catch (RuntimeException e) {
            dest.writeValue(null);
        }
        dest.writeString(title);
        dest.writeString(tag);
        dest.writeInt(resource);
        dest.writeString(data);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<BottomSheetItem> CREATOR = new Parcelable.Creator<BottomSheetItem>() {
        @Override
        public BottomSheetItem createFromParcel(Parcel in) {
            return new BottomSheetItem(in);
        }

        @Override
        public BottomSheetItem[] newArray(int size) {
            return new BottomSheetItem[size];
        }
    };
}
