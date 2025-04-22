/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

package ch.threema.app.services.messageplayer;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.slf4j.Logger;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

/**
 * A message player for animated image formats supported by AnimatedImageDrawable
 * Currently, this is limited to WebP
 */
public class AnimatedImageDrawableMessagePlayer extends MessagePlayer {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AnimatedImageDrawableMessagePlayer");

    private final PreferenceService preferenceService;
    private Drawable imageDrawable;
    private ImageView imageContainer;

    protected AnimatedImageDrawableMessagePlayer(Context context,
                                                 MessageService messageService,
                                                 FileService fileService,
                                                 PreferenceService preferenceService,
                                                 MessageReceiver messageReceiver,
                                                 AbstractMessageModel messageModel) {
        super(context, messageService, fileService, messageReceiver, messageModel);
        this.preferenceService = preferenceService;
    }

    public AnimatedImageDrawableMessagePlayer attachContainer(ImageView container) {
        this.imageContainer = container;
        return this;
    }

    @Override
    public MediaMessageDataInterface getData() {
        return this.getMessageModel().getFileData();
    }

    @Override
    protected AbstractMessageModel setData(MediaMessageDataInterface data) {
        AbstractMessageModel messageModel = this.getMessageModel();
        messageModel.setFileData((FileDataModel) data);
        return messageModel;
    }

    @Override
    protected void open(final File decryptedFile) {
        logger.debug("open(decryptedFile)");
        if (this.currentActivityRef != null && this.currentActivityRef.get() != null && this.isReceiverMatch(this.currentMessageReceiver)) {
            final String mimeType = getMessageModel().getFileData().getMimeType();

            if (!TestUtil.isEmptyOrNull(mimeType) && decryptedFile.exists()) {
                if (preferenceService.isAnimationAutoplay()) {
                    autoPlay(decryptedFile);
                } else {
                    openInExternalPlayer();
                }
            }
        }
    }

    public void autoPlay(final File decryptedFile) {
        logger.debug("autoPlay(decryptedFile)");

        if (this.imageContainer != null && this.currentActivityRef != null && this.currentActivityRef.get() != null && getMessageModel() != null) {
            this.makePause(SOURCE_UNDEFINED);

            final String mimeType = getMessageModel().getFileData().getMimeType();

            if (ConfigUtils.isDisplayableAnimatedImageFormat(mimeType)) {
                Glide.with(getContext())
                    .load(new File(decryptedFile.getPath()))
                    .optionalFitCenter()
                    .error(R.drawable.ic_image_outline)
                    .addListener(new RequestListener<>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                            imageDrawable = resource;
                            return false;
                        }
                    })
                    .into(imageContainer);
            }
        }
    }

    @Override
    public boolean open() {
        logger.debug("open");

        return super.open();
    }

    public boolean autoPlay() {
        logger.debug("autoPlay");

        return super.open(true);
    }

    public void openInExternalPlayer() {
        RuntimeUtil.runOnUiThread(() -> {
            if (currentActivityRef != null && currentActivityRef.get() != null && this.isReceiverMatch(currentMessageReceiver)) {
                Intent intent = new Intent(getContext(), MediaViewerActivity.class);
                IntentDataUtil.append(getMessageModel(), intent);
                intent.putExtra(MediaViewerActivity.EXTRA_ID_REVERSE_ORDER, true);
                currentActivityRef.get().startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_MEDIA_VIEWER);
            }
        });
    }

    @Override
    protected void makePause(int source) {
        logger.debug("makePause");
        if (this.imageContainer != null && this.imageDrawable != null) {
            if (imageDrawable instanceof Animatable) {
                if (((Animatable) imageDrawable).isRunning()) {
                    ((Animatable) this.imageDrawable).stop();
                }
            }
        }
    }

    @Override
    protected void makeResume(int source) {
        logger.debug("makeResume: " + getMessageModel().getId());
        if (this.imageContainer != null && this.imageDrawable != null) {
            if (imageDrawable instanceof Animatable) {
                if (!((Animatable) imageDrawable).isRunning()) {
                    ((Animatable) this.imageDrawable).start();
                }
            }
        }
    }

    @Override
    public void seekTo(int pos) {
    }

    @Override
    public int getDuration() {
        return 0;
    }

    @Override
    public int getPosition() {
        return 0;
    }

    @Override
    public void removeListeners() {
        super.removeListeners();
        logger.debug("removeListeners");

        // release animated image players if item comes out of view
        if (this.imageDrawable != null) {
            if (imageDrawable instanceof Animatable) {
                if (((Animatable) imageDrawable).isRunning()) {
                    ((Animatable) this.imageDrawable).stop();
                }
            }
            this.imageDrawable = null;
        }
    }
}
