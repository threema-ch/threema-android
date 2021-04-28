/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.app.mediaattacher;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * A MediaAttachItem represents a media item in the attacher (e.g. a photo or a video).
 *
 * It is identified by the display name (usually the file name, see {@link #getDisplayName()}).
 */
public class MediaAttachItem implements Parcelable {
	private final int id;
	private final long dateAdded;
	private final long dateTaken;
	private final long dateModified;
	private final String bucketName;
	private final Uri uri;
	private final String displayName;
	private final int orientation;
	private final int duration;
	private final int type;

	public MediaAttachItem(
		int id,
		long dateAdded,
		long dateTaken,
		long dateModified,
		Uri uri,
		String displayName,
		String bucketName,
		int orientation,
		int duration,
		int type
	) {
		this.id = id;
		this.dateAdded = dateAdded;
		this.dateTaken = dateTaken;
		this.dateModified = dateModified;
		this.uri = uri;
		this.displayName = displayName;
		this.bucketName = bucketName;
		this.orientation = orientation;
		this.duration = duration;
		this.type = type;
	}

	public MediaAttachItem(Parcel in) {
		id = in.readInt();
		dateAdded = in.readLong();
		dateTaken = in.readLong();
		dateModified = in.readLong();
		uri = in.readParcelable(Uri.class.getClassLoader());
		displayName = in.readString();
		bucketName = in.readString();
		orientation = in.readInt();
		duration = in.readInt();
		type = in.readInt();
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(id);
		dest.writeLong(dateAdded);
		dest.writeLong(dateTaken);
		dest.writeLong(dateModified);
		dest.writeParcelable(uri, flags);
		dest.writeString(displayName);
		dest.writeString(bucketName);
		dest.writeInt(orientation);
		dest.writeInt(duration);
		dest.writeInt(type);
	}

	public static final Creator<MediaAttachItem> CREATOR = new Creator<MediaAttachItem>() {
		@Override
		public MediaAttachItem createFromParcel(Parcel in) {
			return new MediaAttachItem(in);
		}

		@Override
		public MediaAttachItem[] newArray(int size) {
			return new MediaAttachItem[size];
		}
	};

	public int getId() {
		return id;
	}

	public long getDateAdded() {
		return dateAdded;
	}

	public long getDateTaken() {
		return dateTaken;
	}

	public long getDateModified() {
		return dateModified;
	}

	/**
	 * Return the content URI to this media file.
	 */
	public Uri getUri() {
		return uri;
	}

	/**
	 * Return the display name. For a media file, this will be the file name.
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Return the bucket name (e.g. "Camera" or "Screenshots").
	 */
	public String getBucketName() {
		return bucketName;
	}

	public int getOrientation() {
		return orientation;
	}

	public int getDuration() {
		return duration;
	}

	public int getType() {
		return type;
	}

	@Override
	public int describeContents() {
		return 0;
	}

}
