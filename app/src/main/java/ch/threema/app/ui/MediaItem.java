/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

import static ch.threema.app.services.PreferenceService.ImageScale_DEFAULT;
import static ch.threema.app.services.PreferenceService.VideoSize_DEFAULT;
import static ch.threema.app.utils.BitmapUtil.FLIP_HORIZONTAL;
import static ch.threema.app.utils.BitmapUtil.FLIP_NONE;
import static ch.threema.app.utils.BitmapUtil.FLIP_VERTICAL;

import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.file.FileData;

/**
 * This class holds all meta information about a media item to be sent
 */
public class MediaItem implements Parcelable {
	@MediaType private int type;
	private Uri originalUri; // Uri of original media item before creating a local copy
	private Uri uri;
	private Orientation orientation;
	private int exifRotation;
	private long durationMs;
	private String caption;
	private long startTimeMs;
	private long endTimeMs;
	@BitmapUtil.FlipType private int exifFlip;
	private String mimeType;
	@FileData.RenderingType int renderingType;
	@PreferenceService.ImageScale private int imageScale; // desired image scale
	@PreferenceService.VideoSize private int videoSize; // desired video scale factor
	private String filename;
	private boolean deleteAfterUse;
	private boolean isEdited;
	private boolean muted;

	private static final Logger logger = LoggingUtil.getThreemaLogger("MediaItem");

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({TYPE_FILE, TYPE_IMAGE, TYPE_VIDEO, TYPE_IMAGE_CAM, TYPE_VIDEO_CAM, TYPE_GIF, TYPE_VOICEMESSAGE, TYPE_TEXT, TYPE_LOCATION, TYPE_IMAGE_ANIMATED})
	public @interface MediaType {}
	public static final int TYPE_FILE = 0;
	public static final int TYPE_IMAGE = 1;
	public static final int TYPE_VIDEO = 2;
	public static final int TYPE_IMAGE_CAM = 3;
	public static final int TYPE_VIDEO_CAM = 4;
	public static final int TYPE_GIF = 5;
	public static final int TYPE_VOICEMESSAGE = 6;
	public static final int TYPE_TEXT = 7;
	public static final int TYPE_LOCATION = 8;
	public static final int TYPE_IMAGE_ANIMATED = 9; // animated images such as animated WebP

	public static final long TIME_UNDEFINED = Long.MIN_VALUE;

	public static class Orientation {
		private int rotation;
		private int flip;

		public static Orientation getMixedOrientation(@NonNull Orientation first, @NonNull Orientation second) {
			int mixedRotation = first.getRotation() + second.getRotation();
			int mixedFlip = FLIP_NONE;
			mixedFlip |= first.isHorizontalFlip() ^ second.isHorizontalFlip() ? FLIP_HORIZONTAL : FLIP_NONE;
			mixedFlip |= first.isVerticalFlip() ^ second.isVerticalFlip() ? FLIP_VERTICAL : FLIP_NONE;
			return new Orientation(mixedRotation, mixedFlip);
		}

		public Orientation() {
			this(0, FLIP_NONE);
		}

		public Orientation(int rotation, int flip) {
			this.rotation = rotation;
			this.flip = flip;
		}

		public int getRotation() {
			return rotation;
		}

		public void setRotation(int rotation) {
			this.rotation = rotation;
			clampRotation();
		}

		public void rotateBy(int degrees) {
			this.rotation += degrees;
			clampRotation();
		}

		public int getFlip() {
			return flip;
		}

		public void setFlip(int flip) {
			this.flip = flip;
		}

		public void flip() {
			int currentFlip = flip;
			if (getRotation() == 90 || getRotation() == 270) {
				if ((currentFlip & FLIP_VERTICAL) == FLIP_VERTICAL) {
					// clear vertical flag
					currentFlip &= ~FLIP_VERTICAL;
				} else {
					currentFlip |= FLIP_VERTICAL;
				}
			} else {
				if ((currentFlip & FLIP_HORIZONTAL) == FLIP_HORIZONTAL) {
					// clear horizontal flag
					currentFlip &= ~FLIP_HORIZONTAL;
				} else {
					currentFlip |= FLIP_HORIZONTAL;
				}
			}
			flip = currentFlip;
		}

		public Orientation getInverse() {
			Orientation inverse = new Orientation(-rotation, FLIP_NONE);
			if (rotation == 90 || rotation == 270) {
				inverse.flip |= isVerticalFlip() ? FLIP_HORIZONTAL : FLIP_NONE;
				inverse.flip |= isHorizontalFlip() ? FLIP_VERTICAL : FLIP_NONE;
			} else {
				inverse.flip = flip;
			}
			return inverse;
		}

		/**
		 * Get the transformation matrix based on rotation and flip.
		 */
		@NonNull
		public Matrix getTransformationMatrix() {
			Matrix matrix = new Matrix();
			boolean flipHorizontal = (getFlip() & FLIP_HORIZONTAL) == FLIP_HORIZONTAL;
			boolean flipVertical = (getFlip() & FLIP_VERTICAL) == FLIP_VERTICAL;
			matrix.postScale(flipHorizontal ? -1 : 1, flipVertical ? -1 : 1);
			matrix.postRotate(getRotation());
			return matrix;
		}

		public boolean isHorizontalFlip() {
			return (flip & FLIP_HORIZONTAL) == FLIP_HORIZONTAL;
		}

		public boolean isVerticalFlip() {
			return (flip & FLIP_VERTICAL) == FLIP_VERTICAL;
		}

		private void clampRotation() {
			rotation %= 360;
			if (rotation < 0) {
				rotation += 360;
			}
		}

	}

	public static ArrayList<MediaItem> getFromUris(List<Uri> uris, Context context) {
		return getFromUris(uris, context, false);
	}

	public static ArrayList<MediaItem> getFromUris(@NonNull List<Uri> uris, @NonNull Context context, boolean asFile) {
		ArrayList<MediaItem> mediaItems = new ArrayList<>(uris.size());

		for (Uri uri: uris) {
			mediaItems.add(getFromUri(uri, context, asFile));
		}

		return mediaItems;
	}

	@NonNull
	public static MediaItem getFromUri(@NonNull Uri uri, @NonNull Context context, boolean asFile) {
		String mimeType = FileUtil.getMimeTypeFromUri(context, uri);
		Uri originalUri = uri;
		try {
			// log the number of permissions due to limit https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html
			logger.info("Number of taken persistable uri permissions {}", context.getContentResolver().getPersistedUriPermissions().size());
			context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} catch (Exception e) {
			logger.info(e.getMessage());
			uri = FileUtil.getFileUri(uri);
		}

		MediaItem mediaItem = new MediaItem(uri, mimeType, null);

		// Set exif orientation
		BitmapUtil.ExifOrientation exifOrientation = BitmapUtil.getExifOrientation(context, uri);
		mediaItem.setExifRotation((int) exifOrientation.getRotation());
		mediaItem.setExifFlip(exifOrientation.getFlip());

		mediaItem.setOriginalUri(originalUri);
		mediaItem.setFilename(FileUtil.getFilenameFromUri(context.getContentResolver(), mediaItem));
		if (asFile) {
			if (MimeUtil.isSupportedImageFile(mimeType)) {
				mediaItem.setImageScale(PreferenceService.ImageScale_SEND_AS_FILE);
			} else if (MimeUtil.isVideoFile(mimeType)) {
				mediaItem.setVideoSize(PreferenceService.VideoSize_SEND_AS_FILE);
			} else {
				mediaItem.setType(TYPE_FILE);
			}
		}
		return mediaItem;
	}

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

		this.type = MimeUtil.getMediaTypeFromMimeType(mimeType, uri);
		if (this.type == TYPE_FILE) {
			this.renderingType = FileData.RENDERING_DEFAULT;
		}

		this.uri = uri;
		this.mimeType = mimeType;
		this.caption = caption;
	}

	private void init() {
		this.orientation = new Orientation();
		this.exifRotation = 0;
		this.durationMs = 0;
		this.caption = null;
		this.startTimeMs = 0;
		this.endTimeMs = TIME_UNDEFINED;
		this.exifFlip = BitmapUtil.FLIP_NONE;
		this.mimeType = MimeUtil.MIME_TYPE_DEFAULT;
		this.renderingType = FileData.RENDERING_MEDIA;
		this.imageScale = ImageScale_DEFAULT;
		this.videoSize = VideoSize_DEFAULT;
		this.filename = null;
		this.deleteAfterUse = false;
		isEdited = false;
	}


	public MediaItem(Parcel in) {
		orientation = new Orientation();
		type = in.readInt();
		uri = in.readParcelable(Uri.class.getClassLoader());
		orientation.rotation = in.readInt();
		exifRotation = in.readInt();
		durationMs = in.readLong();
		caption = in.readString();
		startTimeMs = in.readLong();
		endTimeMs = in.readLong();
		orientation.flip = in.readInt();
		exifFlip = in.readInt();
		mimeType = in.readString();
		renderingType = in.readInt();
		imageScale = in.readInt();
		videoSize = in.readInt();
		filename = in.readString();
		deleteAfterUse = in.readInt() != 0;
		originalUri = in.readParcelable(Uri.class.getClassLoader());
		isEdited = in.readInt() != 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(type);
		dest.writeParcelable(uri, flags);
		dest.writeInt(orientation.rotation);
		dest.writeInt(exifRotation);
		dest.writeLong(durationMs);
		dest.writeString(caption);
		dest.writeLong(startTimeMs);
		dest.writeLong(endTimeMs);
		dest.writeInt(orientation.flip);
		dest.writeInt(exifFlip);
		dest.writeString(mimeType);
		dest.writeInt(renderingType);
		dest.writeInt(imageScale);
		dest.writeInt(videoSize);
		dest.writeString(filename);
		dest.writeInt(deleteAfterUse ? 1 : 0);
		dest.writeParcelable(originalUri, flags);
		dest.writeInt(isEdited ? 1 : 0);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	public static final Creator<MediaItem> CREATOR = new Creator<>() {
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
		return orientation.getRotation();
	}

	public void setRotation(int rotation) {
		this.orientation.setRotation(rotation);
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

	/**
	 * Return duration of media object after a possible trimming has been applied
	 * @return duration in ms
	 */
	public long getTrimmedDurationMs() {
		return
			(startTimeMs != 0L && startTimeMs != TIME_UNDEFINED) ||
			(endTimeMs != TIME_UNDEFINED && endTimeMs != durationMs) ?
			endTimeMs - startTimeMs :
			durationMs;
	}

	/**
	 * Return true, if the video needs to be trimmed
	 */
	public boolean needsTrimming() {
		return getDurationMs() != getTrimmedDurationMs();
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public @Nullable String getCaption() {
		return caption;
	}

	public @Nullable String getTrimmedCaption() {
		if (caption != null) {
			return caption.trim();
		}
		return null;
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

	public Orientation getOrientation() {
		return orientation;
	}

	public int getFlip() {
		return orientation.getFlip();
	}

	public void setFlip(int flip) {
		this.orientation.setFlip(flip);
	}

	public void flip() {
		orientation.flip();
	}

	@BitmapUtil.FlipType
	public int getExifFlip() {
		return exifFlip;
	}

	public void setExifFlip(@BitmapUtil.FlipType int exifFlip) {
		this.exifFlip = exifFlip;
	}

	/**
	 * @return the MimeType override
	 */
	public String getMimeType() {
		return mimeType;
	}

	/**
	 * set MimeType override
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

	public @Nullable Uri getOriginalUri() {
		if (originalUri == null) {
			return uri;
		}
		return originalUri;
	}

	/**
	 * Set this to the original location of the file before creating a copy for persistence across activities
	 * @param originalUri Uri of the original media file
	 */
	public void setOriginalUri(Uri originalUri) {
		this.originalUri = originalUri;
	}

	/**
	 * Return true, if the picture has been edited (painted or cropped)
	 */
	public boolean isEdited() {
		return isEdited;
	}

	/**
	 * Mark this media item as edited (painted or cropped)
	 */
	public void setEdited(boolean edited) {
		this.isEdited = edited;
	}

	/**
	 * Return true, if the video should be sent without audio
	 */
	public boolean isMuted() {
		return muted;
	}

	/**
	 * Set flag to remove the audio before sending
	 */
	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	/**
	 * Return true if the media item has been modified. For images this includes: crop, painted,
	 * rotated, or flipped. For videos this includes: different start time, different end time
	 */
	public boolean hasChanges() {
		if (type == TYPE_IMAGE) {
			return isEdited() || orientation.getRotation() != 0 || orientation.getFlip() != BitmapUtil.FLIP_NONE;
		} else if (type == TYPE_VIDEO || type == TYPE_VIDEO_CAM) {
			return needsTrimming() || muted;
		} else {
			return false;
		}
	}

	/**
	 * Return true if the media item will be sent as file. This is the case for files, or for images
	 * or videos with the quality set to file.
	 */
	public boolean sendAsFile() {
		if (type == TYPE_VIDEO || type == TYPE_VIDEO_CAM) {
			return getVideoSize() == PreferenceService.VideoSize_SEND_AS_FILE;
		} else if (type == TYPE_IMAGE || type == TYPE_IMAGE_CAM) {
			return getImageScale() == PreferenceService.ImageScale_SEND_AS_FILE;
		} else {
			return type == TYPE_FILE || type == TYPE_GIF || type == TYPE_IMAGE_ANIMATED;
		}
	}

}
