/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

import androidx.annotation.NonNull;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ImageViewUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.data.media.ImageDataModel;

public class ImageChatAdapterDecorator extends ChatAdapterDecorator {
    private static final Logger logger = getThreemaLogger("ImageChatAdapterDecorator");

    private static final String LISTENER_TAG = "ImageDecorator";

    public ImageChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
        super(context, messageModel, helper);
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
        super.configureChatMessage(holder, position);

        final MessagePlayer imageMessagePlayer = getMessagePlayerService().createPlayer(getMessageModel(),
            (Activity) getContext(), helper.getMessageReceiver(), null);

        logger.debug("configureChatMessage Image");

        holder.messagePlayer = imageMessagePlayer;

        setOnClickListener(view -> {
            if (
                getMessageModel().getState() != MessageState.FS_KEY_MISMATCH &&
                    getMessageModel().getState() != MessageState.SENDFAILED
            ) {
                viewImage(getMessageModel(), holder.attachmentImage);
            }
        }, holder.messageBlockView);

        setControllerClickListener(holder, imageMessagePlayer);

        configureThumbnail(holder);

        if (getContext() != null && holder.attachmentImage != null) {
            holder.attachmentImage.setContentDescription(getContext().getString(R.string.image_placeholder));
        }

        RuntimeUtil.runOnUiThread(() -> {
            setupResendStatus(holder);
            setControllerState(holder, getMessageModel().getImageData());
        });

        configureBodyText(holder, getMessageModel().getCaption());

        configureMessagePlayer(holder, imageMessagePlayer);
    }

    private void configureMessagePlayer(@NonNull ComposeMessageHolder holder, @NonNull MessagePlayer imageMessagePlayer) {
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
                public void onUnknownProgress(AbstractMessageModel messageModel) {
                    RuntimeUtil.runOnUiThread(() -> holder.controller.setProgressing());
                }

                @Override
                public void onEnd(AbstractMessageModel messageModel, final boolean success, final String message) {
                    //hide progressbar
                    RuntimeUtil.runOnUiThread(() -> {
                        if (success) {
                            holder.controller.setHidden();
                        } else {
                            holder.controller.setReadyToDownload();
                            if (!TestUtil.isEmptyOrNull(message) && getContext() != null) {
                                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            });
    }

    private void setControllerClickListener(@NonNull ComposeMessageHolder holder, @NonNull MessagePlayer imageMessagePlayer) {
        if (holder.controller != null) {
            holder.controller.setOnClickListener(new DebouncedOnClickListener(500) {
                @Override
                public void onDebouncedClick(View v) {
                    int status = holder.controller.getStatus();

                    switch (status) {
                        case ControllerView.STATUS_READY_TO_RETRY:
                            propagateControllerRetryClickToParent();
                            break;
                        case ControllerView.STATUS_PROGRESSING:
                            if (MessageUtil.isFileMessageBeingSent(getMessageModel())) {
                                getMessageService().cancelMessageUpload(getMessageModel());
                            } else {
                                imageMessagePlayer.cancel();
                            }
                            break;
                        case ControllerView.STATUS_READY_TO_DOWNLOAD:
                            imageMessagePlayer.open();
                            break;
                        default:
                            viewImage(getMessageModel(), holder.attachmentImage);
                    }
                }
            });
        }
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

        ImageViewUtil.showBitmapOrImagePlaceholder(
            getContext(),
            holder.contentView,
            holder.attachmentImage,
            thumbnail,
            getThumbnailWidth()
        );
        holder.bodyTextView.setWidth(getThumbnailWidth());

        if (thumbnail == null) {
            holder.controller.setHidden();
        } else {
            showHide(holder.controller, false);
        }
    }

    private void viewImage(final AbstractMessageModel m, final View v) {
        if (m.isAvailable()) {
            Intent intent = new Intent(getContext(), MediaViewerActivity.class);
            IntentDataUtil.append(m, intent);
            intent.putExtra(MediaViewerActivity.EXTRA_ID_REVERSE_ORDER, true);
            ((Activity) getContext()).startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_MEDIA_VIEWER);
        }
    }

    private void setControllerState(@NonNull ComposeMessageHolder holder, @NonNull ImageDataModel imageDataModel) {
        if (holder.controller == null) {
            return;
        }
        AbstractMessageModel messageModel = getMessageModel();
        if (messageModel != null) {
            if (messageModel.isOutbox() && !(messageModel instanceof DistributionListMessageModel)) {
                setControllerStateOutgoingMessage(holder, messageModel);
            } else {
                // incoming message
                setControllerStateIncomingMessage(holder, imageDataModel, messageModel);
            }
        } else {
            holder.controller.setHidden();
        }
    }

    private void setControllerStateIncomingMessage(
        @NonNull ComposeMessageHolder holder,
        @NonNull ImageDataModel imageDataModel,
        @NonNull AbstractMessageModel messageModel
    ) {
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

    private void setControllerStateOutgoingMessage(@NonNull ComposeMessageHolder holder, @NonNull AbstractMessageModel messageModel) {
        switch (messageModel.getState()) {
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
                holder.controller.setHidden();
        }
    }
}
