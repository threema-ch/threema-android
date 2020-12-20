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
import android.net.Uri;

import java.lang.annotation.Retention;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.exceptions.MalformedMimeTypeException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.MessageContentsType;

import static ch.threema.app.ThreemaApplication.getAppContext;
import static java.lang.annotation.RetentionPolicy.SOURCE;

public class MimeUtil {

	public static final String MIME_TYPE_ANY = "*/*";
	public static final String MIME_TYPE_VIDEO = "video/*";
	public static final String MIME_TYPE_AUDIO = "audio/*";
	public static final String MIME_TYPE_IMAGE = "image/*";
	public static final String MIME_TYPE_IMAGE_JPG = "image/jpeg";
	public static final String MIME_TYPE_IMAGE_PNG = "image/png";
	public static final String MIME_TYPE_IMAGE_GIF = "image/gif";
	public static final String MIME_TYPE_IMAGE_HEIF = "image/heif";
	public static final String MIME_TYPE_IMAGE_HEIC = "image/heic";
	public static final String MIME_TYPE_IMAGE_TIFF = "image/tiff";
	public static final String MIME_TYPE_VIDEO_MPEG = "video/mpeg";
	public static final String MIME_TYPE_VIDEO_AVC = "video/avc";
	public static final String MIME_TYPE_AUDIO_AAC = "audio/aac";
	public static final String MIME_TYPE_AUDIO_MIDI = "audio/midi";
	public static final String MIME_TYPE_AUDIO_XMIDI = "audio/x-midi";
	public static final String MIME_TYPE_AUDIO_FLAC = "audio/flac";
	public static final String MIME_TYPE_AUDIO_XFLAC = "audio/x-flac";
	public static final String MIME_TYPE_ZIP = "application/zip";
	public static final String MIME_TYPE_PDF = "application/pdf";
	public static final String MIME_TYPE_VCARD = "text/x-vcard";
	public static final String MIME_TYPE_VCARD_ALT = "text/vcard";
	public static final String MIME_TYPE_TEXT = "text/plain";
	public static final String MIME_TYPE_HTML = "text/html";
	public static final String MIME_TYPE_DEFAULT = "application/octet-stream";

	public static final String MIME_VIDEO = "video/";
	public static final String MIME_AUDIO = "audio/";

	@Retention(SOURCE)
	@IntDef({MIME_TYPE_VIDEO_IND, MIME_TYPE_IMAGES_IND, MIME_TYPE_GIF_IND})
	public @interface NavigationMode {}
	public static final int MIME_TYPE_VIDEO_IND = 101;
	public static final int MIME_TYPE_IMAGES_IND = 102;
	public static final int MIME_TYPE_GIF_IND = 103;

	// map from icon resource id to string resource id
	protected static final Map<Integer, Integer> mimeToDescription = new HashMap<Integer, Integer>(){
		{
			put(R.drawable.ic_doc_apk, R.string.mime_android_apk);
			put(R.drawable.ic_doc_audio, R.string.mime_audio);
			put(R.drawable.ic_doc_certificate, R.string.mime_certificate);
			put(R.drawable.ic_doc_codes, R.string.mime_codes);
			put(R.drawable.ic_doc_compressed, R.string.mime_compressed);
			put(R.drawable.ic_doc_contact_am, R.string.mime_contact);
			put(R.drawable.ic_doc_event_am, R.string.mime_event);
			put(R.drawable.ic_doc_font, R.string.mime_font);
			put(R.drawable.ic_image_outline, R.string.mime_image);
			put(R.drawable.ic_doc_pdf, R.string.mime_pdf);
			put(R.drawable.ic_doc_presentation, R.string.mime_presentation);
			put(R.drawable.ic_doc_spreadsheet_am, R.string.mime_spreadsheet);
			put(R.drawable.ic_doc_text_am, R.string.mime_text);
			put(R.drawable.ic_movie_outline, R.string.mime_video);
			put(R.drawable.ic_doc_word, R.string.mime_word);
			put(R.drawable.ic_doc_excel, R.string.mime_spreadsheet);
			put(R.drawable.ic_doc_powerpoint, R.string.mime_presentation);
		}
	};

	public static String getMimeDescription(Context context, String mimeType) {
		int iconRes = IconUtil.getMimeIcon(mimeType);

		if (iconRes == R.drawable.ic_doc_generic_am || iconRes == R.drawable.ic_doc_folder) {
			return mimeType;
		} else {
			return context.getString(mimeToDescription.get(iconRes));
		}
	}

	public static boolean isMidiFile(String mimeType) {
		return mimeType.startsWith(MIME_TYPE_AUDIO_MIDI) || mimeType.startsWith(MIME_TYPE_AUDIO_XMIDI);
	}

	public static boolean isFlacFile(@NonNull String mimeType) {
		return mimeType.startsWith(MIME_TYPE_AUDIO_FLAC) || mimeType.startsWith(MIME_TYPE_AUDIO_XFLAC);
	}

	public static boolean isImageFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith("image/");
	}

	public static boolean isLabelableImageFile(@Nullable String mimeType) {
		return mimeType != null && (mimeType.startsWith(MIME_TYPE_IMAGE_PNG) || mimeType.startsWith(MIME_TYPE_IMAGE_JPG)
			|| mimeType.startsWith(MIME_TYPE_IMAGE_GIF) || mimeType.startsWith(MIME_TYPE_IMAGE_HEIF) || mimeType.startsWith(MIME_TYPE_IMAGE_HEIC));
	}

	public static boolean isStaticImageFile(@Nullable String mimeType) {
		return mimeType != null && (mimeType.startsWith(MIME_TYPE_IMAGE_PNG) || mimeType.startsWith(MIME_TYPE_IMAGE_JPG)
		|| mimeType.startsWith(MIME_TYPE_IMAGE_HEIF) || mimeType.startsWith(MIME_TYPE_IMAGE_HEIC) || mimeType.startsWith(MIME_TYPE_IMAGE_TIFF));
	}

	public static boolean isVideoFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith("video/");
	}

	public static boolean isAudioFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith("audio/");
	}

	public static boolean isTextFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith(MIME_TYPE_TEXT);
	}

	public static boolean isGifFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith(MIME_TYPE_IMAGE_GIF);
	}

	public static boolean isPdfFile(@Nullable String mimeType) {
		return mimeType != null && mimeType.startsWith(MIME_TYPE_PDF);
	}

	public static boolean isContactFile(@Nullable String mimeType) {
		return mimeType != null && (mimeType.startsWith(MIME_TYPE_VCARD) || mimeType.startsWith(MIME_TYPE_VCARD_ALT));
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

	public static @MessageContentsType int getContentTypeFromMimeType(String mimeType) {
		int messageContentsType = MessageContentsType.FILE;
		if (mimeType != null && mimeType.length() > 0) {
			if (MimeUtil.isGifFile(mimeType)) {
				messageContentsType = MessageContentsType.GIF;
			} else if (MimeUtil.isImageFile(mimeType)) {
				messageContentsType = MessageContentsType.IMAGE;
			} else if (MimeUtil.isVideoFile(mimeType)) {
				messageContentsType = MessageContentsType.VIDEO;
			} else if (MimeUtil.isAudioFile(mimeType)) {
				messageContentsType = MessageContentsType.AUDIO;
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
				mimeType = MimeUtil.MIME_TYPE_IMAGE_JPG;
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

	/**
	 * Get the mime type of the file referenced to by the specified Uri
	 * @param uri A content Uri identifying content
	 * @return A MIME type for the content, or null if the URL is invalid or the type is unknown
	 */
	@Nullable
	public static String getMimeTypeFromUri(Uri uri) {
		ContentResolver cR = getAppContext().getContentResolver();
		return cR.getType(uri);
	}
}
