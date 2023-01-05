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
import android.text.format.Formatter;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.slf4j.Logger;

import java.io.File;

import ch.threema.app.R;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ImageViewUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;

public class VideoChatAdapterDecorator extends ChatAdapterDecorator {
	private static final Logger logger = LoggingUtil.getThreemaLogger("VideoChatAdapterDecorator");

	private static final String LISTENER_TAG = "decorator";

	public VideoChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
		super(context, messageModel, helper);
	}

	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		final MessagePlayer videoMessagePlayer = getMessagePlayerService().createPlayer(getMessageModel(),
			(Activity) getContext(), helper.getMessageReceiver());

		logger.debug("configureChatMessage Video on position {} instance {} holder {} messageplayer = {}", position, this, holder, videoMessagePlayer);

		holder.messagePlayer = videoMessagePlayer;

		RuntimeUtil.runOnUiThread(() -> {
			setupResendStatus(holder);
			setControllerState(holder);
		});

		setOnClickListener(v -> {
			if (!isInChoiceMode() &&
				getMessageModel().getState() != MessageState.TRANSCODING &&
				getMessageModel().getState() != MessageState.SENDFAILED &&
				getMessageModel().getState() != MessageState.FS_KEY_MISMATCH
			) {
				videoMessagePlayer.open();
			}
		}, holder.messageBlockView);

		setControllerClickListener(holder, videoMessagePlayer);

		configureThumbnail(holder);

		if (getMessageModel().getType() == MessageType.VIDEO) {
			configureForMessageTypeVideo(holder);
		} else if (getMessageModel().getType() == MessageType.FILE) {
			configureForMessageTypeFile(holder);
		}

		configureVideoMessagePlayer(holder, videoMessagePlayer);
	}

	private void configureForMessageTypeVideo(@NonNull ComposeMessageHolder holder) {
		String datePrefixString = "";
		dateContentDescriptionPrefix = "";

		long duration = getMessageModel().getVideoData().getDuration();
		int size = getMessageModel().getVideoData().getVideoSize();

		//do not show duration if 0
		if (duration > 0) {
			datePrefixString = StringConversionUtil.secondsToString(duration, false);
			dateContentDescriptionPrefix = getContext().getString(R.string.duration) + ": " + StringConversionUtil.getDurationStringHuman(getContext(), duration);
		}

		if (size > 0) {
			datePrefixString += " (" + Formatter.formatShortFileSize(getContext(), size) + ")";
			dateContentDescriptionPrefix = getContext().getString(R.string.file_size) + ": " + Formatter.formatShortFileSize(getContext(), size);
		}

		setDatePrefix(datePrefixString, holder.dateView.getTextSize());

		setDefaultBackground(holder);
	}

	private void configureForMessageTypeFile(@NonNull ComposeMessageHolder holder) {
		String datePrefixString = getMessageModel().getFileData().getDurationString();
		long duration = getMessageModel().getFileData().getDurationSeconds();

		if (!getMessageModel().getFileData().isDownloaded()) {
			long size = getMessageModel().getFileData().getFileSize();
			if (size > 0) {
				if (duration > 0) {
					datePrefixString += " (" + Formatter.formatShortFileSize(getContext(), size) + ")";
				} else {
					datePrefixString = Formatter.formatShortFileSize(getContext(), size);
				}
				dateContentDescriptionPrefix = getContext().getString(R.string.file_size) + ": " + Formatter.formatShortFileSize(getContext(), size);
			}
		} else {
			dateContentDescriptionPrefix = getContext().getString(R.string.duration) + ": " + StringConversionUtil.getDurationStringHuman(getContext(), duration);
		}

		if (holder.dateView != null) {
			setDatePrefix(datePrefixString, 0);
		}

		configureBodyText(holder);

		configureBackground(holder);
	}

	private void configureBackground(@NonNull ComposeMessageHolder holder) {
		if (getMessageModel().getFileData().getRenderingType() == FileData.RENDERING_STICKER) {
			holder.messageBlockView.setBackground(null);
		} else {
			setDefaultBackground(holder);
		}
	}

	private void configureBodyText(@NonNull ComposeMessageHolder holder) {
		if (!TestUtil.empty(getMessageModel().getFileData().getCaption())) {
			holder.bodyTextView.setText(formatTextString(getMessageModel().getFileData().getCaption(), filterString));

			LinkifyUtil.getInstance().linkify(
				(ComposeMessageFragment) helper.getFragment(),
				holder.bodyTextView,
				getMessageModel(),
				true,
				actionModeStatus.getActionModeEnabled(),
				onClickElement);

			showHide(holder.bodyTextView, true);
		} else {
			showHide(holder.bodyTextView, false);
		}
	}

	private void configureVideoMessagePlayer(@NonNull ComposeMessageHolder holder, @NonNull MessagePlayer videoMessagePlayer) {
		videoMessagePlayer
			// decrypt listener
			.addListener(LISTENER_TAG, new MessagePlayer.DecryptionListener() {
				@Override
				public void onStart(AbstractMessageModel messageModel) {
					RuntimeUtil.runOnUiThread(() -> holder.controller.setProgressing(false));
				}

				@Override
				public void onEnd(final AbstractMessageModel messageModel, final boolean success, final String message, File decryptedFile) {
					RuntimeUtil.runOnUiThread(() -> {
						setControllerState(holder);
						if (!success) {
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
			})
			// transcode listener
			.addListener(LISTENER_TAG, new MessagePlayer.TranscodeListener() {
				@Override
				public void onStart() {
					RuntimeUtil.runOnUiThread(() -> {
						logger.debug("**** onStart");
						holder.transcoderView.setProgress(0);
					});
				}

				@Override
				public void onStatusUpdate(final int progress) {
					RuntimeUtil.runOnUiThread(() -> holder.transcoderView.setProgress(progress));
				}
			});
	}

	private void setControllerClickListener(@NonNull ComposeMessageHolder holder, @NonNull MessagePlayer videoMessagePlayer) {
		holder.controller.setOnClickListener(new DebouncedOnClickListener(500) {
			@Override
			public void onDebouncedClick(View v) {
				int status = holder.controller.getStatus();

				logger.debug("onClick status = {}", status);

				switch (status) {
					case ControllerView.STATUS_READY_TO_PLAY:
					case ControllerView.STATUS_READY_TO_DOWNLOAD:
						videoMessagePlayer.open();
						break;
					case ControllerView.STATUS_PROGRESSING:
						if (getMessageModel().isOutbox() && (getMessageModel().getState() == MessageState.PENDING || getMessageModel().getState() == MessageState.SENDING)) {
							getMessageService().cancelMessageUpload(getMessageModel());
						} else {
							videoMessagePlayer.cancel();
						}
						break;
					case ControllerView.STATUS_TRANSCODING:
						// no click while processing
						break;
					default:
						// no action taken for other statuses
						break;
				}
			}
		});
	}

	private void configureThumbnail(@NonNull ComposeMessageHolder holder) {
		Bitmap thumbnail;
		try {
			thumbnail = getFileService().getMessageThumbnailBitmap(getMessageModel(),
				getThumbnailCache());
		} catch (Exception e) {
			logger.error("Exception", e);
			thumbnail = null;
		}

		ImageViewUtil.showRoundedBitmapOrMoviePlaceholder(
			getContext(),
			holder.contentView,
			holder.attachmentImage,
			thumbnail,
			getThumbnailWidth()
		);
		holder.bodyTextView.setWidth(getThumbnailWidth());
		holder.attachmentImage.setContentDescription(getContext().getString(R.string.video_placeholder));
		showHide(holder.bodyTextView, false);
	}

	private void setControllerState(@NonNull ComposeMessageHolder holder) {
		if (getMessageModel().isOutbox() && !(getMessageModel() instanceof DistributionListMessageModel)) {
			setControllerStateOutgoingMessage(holder);
		} else {
			setControllerStateIncomingMessage(holder);
		}
	}

	private void setControllerStateOutgoingMessage(@NonNull ComposeMessageHolder holder) {
		logger.debug("**** Video MessageStatus: {}", getMessageModel().getState());

		switch (getMessageModel().getState()) {
			case TRANSCODING:
				holder.controller.setTranscoding();
				if (holder.transcoderView != null) {
					holder.transcoderView.setMessageModel(getMessageModel());
					holder.transcoderView.setVisibility(View.VISIBLE);
					holder.transcoderView.setProgress(holder.messagePlayer.getTranscodeProgress());
				}
				break;
			case PENDING:
			case SENDING:
				holder.controller.setProgressing();
				if (holder.transcoderView != null) {
					holder.transcoderView.setVisibility(View.GONE);
				}
				break;
			case SENDFAILED:
			case FS_KEY_MISMATCH:
				holder.controller.setRetry();
				if (holder.transcoderView != null) {
					holder.transcoderView.setVisibility(View.GONE);
				}
				break;
			case SENT:
			case DELIVERED:
			case READ:
				holder.controller.setPlay();
				if (holder.transcoderView != null) {
					holder.transcoderView.setVisibility(View.GONE);
				}
				break;
			default:
				break;
		}
	}

	private void setControllerStateIncomingMessage(@NonNull ComposeMessageHolder holder) {
		boolean isDownloaded = getMessageModel().getType() == MessageType.VIDEO
			? getMessageModel().getVideoData().isDownloaded()
			: getMessageModel().getFileData().isDownloaded();

		if (isDownloaded) {
			holder.controller.setPlay();
		} else {
			holder.controller.setReadyToDownload();
		}

		if (holder.messagePlayer != null) {
			logger.debug("messagePlayerState: {}", holder.messagePlayer.getState());

			switch (holder.messagePlayer.getState()) {
				case MessagePlayer.State_DOWNLOADING:
					if (!isDownloaded) {
						holder.controller.setProgressingDeterminate(100);
						holder.controller.setProgress(holder.messagePlayer.getDownloadProgress());
					}
					break;
				case MessagePlayer.State_DECRYPTING:
					holder.controller.setProgressing();
					break;
			}
		}
	}
}
