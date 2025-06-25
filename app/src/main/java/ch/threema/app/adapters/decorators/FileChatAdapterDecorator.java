/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import ch.threema.app.R;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.messageplayer.FileMessagePlayer;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.ImageViewUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.data.media.FileDataModel;

public class FileChatAdapterDecorator extends ChatAdapterDecorator {

    private static final String LISTENER_TAG = "FileChatDecorator";

    private final Context context;
    private FileDataModel fileData;
    private FileMessagePlayer fileMessagePlayer;

    public FileChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
        super(context, messageModel, helper);
        this.context = context;
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
        fileMessagePlayer = (FileMessagePlayer) getMessagePlayerService().createPlayer(getMessageModel(), (Activity) context, helper.getMessageReceiver(), null);

        holder.messagePlayer = fileMessagePlayer;

        fileData = getMessageModel().getFileData();

        setThumbnail(holder, false);

        RuntimeUtil.runOnUiThread(() -> {
            setupResendStatus(holder);
            setControllerState(holder, fileData);
        });

        setControllerClickListener(holder);
        setOnClickListener(view -> {
            if (
                getMessageModel().getState() != MessageState.FS_KEY_MISMATCH &&
                    getMessageModel().getState() != MessageState.SENDFAILED
            ) {
                prepareDownload(fileData, fileMessagePlayer);
            }
        }, holder.messageBlockView);

        configureFileMessagePlayer(holder, position);
        configureBodyText(holder, fileData.getCaption());
        configureTertiaryText(holder);
        configureSecondaryText(holder);
        configureSizeText(holder);
        configureDateView(holder);
    }

    private void configureDateView(@NonNull ComposeMessageHolder holder) {
        if (holder.dateView != null) {
            setDatePrefix(
                FileUtil.getFileMessageDatePrefix(getContext(),
                    getMessageModel(),
                    FileUtil.isImageFile(fileData) ? getContext().getString(R.string.image_placeholder) : null)
            );
        }
    }

    private void configureSizeText(@NonNull ComposeMessageHolder holder) {
        showHide(holder.size, true);
        if (holder.size != null) {
            long size = fileData.getFileSize();
            if (size > 0) {
                holder.size.setText(Formatter.formatShortFileSize(getContext(), fileData.getFileSize()));
            }
        }
    }

    private void configureSecondaryText(@NonNull ComposeMessageHolder holder) {
        showHide(holder.secondaryTextView, true);
        if (holder.secondaryTextView != null) {
            String mimeString = fileData.getMimeType();
            if (holder.secondaryTextView != null) {
                if (!TestUtil.isEmptyOrNull(mimeString)) {
                    holder.secondaryTextView.setText(MimeUtil.getMimeDescription(context, fileData.getMimeType()));
                } else {
                    holder.secondaryTextView.setText("");
                }
            }
        }
    }

    private void configureTertiaryText(@NonNull ComposeMessageHolder holder) {
        showHide(holder.tertiaryTextView, true);
        if (holder.tertiaryTextView != null) {
            String fileName = fileData.getFileName();
            if (!TestUtil.isEmptyOrNull(fileName)) {
                holder.tertiaryTextView.setText(highlightMatches(fileName, filterString));
            } else {
                holder.tertiaryTextView.setText(R.string.no_filename);
            }
        }
    }

    private void configureFileMessagePlayer(@NonNull ComposeMessageHolder holder, int position) {
        fileMessagePlayer
            .addListener(LISTENER_TAG, new MessagePlayer.PlaybackListener() {
                @Override
                public void onPlay(AbstractMessageModel messageModel, boolean autoPlay) {
                    invalidate(holder, position);
                }

                @Override
                public void onPause(AbstractMessageModel messageModel) {
                }

                @Override
                public void onStatusUpdate(AbstractMessageModel messageModel, int position) {
                }

                @Override
                public void onStop(AbstractMessageModel messageModel) {
                }
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
                            if (!TestUtil.isEmptyOrNull(message)) {
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
                public void onUnknownProgress(AbstractMessageModel messageModel) {
                    RuntimeUtil.runOnUiThread(() -> holder.controller.setProgressing());
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
                            if (!TestUtil.isEmptyOrNull(message)) {
                                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            });
    }

    private void setControllerClickListener(@NonNull ComposeMessageHolder holder) {
        if (holder.controller != null) {
            holder.controller.setOnClickListener(new DebouncedOnClickListener(500) {
                @Override
                public void onDebouncedClick(View v) {
                    int status = holder.controller.getStatus();

                    switch (status) {
                        case ControllerView.STATUS_READY_TO_RETRY:
                            propagateControllerRetryClickToParent();
                            break;
                        case ControllerView.STATUS_READY_TO_PLAY:
                        case ControllerView.STATUS_READY_TO_DOWNLOAD:
                        case ControllerView.STATUS_NONE:
                            prepareDownload(fileData, fileMessagePlayer);
                            break;
                        case ControllerView.STATUS_PROGRESSING:
                            if (MessageUtil.isFileMessageBeingSent(getMessageModel())) {
                                getMessageService().cancelMessageUpload(getMessageModel());
                            } else {
                                fileMessagePlayer.cancel();
                            }
                            break;
                        default:
                            // no action taken for other statuses
                            break;
                    }
                }
            });
        }
    }

    private void prepareDownload(final FileDataModel fileData, final FileMessagePlayer fileMessagePlayer) {
        if (TestUtil.required(fileData, fileMessagePlayer)) {
            if (fileData.isDownloaded()) {
                fileMessagePlayer.open();
            } else {
                final PreferenceService preferenceService = getPreferenceService();

                if (preferenceService != null && !preferenceService.getFileSendInfoShown()) {
                    new MaterialAlertDialogBuilder(getContext())
                        .setTitle(R.string.download)
                        .setMessage(R.string.send_as_files_warning)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, (dialog, id) -> {
                            preferenceService.setFileSendInfoShown(true);
                            fileMessagePlayer.open();
                        })
                        .show();
                } else {
                    fileMessagePlayer.open();
                }
            }
        }
    }

    private void setThumbnail(ComposeMessageHolder holder, final boolean updateBitmap) {
        Bitmap thumbnail = null;
        try {
            thumbnail = getFileService().getMessageThumbnailBitmap(getMessageModel(),
                updateBitmap ? null : getThumbnailCache());
        } catch (Exception e) {
            //
        }

        if (FileUtil.isImageFile(fileData) && (fileData.getRenderingType() == FileData.RENDERING_STICKER || fileData.getRenderingType() == FileData.RENDERING_MEDIA)) {
            ImageViewUtil.showRoundedBitmapOrImagePlaceholder(
                getContext(),
                holder.contentView,
                holder.attachmentImage,
                thumbnail,
                helper.getThumbnailWidth()
            );
            holder.bodyTextView.setWidth(helper.getThumbnailWidth());
            if (holder.attachmentImage != null) {
                boolean hasDrawable = holder.attachmentImage.getDrawable() != null;
                holder.attachmentImage.setVisibility(hasDrawable ? View.VISIBLE : View.GONE);
                holder.attachmentImage.setContentDescription(getContext().getString(R.string.image_placeholder));
            }

            if (fileData.getRenderingType() == FileData.RENDERING_STICKER) {
                setStickerBackground(holder);
            } else {
                setDefaultBackground(holder);
            }
        } else {
            if (thumbnail != null) {
                if (holder.controller != null) {
                    holder.controller.setBackgroundImage(thumbnail);
                }
            } else {
                if (holder.controller != null) {
                    holder.controller.setIconResource(IconUtil.getMimeIcon(fileData.getMimeType()));
                }
            }

            if (holder.attachmentImage != null) {
                holder.attachmentImage.setVisibility(View.GONE);
            }

            setDefaultBackground(holder);
        }
    }

    private void setControllerState(@NonNull ComposeMessageHolder holder, @NonNull FileDataModel fileData) {
        if (fileData.isDownloaded()) {
            if (FileUtil.isImageFile(fileData) && (fileData.getRenderingType() == FileData.RENDERING_MEDIA || fileData.getRenderingType() == FileData.RENDERING_STICKER)) {
                holder.controller.setHidden();
            } else {
                holder.controller.setNeutral();
            }
        } else {
            holder.controller.setReadyToDownload();
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

        MessageState state = getMessageModel().getState();
        if (state == null) {
            return;
        }

        switch (state) {
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
            case UPLOADING:
                holder.controller.setProgressing();
                break;
            case SENDFAILED:
            case FS_KEY_MISMATCH:
                holder.controller.setRetry();
                break;
        }
    }
}
