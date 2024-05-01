/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.DrawableRes;
import androidx.annotation.WorkerThread;

import org.msgpack.core.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;

import ch.threema.app.R;
import ch.threema.app.ui.MediaItem;
import ch.threema.base.utils.LoggingUtil;

import static android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC;
import static ch.threema.app.services.MessageServiceImpl.THUMBNAIL_SIZE_PX;

public class IconUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("IconUtil");

	public static @DrawableRes int getMimeCategoryIcon(MimeUtil.MimeCategory category) {
		switch (category) {
			case APK:
				return R.drawable.ic_doc_apk;
			case AUDIO:
				return R.drawable.ic_doc_audio;
			case CERTIFICATE:
				return R.drawable.ic_doc_certificate;
			case CODES:
				return R.drawable.ic_doc_codes;
			case COMPRESSED:
				return R.drawable.ic_doc_compressed;
			case CONTACT:
				return R.drawable.ic_doc_contact;
			case DOCUMENT:
				return R.drawable.ic_doc_document;
			case EVENT:
				return R.drawable.ic_doc_event;
			case FOLDER:
				return R.drawable.ic_doc_folder;
			case FONT:
				return R.drawable.ic_doc_font;
			case IMAGE:
				return R.drawable.ic_doc_image;
			case PDF:
				return R.drawable.ic_doc_pdf;
			case PRESENTATION:
				return R.drawable.ic_doc_presentation;
			case SPREADSHEET:
				return R.drawable.ic_doc_spreadsheet;
			case TEXT:
				return R.drawable.ic_doc_text;
			case VIDEO:
				return R.drawable.ic_doc_video;
			case WORD:
				return R.drawable.ic_doc_word;
			case EXCEL:
				return R.drawable.ic_doc_excel;
			case POWERPOINT:
				return R.drawable.ic_doc_powerpoint;
			default:
				return R.drawable.ic_doc_file;
		}
	}

	public static @DrawableRes int getMimeIcon(@Nullable String mimeType) {
		MimeUtil.MimeCategory category = MimeUtil.getMimeCategory(mimeType);
		return getMimeCategoryIcon(category);
	}

	@WorkerThread
	public static @Nullable Bitmap getThumbnailFromUri(Context context, Uri uri, int thumbSize, String mimeType, boolean ignoreExifRotate) {
		logger.debug("getThumbnailFromUri");

		String docId = null;
		long imageId = -1;
		Bitmap thumbnailBitmap = null;
		ContentResolver contentResolver = context.getContentResolver();
		BitmapUtil.ExifOrientation exifOrientation = BitmapUtil.getExifOrientation(context, uri);

		if (!MimeUtil.MIME_TYPE_IMAGE_JPEG.equals(mimeType) && !MimeUtil.MIME_TYPE_IMAGE_PNG.equals(mimeType) && !MimeUtil.MIME_TYPE_IMAGE_HEIF.equals(mimeType) && !MimeUtil.MIME_TYPE_IMAGE_HEIC.equals(mimeType)) {
			if (DocumentsContract.isDocumentUri(context, uri)) {
				// Note: these thumbnails MAY or MAY NOT have EXIF rotation already applied. So we can't use them for JPEG
				Point thumbPoint = new Point(thumbSize, thumbSize);
				try {
					thumbnailBitmap = DocumentsContract.getDocumentThumbnail(contentResolver, uri, thumbPoint, new CancellationSignal());
					if (thumbnailBitmap != null) {
						return thumbnailBitmap;
					}
				} catch (Exception e) {
					// ignore - no thumbnail found
					logger.error("Exception", e);
				}

				// get id from document provider
				docId = DocumentsContract.getDocumentId(uri);
			} else if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
				docId = uri.getLastPathSegment();
			}

			if (!TestUtil.empty(docId)) {
				final String[] split = docId.split(":");
				if (split.length >= 2) {
					final String idString = split[1];

					if (!TestUtil.empty(idString)) {
						try {
							imageId = Long.parseLong(idString);
						} catch (NumberFormatException x) {
							logger.error("Exception", x);
						}
					}
				}
			}

			if (imageId == -1) {
				// query media store for thumbnail
				String[] columns = new String[]{MediaStore.Images.Thumbnails._ID};
				try (Cursor cursor = contentResolver.query(uri, columns, null, null, null)) {

					if (cursor != null && cursor.moveToFirst()) {
						imageId = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Thumbnails._ID));
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				}
			}

			logger.debug("Thumbnail image id: " + imageId);

			if (imageId > 0) {
				// may throw java.lang.SecurityException
				try {
					thumbnailBitmap = MediaStore.Images.Thumbnails.getThumbnail(contentResolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND, null);
				} catch (Exception e) {
					logger.error("Exception", e);
				}
			}
		}

		if (thumbnailBitmap == null) {
			// PNGs or GIFs may contain transparency
			boolean mayContainTransparency = MimeUtil.MIME_TYPE_IMAGE_PNG.equals(mimeType) || MimeUtil.MIME_TYPE_IMAGE_GIF.equals(mimeType);
			thumbnailBitmap = BitmapUtil.safeGetBitmapFromUri(context, uri, thumbSize, !mayContainTransparency, true, false);
		}

		if (thumbnailBitmap == null && MimeUtil.isVideoFile(mimeType)) {
			thumbnailBitmap = getVideoThumbnailFromUri(context, uri);

			if (thumbnailBitmap == null) {
				String path = FileUtil.getRealPathFromURI(context, uri);

				if (path != null) {
					thumbnailBitmap = ThumbnailUtils.createVideoThumbnail(
						path,
						MediaStore.Images.Thumbnails.MINI_KIND);
				}
			}
		}

		if (thumbnailBitmap != null && !ignoreExifRotate && (exifOrientation.getRotation() != 0f || exifOrientation.getFlip() != BitmapUtil.FLIP_NONE)) {
			return BitmapUtil.rotateBitmap(thumbnailBitmap, exifOrientation.getRotation(), exifOrientation.getFlip());
		}

		return thumbnailBitmap;
	}

	public static Bitmap getVideoThumbnailFromUri(Context context, Uri uri) {
		// do not use automatic resource management on MediaMetadataRetriever
		final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			retriever.setDataSource(context, uri);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
				return retriever.getScaledFrameAtTime(1, OPTION_CLOSEST_SYNC, THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX);
			} else {
				return BitmapUtil.resizeBitmapExactlyToMaxWidth(retriever.getFrameAtTime(1), THUMBNAIL_SIZE_PX);
			}
		} catch (Exception e) {
			//do not show the exception!
			logger.error("Exception", e);
		} finally {
			try {
				retriever.release();
			} catch (IOException e) {
				logger.debug("Failed to release MediaMetadataRetriever");
			}
		}
		return null;
	}

	@Nullable
	public static Bitmap getVideoThumbnailFromUri(Context context, MediaItem mediaItem) {
		long timeUs = mediaItem.getStartTimeMs() == 0 ? 1L : mediaItem.getStartTimeMs() * 1000;

		// do not use automatic resource management on MediaMetadataRetriever
		final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			retriever.setDataSource(context, mediaItem.getUri());
			// getScaledFrameAtTime() returns unfiltered bitmaps that look bad at low resolutions
			return BitmapUtil.resizeBitmapExactlyToMaxWidth(retriever.getFrameAtTime(timeUs), THUMBNAIL_SIZE_PX);
		} catch (Exception e) {
			//do not show the exception!
			logger.error("Exception", e);
		} finally {
			try {
				retriever.release();
			} catch (IOException e) {
				logger.debug("Failed to release MediaMetadataRetriever");
			}
		}
		return null;
	}
}
