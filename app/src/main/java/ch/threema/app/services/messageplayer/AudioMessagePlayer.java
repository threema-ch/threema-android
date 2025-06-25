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

package ch.threema.app.services.messageplayer;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static androidx.media3.common.C.TIME_UNSET;
import static ch.threema.app.ThreemaApplication.getAppContext;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import ch.threema.app.R;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.logging.ThreemaLogger;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

public class AudioMessagePlayer extends MessagePlayer {
    private final Logger logger = LoggingUtil.getThreemaLogger("AudioMessagePlayer");

    private static final int SEEKBAR_UPDATE_FREQUENCY = 50;
    private File decryptedFile = null;
    private Uri decryptedFileUri = null;
    private int duration = 0; // duration in milliseconds
    private int position = 0; // position in milliseconds
    private Thread mediaPositionListener;
    private final PreferenceService preferenceService;
    @NonNull
    private final NotificationPreferenceService notificationPreferenceService;
    private final FileService fileService;
    private final ConversationCategoryService conversationCategoryService;
    private final ListenableFuture<MediaController> mediaControllerFuture;

    protected AudioMessagePlayer(
        @NonNull Context context,
        @NonNull MessageService messageService,
        @NonNull FileService fileService,
        @NonNull PreferenceService preferenceService,
        @NonNull NotificationPreferenceService notificationPreferenceService,
        @NonNull ConversationCategoryService conversationCategoryService,
        @NonNull MessageReceiver<?> messageReceiver,
        @NonNull ListenableFuture<MediaController> mediaControllerFuture,
        @NonNull AbstractMessageModel messageModel
    ) {
        super(context, messageService, fileService, messageReceiver, messageModel);

        this.preferenceService = preferenceService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.fileService = fileService;
        this.conversationCategoryService = conversationCategoryService;
        this.mediaControllerFuture = mediaControllerFuture;

        logger.info("New AudioMediaPlayer instance: {}", messageModel.getId());

        if (logger instanceof ThreemaLogger) {
            ((ThreemaLogger) logger).setPrefix(String.valueOf(messageModel.getId()));
        }
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsLoadingChanged(boolean isLoading) {
            logger.info(isLoading ? "onLoading" : "onLoaded");
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            MediaController mediaController = getMediaController();
            if (mediaController != null) {
                if (isPlaying) {
                    logger.info("onPlay");
                    makeResume(SOURCE_UI_TOGGLE);
                } else if (mediaController.getPlaybackState() != Player.STATE_ENDED && playerMediaMatchesControllerMedia()) {
                    logger.info("onPause");
                    makePause(SOURCE_UI_TOGGLE);
                }
            }
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                logger.info("onStopped");
                AudioMessagePlayer.super.stop();
                ListenerManager.messagePlayerListener.handle(listener -> listener.onAudioPlayEnded(getMessageModel(), mediaControllerFuture));
            } else if (playbackState == Player.STATE_READY) {
                logger.info("onReady");
                markAsConsumed();
                prepared();
            }
        }

        @Override
        public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                logger.info("onSeekEnded {} {} {}", reason, oldPosition.positionMs, newPosition.positionMs);

                // seek ended
                if (oldPosition != newPosition) {
                    position = (int) newPosition.positionMs;
                    onSeekCompleted();
                }
            }
        }
    };

    @Override
    public MediaMessageDataInterface getData() {
        if (getMessageModel().getType() == MessageType.VOICEMESSAGE) {
            return this.getMessageModel().getAudioData();
        } else {
            return this.getMessageModel().getFileData();
        }
    }

    @Override
    protected AbstractMessageModel setData(MediaMessageDataInterface data) {
        AbstractMessageModel messageModel = this.getMessageModel();
        if (messageModel.getType() == MessageType.VOICEMESSAGE) {
            messageModel.setAudioData((AudioDataModel) data);
        } else {
            messageModel.setFileData((FileDataModel) data);
        }
        return messageModel;
    }

    @Override
    protected void open(File decryptedFile) {
        this.decryptedFile = decryptedFile;
        this.decryptedFileUri = fileService.getShareFileUri(decryptedFile, null);
        this.position = 0;
        this.duration = 0;

        logger.info("Open voice message file {}", decryptedFileUri);

        MediaController mediaController = getMediaController();
        if (mediaController != null) {
            String displayName;
            Bitmap artworkBitmap = null;
            if (!this.notificationPreferenceService.isShowMessagePreview() || this.conversationCategoryService.isPrivateChat(currentMessageReceiver.getUniqueIdString())) {
                displayName = getContext().getString(R.string.notification_channel_voice_message_player);
            } else {
                displayName = currentMessageReceiver.getDisplayName();
                artworkBitmap = currentMessageReceiver.getAvatar();
            }

            MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(displayName)
                .setArtist(getContext().getString(R.string.voice_message_from,
                    LocaleUtil.formatTimeStampStringAbsolute(getAppContext(), getMessageModel().getCreatedAt().getTime())));

            if (artworkBitmap != null) {
                metadataBuilder.setArtworkData(BitmapUtil.bitmapToByteArray(artworkBitmap, Bitmap.CompressFormat.JPEG, 80), MediaMetadata.PICTURE_TYPE_FRONT_COVER);
            }

            final MediaItem mediaItem = new MediaItem.Builder()
                .setMediaMetadata(metadataBuilder.build())
                .setMediaId(decryptedFileUri.toString())
                .setUri(decryptedFileUri)
                .build();

            // cleanup old media player instance
            if (this.mediaPositionListener != null) {
                this.mediaPositionListener.interrupt();
            }
            mediaController.stop();
            mediaController.removeListener(playerListener);
            mediaController.clearMediaItems();

            // add new item and prepare
            mediaController.addMediaItem(mediaItem);
            mediaController.setPlayWhenReady(false);
            mediaController.addListener(playerListener);
            mediaController.prepare();

            logger.info("MediaController prepared");
        } else {
            logger.info("Unable to get MediaController");
        }
    }

    /**
     * called after the media player was prepared
     */
    private void prepared() {
        logger.info("Media Player is prepared");

        MediaController mediaController = getMediaController();
        if (mediaController == null) {
            return;
        }

        if (!playerMediaMatchesControllerMedia()) {
            // another media player
            logger.info("Player media does not match controller media");
            return;
        }

        final long longDuration = mediaController.getDuration();
        duration = (int) longDuration;
        if (longDuration == TIME_UNSET) {
            MediaMessageDataInterface d = this.getData();
            if (d instanceof AudioDataModel) {
                duration = (int) (((AudioDataModel) d).getDuration() * SECOND_IN_MILLIS);
            } else if (d instanceof FileDataModel) {
                duration = (int) (((FileDataModel) d).getDurationSeconds() * SECOND_IN_MILLIS);
            }
        }
        logger.info("Duration = {}", duration);

        if (this.position > mediaController.getCurrentPosition()) {
            mediaController.seekTo(this.position);
        } else {
            onSeekCompleted();
        }
    }

    private void onSeekCompleted() {
        logger.info("Seek completed. Play from position {}", this.position);

        MediaController mediaController = getMediaController();
        if (mediaController != null) {

            float audioPlaybackSpeed = preferenceService.getAudioPlaybackSpeed();
            mediaController.setPlaybackSpeed(audioPlaybackSpeed);

            float newPlaybackSpeed = mediaController.getPlaybackParameters().speed;

            if (audioPlaybackSpeed != newPlaybackSpeed) {
                preferenceService.setAudioPlaybackSpeed(newPlaybackSpeed);
            }

            mediaController.play();

            initPositionListener();
        }
    }

    private void initPositionListener() {
        logger.debug("initPositionListener");

        if (this.mediaPositionListener != null) {
            this.mediaPositionListener.interrupt();
        }

        this.mediaPositionListener = new Thread(() -> {
            logger.debug("initPositionListener Thread started");

            boolean cont = true;
            while (cont) {
                try {
                    Thread.sleep(SEEKBAR_UPDATE_FREQUENCY);

                    RuntimeUtil.runOnUiThread(() -> {
                        MediaController mediaController = getMediaController();
                        if (mediaController != null && mediaController.isConnected() && mediaController.isPlaying()) {
                            int newPosition = (int) mediaController.getCurrentPosition();
                            if (newPosition > position) {
                                position = newPosition;
                                this.updatePlayState();
                            }
                        }
                    });

                    cont = !Thread.interrupted();
                } catch (Exception e) {
                    cont = false;
                }
            }
            logger.debug("initPositionListener Thread ended");
        });
        this.mediaPositionListener.start();
    }

    @Override
    public void pause(int source) {
        MediaController mediaController = getMediaController();
        if (mediaController != null && playerMediaMatchesControllerMedia()) {
            mediaController.pause();
        }
    }

    @Override
    protected void makePause(int source) {
        logger.info("makePause with source {}", source);
        this.state = State_PAUSE;
        synchronized (this.playbackListeners) {
            for (Map.Entry<String, PlaybackListener> l : this.playbackListeners.entrySet()) {
                l.getValue().onPause(
                    getMessageModel()
                );
            }
        }
    }

    @Override
    protected void play(final boolean autoPlay) {
        logger.info("Play button pressed");
        if (this.state == State_PAUSE) {
            MediaController mediaController = getMediaController();
            if (mediaController != null) {
                if (playerMediaMatchesControllerMedia()) {
                    mediaController.play();
                } else {
                    open(decryptedFile);
                }
            }
            return;
        }

        super.play(autoPlay);
    }

    @Override
    protected void makeResume(int source) {
        logger.info("makeResume with source {} state (should be != 5) {}", source, state);
        this.state = State_PLAYING;
        synchronized (this.playbackListeners) {
            for (Map.Entry<String, PlaybackListener> l : this.playbackListeners.entrySet()) {
                l.getValue().onPlay(getMessageModel(), false);
            }
        }
    }

    private void releasePlayer() {
        logger.info("Release Player");

        if (mediaPositionListener != null) {
            logger.debug("mediaPositionListener.interrupt()");
            mediaPositionListener.interrupt();
            mediaPositionListener = null;
        }

        MediaController mediaController = getMediaController();
        if (mediaController != null) {
            if (playerMediaMatchesControllerMedia()) {
                logger.info("MediaController stopped and cleared");
                mediaController.stop();
                mediaController.clearMediaItems();
                this.position = 0;
                this.duration = 0;
            } else {
                mediaController.removeListener(playerListener);
                synchronized (this.playbackListeners) {
                    for (Map.Entry<String, PlaybackListener> l : this.playbackListeners.entrySet()) {
                        l.getValue().onStop(getMessageModel());
                    }
                }
            }
        }
    }

    private boolean playerMediaMatchesControllerMedia() {
        if (decryptedFile != null && decryptedFileUri != null) {
            MediaController mediaController = getMediaController();
            if (mediaController != null && mediaController.getMediaItemCount() > 0) {
                return decryptedFileUri.toString().equals(mediaController.getMediaItemAt(0).mediaId);
            }
        }
        return false;
    }

    @Override
    public boolean stop() {
        if (!playerMediaMatchesControllerMedia()) {
            logger.debug("stop");
            super.stop();
            releasePlayer();
        }
        return true;
    }

    @Override
    public float togglePlaybackSpeed(float preferenceSpeed) {
        float currentSpeed = preferenceSpeed;
        MediaController mediaController = getMediaController();
        if (mediaController != null) {
            currentSpeed = mediaController.getPlaybackParameters().speed;
        }

        float newSpeed = 1f;

        if (currentSpeed == 1f) {
            newSpeed = 1.25f;
        } else if (currentSpeed == 1.25f) {
            newSpeed = 1.5f;
        } else if (currentSpeed == 1.5f) {
            newSpeed = 2f;
        } else if (currentSpeed == 2f) {
            newSpeed = 0.5f;
        }

        if (mediaController != null) {
            mediaController.setPlaybackSpeed(newSpeed);
        }
        preferenceService.setAudioPlaybackSpeed(newSpeed);
        return newSpeed;
    }

    @Override
    public void seekTo(int pos) {
        if (pos >= 0) {
            MediaController mediaController = getMediaController();
            if (mediaController != null && playerMediaMatchesControllerMedia()) {
                mediaController.seekTo(pos);
            }
        }
    }

    @Override
    public int getDuration() {
        return this.duration;
    }

    @Override
    public int getPosition() {
        if (this.getState() == State_PLAYING || this.getState() == State_PAUSE) {
            return this.position;
        }
        return 0;
    }

    @Nullable
    public MediaController getMediaController() {
        if (mediaControllerFuture.isDone()) {
            try {
                return mediaControllerFuture.get();
            } catch (ExecutionException e) {
                logger.error("Media Controller exception", e);
            } catch (InterruptedException e) {
                logger.error("Media Controller interrupted exception", e);
                Thread.currentThread().interrupt();
            } catch (CancellationException e) {
                logger.error("Media Controller cancelled", e);
            }
        }
        return null;
    }
}

