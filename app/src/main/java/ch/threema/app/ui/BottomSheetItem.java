/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2022 Threema GmbH
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

	private Bitmap bitmap;
	private String title;
	private String tag;
	private @DrawableRes int resource;

	public BottomSheetItem(Bitmap bitmap, String title, String tag) {
		this.bitmap = bitmap;
		this.title = title;
		this.tag = tag;
		this.resource = 0;
	}

	public BottomSheetItem(@DrawableRes int resource, String title, String tag) {
		this.bitmap = null;
		this.title = title;
		this.tag = tag;
		this.resource = resource;
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

	protected BottomSheetItem(Parcel in) {
		bitmap = (Bitmap) in.readValue(Bitmap.class.getClassLoader());
		title = in.readString();
		tag = in.readString();
		resource = in.readInt();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeValue(bitmap);
		dest.writeString(title);
		dest.writeString(tag);
		dest.writeInt(resource);
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
