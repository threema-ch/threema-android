/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import com.google.android.exoplayer2.ui.DefaultTimeBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import ch.threema.client.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;

import static ch.threema.storage.models.data.media.FileDataModel.METADATA_KEY_DURATION;

public class VideoChatAdapterDecorator extends ChatAdapterDecorator {
	private static final Logger logger = LoggerFactory.getLogger(VideoChatAdapterDecorator.class);

	private static final String LISTENER_TAG = "decorator";

	public VideoChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
		super(context, messageModel, helper);
	}

	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		final MessagePlayer videoMessagePlayer = this.getMessagePlayerService().createPlayer(this.getMessageModel(),
				(Activity) this.getContext(), helper.getMessageReceiver());

		logger.debug("configureChatMessage Video on position " + position + " instance " + VideoChatAdapterDecorator.this + " holder " + holder + " messageplayer = " + videoMessagePlayer);

		holder.messagePlayer = videoMessagePlayer;

		RuntimeUtil.runOnUiThread(() -> setControllerState(holder));

		Bitmap thumbnail;
		try {
			thumbnail = this.getFileService().getMessageThumbnailBitmap(this.getMessageModel(),
					this.getThumbnailCache());
		} catch (Exception e) {
			logger.error("Exception", e);
			thumbnail = null;
		}

		this.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!isInChoiceMode() && getMessageModel().getState() != MessageState.TRANSCODING) {
					videoMessagePlayer.open();
				}
			}
		}, holder.messageBlockView);

		holder.controller.setOnClickListener(new DebouncedOnClickListener(500) {
			@Override
			public void onDebouncedClick(View v) {
				int status = holder.controller.getStatus();

				logger.debug("onClick status = " + status);

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
					case ControllerView.STATUS_READY_TO_RETRY:
						if (onClickRetry != null) {
							onClickRetry.onClick(getMessageModel());
						}
				}
			}
		});

		if (thumbnail != null) {
			ImageViewUtil.showRoundedBitmap(
					getContext(),
					holder.contentView,
					holder.attachmentImage,
					thumbnail,
					this.getThumbnailWidth()
			);
			holder.bodyTextView.setWidth(this.getThumbnailWidth());

		} else {
			ImageViewUtil.showPlaceholderBitmap(
				holder.contentView,
				holder.attachmentImage,
				this.getThumbnailWidth()
			);
			holder.bodyTextView.setWidth(0);
		}

		holder.attachmentImage.setContentDescription(getContext().getString(R.string.video_placeholder));

		this.showHide(holder.bodyTextView, false);

		if (this.getMessageModel().getType() == MessageType.VIDEO && this.getMessageModel().getVideoData() != null) {
			String datePrefixString = "";
			this.dateContentDescriptionPreifx = "";

			long duration = this.getMessageModel().getVideoData().getDuration();
			int size = this.getMessageModel().getVideoData().getVideoSize();

			//do not show duration if 0
			if (duration > 0) {
				datePrefixString = StringConversionUtil.secondsToString(duration, false);
				this.dateContentDescriptionPreifx = getContext().getString(R.string.duration) + ": " + StringConversionUtil.getDurationStringHuman(getContext(), duration);
			}

			if (size > 0) {
				datePrefixString += " (" + Formatter.formatShortFileSize(getContext(), size) + ")";
			}

			this.setDatePrefix(datePrefixString, holder.dateView.getTextSize());

			setDefaultBackground(holder);
		} else if (this.getMessageModel().getType() == MessageType.FILE && this.getMessageModel().getFileData() != null) {
			String datePrefixString = "";
			long duration = 0;

			Float durationF = this.getMessageModel().getFileData().getMetaDataFloat(METADATA_KEY_DURATION);
			if (durationF != null) {
				duration = durationF.longValue();
				if (duration > 0) {
					datePrefixString = StringConversionUtil.secondsToString(duration, false);
					this.dateContentDescriptionPreifx = getContext().getString(R.string.duration) + ": " + StringConversionUtil.getDurationStringHuman(getContext(), duration);
				}
			}

			if (this.getMessageModel().getFileData().isDownloaded()) {
				datePrefixString = "";
			} else {
				long size = this.getMessageModel().getFileData().getFileSize();
				if (size > 0) {
					if (duration > 0) {
						datePrefixString += " (" + Formatter.formatShortFileSize(getContext(), size) + ")";
					} else {
						datePrefixString = Formatter.formatShortFileSize(getContext(), size);
					}
				}
			}

			if (holder.dateView != null) {
				this.setDatePrefix(
					datePrefixString,
					0);
			}

			if (!TestUtil.empty(this.getMessageModel().getFileData().getCaption())) {
				holder.bodyTextView.setText(formatTextString(this.getMessageModel().getFileData().getCaption(), this.filterString));

				LinkifyUtil.getInstance().linkify(
					(ComposeMessageFragment) helper.getFragment(),
					holder.bodyTextView,
					this.getMessageModel(),
					this.getMessageModel().getFileData().getCaption().length() < 80,
					actionModeStatus.getActionModeEnabled(),
					onClickElement);

				this.showHide(holder.bodyTextView, true);
			} else {
				this.showHide(holder.bodyTextView, false);
			}

			if (this.getMessageModel().getFileData().getRenderingType() == FileData.RENDERING_STICKER) {
				holder.messageBlockView.setBackground(null);
			} else {
				setDefaultBackground(holder);
			}
		}

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
						RuntimeUtil.runOnUiThread(() -> {
							holder.transcoderView.setProgress(progress);
						});
					}
				});
	}

	private void setControllerState(ComposeMessageHolder holder) {
		if (this.getMessageModel().isOutbox() && !(this.getMessageModel() instanceof DistributionListMessageModel)) {
			// outgoing message
			logger.debug("**** Video MessageStatus: " + this.getMessageModel().getState());

			switch (this.getMessageModel().getState()) {
				case TRANSCODING:
					holder.controller.setTranscoding();
					if (holder.transcoderView != null) {
						holder.transcoderView.setMessageModel(this.getMessageModel());
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
		} else {
			// incoming message
			boolean isDownloaded = this.getMessageModel().getType() == MessageType.VIDEO ?
				(this.getMessageModel().getVideoData() != null && this.getMessageModel().getVideoData().isDownloaded()) :
				(this.getMessageModel().getFileData() != null && this.getMessageModel().getFileData().isDownloaded());

			if (isDownloaded) {
				holder.controller.setPlay();
			} else {
				holder.controller.setReadyToDownload();
			}

			if (holder.messagePlayer != null) {
				logger.debug("messagePlayerState: " + holder.messagePlayer.getState());

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
}
