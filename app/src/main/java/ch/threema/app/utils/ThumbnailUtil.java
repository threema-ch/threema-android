/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import org.slf4j.Logger;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.ui.MediaItem;
import ch.threema.base.utils.LoggingUtil;

public class ThumbnailUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ThumbnailUtil");

	private ThumbnailUtil() {}

	public static @Nullable byte[] generateThumbnailData(
		@NonNull Context context,
		@NonNull String mimeType,
		@Nullable File file
	) {
		Bitmap thumbnail = getThumbnailBitmapFromFile(context, file, mimeType);

		if (thumbnail != null) {
			byte[] thumbnailData = MimeUtil.MIME_TYPE_IMAGE_JPG.equals(mimeType)
				? BitmapUtil.bitmapToJpegByteArray(thumbnail)
				: BitmapUtil.bitmapToPngByteArray(thumbnail);
			thumbnail.recycle();
			return thumbnailData;
		} else {
			logger.warn("Could not generate thumbnail");
			return null;
		}
	}

	private static @Nullable Bitmap getThumbnailBitmapFromFile(
		@NonNull Context context,
		@Nullable File file,
		@NonNull String mimeType
	) {
		if (file == null || !file.exists()) {
			return null;
		}

		Uri uri = Uri.fromFile(file);

		switch (MimeUtil.getMediaTypeFromMimeType(mimeType)) {
			case MediaItem.TYPE_IMAGE:
				return BitmapUtil.safeGetBitmapFromUri(context, uri, MessageServiceImpl.THUMBNAIL_SIZE_PX, false, true);
			case MediaItem.TYPE_GIF:
				return IconUtil.getThumbnailFromUri(context, uri, MessageServiceImpl.THUMBNAIL_SIZE_PX, mimeType, true);
			case MediaItem.TYPE_VIDEO:
				return IconUtil.getVideoThumbnailFromUri(context, uri);
			default:
				return null;
		}
	}
}
