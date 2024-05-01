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

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import ch.threema.app.R;
import ch.threema.app.exceptions.MalformedMimeTypeException;
import ch.threema.app.ui.MediaItem;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

import static ch.threema.app.ui.MediaItem.TYPE_FILE;
import static ch.threema.app.ui.MediaItem.TYPE_GIF;
import static ch.threema.app.ui.MediaItem.TYPE_IMAGE;
import static ch.threema.app.ui.MediaItem.TYPE_VIDEO;
import static ch.threema.app.ui.MediaItem.TYPE_VOICEMESSAGE;
import static ch.threema.domain.protocol.csp.messages.file.FileData.RENDERING_MEDIA;

public class MimeUtil {

	public static final String MIME_TYPE_ANY = "*/*";
	public static final String MIME_TYPE_VIDEO = "video/*";
	public static final String MIME_TYPE_AUDIO = "audio/*";
	public static final String MIME_TYPE_IMAGE = "image/*";
	public static final String MIME_TYPE_IMAGE_JPEG = "image/jpeg";
	// Due to a bug in android, some jpeg images may use 'image/jpg' as mime type. Note that we
	// should always use 'jpeg' instead of jpg.
	public static final String MIME_TYPE_IMAGE_JPG = "image/jpg";
	public static final String MIME_TYPE_IMAGE_PNG = "image/png";
	public static final String MIME_TYPE_IMAGE_GIF = "image/gif";
	public static final String MIME_TYPE_IMAGE_WEBP = "image/webp";
	public static final String MIME_TYPE_IMAGE_HEIF = "image/heif";
	public static final String MIME_TYPE_IMAGE_HEIC = "image/heic";
	public static final String MIME_TYPE_IMAGE_TIFF = "image/tiff";
	public static final String MIME_TYPE_IMAGE_SVG = "image/svg+xml";
	public static final String MIME_TYPE_VIDEO_MPEG = "video/mpeg";
	public static final String MIME_TYPE_VIDEO_MP4 = "video/mp4";
	public static final String MIME_TYPE_VIDEO_AVC = "video/avc";
	public static final String MIME_TYPE_AUDIO_AAC = "audio/aac";
	public static final String MIME_TYPE_AUDIO_M4A = "audio/x-m4a"; // mime type used by ios voice messages
	public static final String MIME_TYPE_AUDIO_MIDI = "audio/midi";
	public static final String MIME_TYPE_AUDIO_XMIDI = "audio/x-midi";
	public static final String MIME_TYPE_AUDIO_FLAC = "audio/flac";
	public static final String MIME_TYPE_AUDIO_XFLAC = "audio/x-flac";
	public static final String MIME_TYPE_AUDIO_OGG = "audio/ogg";
	public static final String MIME_TYPE_ZIP = "application/zip";
	public static final String MIME_TYPE_PDF = "application/pdf";
	public static final String MIME_TYPE_VCARD = "text/x-vcard";
	public static final String MIME_TYPE_VCARD_ALT = "text/vcard";
	public static final String MIME_TYPE_TEXT = "text/plain";
	public static final String MIME_TYPE_HTML = "text/html";
	public static final String MIME_TYPE_DEFAULT = "application/octet-stream";
	public static final String MIME_TYPE_EMAIL = "message/rfc822";
	public static final String MIME_TYPE_GPX = "application/gpx+xml";
	public static final String MIME_TYPE_APPLE_PKPASS = "application/vnd.apple.pkpass";

	public static final String MIME_VIDEO = "video/";
	public static final String MIME_AUDIO = "audio/";

	// map from icon resource id to string resource id
	protected static final EnumMap<MimeCategory, Integer> mimeToDescription = new EnumMap<>(MimeCategory.class);

	private static final Map<String, MimeCategory> mimeMap = new HashMap<>();

	static {
		mimeToDescription.put(MimeCategory.APK, R.string.mime_android_apk);
		mimeToDescription.put(MimeCategory.AUDIO, R.string.mime_audio);
		mimeToDescription.put(MimeCategory.CERTIFICATE, R.string.mime_certificate);
		mimeToDescription.put(MimeCategory.CODES, R.string.mime_codes);
		mimeToDescription.put(MimeCategory.COMPRESSED, R.string.mime_compressed);
		mimeToDescription.put(MimeCategory.CONTACT, R.string.mime_contact);
		mimeToDescription.put(MimeCategory.EVENT, R.string.mime_event);
		mimeToDescription.put(MimeCategory.FONT, R.string.mime_font);
		mimeToDescription.put(MimeCategory.IMAGE, R.string.mime_image);
		mimeToDescription.put(MimeCategory.PDF, R.string.mime_pdf);
		mimeToDescription.put(MimeCategory.PRESENTATION, R.string.mime_presentation);
		mimeToDescription.put(MimeCategory.SPREADSHEET, R.string.mime_spreadsheet);
		mimeToDescription.put(MimeCategory.TEXT, R.string.mime_text);
		mimeToDescription.put(MimeCategory.VIDEO, R.string.mime_video);
		mimeToDescription.put(MimeCategory.WORD, R.string.mime_word);
		mimeToDescription.put(MimeCategory.EXCEL, R.string.mime_spreadsheet);
		mimeToDescription.put(MimeCategory.POWERPOINT, R.string.mime_presentation);

		// Package
		mimeMap.put("application/vnd.android.package-archive", MimeCategory.APK);

		// Audio
		mimeMap.put("application/ogg", MimeCategory.AUDIO);
		mimeMap.put("application/x-flac", MimeCategory.AUDIO);

		// Certificate
		mimeMap.put("application/pgp-keys", MimeCategory.CERTIFICATE);
		mimeMap.put("application/pgp-signature", MimeCategory.CERTIFICATE);
		mimeMap.put("application/x-pkcs12", MimeCategory.CERTIFICATE);
		mimeMap.put("application/x-pkcs7-certreqresp", MimeCategory.CERTIFICATE);
		mimeMap.put("application/x-pkcs7-crl", MimeCategory.CERTIFICATE);
		mimeMap.put("application/x-x509-ca-cert", MimeCategory.CERTIFICATE);
		mimeMap.put("application/x-x509-user-cert", MimeCategory.CERTIFICATE);
		mimeMap.put("application/x-pkcs7-certificates", MimeCategory.CERTIFICATE);
		mimeMap.put("application/x-pkcs7-mime", MimeCategory.CERTIFICATE);
		mimeMap.put("application/x-pkcs7-signature", MimeCategory.CERTIFICATE);

		// Source code
		mimeMap.put("application/rdf+xml", MimeCategory.CODES);
		mimeMap.put("application/rss+xml", MimeCategory.CODES);
		mimeMap.put("application/x-object", MimeCategory.CODES);
		mimeMap.put("application/xhtml+xml", MimeCategory.CODES);
		mimeMap.put("text/css", MimeCategory.CODES);
		mimeMap.put(MIME_TYPE_HTML, MimeCategory.CODES);
		mimeMap.put("text/xml", MimeCategory.CODES);
		mimeMap.put("text/x-c++hdr", MimeCategory.CODES);
		mimeMap.put("text/x-c++src", MimeCategory.CODES);
		mimeMap.put("text/x-chdr", MimeCategory.CODES);
		mimeMap.put("text/x-csrc", MimeCategory.CODES);
		mimeMap.put("text/x-dsrc", MimeCategory.CODES);
		mimeMap.put("text/x-csh", MimeCategory.CODES);
		mimeMap.put("text/x-haskell", MimeCategory.CODES);
		mimeMap.put("text/x-java", MimeCategory.CODES);
		mimeMap.put("text/x-literate-haskell", MimeCategory.CODES);
		mimeMap.put("text/x-pascal", MimeCategory.CODES);
		mimeMap.put("text/x-tcl", MimeCategory.CODES);
		mimeMap.put("text/x-tex", MimeCategory.CODES);
		mimeMap.put("application/x-latex", MimeCategory.CODES);
		mimeMap.put("application/x-texinfo", MimeCategory.CODES);
		mimeMap.put("application/atom+xml", MimeCategory.CODES);
		mimeMap.put("application/ecmascript", MimeCategory.CODES);
		mimeMap.put("application/json", MimeCategory.CODES);
		mimeMap.put("application/javascript", MimeCategory.CODES);
		mimeMap.put("application/xml", MimeCategory.CODES);
		mimeMap.put("text/javascript", MimeCategory.CODES);
		mimeMap.put("application/x-javascript", MimeCategory.CODES);

		// Compressed
		mimeMap.put("application/mac-binhex40", MimeCategory.COMPRESSED);
		mimeMap.put("application/rar", MimeCategory.COMPRESSED);
		mimeMap.put(MIME_TYPE_ZIP, MimeCategory.COMPRESSED);
		mimeMap.put("application/x-apple-diskimage", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-debian-package", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-gtar", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-iso9660-image", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-lha", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-lzh", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-lzx", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-stuffit", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-tar", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-webarchive", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-webarchive-xml", MimeCategory.COMPRESSED);
		mimeMap.put("application/gzip", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-7z-compressed", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-deb", MimeCategory.COMPRESSED);
		mimeMap.put("application/x-rar-compressed", MimeCategory.COMPRESSED);

		// Contact
		mimeMap.put(MIME_TYPE_VCARD, MimeCategory.CONTACT);
		mimeMap.put(MIME_TYPE_VCARD_ALT, MimeCategory.CONTACT);

		// Document
		mimeMap.put("application/vnd.oasis.opendocument.text", MimeCategory.DOCUMENT);
		mimeMap.put("application/vnd.oasis.opendocument.text-master", MimeCategory.DOCUMENT);
		mimeMap.put("application/vnd.oasis.opendocument.text-template", MimeCategory.DOCUMENT);
		mimeMap.put("application/vnd.oasis.opendocument.text-web", MimeCategory.DOCUMENT);
		mimeMap.put("application/vnd.stardivision.writer", MimeCategory.DOCUMENT);
		mimeMap.put("application/vnd.stardivision.writer-global", MimeCategory.DOCUMENT);
		mimeMap.put("application/vnd.sun.xml.writer", MimeCategory.DOCUMENT);
		mimeMap.put("application/vnd.sun.xml.writer.global", MimeCategory.DOCUMENT);
		mimeMap.put("application/vnd.sun.xml.writer.template", MimeCategory.DOCUMENT);
		mimeMap.put("application/x-abiword", MimeCategory.DOCUMENT);
		mimeMap.put("application/x-kword", MimeCategory.DOCUMENT);

		// Event
		mimeMap.put("text/calendar", MimeCategory.EVENT);
		mimeMap.put("text/x-vcalendar", MimeCategory.EVENT);

		// Folder
		mimeMap.put(DocumentsContract.Document.MIME_TYPE_DIR, MimeCategory.FOLDER);

		// Font
		mimeMap.put("application/x-font", MimeCategory.FONT);
		mimeMap.put("application/font-woff", MimeCategory.FONT);
		mimeMap.put("application/x-font-woff", MimeCategory.FONT);
		mimeMap.put("application/x-font-ttf", MimeCategory.FONT);
		mimeMap.put("font/ttf", MimeCategory.FONT);
		mimeMap.put("font/woff", MimeCategory.FONT);

		// Image
		mimeMap.put("application/vnd.oasis.opendocument.graphics", MimeCategory.IMAGE);
		mimeMap.put("application/vnd.oasis.opendocument.graphics-template", MimeCategory.IMAGE);
		mimeMap.put("application/vnd.oasis.opendocument.image", MimeCategory.IMAGE);
		mimeMap.put("application/vnd.stardivision.draw", MimeCategory.IMAGE);
		mimeMap.put("application/vnd.sun.xml.draw", MimeCategory.IMAGE);
		mimeMap.put("application/vnd.sun.xml.draw.template", MimeCategory.IMAGE);

		// PDF
		mimeMap.put(MIME_TYPE_PDF, MimeCategory.PDF);

		// Presentation
		mimeMap.put("application/vnd.stardivision.impress", MimeCategory.PRESENTATION);
		mimeMap.put("application/vnd.sun.xml.impress", MimeCategory.PRESENTATION);
		mimeMap.put("application/vnd.sun.xml.impress.template", MimeCategory.PRESENTATION);
		mimeMap.put("application/x-kpresenter", MimeCategory.PRESENTATION);
		mimeMap.put("application/vnd.oasis.opendocument.presentation", MimeCategory.PRESENTATION);

		// Spreadsheet
		mimeMap.put("application/vnd.oasis.opendocument.spreadsheet", MimeCategory.SPREADSHEET);
		mimeMap.put("application/vnd.oasis.opendocument.spreadsheet-template", MimeCategory.SPREADSHEET);
		mimeMap.put("application/vnd.stardivision.calc", MimeCategory.SPREADSHEET);
		mimeMap.put("application/vnd.sun.xml.calc", MimeCategory.SPREADSHEET);
		mimeMap.put("application/vnd.sun.xml.calc.template", MimeCategory.SPREADSHEET);
		mimeMap.put("application/x-kspread", MimeCategory.SPREADSHEET);

		// Text
		mimeMap.put(MIME_TYPE_TEXT, MimeCategory.TEXT);

		// Video
		mimeMap.put("application/x-quicktimeplayer", MimeCategory.VIDEO);
		mimeMap.put("application/x-shockwave-flash", MimeCategory.VIDEO);

		// Word
		mimeMap.put("application/msword", MimeCategory.WORD);
		mimeMap.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", MimeCategory.WORD);
		mimeMap.put("application/vnd.openxmlformats-officedocument.wordprocessingml.template", MimeCategory.WORD);

		// Excel
		mimeMap.put("application/vnd.ms-excel", MimeCategory.EXCEL);
		mimeMap.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", MimeCategory.EXCEL);
		mimeMap.put("application/vnd.openxmlformats-officedocument.spreadsheetml.template", MimeCategory.EXCEL);

		// Powerpoint
		mimeMap.put("application/vnd.ms-powerpoint", MimeCategory.POWERPOINT);
		mimeMap.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", MimeCategory.POWERPOINT);
		mimeMap.put("application/vnd.openxmlformats-officedocument.presentationml.template", MimeCategory.POWERPOINT);
		mimeMap.put("application/vnd.openxmlformats-officedocument.presentationml.slideshow", MimeCategory.POWERPOINT);
	}

	/**
	 * List of bitmap image mime types natively supported by android
	 */
	private static final String[] supportedImageMimeTypes = {
		MIME_TYPE_IMAGE_JPEG,
		MIME_TYPE_IMAGE_JPG,
		MIME_TYPE_IMAGE_PNG,
		MIME_TYPE_IMAGE_GIF,
		MIME_TYPE_IMAGE_WEBP,
		MIME_TYPE_IMAGE_HEIC
	};

	public enum MimeCategory {
		APK,
		AUDIO,
		CERTIFICATE,
		CODES,
		COMPRESSED,
		CONTACT,
		DOCUMENT,
		EVENT,
		FOLDER,
		FONT,
		IMAGE,
		PDF,
		PRESENTATION,
		SPREADSHEET,
		TEXT,
		VIDEO,
		WORD,
		EXCEL,
		POWERPOINT,
		OTHER,
	}

	public static @NonNull MimeCategory getMimeCategory(@Nullable String mimeType) {
		MimeCategory category = mimeMap.get(mimeType);
		if (category == null && mimeType != null) {
			final String typeOnly = mimeType.split("/")[0];
			if ("audio".equals(typeOnly)) {
				category = MimeCategory.AUDIO;
			} else if ("image".equals(typeOnly)) {
				category = MimeCategory.IMAGE;
			} else if ("text".equals(typeOnly)) {
				category = MimeCategory.TEXT;
			} else if ("video".equals(typeOnly)) {
				category = MimeCategory.VIDEO;
			}
		}
		return category != null ? category : MimeCategory.OTHER;
	}

	public static @NonNull String getMimeDescription(@NonNull Context context, @NonNull String mimeType) {
		@StringRes Integer description = getMimeDescription(getMimeCategory(mimeType));
		if (description == null) {
			return mimeType;
		} else {
			return context.getString(description);
		}
	}

	public static @StringRes Integer getMimeDescription(@Nullable MimeCategory category) {
		return mimeToDescription.get(category);
	}

	public static boolean isMidiFile(String mimeType) {
		return mimeType.startsWith(MIME_TYPE_AUDIO_MIDI) || mimeType.startsWith(MIME_TYPE_AUDIO_XMIDI);
	}

	public static boolean isFlacFile(@NonNull String mimeType) {
		return mimeType.startsWith(MIME_TYPE_AUDIO_FLAC) || mimeType.startsWith(MIME_TYPE_AUDIO_XFLAC);
	}

	public static boolean isImageFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith("image/") && !MimeUtil.isSVGFile(mimeType);
	}

	/**
	 * Check if the current mime type hints to an Android-natively supported bitmap image format
	 * @param mimeType Mime Type to check such as "image/png"
	 * @return true if format is supported, false otherwise
	 */
	public static boolean isSupportedImageFile(@Nullable String mimeType) {
		if (mimeType != null && mimeType.startsWith("image/")) {
			for (String type: supportedImageMimeTypes) {
				if (mimeType.startsWith(type)) {
					return true;
				}
			}
		}
		return false;
	}

	public static String[] getSupportedImageMimeTypes() {
		return supportedImageMimeTypes;
	}

	public static boolean isVideoFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith("video/");
	}

	public static boolean isAudioFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith("audio/");
	}

	public static boolean isText(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith(MIME_TYPE_TEXT);
	}

	public static boolean isFileType(int mediaType) {
		return mediaType == MediaItem.TYPE_FILE;
	}

	public static boolean isGifFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith(MIME_TYPE_IMAGE_GIF);
	}

	public static boolean isWebPFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith(MIME_TYPE_IMAGE_WEBP);
	}

	public static boolean isPdfFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith(MIME_TYPE_PDF);
	}

	public static boolean isContactFile(@Nullable String mimeType) {
		return mimeType != null && (mimeType.startsWith(MIME_TYPE_VCARD) || mimeType.startsWith(MIME_TYPE_VCARD_ALT));
	}

	public static boolean isSVGFile(String mimeType) {
		return mimeType != null && mimeType.startsWith(MIME_TYPE_IMAGE_SVG);
	}

	@NonNull
	private static String getType(String mimeType) throws MalformedMimeTypeException {
		if (mimeType != null) {
			String[] parts = mimeType.split("/");
			if (parts.length == 2 && parts[0].length() > 0) {
				return parts[0];
			}
		}
		throw new MalformedMimeTypeException();
	}

	@NonNull
	private static String getSubType(String mimeType) throws MalformedMimeTypeException {
		if (mimeType != null) {
			String[] parts = mimeType.split("/");
			if (parts.length == 2 && parts[1].length() > 0) {
				String subType = parts[1];
				// strip parameter part
				parts = subType.split(";");
				if (parts.length > 1 && parts[1].length() > 0) {
					subType = parts[0];
				}
				return subType;
			}
		}
		throw new MalformedMimeTypeException();
	}

	@NonNull
	public static String getCommonMimeType(@NonNull String mimeType1, @NonNull String mimeType2) {
		try {
			if (getType(mimeType1).equals(getType(mimeType2))) {
				if (getSubType(mimeType1).equals(getSubType(mimeType2))) {
					return mimeType1;
				}
				return getType(mimeType1) + "/*";
			}
		} catch (MalformedMimeTypeException ignored) {}

		return MIME_TYPE_ANY;
	}

	public static @MessageContentsType int getContentTypeFromFileData(@NonNull FileDataModel fileDataModel) {
		String mimeType = fileDataModel.getMimeType();

		int messageContentsType = MessageContentsType.FILE;
		if (mimeType.length() > 0) {
			if (MimeUtil.isGifFile(mimeType)) {
				messageContentsType = MessageContentsType.GIF;
			} else if (MimeUtil.isImageFile(mimeType)) {
				messageContentsType = MessageContentsType.IMAGE;
			} else if (MimeUtil.isVideoFile(mimeType)) {
				messageContentsType = MessageContentsType.VIDEO;
			} else if (MimeUtil.isAudioFile(mimeType)) {
				if (fileDataModel.getRenderingType() == RENDERING_MEDIA) {
					messageContentsType = MessageContentsType.VOICE_MESSAGE;
				} else {
					messageContentsType = MessageContentsType.AUDIO;
				}
			} else if (MimeUtil.isContactFile(mimeType)) {
				messageContentsType = MessageContentsType.CONTACT;
			}
		}
		return messageContentsType;
	}

	@NonNull
	public static String getMimeTypeFromMessageModel(AbstractMessageModel messageModel) {
		String mimeType;

		switch (messageModel.getType()) {
			case IMAGE:
				mimeType = MimeUtil.MIME_TYPE_IMAGE_JPEG;
				break;
			case VIDEO:
				mimeType = MimeUtil.MIME_TYPE_VIDEO_AVC;
				break;
			case VOICEMESSAGE:
				mimeType = MimeUtil.MIME_TYPE_AUDIO_AAC;
				break;
			case TEXT:
				mimeType = MimeUtil.MIME_TYPE_TEXT;
				break;
			case FILE:
				mimeType = messageModel.getFileData().getMimeType();
				break;
			default:
				mimeType = MimeUtil.MIME_TYPE_DEFAULT;
				break;
		}
		return mimeType;
	}

	public static @MediaItem.MediaType int getMediaTypeFromMimeType(String mimeType, Uri uri) {
		if (MimeUtil.isSupportedImageFile(mimeType)) {
			if (MimeUtil.isGifFile(mimeType)) {
				return TYPE_GIF;
			} else {
				if (ConfigUtils.isSupportedAnimatedImageFormat(mimeType) && FileUtil.isAnimatedImageFile(uri)) {
					return MediaItem.TYPE_IMAGE_ANIMATED;
				}
				return TYPE_IMAGE;
			}
		} else if (MimeUtil.isVideoFile(mimeType)) {
			return TYPE_VIDEO;
		} else if (MimeUtil.isAudioFile(mimeType) && (mimeType.startsWith(MimeUtil.MIME_TYPE_AUDIO_AAC) || mimeType.startsWith(MimeUtil.MIME_TYPE_AUDIO_M4A))) {
			return TYPE_VOICEMESSAGE;
		}
		return TYPE_FILE;
	}
}
