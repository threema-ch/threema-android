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
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.slf4j.Logger;

import java.io.File;

import ch.threema.app.R;
import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.services.messageplayer.AnimatedImageDrawableMessagePlayer;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.data.media.FileDataModel;

/**
 * A decorator for animated image formats natively supported by AnimatedImageDrawable and/or by Glide
 */
public class AnimatedImageDrawableDecorator extends ChatAdapterDecorator {
    private static final Logger logger = getThreemaLogger("AnimatedImageDrawableDecorator");

    private static final String LISTENER_TAG = "decorator";
    private AnimatedImageDrawableMessagePlayer animatedImageDrawableMessagePlayer;

    public AnimatedImageDrawableDecorator(Context context, AbstractMessageModel messageModel, Helper decoratorHelper) {
        super(context, messageModel, decoratorHelper);
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
        final long fileSize;

        super.configureChatMessage(holder, position);

        logger.debug("configureChatMessage - position " + position);

        animatedImageDrawableMessagePlayer = (AnimatedImageDrawableMessagePlayer) getMessagePlayerService().createPlayer(getMessageModel(), (Activity) getContext(), helper.getMessageReceiver(), null);
        holder.messagePlayer = animatedImageDrawableMessagePlayer;

        /*
         * setup click listeners
         */
        if (holder.controller != null) {
            holder.controller.setOnClickListener(v -> {
                int status = holder.controller.getStatus();

                switch (status) {
                    case ControllerView.STATUS_READY_TO_RETRY:
                        propagateControllerRetryClickToParent();
                        break;
                    case ControllerView.STATUS_READY_TO_PLAY:
                    case ControllerView.STATUS_READY_TO_DOWNLOAD:
                        animatedImageDrawableMessagePlayer.open();
                        break;
                    case ControllerView.STATUS_PROGRESSING:
                        if (getMessageModel().isOutbox() && (getMessageModel().getState() == MessageState.TRANSCODING ||
                            getMessageModel().getState() == MessageState.PENDING ||
                            getMessageModel().getState() == MessageState.SENDING ||
                            getMessageModel().getState() == MessageState.UPLOADING)) {
                            getMessageService().remove(getMessageModel());
                        } else {
                            animatedImageDrawableMessagePlayer.cancel();
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
                if ((!getPreferenceService().isAnimationAutoplay() ||
                    holder.controller.getStatus() == ControllerView.STATUS_READY_TO_DOWNLOAD)) {
                    animatedImageDrawableMessagePlayer.open();
                }
                if (getPreferenceService().isAnimationAutoplay() && holder.controller.getStatus() == ControllerView.STATUS_NONE) {
                    animatedImageDrawableMessagePlayer.openInExternalPlayer();
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

        int width = getThumbnailWidth();
        int height;
        if (thumbnail != null) {
            height = (int) ((float) thumbnail.getHeight() * getThumbnailWidth() / thumbnail.getWidth());
        } else {
            height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        ViewGroup.LayoutParams params = holder.contentView.getLayoutParams();
        params.width = width;
        params.height = height;
        holder.contentView.setLayoutParams(params);

        params = holder.attachmentImage.getLayoutParams();
        params.width = width;
        params.height = height;
        holder.attachmentImage.setLayoutParams(params);
        holder.attachmentImage.setVisibility(View.VISIBLE);

        Glide.with(getContext())
            .load(thumbnail)
            .optionalFitCenter()
            .override(width, height)
            .error(R.drawable.ic_image_outline)
            .into(holder.attachmentImage);

        holder.bodyTextView.setWidth(width);

        if (holder.attachmentImage != null) {
            holder.attachmentImage.invalidate();
        }
        if (fileData.getRenderingType() == FileData.RENDERING_STICKER) {
            setStickerBackground(holder);
        } else {
            setDefaultBackground(holder);
        }

        configureBodyText(holder, fileData.getCaption());

        RuntimeUtil.runOnUiThread(() -> setControllerState(holder, fileData, fileSize));

        setDatePrefix(FileUtil.getFileMessageDatePrefix(getContext(), getMessageModel(), "WebP"));

        animatedImageDrawableMessagePlayer
            .attachContainer(holder.attachmentImage)
            // decryption
            .addListener(LISTENER_TAG, new MessagePlayer.DecryptionListener() {
                @Override
                public void onStart(AbstractMessageModel messageModel) {
                    RuntimeUtil.runOnUiThread(() -> {
                        if (!helper.getPreferenceService().isAnimationAutoplay()) {
                            holder.controller.setProgressing();
                        }
                    });
                }

                @Override
                public void onEnd(final AbstractMessageModel messageModel, final boolean success, final String message, final File decryptedFile) {
                    RuntimeUtil.runOnUiThread(() -> {
                        holder.controller.setNeutral();
                        if (success) {
                            if (helper.getPreferenceService().isAnimationAutoplay()) {
                                holder.controller.setVisibility(View.INVISIBLE);
                            } else {
                                setControllerState(holder, messageModel.getFileData(), messageModel.getFileData().getFileSize());
                            }
                        } else {
                            holder.controller.setVisibility(View.GONE);
                            if (!TestUtil.isEmptyOrNull(message)) {
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
                public void onUnknownProgress(AbstractMessageModel messageModel) {
                    RuntimeUtil.runOnUiThread(() -> holder.controller.setProgressing());
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
                            if (!TestUtil.isEmptyOrNull(message)) {
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
                case UPLOADING:
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
            if (helper.getPreferenceService().isAnimationAutoplay() && animatedImageDrawableMessagePlayer != null) {
                animatedImageDrawableMessagePlayer.autoPlay();
                holder.controller.setVisibility(View.INVISIBLE);
            } else {
                holder.controller.setPlay();
            }
        } else {
            if (helper.getPreferenceService().isAnimationAutoplay() && animatedImageDrawableMessagePlayer != null && fileSize < MessageServiceImpl.FILE_AUTO_DOWNLOAD_MAX_SIZE_ISO) {
                animatedImageDrawableMessagePlayer.autoPlay();
                holder.controller.setVisibility(View.INVISIBLE);
            } else {
                holder.controller.setReadyToDownload();
            }
        }
    }
}
