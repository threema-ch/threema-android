/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

package ch.threema.app.adapters.decorators;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Toast;

import org.slf4j.Logger;

import java.io.File;

import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.services.messageplayer.GifMessagePlayer;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.ImageViewUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.data.media.FileDataModel;

public class AnimGifChatAdapterDecorator extends ChatAdapterDecorator {
	private static final Logger logger = LoggingUtil.getThreemaLogger("AnimGifChatAdapterDecorator");

	private static final String LISTENER_TAG = "decorator";
	private GifMessagePlayer gifMessagePlayer;

	public AnimGifChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper decoratorHelper) {
		super(context, messageModel, decoratorHelper);
	}

	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		final long fileSize;

		logger.debug("configureChatMessage - position " + position);

		gifMessagePlayer = (GifMessagePlayer) getMessagePlayerService().createPlayer(getMessageModel(), (Activity) getContext(), helper.getMessageReceiver());
		holder.messagePlayer = gifMessagePlayer;

		/*
		 * setup click listeners
		 */
		if (holder.controller != null) {
			holder.controller.setOnClickListener(v -> {
				int status = holder.controller.getStatus();

				switch (status) {
					case ControllerView.STATUS_READY_TO_PLAY:
					case ControllerView.STATUS_READY_TO_DOWNLOAD:
						gifMessagePlayer.open();
						break;
					case ControllerView.STATUS_PROGRESSING:
						if (getMessageModel().isOutbox() && (getMessageModel().getState() == MessageState.TRANSCODING ||
							getMessageModel().getState() == MessageState.PENDING ||
							getMessageModel().getState() == MessageState.SENDING)) {
							getMessageService().remove(getMessageModel());
						} else {
							gifMessagePlayer.cancel();
						}
						break;
					default:
						// no action taken for other statuses
						break;
				}
			});
		}
		setOnClickListener(v -> {
			if (!isInChoiceMode()) {
				if ((!getPreferenceService().isGifAutoplay() ||
					holder.controller.getStatus() == ControllerView.STATUS_READY_TO_DOWNLOAD)) {
					gifMessagePlayer.open();
				}
				if (getPreferenceService().isGifAutoplay() && holder.controller.getStatus() == ControllerView.STATUS_NONE) {
					gifMessagePlayer.openInExternalPlayer(null);
				}
			}
		}, holder.messageBlockView);

		/*
		 * get thumbnail
		 */
		Bitmap thumbnail;
		try {
			thumbnail = getFileService().getMessageThumbnailBitmap(getMessageModel(),
				getThumbnailCache());
		} catch (Exception e) {
			logger.error("Exception", e);
			thumbnail = null;
		}

		final FileDataModel fileData = getMessageModel().getFileData();
		fileSize = fileData.getFileSize();

		ImageViewUtil.showRoundedBitmapOrImagePlaceholder(
			getContext(),
			holder.contentView,
			holder.attachmentImage,
			thumbnail,
			getThumbnailWidth()
		);
		holder.bodyTextView.setWidth(getThumbnailWidth());

		if (holder.attachmentImage != null) {
			holder.attachmentImage.invalidate();
		}
		if (fileData.getRenderingType() == FileData.RENDERING_STICKER) {
			holder.messageBlockView.setBackground(null);
		} else {
			setDefaultBackground(holder);
		}

		if (!TestUtil.empty(fileData.getCaption())) {
			holder.bodyTextView.setText(formatTextString(fileData.getCaption(), filterString));
			showHide(holder.bodyTextView, true);
		} else {
			showHide(holder.bodyTextView, false);
		}

		RuntimeUtil.runOnUiThread(() -> setControllerState(holder, fileData, fileSize));

		setDatePrefix(FileUtil.getFileMessageDatePrefix(getContext(), getMessageModel(), "GIF"), 0);

		gifMessagePlayer
				.attachContainer(holder.attachmentImage)
				// decryption
				.addListener(LISTENER_TAG, new MessagePlayer.DecryptionListener() {
					@Override
					public void onStart(AbstractMessageModel messageModel) {
						RuntimeUtil.runOnUiThread(() -> {
							if (!helper.getPreferenceService().isGifAutoplay()) {
								holder.controller.setProgressing();
							}
						});
					}

					@Override
					public void onEnd(final AbstractMessageModel messageModel, final boolean success, final String message, final File decryptedFile) {
						RuntimeUtil.runOnUiThread(() -> {
							holder.controller.setNeutral();
							if (success) {
								if (helper.getPreferenceService().isGifAutoplay()) {
									holder.controller.setVisibility(View.INVISIBLE);
								} else {
									setControllerState(holder, messageModel.getFileData(), messageModel.getFileData().getFileSize());
								}
							} else {
								holder.controller.setVisibility(View.GONE);
								if (!TestUtil.empty(message)) {
									Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
								}
							}
						});
					}
				})
				// download listener
				.addListener(LISTENER_TAG, new MessagePlayer.DownloadListener() {
					@Override
					public void onStart(AbstractMessageModel messageModel) {
						RuntimeUtil.runOnUiThread(() -> holder.controller.setProgressingDeterminate(100));
					}

					@Override
					public void onStatusUpdate(AbstractMessageModel messageModel, final int progress) {
						RuntimeUtil.runOnUiThread(() -> holder.controller.setProgress(progress));
					}

					@Override
					public void onEnd(AbstractMessageModel messageModel, final boolean success, final String message) {
						//hide progressbar
						RuntimeUtil.runOnUiThread(() -> {
							// report error
							if (success) {
								holder.controller.setPlay();
							} else {
								holder.controller.setReadyToDownload();
								if (!TestUtil.empty(message)) {
									Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
								}
							}
						});
					}
				});

	}

	private void setControllerState(ComposeMessageHolder holder, FileDataModel fileData, long fileSize) {
		if (getMessageModel().isOutbox()) {
			// outgoing message
			switch (getMessageModel().getState()) {
				case TRANSCODING:
					holder.controller.setTranscoding();
					break;
				case PENDING:
				case SENDING:
					holder.controller.setProgressing();
					break;
				case SENDFAILED:
				case FS_KEY_MISMATCH:
					holder.controller.setRetry();
					break;
				default:
					setAutoplay(fileData, fileSize, holder);
			}
		} else {
			// incoming message
			if (getMessageModel() != null && getMessageModel().getState() == MessageState.PENDING) {
				if (fileData.isDownloaded()) {
					holder.controller.setProgressing();
				} else {
					holder.controller.setProgressingDeterminate(100);
				}
			} else {
				setAutoplay(fileData, fileSize, holder);
			}
		}
	}

	private void setAutoplay(FileDataModel fileData, long fileSize, ComposeMessageHolder holder) {
		logger.debug("setAutoPlay holder position " + holder.position);

		if (fileData.isDownloaded()) {
			if (helper.getPreferenceService().isGifAutoplay() && gifMessagePlayer != null) {
				gifMessagePlayer.autoPlay();
				holder.controller.setVisibility(View.INVISIBLE);
			} else {
				holder.controller.setPlay();
			}
		} else {
			if (helper.getPreferenceService().isGifAutoplay() && gifMessagePlayer != null && fileSize < MessageServiceImpl.FILE_AUTO_DOWNLOAD_MAX_SIZE_ISO) {
				gifMessagePlayer.autoPlay();
				holder.controller.setVisibility(View.INVISIBLE);
			} else {
				holder.controller.setReadyToDownload();
			}
		}
	}
}
