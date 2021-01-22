/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.client.file.FileData;

import static ch.threema.app.services.PreferenceService.ImageScale_DEFAULT;
import static ch.threema.app.services.PreferenceService.VideoSize_DEFAULT;

/**
 * This class holds all meta information about a media item to be sent
 */
public class MediaItem implements Parcelable {
	@MediaType private int type;
	private Uri uri;
	private int rotation;
	private int exifRotation;
	private long durationMs;
	private String caption;
	private long startTimeMs;
	private long endTimeMs;
	@BitmapUtil.FlipType private int flip;
	@BitmapUtil.FlipType private int exifFlip;
	private String mimeType;
	@FileData.RenderingType int renderingType;
	@PreferenceService.ImageScale private int imageScale; // desired image scale
	@PreferenceService.VideoSize private int videoSize; // desired video scale factor
	private String filename;
	private boolean deleteAfterUse;

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({TYPE_FILE, TYPE_IMAGE, TYPE_VIDEO, TYPE_IMAGE_CAM, TYPE_VIDEO_CAM, TYPE_GIF, TYPE_VOICEMESSAGE, TYPE_TEXT})
	public @interface MediaType {}
	public static final int TYPE_FILE = 0;
	public static final int TYPE_IMAGE = 1;
	public static final int TYPE_VIDEO = 2;
	public static final int TYPE_IMAGE_CAM = 3;
	public static final int TYPE_VIDEO_CAM = 4;
	public static final int TYPE_GIF = 5;
	public static final int TYPE_VOICEMESSAGE = 6;
	public static final int TYPE_TEXT = 7;

	public static final long TIME_UNDEFINED = Long.MIN_VALUE;

	public MediaItem(Uri uri, @MediaType int type) {
		init();

		this.type = type;
		this.uri = uri;
	}

	public MediaItem(Uri uri, @MediaType int type, String mimeType, String caption) {
		init();

		this.type = type;
		this.uri = uri;
		this.mimeType = mimeType;
		this.caption = caption;
	}

	public MediaItem(Uri uri, String mimeType, String caption) {
		init();

		if (MimeUtil.isImageFile(mimeType)) {
			if (MimeUtil.isGifFile(mimeType)) {
				this.type = TYPE_GIF;
			} else {
				this.type = TYPE_IMAGE;
			}
		} else if (MimeUtil.isVideoFile(mimeType)) {
			this.type = TYPE_VIDEO;
		} else if (MimeUtil.isAudioFile(mimeType) && mimeType.startsWith(MimeUtil.MIME_TYPE_AUDIO_AAC)) {
			this.type = TYPE_VOICEMESSAGE;
		} else {
			this.type = TYPE_FILE;
			this.renderingType = FileData.RENDERING_DEFAULT;
		}

		this.uri = uri;
		this.mimeType = mimeType;
		this.caption = caption;
	}

	private void init() {
		this.rotation = 0;
		this.exifRotation = 0;
		this.durationMs = 0;
		this.caption = null;
		this.startTimeMs = 0;
		this.endTimeMs = TIME_UNDEFINED;
		this.flip = BitmapUtil.FLIP_NONE;
		this.exifFlip = BitmapUtil.FLIP_NONE;
		this.mimeType = MimeUtil.MIME_TYPE_DEFAULT;
		this.renderingType = FileData.RENDERING_MEDIA;
		this.imageScale = ImageScale_DEFAULT;
		this.videoSize = VideoSize_DEFAULT;
		this.filename = null;
		this.deleteAfterUse = false;
	}


	public MediaItem(Parcel in) {
		type = in.readInt();
		uri = in.readParcelable(Uri.class.getClassLoader());
		rotation = in.readInt();
		exifRotation = in.readInt();
		durationMs = in.readLong();
		caption = in.readString();
		startTimeMs = in.readLong();
		endTimeMs = in.readLong();
		flip = in.readInt();
		exifFlip = in.readInt();
		mimeType = in.readString();
		renderingType = in.readInt();
		imageScale = in.readInt();
		videoSize = in.readInt();
		filename = in.readString();
		deleteAfterUse = in.readInt() != 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(type);
		dest.writeParcelable(uri, flags);
		dest.writeInt(rotation);
		dest.writeInt(exifRotation);
		dest.writeLong(durationMs);
		dest.writeString(caption);
		dest.writeLong(startTimeMs);
		dest.writeLong(endTimeMs);
		dest.writeInt(flip);
		dest.writeInt(exifFlip);
		dest.writeString(mimeType);
		dest.writeInt(renderingType);
		dest.writeInt(imageScale);
		dest.writeInt(videoSize);
		dest.writeString(filename);
		dest.writeInt(deleteAfterUse ? 1 : 0);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	public static final Creator<MediaItem> CREATOR = new Creator<MediaItem>() {
		@Override
		public MediaItem createFromParcel(Parcel in) {
			return new MediaItem(in);
		}

		@Override
		public MediaItem[] newArray(int size) {
			return new MediaItem[size];
		}
	};

	@MediaType
	public int getType() {
		return type;
	}

	public void setType(@MediaType int type) {
		this.type = type;
	}

	public Uri getUri() {
		return uri;
	}

	public void setUri(Uri uri) {
		this.uri = uri;
	}

	public int getRotation() {
		return rotation;
	}

	public void setRotation(int rotation) {
		this.rotation = rotation;
	}

	public int getExifRotation() {
		return exifRotation;
	}

	public void setExifRotation(int exifRotation) {
		this.exifRotation = exifRotation;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public @Nullable String getCaption() {
		return caption;
	}

	public void setCaption(@Nullable String caption) {
		this.caption = caption;
	}

	public long getStartTimeMs() {
		return startTimeMs;
	}

	public void setStartTimeMs(long startTimeMs) {
		this.startTimeMs = startTimeMs;
	}

	public long getEndTimeMs() {
		return endTimeMs;
	}

	public void setEndTimeMs(long endTimeMs) {
		this.endTimeMs = endTimeMs;
	}

	public int getFlip() {
		return flip;
	}

	public void setFlip(int flip) {
		this.flip = flip;
	}

	@BitmapUtil.FlipType
	public int getExifFlip() {
		return exifFlip;
	}

	public void setExifFlip(@BitmapUtil.FlipType int exifFlip) {
		this.exifFlip = exifFlip;
	}

	/**
	 * get MimeType override
	 * @return
	 */
	public String getMimeType() {
		return mimeType;
	}

	/**
	 * set MimeType override
	 * @param mimeType
	 */
	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public @FileData.RenderingType int getRenderingType() {
		return renderingType;
	}

	public void setRenderingType(@FileData.RenderingType int renderingType) {
		this.renderingType = renderingType;
	}

	public @PreferenceService.ImageScale int getImageScale() {
		return imageScale;
	}

	public void setImageScale(@PreferenceService.ImageScale int imageScale) {
		this.imageScale = imageScale;
	}

	public @PreferenceService.VideoSize int getVideoSize() {
		return videoSize;
	}

	public void setVideoSize(@PreferenceService.VideoSize int videoSize) {
		this.videoSize = videoSize;
	}

	public @Nullable String getFilename() {
		return filename;
	}

	public void setFilename(@Nullable String filename) {
		this.filename = filename;
	}

	public boolean getDeleteAfterUse() {
		return deleteAfterUse;
	}

	/**
	 * Set this flag if the file is temporary and can be deleted after use
	 * @param deleteAfterUse 1 to signal the file is expendable, 0 otherwise
	 */
	public void setDeleteAfterUse(boolean deleteAfterUse) {
		this.deleteAfterUse = deleteAfterUse;
	}
}
