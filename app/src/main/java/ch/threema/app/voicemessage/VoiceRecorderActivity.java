/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.voicemessage;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

import android.animation.LayoutTransition;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.button.MaterialButton;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import ch.threema.app.R;
import ch.threema.app.activities.ThreemaAppCompatActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ActivityService;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ElapsedTimeFormatter;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.MediaPlayerStateWrapper;
import ch.threema.app.utils.MimeUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class VoiceRecorderActivity extends ThreemaAppCompatActivity implements DefaultLifecycleObserver, View.OnClickListener, AudioRecorder.OnStopListener, AudioManager.OnAudioFocusChangeListener, GenericAlertDialog.DialogClickListener {
    private static final Logger logger = getThreemaLogger("VoiceRecorderActivity");

    private static final String DIALOG_TAG_CANCEL_CONFIRM = "cc";
    private static final String DIALOG_TAG_EXPIRED_CONFIRM = "ec";
    public static final String VOICEMESSAGE_FILE_EXTENSION = ".aac";
    private static final int DISCARD_CONFIRMATION_THRESHOLD_SECONDS = 10;
    private static final int PERMISSION_REQUEST_BLUETOOTH_CONNECT = 45454;

    private enum MediaState {
        STATE_NONE,
        STATE_RECORDING,
        STATE_PLAYING,
        STATE_PAUSED,
        STATE_PLAYING_PAUSED
    }

    private MediaRecorder mediaRecorder;
    private MediaPlayerStateWrapper mediaPlayer;
    private MediaState status = MediaState.STATE_NONE;
    private TextView timerTextView;
    private MaterialButton sendButton;
    private ImageView playButton;
    private ImageView pauseButton;
    private ImageView recordImage;
    private ImageView bluetoothToggle;
    private SeekBar seekBar;
    private Uri uri;
    private int recordingDuration;
    private long startTimestamp, pauseTimestamp, pauseDuration;
    private Handler timeDisplayHandler, blinkingHandler;
    private Runnable timeDisplayRunnable, blinkingRunnable;
    private boolean hasAudioFocus = false;
    private AudioManager audioManager;
    private BroadcastReceiver audioStateChangedReceiver;
    private static int scoAudioState;
    private MessageReceiver<?> messageReceiver;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private static final int KEEP_ALIVE_DELAY = 20000;
    private final static Handler keepAliveHandler = new Handler();
    private final Runnable keepAliveTask = new Runnable() {
        @Override
        public void run() {
            ActivityService.activityUserInteract(VoiceRecorderActivity.this);
            keepAliveHandler.postDelayed(keepAliveTask, KEEP_ALIVE_DELAY);
        }
    };

    private final Choreographer.FrameCallback updateSeekbarCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            updateSeekbar();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getLifecycle().addObserver(this);
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!isSessionScopeReady()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_voice_recorder);

        // keep screen on during recording
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!ConfigUtils.requestBluetoothConnectPermissions(this, null, PERMISSION_REQUEST_BLUETOOTH_CONNECT,
                ActivityCompat.shouldShowRequestPermissionRationale(this, BLUETOOTH_CONNECT))) {
                return;
            }
        }

        postPermissionOnCreate();
    }

    private void postPermissionOnCreate() {
        Intent intent = getIntent();
        if (intent != null) {
            messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(this, intent);
            if (messageReceiver == null) {
                logger.info("No message receiver");
                finish();
                return;
            }

            try {
                File file = File.createTempFile("voice-", VOICEMESSAGE_FILE_EXTENSION, dependencies.getFileService().getIntTmpPath());
                uri = Uri.fromFile(file);
            } catch (IOException e) {
                logger.error("Failed to open temp file");
                this.finish();
            }

            LinearLayout buttonLayout = findViewById(R.id.button_layout);
            buttonLayout.getLayoutTransition().enableTransitionType(LayoutTransition.APPEARING | LayoutTransition.DISAPPEARING);

            timerTextView = findViewById(R.id.timer_text);

            sendButton = findViewById(R.id.send_button);
            sendButton.setOnClickListener(view -> {
                sendButton.setEnabled(false);
                stopAndReleaseMediaPlayer(mediaPlayer);
                sendRecording(false);
            });

            ImageView discardButton = findViewById(R.id.discard_button);
            discardButton.setOnClickListener(this);

            playButton = findViewById(R.id.play_button);
            playButton.setOnClickListener(this);

            pauseButton = findViewById(R.id.pause_button);
            pauseButton.setOnClickListener(this);

            this.seekBar = findViewById(R.id.seekbar);
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        pausePlayback();
                        updateTimeDisplay();
                        mediaPlayer.seekTo(progress);
                        seekBar.setProgress(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    pausePlayback();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    startPlayback();
                }
            });

            recordImage = findViewById(R.id.record_image);

            timeDisplayHandler = new Handler();
            blinkingHandler = new Handler();

            if (!startRecording()) {
                Toast.makeText(this, R.string.recording_canceled, Toast.LENGTH_LONG).show();
                reallyCancelRecording();
            }

        } else {
            reallyCancelRecording();
        }

        muteAllStreams();

        bluetoothToggle = findViewById(R.id.bluetooth_toggle);

        if (isBluetoothEnabled()) {
            if (bluetoothToggle != null) {
                bluetoothToggle.setVisibility(View.VISIBLE);
                bluetoothToggle.setOnClickListener(this);
            }

            audioStateChangedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    scoAudioState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);

                    String stateString = "";
                    switch (scoAudioState) {
                        case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                            stateString = "connected";
                            break;
                        case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                            stateString = "disconnected";
                            break;
                        case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                            stateString = "connecting";
                            break;
                        case AudioManager.SCO_AUDIO_STATE_ERROR:
                            stateString = "error";
                            break;
                        default:
                            break;
                    }

                    logger.debug("Audio SCO state: " + stateString);
                    updateBluetoothButton();
                }
            };
            registerReceiver(audioStateChangedReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));

            if (!dependencies.getPreferenceService().getVoiceRecorderBluetoothDisabled()) {
                try {
                    audioManager.startBluetoothSco();
                } catch (Exception ignored) {
                }
            }
        } else {
            if (bluetoothToggle != null) {
                bluetoothToggle.setVisibility(View.INVISIBLE);
                bluetoothToggle.setOnClickListener(null);
            }
        }
    }

    @Override
    protected void onStop() {
        logger.debug("onStop");
        super.onStop();
    }

    @Override
    protected void onStart() {
        logger.debug("onStart");
        super.onStart();
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        super.addContentView(view, params);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        logger.debug("onResume");
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        logger.debug("onPause");
        pauseMedia();
    }

    @SuppressWarnings("MissingPermission")
    private boolean isBluetoothEnabled() {
        if (audioManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean result = bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothAdapter.STATE_CONNECTED;

        logger.debug("isBluetoothEnabled = {}", result);

        return result;
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            logger.info("MediaRecorder released {}", mediaRecorder);
            mediaRecorder = null;
        }
    }

    private void updateTimeDisplay() {
        long duration = 0L;
        if (status == MediaState.STATE_RECORDING) {
            duration = getRecordingDuration();
        } else if ((status == MediaState.STATE_PLAYING || status == MediaState.STATE_PLAYING_PAUSED) && mediaPlayer != null) {
            duration = mediaPlayer.getCurrentPosition() / 1000;
        }
        timerTextView.setText(ElapsedTimeFormatter.secondsToString(duration));
    }

    private void updateSeekbar() {
        int currentPos = mediaPlayer.getCurrentPosition();
        seekBar.setProgress(currentPos);
    }

    private void updateBlinkingDisplay() {
        recordImage.setVisibility(recordImage.getVisibility() == View.VISIBLE ?
            View.INVISIBLE : View.VISIBLE);
    }

    private void updateBluetoothButton() {
        if (bluetoothToggle != null) {
            @DrawableRes int stateRes;

            switch (scoAudioState) {
                case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                    stateRes = R.drawable.ic_bluetooth_connected;
                    dependencies.getPreferenceService().setVoiceRecorderBluetoothDisabled(false);
                    break;
                case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                case AudioManager.SCO_AUDIO_STATE_ERROR:
                    stateRes = R.drawable.ic_bluetooth_disabled;
                    dependencies.getPreferenceService().setVoiceRecorderBluetoothDisabled(true);
                    break;
                case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                default:
                    stateRes = R.drawable.ic_bluetooth_searching_outline;
                    break;
            }
            bluetoothToggle.setImageResource(stateRes);
        }
    }

    public void startSeekbar() {
        Choreographer.getInstance().postFrameCallback(updateSeekbarCallback);
    }

    private void startTimer() {
        timeDisplayRunnable = () -> {
            updateTimeDisplay();
            timeDisplayHandler.postDelayed(timeDisplayRunnable, 1000);
        };
        timeDisplayHandler.post(timeDisplayRunnable);
    }

    private void startBlinking() {
        blinkingRunnable = () -> {
            updateBlinkingDisplay();
            blinkingHandler.postDelayed(blinkingRunnable, 600);
        };
        blinkingHandler.post(blinkingRunnable);
    }

    private void stopTimer() {
        timeDisplayHandler.removeCallbacks(timeDisplayRunnable);
    }

    private void stopBlinking() {
        blinkingHandler.removeCallbacks(blinkingRunnable);
    }

    private void stopUpdateSeekbar() {
        Choreographer.getInstance().removeFrameCallback(updateSeekbarCallback);
    }

    private void resetTimerDisplay() {
        timerTextView.setText(ElapsedTimeFormatter.secondsToString(0));
    }

    private boolean startRecording() {
        recordingDuration = 0;
        pauseDuration = 0;

        AudioRecorder audioRecorder;

        audioRecorder = new AudioRecorder(this);
        audioRecorder.setOnStopListener(this);
        try {
            logger.info("Now recording to {}", uri);
            mediaRecorder = audioRecorder.prepare(uri, getDefaultSamplingRate());
            logger.info("Started recording with {}", this.mediaRecorder);
            if (mediaRecorder != null) {
                startTimestamp = System.nanoTime();
                mediaRecorder.start();
            } else throw new Exception();

        } catch (Exception e) {
            logger.info("Error opening media recorder");
            logger.error("Media Recorder Exception occurred", e);
            releaseMediaRecorder();
            return false;
        }

        updateMediaState(MediaState.STATE_RECORDING);

        startTimer();

        return true;
    }

    public static int getDefaultSamplingRate() {
        return ConfigUtils.hasBrokenAudioRecorder() ? 44000 : 44100;
    }

    private int getRecordingDuration() {
        long timeDiff = System.nanoTime() - startTimestamp - pauseDuration;
        return (int) (timeDiff / 1000 / 1000 / 1000); // convert nanoseconds to seconds
    }

    private void stopRecording() {
        if (status == MediaState.STATE_RECORDING || status == MediaState.STATE_PAUSED) {
            recordingDuration = 0;

            // stop recording and release recorder
            try {
                if (mediaRecorder != null) {
                    logger.info("Stopped recording with {}", mediaRecorder);
                    mediaRecorder.stop();  // stop the recording
                }
                recordingDuration = getRecordingDuration() + 1;
            } catch (RuntimeException stopException) {
                // invalid recording
            }

            releaseMediaRecorder(); // release the MediaRecorder object
            stopTimer();
        }
        updateMediaState(MediaState.STATE_NONE);
    }

    private void pausePlayback() {
        mediaPlayer.pause();
        updateMediaState(MediaState.STATE_PLAYING_PAUSED);
    }

    private void startPlayback() {
        mediaPlayer.start();
        updateMediaState(MediaState.STATE_PLAYING);
    }

    private void pauseMedia() {
        logger.info("Pause media recording");
        if (status == MediaState.STATE_RECORDING) {
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.pause();  // pause the recording
                } catch (Exception e) {
                    logger.error(
                        "Unexpected MediaRecorder Exception while pausing recording audio",
                        e
                    );
                }
                pauseTimestamp = System.nanoTime();
                updateMediaState(MediaState.STATE_PAUSED);
            }
        }
        if (status == MediaState.STATE_PLAYING) {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.pause();
                } catch (Exception e) {
                    logger.error(
                        "Unexpected MediaRecorder Exception while pausing playing audio",
                        e
                    );
                }
                pauseTimestamp = System.nanoTime();
                updateMediaState(MediaState.STATE_PLAYING_PAUSED);
            }
        }
    }

    private void resumeRecording() {
        logger.info("Resume media recording");
        if (status == MediaState.STATE_PAUSED) {
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.resume();
                } catch (Exception e) {
                    logger.warn(
                        "Unexpected MediaRecorder Exception while resuming playing audio",
                        e
                    );
                }
                pauseDuration += System.nanoTime() - pauseTimestamp;
                updateMediaState(MediaState.STATE_RECORDING);
            }
        }
    }

    /**
     * Get Duration of media contained in the media file pointed at by uri in ms.
     *
     * @return Duration in ms or 0 if the media player was unable to open this file
     */
    private int getDurationFromFile() {
        logger.info("Attempting to retrieve duration from file {}", uri);
        MediaPlayer durationCheckMediaPlayer = MediaPlayer.create(this, uri);
        if (durationCheckMediaPlayer != null) {
            int duration = durationCheckMediaPlayer.getDuration();
            if (duration == 0) {
                logger.info("Duration check returned 0");
            }
            durationCheckMediaPlayer.release();
            logger.info("Duration in ms {}", duration);

            return duration;
        }
        logger.info("Unable to create a media player for checking size. File already deleted by OS?");
        return 0;
    }

    private void reallySendRecording() {
        releaseMediaRecorder();

        if (this.mediaPlayer != null) {
            this.mediaPlayer.release();
            this.mediaPlayer = null;
        }

        long fileDurationSeconds = getDurationFromFile();
        if (fileDurationSeconds > 0L) {
            if (fileDurationSeconds < DateUtils.SECOND_IN_MILLIS) {
                fileDurationSeconds = DateUtils.SECOND_IN_MILLIS;
            }
            MediaItem mediaItem = new MediaItem(uri, MimeUtil.MIME_TYPE_AUDIO_AAC, null);
            mediaItem.setDurationMs(fileDurationSeconds);
            dependencies.getMessageService().sendMediaAsync(Collections.singletonList(mediaItem), Collections.singletonList(messageReceiver));
            this.finish();
        } else {
            Toast.makeText(this, R.string.unable_to_determine_recording_length, Toast.LENGTH_LONG).show();
            sendButton.setEnabled(true);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
    }

    private void reallyCancelRecording() {
        stopRecording();
        this.finish();
    }

    private void sendRecording(boolean isCancelable) {
        if (status == MediaState.STATE_RECORDING || status == MediaState.STATE_PAUSED) {
            stopRecording();
        }

        if (recordingDuration > 0) {
            if (isCancelable) {
                GenericAlertDialog.newInstance(
                    R.string.recording_stopped_title,
                    R.string.recording_stopped_message,
                    R.string.yes,
                    R.string.no,
                    false
                ).show(getSupportFragmentManager(), DIALOG_TAG_EXPIRED_CONFIRM);
            } else {
                reallySendRecording();
            }
        } else {
            reallyCancelRecording();
        }
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
        cancelRecording();
    }

    private void cancelRecording() {
        GenericAlertDialog.newInstance(R.string.cancel_recording, R.string.cancel_recording_message, R.string.yes, R.string.no, false).show(getSupportFragmentManager(), DIALOG_TAG_CANCEL_CONFIRM);
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        if (id == R.id.discard_button) {
            stopAndReleaseMediaPlayer(mediaPlayer);
            if (status == MediaState.STATE_RECORDING && getRecordingDuration() >= DISCARD_CONFIRMATION_THRESHOLD_SECONDS) {
                stopRecording();
                cancelRecording();
            } else {
                reallyCancelRecording();
            }
        } else if (id == R.id.play_button) {
            switch (status) {
                case STATE_NONE:
                    playRecording();
                    break;
                case STATE_RECORDING:
                    stopRecording();
                    resetTimerDisplay();
                case STATE_PAUSED:
                    stopRecording();
                    resetTimerDisplay();
                    break;
                case STATE_PLAYING:
                    pausePlayback();
                    break;
                case STATE_PLAYING_PAUSED: {
                    startPlayback();
                }
            }
        } else if (id == R.id.pause_button) {
            switch (status) {
                case STATE_PAUSED:
                    resumeRecording();
                    break;
                case STATE_RECORDING:
                    pauseMedia();
                    break;
            }
        } else if (id == R.id.bluetooth_toggle) {
            try {
                if (audioManager.isBluetoothScoOn()) {
                    audioManager.stopBluetoothSco();
                } else {
                    audioManager.startBluetoothSco();
                }
            } catch (Exception ignored) {
            }
            updateBluetoothButton();
        }
    }

    private void stopAndReleaseMediaPlayer(MediaPlayerStateWrapper mp) {
        if (mp != null) {
            stopTimer();
            stopUpdateSeekbar();
            stopBlinking();

            if (mp.isPlaying()) {
                mp.stop();
            }
            mp.reset();
            mp.release();
            mediaPlayer = null;
        }
    }

    private void playRecording() {
        if (recordingDuration > 0 && uri != null) {
            if (mediaPlayer != null) {
                stopAndReleaseMediaPlayer(mediaPlayer);
            }

            mediaPlayer = new MediaPlayerStateWrapper();
            if (scoAudioState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            try {
                mediaPlayer.setDataSource(this, uri);
                mediaPlayer.prepare();
                mediaPlayer.setOnPreparedListener(mp -> {
                    seekBar.setMax(mp.getDuration());
                    resetTimerDisplay();
                    mediaPlayer.start();
                    updateMediaState(MediaState.STATE_PLAYING);
                });
                mediaPlayer.setOnCompletionListener(mp -> {
                    updateMediaState(MediaState.STATE_PLAYING_PAUSED);
                    seekBar.setProgress(seekBar.getMax());
                });
            } catch (Exception e) {
                logger.debug("unable to play recording.");
                stopAndReleaseMediaPlayer(mediaPlayer);
            }
        }
    }

    private void updateMediaState(MediaState mediaState) {
        status = mediaState;

        switch (status) {
            case STATE_NONE:
                pauseButton.setVisibility(View.INVISIBLE);
                playButton.setImageResource(R.drawable.ic_play);
                playButton.setContentDescription(getString(R.string.play));
                stopBlinking();
                stopTimer();
                stopUpdateSeekbar();
                recordImage.setVisibility(View.INVISIBLE);
                inhibitPinLock(false);
                break;
            case STATE_PLAYING:
                pauseButton.setVisibility(View.INVISIBLE);
                seekBar.setVisibility(View.VISIBLE);
                playButton.setImageResource(R.drawable.ic_pause);
                playButton.setContentDescription(getString(R.string.stop));
                recordImage.setImageResource(R.drawable.ic_play);
                recordImage.setColorFilter(ConfigUtils.getColorFromAttribute(this, R.attr.colorOnSurface), PorterDuff.Mode.SRC_IN);
                recordImage.setVisibility(View.VISIBLE);
                startBlinking();
                startTimer();
                startSeekbar();
                inhibitPinLock(true);
                break;
            case STATE_RECORDING:
                pauseButton.setImageResource(R.drawable.ic_pause);
                pauseButton.clearColorFilter();
                pauseButton.setVisibility(View.VISIBLE);
                pauseButton.setContentDescription(getString(R.string.pause));
                playButton.setImageResource(R.drawable.ic_stop);
                playButton.setContentDescription(getString(R.string.stop));
                recordImage.setImageResource(R.drawable.ic_record);
                recordImage.clearColorFilter();
                recordImage.setVisibility(View.VISIBLE);
                startBlinking();
                startTimer();
                inhibitPinLock(true);
                break;
            case STATE_PAUSED:
                pauseButton.setImageResource(R.drawable.ic_record);
                pauseButton.setColorFilter(getResources().getColor(R.color.material_red), PorterDuff.Mode.SRC_IN);
                pauseButton.setVisibility(View.VISIBLE);
                pauseButton.setContentDescription(getString(R.string.continue_recording));
                playButton.setImageResource(R.drawable.ic_stop);
                playButton.setContentDescription(getString(R.string.stop));
                recordImage.setVisibility(View.INVISIBLE);
                stopBlinking();
                stopTimer();
                inhibitPinLock(true);
                break;
            case STATE_PLAYING_PAUSED:
                playButton.setImageResource(R.drawable.ic_play);
                playButton.setContentDescription(getString(R.string.play));
                recordImage.setVisibility(View.INVISIBLE);
                stopBlinking();
                stopTimer();
                stopUpdateSeekbar();
                inhibitPinLock(false);
                break;
            default:
                playButton.setImageResource(R.drawable.ic_play);
                break;
        }
    }

    @Override
    public void onRecordingStop() {
        sendRecording(true);
    }

    @Override
    public void onRecordingCancel() {
        Toast.makeText(this, R.string.recording_canceled, Toast.LENGTH_LONG).show();
        reallyCancelRecording();
    }

    private void muteAllStreams() {
        logger.debug("muteAllStreams");

        if (!hasAudioFocus) {
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
            hasAudioFocus = true;
        }
    }

    private void unmuteAllStreams() {
        logger.debug("unmuteAllStreams");

        audioManager.abandonAudioFocus(this);
        hasAudioFocus = false;
    }

    /**
     * Keep timed Pin lock from activating by simulating activity
     *
     * @param value true if Pin lock should be deactivated, false otherwise
     */
    protected void inhibitPinLock(boolean value) {
        if (value) {
            keepAliveHandler.postDelayed(keepAliveTask, KEEP_ALIVE_DELAY);
        } else {
            keepAliveHandler.removeCallbacks(keepAliveTask);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                hasAudioFocus = false;

                if (status == MediaState.STATE_PLAYING) {
                    stopAndReleaseMediaPlayer(mediaPlayer);
                    updateMediaState(MediaState.STATE_NONE);
                } else if (status == MediaState.STATE_RECORDING) {
                    stopRecording();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                break;
        }
    }

    @Override
    protected void onDestroy() {
        logger.debug("onDestroy");
        if (timeDisplayHandler != null) {
            timeDisplayHandler.removeCallbacksAndMessages(null);
        }
        if (blinkingHandler != null) {
            blinkingHandler.removeCallbacksAndMessages(null);
        }
        Choreographer.getInstance().removeFrameCallback(updateSeekbarCallback);

        if (isBluetoothEnabled()) {
            logger.debug("stopBluetoothSco");
            try {
                audioManager.stopBluetoothSco();
            } catch (Exception ignored) {
            }
            if (audioStateChangedReceiver != null) {
                try {
                    unregisterReceiver(audioStateChangedReceiver);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        unmuteAllStreams();
        super.onDestroy();
    }

    @Override
    public void onYes(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_CANCEL_CONFIRM:
                reallyCancelRecording();
                break;
            case DIALOG_TAG_EXPIRED_CONFIRM:
                reallySendRecording();
                break;
        }

    }

    @Override
    public void onNo(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_CANCEL_CONFIRM:
                break;
            case DIALOG_TAG_EXPIRED_CONFIRM:
                reallyCancelRecording();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_BLUETOOTH_CONNECT) {
            postPermissionOnCreate();
        }
    }
}
