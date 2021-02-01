/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import ch.threema.app.R;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.messageplayer.FileMessagePlayer;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.ImageViewUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.client.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.data.media.FileDataModel;

public class FileChatAdapterDecorator extends ChatAdapterDecorator {
	private static final Logger logger = LoggerFactory.getLogger(FileChatAdapterDecorator.class);

	private static final String LISTENER_TAG = "FileChatDecorator";

	private Context context;
	private FileDataModel fileData;
	private FileMessagePlayer fileMessagePlayer;

	public FileChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
		super(context, messageModel, helper);
		this.context = context;
	}

	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		this.fileMessagePlayer = (FileMessagePlayer) this.getMessagePlayerService().createPlayer(this.getMessageModel(), (Activity) context, this.helper.getMessageReceiver());

		holder.messagePlayer = fileMessagePlayer;
		holder.controller.setClickable(false);

		fileData = this.getMessageModel().getFileData();

		setThumbnail(holder, false);

		RuntimeUtil.runOnUiThread(() -> setControllerState(holder, fileData));

		if (holder.controller != null) {
			holder.controller.setOnClickListener(new DebouncedOnClickListener(500) {
				@Override
				public void onDebouncedClick(View v) {
					int status = holder.controller.getStatus();

					switch (status) {
						case ControllerView.STATUS_READY_TO_PLAY:
						case ControllerView.STATUS_READY_TO_DOWNLOAD:
						case ControllerView.STATUS_NONE:
							FileChatAdapterDecorator.this.prepareDownload(fileData, fileMessagePlayer);
							break;
						case ControllerView.STATUS_PROGRESSING:
							if (FileChatAdapterDecorator.this.getMessageModel().isOutbox() && (FileChatAdapterDecorator.this.getMessageModel().getState() == MessageState.PENDING || FileChatAdapterDecorator.this.getMessageModel().getState() == MessageState.SENDING)) {
								FileChatAdapterDecorator.this.getMessageService().cancelMessageUpload(FileChatAdapterDecorator.this.getMessageModel());
							} else {
								fileMessagePlayer.cancel();
							}
							break;
						case ControllerView.STATUS_READY_TO_RETRY:
							if (onClickRetry != null) {
								onClickRetry.onClick(FileChatAdapterDecorator.this.getMessageModel());
							}
					}
				}
			});
		}
		this.setOnClickListener(view -> prepareDownload(fileData, fileMessagePlayer), holder.messageBlockView);

		fileMessagePlayer
				.addListener(LISTENER_TAG, new MessagePlayer.PlaybackListener() {
					@Override
					public void onPlay(AbstractMessageModel messageModel, boolean autoPlay) {
						RuntimeUtil.runOnUiThread(() -> invalidate(holder, position));
					}

					@Override
					public void onPause(AbstractMessageModel messageModel) {}

					@Override
					public void onStatusUpdate(AbstractMessageModel messageModel, int position) {}

					@Override
					public void onStop(AbstractMessageModel messageModel) {}
				})
				.addListener(LISTENER_TAG, new MessagePlayer.DecryptionListener() {
					@Override
					public void onStart(AbstractMessageModel messageModel) {
						RuntimeUtil.runOnUiThread(() -> holder.controller.setProgressing(false));
					}

					@Override
					public void onEnd(AbstractMessageModel messageModel, final boolean success, final String message, File decryptedFile) {
						RuntimeUtil.runOnUiThread(() -> {
							if (!success) {
								holder.controller.setReadyToDownload();
								if (!TestUtil.empty(message)) {
									Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
								}
							} else {
								holder.controller.setHidden();
							}
						});
					}
				})
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
						RuntimeUtil.runOnUiThread(() -> {
							if (success) {
								if (FileUtil.isImageFile(fileData) && (fileData.getRenderingType() == FileData.RENDERING_STICKER || fileData.getRenderingType() == FileData.RENDERING_MEDIA)) {
									holder.controller.setHidden();
								} else {
									holder.controller.setNeutral();
									setThumbnail(holder, false);
								}
							} else {
								holder.controller.setReadyToDownload();
								if (!TestUtil.empty(message)) {
									Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
								}
							}
						});
					}
				});

		this.showHide(holder.tertiaryTextView, true);
		this.showHide(holder.secondaryTextView, true);
		this.showHide(holder.size, true);

		if (!TestUtil.empty(fileData.getCaption())) {
			holder.bodyTextView.setText(formatTextString(fileData.getCaption(), this.filterString));

			LinkifyUtil.getInstance().linkify(
				(ComposeMessageFragment) helper.getFragment(),
				holder.bodyTextView,
				this.getMessageModel(),
				fileData.getCaption().length() < 80,
				actionModeStatus.getActionModeEnabled(),
				onClickElement);

			this.showHide(holder.bodyTextView, true);
		} else {
			this.showHide(holder.bodyTextView, false);
		}

		if (holder.tertiaryTextView != null) {
			String fileName = fileData.getFileName();
			if (!TestUtil.empty(fileName)) {
				holder.tertiaryTextView.setText(highlightMatches(fileName, this.filterString));
			} else {
				holder.tertiaryTextView.setText(R.string.no_filename);
			}
		}

		if (holder.secondaryTextView != null) {
			String mimeString = fileData.getMimeType();
			if (holder.secondaryTextView != null) {
				if (!TestUtil.empty(mimeString)) {
					holder.secondaryTextView.setText(MimeUtil.getMimeDescription(context, fileData.getMimeType()));
				} else {
					holder.secondaryTextView.setText("");
				}
			}
		}

		if (holder.size != null) {
			long size = fileData.getFileSize();
			if (size > 0) {
				holder.size.setText(Formatter.formatShortFileSize(getContext(), fileData.getFileSize()));
			}
		}

		if (holder.dateView != null) {
			this.setDatePrefix(
				FileUtil.getFileMessageDatePrefix(getContext(),
				getMessageModel(),
				FileUtil.isImageFile(fileData) ? getContext().getString(R.string.image_placeholder) : null),
				0);
		}
	}

	private void prepareDownload(final FileDataModel fileData, final FileMessagePlayer fileMessagePlayer) {
		if (TestUtil.required(fileData, fileMessagePlayer)) {
			if (fileData.isDownloaded()) {
				fileMessagePlayer.open();
			} else {
				if (!this.getMessageModel().isOutbox()) {
					final PreferenceService preferenceService = getPreferenceService();

					if (preferenceService != null && !preferenceService.getFileSendInfoShown()) {
						new MaterialAlertDialogBuilder(getContext())
								.setTitle(R.string.download)
								.setMessage(R.string.send_as_files_warning)
								.setNegativeButton(R.string.cancel, null)
								.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										preferenceService.setFileSendInfoShown(true);
										fileMessagePlayer.open();
									}
								})
								.show();
					} else {
						fileMessagePlayer.open();
					}
				}
			}
		}
	}

	private void setThumbnail(ComposeMessageHolder holder, final boolean updateBitmap) {
		Bitmap thumbnail = null;
		try {
			thumbnail = this.getFileService().getMessageThumbnailBitmap(this.getMessageModel(),
				updateBitmap ? null : this.getThumbnailCache());
		} catch (Exception e) {
			//
		}

		if (FileUtil.isImageFile(fileData) && (fileData.getRenderingType() == FileData.RENDERING_STICKER || fileData.getRenderingType() == FileData.RENDERING_MEDIA)) {
			ImageViewUtil.showRoundedBitmap(
					getContext(),
					holder.contentView,
					holder.attachmentImage,
					thumbnail,
					helper.getThumbnailWidth()
			);
			holder.bodyTextView.setWidth(helper.getThumbnailWidth());
			if (holder.attachmentImage != null) {
				holder.attachmentImage.setVisibility(thumbnail != null ? View.VISIBLE : View.GONE);
				holder.attachmentImage.setContentDescription(getContext().getString(R.string.image_placeholder));
			}

			if (fileData.getRenderingType() == FileData.RENDERING_STICKER) {
				holder.messageBlockView.setBackground(null);
			} else {
				setDefaultBackground(holder);
			}
		} else {
			if (thumbnail == null) {
				try {
					thumbnail = getFileService().getDefaultMessageThumbnailBitmap(context, getMessageModel(), null, fileData.getMimeType());
					if (thumbnail != null) {
						thumbnail = AvatarConverterUtil.convert(getContext().getResources(), thumbnail, getContext().getResources().getColor(R.color.item_controller_color), Color.WHITE);
					}
				} catch (Exception e) {
					//
				}
			} else {
				thumbnail = AvatarConverterUtil.convert(getContext().getResources(), thumbnail);
			}

			if (thumbnail != null) {
				if (holder.controller != null) {
					holder.controller.setBackgroundImage(thumbnail);
				}
			}

			if (holder.attachmentImage != null) {
				holder.attachmentImage.setVisibility(View.GONE);
			}

			setDefaultBackground(holder);
		}
	}

	private void setControllerState(ComposeMessageHolder holder, FileDataModel fileData) {
		if (this.getMessageModel().isOutbox() && !(this.getMessageModel() instanceof DistributionListMessageModel)) {
			// outgoing message

			switch (this.getMessageModel().getState()) {
				case TRANSCODING:
					holder.controller.setTranscoding();
					if (holder.transcoderView != null) {
						holder.transcoderView.setProgress(holder.messagePlayer.getTranscodeProgress());
					}
					break;
				case PENDING:
					setThumbnail(holder, true);
					// fallthrough
				case SENDING:
					holder.controller.setProgressing();
					break;
				case SENDFAILED:
					holder.controller.setRetry();
					break;
				case SENT:
				default:
					if (FileUtil.isImageFile(fileData) && (fileData.getRenderingType() == FileData.RENDERING_MEDIA || fileData.getRenderingType() == FileData.RENDERING_STICKER)) {
						holder.controller.setHidden();
					} else {
						holder.controller.setNeutral();
					}
			}
		} else {
			// incoming message
			if (fileData != null) {
				if (fileData.isDownloaded()) {
					if (FileUtil.isImageFile(fileData) && (fileData.getRenderingType() == FileData.RENDERING_MEDIA || fileData.getRenderingType() == FileData.RENDERING_STICKER)) {
						holder.controller.setHidden();
					} else {
						holder.controller.setNeutral();
					}
				} else {
					holder.controller.setReadyToDownload();
				}
			}
			if (holder.messagePlayer != null) {
				switch (holder.messagePlayer.getState()) {
					case MessagePlayer.State_DOWNLOADING:
						holder.controller.setProgressingDeterminate(100);
						holder.controller.setProgress(holder.messagePlayer.getDownloadProgress());
						break;
					case MessagePlayer.State_DECRYPTING:
						holder.controller.setProgressing();
						break;
				}
			}
		}
	}
}
