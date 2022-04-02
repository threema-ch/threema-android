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

import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.core.os.BuildCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.SendMediaActivity;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.file.FileData;

import static ch.threema.app.services.MessageServiceImpl.THUMBNAIL_SIZE_PX;

public class ContentCommitComposeEditText extends ComposeEditText {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ContentCommitComposeEditText");

	private MessageReceiver messageReceiver;
	private MessageService messageService;

	public ContentCommitComposeEditText(Context context) {
		super(context);
		init();
	}

	public ContentCommitComposeEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public ContentCommitComposeEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public void setMessageReceiver(MessageReceiver messageReceiver) {
		this.messageReceiver = messageReceiver;
	}

	private void init() {
		try {
			this.messageService = ThreemaApplication.getServiceManager().getMessageService();
		} catch (ThreemaException | NullPointerException e) {
			logger.debug("MessageService not available");
		}
	}

	@Override
	public InputConnection onCreateInputConnection(@NonNull EditorInfo editorInfo) {
		final String[] mimeTypes = new String[]{"image/gif", "image/jpeg", "image/png"};

		final InputConnection ic = super.onCreateInputConnection(editorInfo);
		EditorInfoCompat.setContentMimeTypes(editorInfo, mimeTypes);

		final InputConnectionCompat.OnCommitContentListener callback =
			(inputContentInfo, flags, opts) -> {
				if (BuildCompat.isAtLeastNMR1() && (flags &
						InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
					try {
						inputContentInfo.requestPermission();
					} catch (Exception e) {
						return false; // return false if failed
					}
				}

				if (messageReceiver != null) {
					final Uri uri = inputContentInfo.getContentUri();
					final ClipDescription description = inputContentInfo.getDescription();
					final String mimeType = description.getMimeType(0);

					if (!isSticker(uri, mimeType)) {
						// go through SendMediaActivity if this item does not qualify as a sticker
						ArrayList<MediaItem> mediaItems = new ArrayList<>();
						mediaItems.add(new MediaItem(uri, mimeType, null));

						MessageReceiver[] messageReceivers = new MessageReceiver[1];
						messageReceivers[0] = messageReceiver;

						Intent intent = IntentDataUtil.addMessageReceiversToIntent(new Intent(getContext(), SendMediaActivity.class), messageReceivers);
						intent.putExtra(SendMediaActivity.EXTRA_MEDIA_ITEMS, mediaItems);
						intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, messageReceiver.getDisplayName());
						getContext().startActivity(intent);

					} else {
						String caption = null;

						if (messageService != null) {
							MediaItem mediaItem = new MediaItem(
								uri,
								MimeUtil.isGifFile(mimeType) ?
									MediaItem.TYPE_GIF :
									MediaItem.TYPE_IMAGE);
							mediaItem.setCaption(caption);
							mediaItem.setMimeType(mimeType);
							mediaItem.setRenderingType(
								MimeUtil.MIME_TYPE_IMAGE_JPG.equalsIgnoreCase(mimeType) ?
								FileData.RENDERING_MEDIA :
								FileData.RENDERING_STICKER);
							messageService.sendMediaAsync(Collections.singletonList(mediaItem), Collections.singletonList(messageReceiver));
						}
					}
					return true;
				}
				if (BuildCompat.isAtLeastNMR1()) {
					inputContentInfo.releasePermission();
				}
				return false;
			};

		return InputConnectionCompat.createWrapper(ic, editorInfo, callback);
	}

	private boolean isSticker(Uri uri, String mimeType) {
		boolean hasTransparency = false;
		if (MimeUtil.MIME_TYPE_IMAGE_PNG.equalsIgnoreCase(mimeType) || MimeUtil.MIME_TYPE_IMAGE_GIF.equals(mimeType)) {
			Bitmap thumbnailBitmap = IconUtil.getThumbnailFromUri(ThreemaApplication.getAppContext(), uri, THUMBNAIL_SIZE_PX, mimeType, true);
			if (thumbnailBitmap != null) {
				hasTransparency = BitmapUtil.hasTransparency(thumbnailBitmap);
				thumbnailBitmap.recycle();
			}
		}
		return hasTransparency;
	}
}

