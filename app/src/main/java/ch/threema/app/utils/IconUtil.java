/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2020 Threema GmbH
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
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;

import org.msgpack.core.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import androidx.annotation.WorkerThread;
import ch.threema.app.R;
import ch.threema.app.ui.MediaItem;

import static android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC;
import static ch.threema.app.services.MessageServiceImpl.THUMBNAIL_SIZE_PX;

public class IconUtil {
	private static final Logger logger = LoggerFactory.getLogger(IconUtil.class);
	private static final HashMap<String, Integer> mimeIcons = new HashMap<>();

	private static void add(String mimeType, int resId) {
		if (mimeIcons.put(mimeType, resId) != null) {
			throw new RuntimeException(mimeType + " already registered!");
		}
	}

	static {
		int icon;

		// Package
		icon = R.drawable.ic_doc_apk;
		add("application/vnd.android.package-archive", icon);

		// Audio
		icon = R.drawable.ic_doc_audio;
		add("application/ogg", icon);
		add("application/x-flac", icon);

		// Certificate
		icon = R.drawable.ic_doc_certificate;
		add("application/pgp-keys", icon);
		add("application/pgp-signature", icon);
		add("application/x-pkcs12", icon);
		add("application/x-pkcs7-certreqresp", icon);
		add("application/x-pkcs7-crl", icon);
		add("application/x-x509-ca-cert", icon);
		add("application/x-x509-user-cert", icon);
		add("application/x-pkcs7-certificates", icon);
		add("application/x-pkcs7-mime", icon);
		add("application/x-pkcs7-signature", icon);

		// Source code
		icon = R.drawable.ic_doc_codes;
		add("application/rdf+xml", icon);
		add("application/rss+xml", icon);
		add("application/x-object", icon);
		add("application/xhtml+xml", icon);
		add("text/css", icon);
		add("text/html", icon);
		add("text/xml", icon);
		add("text/x-c++hdr", icon);
		add("text/x-c++src", icon);
		add("text/x-chdr", icon);
		add("text/x-csrc", icon);
		add("text/x-dsrc", icon);
		add("text/x-csh", icon);
		add("text/x-haskell", icon);
		add("text/x-java", icon);
		add("text/x-literate-haskell", icon);
		add("text/x-pascal", icon);
		add("text/x-tcl", icon);
		add("text/x-tex", icon);
		add("application/x-latex", icon);
		add("application/x-texinfo", icon);
		add("application/atom+xml", icon);
		add("application/ecmascript", icon);
		add("application/json", icon);
		add("application/javascript", icon);
		add("application/xml", icon);
		add("text/javascript", icon);
		add("application/x-javascript", icon);

		// Compressed
		icon = R.drawable.ic_doc_compressed;
		add("application/mac-binhex40", icon);
		add("application/rar", icon);
		add("application/zip", icon);
		add("application/x-apple-diskimage", icon);
		add("application/x-debian-package", icon);
		add("application/x-gtar", icon);
		add("application/x-iso9660-image", icon);
		add("application/x-lha", icon);
		add("application/x-lzh", icon);
		add("application/x-lzx", icon);
		add("application/x-stuffit", icon);
		add("application/x-tar", icon);
		add("application/x-webarchive", icon);
		add("application/x-webarchive-xml", icon);
		add("application/gzip", icon);
		add("application/x-7z-compressed", icon);
		add("application/x-deb", icon);
		add("application/x-rar-compressed", icon);

		// Contact
		icon = R.drawable.ic_doc_contact_am;
		add("text/x-vcard", icon);
		add("text/vcard", icon);

		// Event
		icon = R.drawable.ic_doc_event_am;
		add("text/calendar", icon);
		add("text/x-vcalendar", icon);

		// Font
		icon = R.drawable.ic_doc_font;
		add("application/x-font", icon);
		add("application/font-woff", icon);
		add("application/x-font-woff", icon);
		add("application/x-font-ttf", icon);

		// Image
		icon = R.drawable.ic_image_outline;
		add("application/vnd.oasis.opendocument.graphics", icon);
		add("application/vnd.oasis.opendocument.graphics-template", icon);
		add("application/vnd.oasis.opendocument.image", icon);
		add("application/vnd.stardivision.draw", icon);
		add("application/vnd.sun.xml.draw", icon);
		add("application/vnd.sun.xml.draw.template", icon);

		// PDF
		icon = R.drawable.ic_doc_pdf;
		add("application/pdf", icon);

		// Presentation
		icon = R.drawable.ic_doc_presentation;
		add("application/vnd.stardivision.impress", icon);
		add("application/vnd.sun.xml.impress", icon);
		add("application/vnd.sun.xml.impress.template", icon);
		add("application/x-kpresenter", icon);
		add("application/vnd.oasis.opendocument.presentation", icon);

		// Spreadsheet
		icon = R.drawable.ic_doc_spreadsheet_am;
		add("application/vnd.oasis.opendocument.spreadsheet", icon);
		add("application/vnd.oasis.opendocument.spreadsheet-template", icon);
		add("application/vnd.stardivision.calc", icon);
		add("application/vnd.sun.xml.calc", icon);
		add("application/vnd.sun.xml.calc.template", icon);
		add("application/x-kspread", icon);

		// Text
		icon = R.drawable.ic_doc_text_am;
		add("application/vnd.oasis.opendocument.text", icon);
		add("application/vnd.oasis.opendocument.text-master", icon);
		add("application/vnd.oasis.opendocument.text-template", icon);
		add("application/vnd.oasis.opendocument.text-web", icon);
		add("application/vnd.stardivision.writer", icon);
		add("application/vnd.stardivision.writer-global", icon);
		add("application/vnd.sun.xml.writer", icon);
		add("application/vnd.sun.xml.writer.global", icon);
		add("application/vnd.sun.xml.writer.template", icon);
		add("application/x-abiword", icon);
		add("application/x-kword", icon);

		// Video
		icon = R.drawable.ic_movie_outline;
		add("application/x-quicktimeplayer", icon);
		add("application/x-shockwave-flash", icon);

		// Word
		icon = R.drawable.ic_doc_word;
		add("application/msword", icon);
		add("application/vnd.openxmlformats-officedocument.wordprocessingml.document", icon);
		add("application/vnd.openxmlformats-officedocument.wordprocessingml.template", icon);

		// Excel
		icon = R.drawable.ic_doc_excel;
		add("application/vnd.ms-excel", icon);
		add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", icon);
		add("application/vnd.openxmlformats-officedocument.spreadsheetml.template", icon);

		// Powerpoint
		icon = R.drawable.ic_doc_powerpoint;
		add("application/vnd.ms-powerpoint", icon);
		add("application/vnd.openxmlformats-officedocument.presentationml.presentation", icon);
		add("application/vnd.openxmlformats-officedocument.presentationml.template", icon);
		add("application/vnd.openxmlformats-officedocument.presentationml.slideshow", icon);
	}

	public static int getMimeIcon(String mimeType) {
		if (mimeType == null) {
			return R.drawable.ic_doc_generic_am;
		}

		// folder
		if (Document.MIME_TYPE_DIR.equals(mimeType)) {
			return R.drawable.ic_doc_folder;
		}

		// Look for exact match first
		Integer resId = mimeIcons.get(mimeType);
		if (resId != null) {
			return resId;
		}

		// Otherwise look for partial match
		final String typeOnly = mimeType.split("/")[0];
		if ("audio".equals(typeOnly)) {
			return R.drawable.ic_doc_audio;
		} else if ("image".equals(typeOnly)) {
			return R.drawable.ic_image_outline;
		} else if ("text".equals(typeOnly)) {
			return R.drawable.ic_doc_text_am;
		} else if ("video".equals(typeOnly)) {
			return R.drawable.ic_movie_outline;
		} else {
			return R.drawable.ic_doc_generic_am;
		}
	}

	@WorkerThread
	public static @Nullable Bitmap getThumbnailFromUri(Context context, Uri uri, int thumbSize, String mimeType, boolean ignoreExifRotate) {
		logger.debug("getThumbnailFromUri");

		String docId = null;
		long imageId = -1;
		Bitmap thumbnailBitmap = null;
		ContentResolver contentResolver = context.getContentResolver();
		BitmapUtil.ExifOrientation exifOrientation = BitmapUtil.rotationForImage(context, uri);

		if (!MimeUtil.MIME_TYPE_IMAGE_JPG.equals(mimeType) && !MimeUtil.MIME_TYPE_IMAGE_PNG.equals(mimeType) && !MimeUtil.MIME_TYPE_IMAGE_HEIF.equals(mimeType) && !MimeUtil.MIME_TYPE_IMAGE_HEIC.equals(mimeType)) {
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
			} else if ("content".equalsIgnoreCase(uri.getScheme())) {
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
			thumbnailBitmap = BitmapUtil.safeGetBitmapFromUri(context, uri, thumbSize, mayContainTransparency, !mayContainTransparency, true);
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

		if (thumbnailBitmap != null && !ignoreExifRotate && (exifOrientation.getRotation() != 0f || exifOrientation.getFlip() != 0f)) {
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
			retriever.release();
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
			retriever.release();
		}
		return null;
	}
}
