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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import org.slf4j.Logger;

import java.io.File;

import ch.threema.app.R;
import ch.threema.app.services.ActivityService;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.AudioProgressBarView;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.logging.ThreemaLogger;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.FileDataModel;

import static ch.threema.app.utils.MessageUtilKt.getUiContentColor;

public class AudioChatAdapterDecorator extends ChatAdapterDecorator {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AudioChatAdapterDecorator");

    private static final String LISTENER_TAG = "decorator";
    private MessagePlayer audioMessagePlayer;

    public AudioChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
        super(context, messageModel, helper);

        if (logger instanceof ThreemaLogger) {
            ((ThreemaLogger) logger).setPrefix(String.valueOf(getMessageModel().getId()));
        }

        logger.info("New AudioChatAdapterDecorator instance for {}", messageModel.getId());
    }

    @Override
    protected void applyContentColor(
        final @NonNull ComposeMessageHolder viewHolder,
        final @NonNull ColorStateList contentColor
    ) {
        super.applyContentColor(viewHolder, contentColor);
        viewHolder.audioMessageIcon.setImageTintList(getUiContentColor(getMessageModel(), getContext()));
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {

        logger.info("configureChatMessage for {}", getMessageModel().getId());

        AudioDataModel audioDataModel;
        FileDataModel fileDataModel;
        final long duration;
        final boolean isDownloaded;
        String caption = null;

        if (getMessageModel().getType() == MessageType.VOICEMESSAGE) {
            audioDataModel = getMessageModel().getAudioData();
            duration = audioDataModel.getDuration();
            isDownloaded = audioDataModel.isDownloaded();
        } else {
            fileDataModel = getMessageModel().getFileData();
            duration = fileDataModel.getDurationSeconds();
            isDownloaded = fileDataModel.isDownloaded();
            caption = fileDataModel.getCaption();
        }

        audioMessagePlayer = getMessagePlayerService().createPlayer(getMessageModel(),
            helper.getFragment().getActivity(), helper.getMessageReceiver(), helper.getMediaControllerFuture());

        setOnClickListener(view -> {
            // no action on onClick
        }, holder.messageBlockView);

        holder.messagePlayer = audioMessagePlayer;
        holder.readOnButton.setOnClickListener(v -> {
            float currentSpeed = getPreferenceService().getAudioPlaybackSpeed();
            float speed = audioMessagePlayer.togglePlaybackSpeed(currentSpeed);
            setSpeedButtonText(holder, speed);
        });

        setSpeedButtonText(holder, getPreferenceService().getAudioPlaybackSpeed());
        holder.seekBar.setMessageModel(getMessageModel(), helper.getThumbnailCache());
        holder.seekBar.setEnabled(false);
        holder.readOnButton.setVisibility(View.GONE);
        holder.audioMessageIcon.setVisibility(View.VISIBLE);
        holder.controller.setOnClickListener(v -> {
            int status = holder.controller.getStatus();

            switch (status) {
                case ControllerView.STATUS_READY_TO_RETRY:
                    propagateControllerRetryClickToParent();
                    break;
                case ControllerView.STATUS_READY_TO_PLAY:
                case ControllerView.STATUS_PLAYING:
                case ControllerView.STATUS_READY_TO_DOWNLOAD:
                    if (holder.seekBar != null && audioMessagePlayer != null) {
                        audioMessagePlayer.togglePlayPause();
                    }
                    break;
                case ControllerView.STATUS_PROGRESSING:
                    if (MessageUtil.isFileMessageBeingSent(getMessageModel())) {
                        getMessageService().cancelMessageUpload(getMessageModel());
                    } else {
                        audioMessagePlayer.cancel();
                    }
                    break;
                default:
                    // no action taken for other statuses
                    break;
            }
        });

        RuntimeUtil.runOnUiThread(() -> {
            setupResendStatus(holder);

            holder.controller.setNeutral();

            //reset progressbar
            updateProgressCount(holder, 0);

            if (audioMessagePlayer != null) {
                boolean isPlaying = false;
                if (holder.seekBar != null) {
                    holder.seekBar.setEnabled(false);
                }

                switch (audioMessagePlayer.getState()) {
                    case MessagePlayer.State_NONE:
                        if (isDownloaded) {
                            if (holder.seekBar != null) {
                                updateProgressCount(holder, 0);
                                holder.seekBar.setMessageModel(getMessageModel(), helper.getThumbnailCache());
                            }
                            holder.controller.setPlay();
                        } else {
                            if (helper.getDownloadService().isDownloading(getMessageModel().getId())) {
                                holder.controller.setProgressing(false);
                            } else {
                                holder.controller.setReadyToDownload();
                            }
                        }
                        break;
                    case MessagePlayer.State_DOWNLOADING:
                    case MessagePlayer.State_DECRYPTING:
                        //show loading
                        holder.controller.setProgressing();
                        break;
                    case MessagePlayer.State_DOWNLOADED:
                    case MessagePlayer.State_DECRYPTED:
                        if (holder.seekBar != null) {
                            updateProgressCount(holder, 0);
                            holder.seekBar.setMessageModel(getMessageModel(), helper.getThumbnailCache());
                        }
                        holder.controller.setPlay();
                        break;
                    case MessagePlayer.State_PLAYING:
                        isPlaying = true;
                        logger.debug("playing");
                        // fallthrough
                    case MessagePlayer.State_PAUSE:
                        if (isPlaying) {
                            holder.controller.setPause();
                        } else {
                            holder.controller.setPlay();
                        }
                        changePlayingState(holder, isPlaying);

                        if (holder.seekBar != null && audioMessagePlayer.getDuration() > 0) {
                            holder.seekBar.setEnabled(true);
                            logger.debug("SeekBar: Duration = " + audioMessagePlayer.getDuration());
                            holder.seekBar.setMax(audioMessagePlayer.getDuration());
                            logger.debug("SeekBar: Position = " + audioMessagePlayer.getPosition());
                            updateProgressCount(holder, audioMessagePlayer.getPosition());
                            holder.seekBar.setOnSeekBarChangeListener(new AudioProgressBarView.OnSeekBarChangeListener() {
                                @Override
                                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                }

                                @Override
                                public void onStartTrackingTouch(SeekBar seekBar) {
                                }

                                @Override
                                public void onStopTrackingTouch(SeekBar seekBar) {
                                    audioMessagePlayer.seekTo(seekBar.getProgress());
                                }
                            });
                        }
                        break;
                }

                audioMessagePlayer
                    .addListener(LISTENER_TAG, humanReadableMessage -> RuntimeUtil.runOnUiThread(() -> Toast.makeText(getContext(), humanReadableMessage, Toast.LENGTH_SHORT).show()))

                    .addListener(LISTENER_TAG, new MessagePlayer.DecryptionListener() {
                        @Override
                        public void onStart(AbstractMessageModel messageModel) {
                            invalidate(holder, position);
                        }

                        @Override
                        public void onEnd(final AbstractMessageModel messageModel, boolean success, final String message, File decryptedFile) {
                            if (!success) {
                                RuntimeUtil.runOnUiThread(() -> {
                                    holder.controller.setPlay();
                                    if (!TestUtil.isEmptyOrNull(message)) {
                                        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                            invalidate(holder, position);
                        }
                    })

                    .addListener(LISTENER_TAG, new MessagePlayer.DownloadListener() {
                        @Override
                        public void onStart(AbstractMessageModel messageModel) {
                            invalidate(holder, position);
                        }

                        @Override
                        public void onStatusUpdate(AbstractMessageModel messageModel, int progress) {
                        }

                        @Override
                        public void onEnd(final AbstractMessageModel messageModel, boolean success, final String message) {
                            if (!success) {
                                RuntimeUtil.runOnUiThread(() -> {
                                    holder.controller.setReadyToDownload();
                                    if (!TestUtil.isEmptyOrNull(message)) {
                                        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                            invalidate(holder, position);
                        }
                    })

                    .addListener(LISTENER_TAG, new MessagePlayer.PlaybackListener() {
                        @Override
                        public void onPlay(final AbstractMessageModel messageModel, boolean autoPlay) {
                            RuntimeUtil.runOnUiThread(() -> {
                                if (holder.position == position && getMessageModel().getId() == messageModel.getId()) {
                                    logger.debug("onPlay");
                                    invalidate(holder, position);
                                    changePlayingState(holder, true);
                                }
                            });
                        }

                        @Override
                        public void onPause(final AbstractMessageModel messageModel) {
                            RuntimeUtil.runOnUiThread(() -> {
                                if (holder.position == position && getMessageModel().getId() == messageModel.getId()) {
                                    logger.debug("onPause");
                                    invalidate(holder, position);
                                    changePlayingState(holder, false);
                                }
                            });
                        }

                        @Override
                        public void onStatusUpdate(final AbstractMessageModel messageModel, final int pos) {
                            RuntimeUtil.runOnUiThread(() -> {
                                if (holder.position == position && getMessageModel().getId() == messageModel.getId()) {
                                    if (holder.seekBar != null) {
                                        if (holder.seekBar.getMax() != holder.messagePlayer.getDuration()) {
                                            logger.info("Audio message player duration changed old={} new={}", holder.seekBar.getMax(), holder.messagePlayer.getDuration());
                                            holder.seekBar.setMax(holder.messagePlayer.getDuration());
                                        }
                                    }
                                    updateProgressCount(holder, pos);

                                    // make sure pinlock is not activated while playing
                                    ActivityService.activityUserInteract(helper.getFragment().getActivity());
                                }
                            });
                        }

                        @Override
                        public void onStop(final AbstractMessageModel messageModel) {
                            RuntimeUtil.runOnUiThread(() -> {
                                if (holder.position == position && getMessageModel().getId() == messageModel.getId()) {
                                    logger.debug("onStop getMessageModel {} messageModel {} position {}", getMessageModel().getId(), messageModel.getId(), position);
                                    invalidate(holder, position);
                                    if (messageModel.isAvailable()) {
                                        holder.controller.setPlay();
                                    } else {
                                        holder.controller.setReadyToDownload();
                                    }
                                    holder.seekBar.setEnabled(false);
                                    updateProgressCount(holder, 0);
                                    changePlayingState(holder, false);
                                }
                            });
                        }
                    });
            } else {
                //no player => no playable file
                holder.controller.setNeutral();

                if (getMessageModel().getState() == MessageState.SENDFAILED) {
                    holder.controller.setRetry();
                }
            }

            // Message state will be null if the message was deleted
            if (getMessageModel().isOutbox() && getMessageModel().getState() != null) {
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
                        break;
                }
            }
        });

        //do not show duration if 0
        if (duration > 0) {
            setDatePrefix(StringConversionUtil.secondsToString(duration, false));
            setDuration(duration);
            dateContentDescriptionPrefix = getContext().getString(R.string.duration) + ": " + StringConversionUtil.getDurationStringHuman(getContext(), duration);
        }

        if (holder.contentView != null) {
            //one size fits all :-)
            holder.contentView.getLayoutParams().width = ConfigUtils.getPreferredAudioMessageWidth(getContext(), false);
        }

        configureBodyText(holder, caption);
    }

    @UiThread
    private void updateProgressCount(final ComposeMessageHolder holder, int value) {
        if (holder != null && holder.size != null && holder.seekBar != null) {
            holder.seekBar.setProgress(value);
            holder.size.setText(StringConversionUtil.secondsToString((long) value / 1000, false));
        }
    }

    @UiThread
    private synchronized void changePlayingState(final ComposeMessageHolder holder, boolean isPlaying) {
        logger.debug("changePlayingState for {} to {}", getMessageModel().getId(), isPlaying);
        holder.readOnButton.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
        holder.audioMessageIcon.setVisibility(isPlaying ? View.GONE : View.VISIBLE);
    }

    @SuppressLint("DefaultLocale")
    private void setSpeedButtonText(final ComposeMessageHolder holder, float speed) {
        holder.readOnButton.setText(
            speed % 1.0 != 0L ?
                String.format("%sx", speed) :
                String.format(" %.0fx ", speed)
        );
    }
}
