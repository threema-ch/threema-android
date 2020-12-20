/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ImageViewUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.data.media.ImageDataModel;

public class ImageChatAdapterDecorator extends ChatAdapterDecorator {
	private static final Logger logger = LoggerFactory.getLogger(ImageChatAdapterDecorator.class);

	private static final String LISTENER_TAG = "ImageDecorator";

	public ImageChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
		super(context, messageModel, helper);
	}

	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		final MessagePlayer imageMessagePlayer = this.getMessagePlayerService().createPlayer(this.getMessageModel(),
				(Activity) this.getContext(), helper.getMessageReceiver());
		logger.debug("configureChatMessage Image");

		holder.messagePlayer = imageMessagePlayer;

		Bitmap thumbnail;
		try {
			thumbnail = this.getFileService().getMessageThumbnailBitmap(this.getMessageModel(),
					this.getThumbnailCache());
		} catch (Exception e) {
			logger.error("Exception", e);
			thumbnail = null;
		}

		this.setOnClickListener(view -> viewImage(getMessageModel(), holder.attachmentImage), holder.messageBlockView);

		if (holder.controller != null) {
			holder.controller.setOnClickListener(v -> {
				int status = holder.controller.getStatus();

				switch (status) {
					case ControllerView.STATUS_PROGRESSING:
						if (getMessageModel().isOutbox() && (getMessageModel().getState() == MessageState.PENDING || getMessageModel().getState() == MessageState.SENDING)) {
							getMessageService().cancelMessageUpload(getMessageModel());
						} else {
							imageMessagePlayer.cancel();
						}
						break;
					case ControllerView.STATUS_READY_TO_RETRY:
						if (onClickRetry != null) {
							onClickRetry.onClick(getMessageModel());
						}
						break;
					case ControllerView.STATUS_READY_TO_DOWNLOAD:
						imageMessagePlayer.open();
						break;
					default:
						viewImage(getMessageModel(), holder.attachmentImage);
				}
			});
		}

		if (thumbnail != null) {
			ImageViewUtil.showRoundedBitmap(
					getContext(),
					holder.contentView,
					holder.attachmentImage,
					thumbnail,
					this.getThumbnailWidth()
			);
			holder.bodyTextView.setWidth(this.getThumbnailWidth());
			this.showHide(holder.controller, false);
		} else {
			ImageViewUtil.showPlaceholderBitmap(
				holder.contentView,
				holder.attachmentImage,
				this.getThumbnailWidth()
			);
			holder.bodyTextView.setWidth(0);
			holder.controller.setHidden();
		}

		if (getContext() != null && holder.attachmentImage != null) {
			holder.attachmentImage.setContentDescription(getContext().getString(R.string.image_placeholder));
		}

		RuntimeUtil.runOnUiThread(() -> setControllerState(holder, getMessageModel().getImageData()));

		if (!TestUtil.empty(getMessageModel().getCaption())) {
			holder.bodyTextView.setText(formatTextString(getMessageModel().getCaption(), this.filterString));

			LinkifyUtil.getInstance().linkify(
				(ComposeMessageFragment) helper.getFragment(),
				holder.bodyTextView,
				this.getMessageModel(),
				getMessageModel().getCaption().length() < 80,
				actionModeStatus.getActionModeEnabled(),
				onClickElement);

			this.showHide(holder.bodyTextView, true);
		} else {
			this.showHide(holder.bodyTextView, false);
		}

		imageMessagePlayer
				// download listener
				.addListener(LISTENER_TAG, new MessagePlayer.DownloadListener() {
					@Override
					public void onStart(AbstractMessageModel messageModel) {
						RuntimeUtil.runOnUiThread(() -> holder.controller.setProgressing(false));
					}

					@Override
					public void onStatusUpdate(AbstractMessageModel messageModel, final int progress) {
					}

					@Override
					public void onEnd(AbstractMessageModel messageModel, final boolean success, final String message) {
						//hide progressbar
						RuntimeUtil.runOnUiThread(() -> {
							if (success) {
								holder.controller.setHidden();
							} else {
								holder.controller.setReadyToDownload();
								if (!TestUtil.empty(message) && getContext() != null) {
									Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
								}
							}
						});
					}
				});
	}

	private void viewImage(final AbstractMessageModel m, final View v) {
		if (m.isAvailable()) {
			Intent intent = new Intent(getContext(), MediaViewerActivity.class);
			IntentDataUtil.append(m, intent);
			intent.putExtra(MediaViewerActivity.EXTRA_ID_REVERSE_ORDER, true);
			AnimationUtil.startActivityForResult((Activity) getContext(), v, intent, ThreemaActivity.ACTIVITY_ID_MEDIA_VIEWER);
		}
	}

	private void setControllerState(@NonNull ComposeMessageHolder holder, ImageDataModel imageDataModel) {
		if (holder.controller == null) {
			return;
		}
		AbstractMessageModel messageModel = this.getMessageModel();
		if (messageModel != null) {
			if (messageModel.isOutbox() && !(messageModel instanceof DistributionListMessageModel)) {
				// outgoing message
				switch (messageModel.getState()) {
					case TRANSCODING:
						holder.controller.setTranscoding();
						break;
					case PENDING:
					case SENDING:
						holder.controller.setProgressing();
						break;
					case SENDFAILED:
						holder.controller.setRetry();
						break;
					default:
						holder.controller.setHidden();
				}
			} else {
				// incoming message
				if (TestUtil.required(imageDataModel)) {
					if (imageDataModel.isDownloaded()) {
						holder.controller.setHidden();
					} else {
						if (holder.messagePlayer.getState() == MessagePlayer.State_DOWNLOADING) {
							// set correct state if re-entering this chat
							holder.controller.setProgressing(false);
						} else {
							if (helper.getDownloadService().isDownloading(messageModel.getId())) {
								holder.controller.setProgressing(false);
							} else {
								holder.controller.setReadyToDownload();
							}
						}
					}
				}
			}
		} else {
			holder.controller.setHidden();
		}
	}
}
