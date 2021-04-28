/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.listeners.SensorListener;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SensorService;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.MimeUtil;

public class VoiceRecorderActivity extends AppCompatActivity implements View.OnClickListener, AudioRecorder.OnStopListener, AudioManager.OnAudioFocusChangeListener, GenericAlertDialog.DialogClickListener, SensorListener {
	private static final Logger logger = LoggerFactory.getLogger(VoiceRecorderActivity.class);

	private static final String DIALOG_TAG_CANCEL_CONFIRM = "cc";
	private static final String DIALOG_TAG_EXPIRED_CONFIRM = "ec";

	public static final int MAX_VOICE_MESSAGE_LENGTH_MILLIS = (int) DateUtils.HOUR_IN_MILLIS;
	private static final String SENSOR_TAG_VOICE_RECORDER = "voice";

	public static final int DEFAULT_SAMPLING_RATE_HZ = 22050;
	public static final int BLUETOOTH_SAMPLING_RATE_HZ = 8000;

	public static final String VOICEMESSAGE_FILE_EXTENSION = ".aac";

	private enum MediaState {
		STATE_NONE,
		STATE_RECORDING,
		STATE_PLAYING,
		STATE_PAUSED,
		STATE_PLAYING_PAUSED
	}

	private MediaRecorder mediaRecorder;
	private MediaPlayer mediaPlayer;
	private MediaState status = MediaState.STATE_NONE;
	private TextView timerTextView;
	private ImageView sendButton, discardButton, playButton, pauseButton, recordImage, bluetoothToogle;
	private SeekBar seekBar;
	private Uri uri;
	private int recordingDuration;
	private long startTimestamp, pauseTimestamp, pauseDuration;
	private Handler timeDisplayHandler, blinkingHandler, seekBarHandler;
	private Runnable timeDisplayRunnable, blinkingRunnable, updateSeekBarRunnable;
	private boolean hasAudioFocus = false;
	private AudioManager audioManager;
	private BroadcastReceiver audioStateChangedReceiver;
	private static int scoAudioState;
	private boolean hasFocus = false;
	private MessageReceiver messageReceiver;

	private PreferenceService preferenceService;
	private SensorService sensorService;
	private MessageService messageService;
	private FileService fileService;

	private static final int KEEP_ALIVE_DELAY = 20000;
	private final static Handler keepAliveHandler = new Handler();
	private final Runnable keepAliveTask = new Runnable() {
		@Override
		public void run() {
			ThreemaApplication.activityUserInteract(VoiceRecorderActivity.this);
			keepAliveHandler.postDelayed(keepAliveTask, KEEP_ALIVE_DELAY);
		}
	};

	private boolean supportsPauseResume() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		ConfigUtils.configureActivityTheme(this);

		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_voice_recorder);

		// keep screen on during recording
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager != null) {
				preferenceService = serviceManager.getPreferenceService();
				sensorService = serviceManager.getSensorService();
				messageService = serviceManager.getMessageService();
				fileService = serviceManager.getFileService();
			}
		} catch (Exception e) {
			logger.error("Exception", e);
			this.finish();
			return;
		}

		if (preferenceService == null || sensorService == null || messageService == null || fileService == null) {
			logger.info("Services missing.");
			this.finish();
			return;
		}

		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		Intent intent = getIntent();

		if (intent != null) {
			messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(this, intent);

			try {
				File file = fileService.createTempFile(".audio", VOICEMESSAGE_FILE_EXTENSION, false);
				uri = Uri.fromFile(file);
			} catch (IOException e) {
				logger.error("Failed to open temp file");
				this.finish();
			}

			timerTextView = findViewById(R.id.timer_text);

			sendButton = findViewById(R.id.send_button);
			sendButton.setOnClickListener(this);

			discardButton = findViewById(R.id.discard_button);
			discardButton.setOnClickListener(this);

			playButton = findViewById(R.id.play_button);
			playButton.setOnClickListener(this);

			pauseButton = findViewById(R.id.pause_button);
			if (supportsPauseResume()) {
				pauseButton.setOnClickListener(this);
			} else {
				pauseButton.setVisibility(View.INVISIBLE);
			}

			this.seekBar = findViewById(R.id.seekbar);
			seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if(fromUser){
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
			seekBarHandler = new Handler();

			if (!startRecording())
				reallyCancelRecording();

		} else {
			reallyCancelRecording();
		}

		muteAllStreams();

		bluetoothToogle = findViewById(R.id.bluetooth_toggle);

		if (isBluetoothEnabled()) {
			if (bluetoothToogle != null) {
				bluetoothToogle.setVisibility(View.VISIBLE);
				bluetoothToogle.setOnClickListener(this);
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

			if (!preferenceService.getVoiceRecorderBluetoothDisabled()) {
				try {
					audioManager.startBluetoothSco();
				} catch (Exception ignored) {
				}
			}
		} else {
			if (bluetoothToogle != null) {
				bluetoothToogle.setVisibility(View.INVISIBLE);
				bluetoothToogle.setOnClickListener(null);
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
	protected void onPause() {
		logger.debug("onPause");
		if (!ConfigUtils.isSamsungDevice()) {
			reallyOnPause();
		}
		super.onPause();
	}

	private void reallyOnPause() {
		logger.debug("reallyOnPause");
		pauseMedia();
	}

	@Override
	protected void onResume() {
		logger.debug("onResume");
		super.onResume();
	}

	public void onWindowFocusChanged(boolean hasFocus) {
		logger.debug("onWindowFocusChanged " + hasFocus);
		// workaround for proximity wake lock causing calls to onPause/onResume on Samsung devices:
		// see: http://stackoverflow.com/questions/35318649/android-proximity-sensor-issue-only-in-samsung-devices
		if (!hasFocus) {
			reallyOnPause();
			this.hasFocus = false;
		}
	}

	private boolean isBluetoothEnabled() {
		if (audioManager == null) {
			return false;
		}

		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		boolean result = bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED;

		logger.debug("isBluetoothEnabled = {}",result);

		return result;
	}

	private void releaseMediaRecorder() {
		if (mediaRecorder != null) {
			mediaRecorder.reset();   // clear recorder configuration
			mediaRecorder.release(); // release the recorder object
			mediaRecorder = null;
		}
	}

	private void updateTimeDisplay() {
		int duration, minutes = 0, seconds = 0;
		if (status == MediaState.STATE_RECORDING) {
			duration = getRecordingDuration();
			minutes = (duration % 3600) / 60;
			seconds = duration % 60;
		} else if ((status == MediaState.STATE_PLAYING || status == MediaState.STATE_PLAYING_PAUSED) && mediaPlayer != null) {
			duration = mediaPlayer.getCurrentPosition();
			minutes = duration / 60000;
			seconds = (duration % 60000) / 1000;
		}
		timerTextView.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
	}

	private void updateSeekbar(){
		int currentPos = mediaPlayer.getCurrentPosition();
		seekBar.setProgress(currentPos);
	}

	private void updateBlinkingDisplay() {
		recordImage.setVisibility(recordImage.getVisibility() == View.VISIBLE ?
				View.INVISIBLE : View.VISIBLE);
	}

	private void updateBluetoothButton() {
		if (bluetoothToogle != null) {
			@DrawableRes int stateRes;

			switch (scoAudioState) {
				case AudioManager.SCO_AUDIO_STATE_CONNECTED:
					stateRes = R.drawable.ic_bluetooth_connected;
					preferenceService.setVoiceRecorderBluetoothDisabled(false);
					break;
				case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
				case AudioManager.SCO_AUDIO_STATE_ERROR:
					stateRes = R.drawable.ic_bluetooth_disabled;
					preferenceService.setVoiceRecorderBluetoothDisabled(true);
					break;
				case AudioManager.SCO_AUDIO_STATE_CONNECTING:
				default:
					stateRes = R.drawable.ic_bluetooth_searching_outline;
					break;
			}
			bluetoothToogle.setImageResource(stateRes);
		}
	}

	public void startSeekbar(){
		updateSeekBarRunnable = new Runnable() {
			@Override
			public void run() {
				updateSeekbar();
				seekBarHandler.postDelayed(updateSeekBarRunnable, 1);
			}
		};
		seekBarHandler.post(updateSeekBarRunnable);
	}

	private void startTimer() {
		timeDisplayRunnable = new Runnable() {
			@Override
			public void run() {
				updateTimeDisplay();
				timeDisplayHandler.postDelayed(timeDisplayRunnable, 1000);
			}
		};
		timeDisplayHandler.post(timeDisplayRunnable);
	}

	private void startBlinking() {
		blinkingRunnable = new Runnable() {
			@Override
			public void run() {
				updateBlinkingDisplay();
				blinkingHandler.postDelayed(blinkingRunnable, 600);
			}
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
		seekBarHandler.removeCallbacks(updateSeekBarRunnable);
	}

	private void resetTimerDisplay() {
		timerTextView.setText(String.format(Locale.US, "%02d:%02d", 0, 0));
	}

	private boolean startRecording() {
		recordingDuration = 0;
		pauseDuration = 0;

		AudioRecorder audioRecorder;

		audioRecorder = new AudioRecorder(this);
		audioRecorder.setOnStopListener(this);
		logger.info("new audioRecorder instance {}", audioRecorder);
		try {
			mediaRecorder = audioRecorder.prepare(uri, MAX_VOICE_MESSAGE_LENGTH_MILLIS,
			scoAudioState == AudioManager.SCO_AUDIO_STATE_CONNECTED ?
			BLUETOOTH_SAMPLING_RATE_HZ :
			DEFAULT_SAMPLING_RATE_HZ );
			logger.info("Started recording with mediaRecorder instance {}", this.mediaRecorder);
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

	private int getRecordingDuration() {
		long timeDiff = System.nanoTime() - startTimestamp - pauseDuration;
		return (int) (timeDiff / 1000 / 1000 / 1000); // convert nanoseconds to seconds
	}

	private int stopRecording() {
		if (status == MediaState.STATE_RECORDING || status == MediaState.STATE_PAUSED) {
			recordingDuration = 0;

			// stop recording and release recorder
			try {
				if (mediaRecorder != null) {
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

		return recordingDuration;
	}

	private void pausePlayback(){
		mediaPlayer.pause();
		updateMediaState(MediaState.STATE_PLAYING_PAUSED);
	}

	private void startPlayback(){
		mediaPlayer.start();
		updateMediaState(MediaState.STATE_PLAYING);
	}

	private void pauseMedia() {
		if (supportsPauseResume()) {
			logger.info("Pause media recording");
			if (status == MediaState.STATE_RECORDING) {
				if (mediaRecorder != null) {
					try {
						mediaRecorder.pause();  // pause the recording
					} catch (Exception e) {
						logger.warn(
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
						mediaPlayer.pause();  // pause the recording
					} catch (Exception e) {
						logger.warn(
							"Unexpected MediaRecorder Exception while pausing playing audio",
							e
						);
					}
					pauseTimestamp = System.nanoTime();
					updateMediaState(MediaState.STATE_PLAYING_PAUSED);
				}
			}
		} else  {
			stopRecording();
		}
	}

	private void resumeRecording() {
		if (supportsPauseResume()) {
			logger.info("Resume media recording");
			if (status == MediaState.STATE_PAUSED) {
				if (mediaRecorder != null) {
					try {
						mediaRecorder.resume();  // pause the recording
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
	}

	/**
	 * Get Duration of media contained in the media file pointed at by uri in ms.
	 * @return Duration in ms or 0 if the media player was unable to open this file
	 */
	private int getDurationFromFile() {
		MediaPlayer mediaPlayer = MediaPlayer.create(this, uri);
		if (mediaPlayer != null) {
			int duration = mediaPlayer.getDuration();
			mediaPlayer.release();
			return duration;
		}
		return 0;
	}

	private void returnData() {
		MediaItem mediaItem = new MediaItem(uri, MimeUtil.MIME_TYPE_AUDIO_AAC, null);
		mediaItem.setDurationMs(getDurationFromFile());
		messageService.sendMediaAsync(Collections.singletonList(mediaItem), Collections.singletonList(messageReceiver));

		this.finish();
	}

	@Override
	public void finish() {
		super.finish();
		overridePendingTransition(0, R.anim.slide_out_left_short);
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
				GenericAlertDialog.newInstance(R.string.recording_stopped_title, R.string.recording_stopped_message, R.string.yes, R.string.no, false).show(getSupportFragmentManager(), DIALOG_TAG_EXPIRED_CONFIRM);
			} else {
				returnData();
			}
		} else {
			reallyCancelRecording();
		}
	}

	@Override
	public void onBackPressed() {
		cancelRecording();
	}

	private void cancelRecording() {
		GenericAlertDialog.newInstance(R.string.cancel_recording, R.string.cancel_recording_message, R.string.yes, R.string.no, false).show(getSupportFragmentManager(), DIALOG_TAG_CANCEL_CONFIRM);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.send_button:
				stopAndReleaseMediaPlayer(mediaPlayer);
				sendRecording(false);
				break;
			case R.id.discard_button:
				stopAndReleaseMediaPlayer(mediaPlayer);
				if (status == MediaState.STATE_RECORDING && getRecordingDuration() >= 5) {
					stopRecording();
					cancelRecording();
				} else {
					reallyCancelRecording();
				}
				break;
			case R.id.play_button:
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
					case STATE_PLAYING_PAUSED:{
						startPlayback();
					}
				}
				break;
			case R.id.pause_button:
				switch (status) {
					case STATE_PAUSED:
						resumeRecording();
						break;
					case STATE_RECORDING:
						pauseMedia();
						break;
				}
				break;
			case R.id.bluetooth_toggle:
				try {
					if (audioManager.isBluetoothScoOn()) {
						audioManager.stopBluetoothSco();
					} else {
						audioManager.startBluetoothSco();
					}
				} catch (Exception ignored) {
				}
				updateBluetoothButton();
				break;
			default:
				break;

		}
	}

	private void stopAndReleaseMediaPlayer(MediaPlayer mp) {
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

			mediaPlayer = new MediaPlayer();
			if (scoAudioState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
			} else {
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			}

			try {
				mediaPlayer.setDataSource(this, uri);
				mediaPlayer.prepare();
				mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
					@Override
					public void onPrepared(MediaPlayer mp) {
						seekBar.setMax(mp.getDuration());
						resetTimerDisplay();
						mediaPlayer.start();
						updateMediaState(MediaState.STATE_PLAYING);
					}
				});
				mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
					@Override
					public void onCompletion(MediaPlayer mp) {
						updateMediaState(MediaState.STATE_PLAYING_PAUSED);
						seekBar.setProgress(seekBar.getMax());
					}
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
				activateSensors(false);
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
				activateSensors(false);
				pauseButton.setVisibility(View.INVISIBLE);
				seekBar.setVisibility(View.VISIBLE);
				playButton.setImageResource(R.drawable.ic_pause);
				playButton.setContentDescription(getString(R.string.stop));
				recordImage.setImageResource(R.drawable.ic_play);
				if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
					recordImage.setColorFilter(getResources().getColor(R.color.dark_text_color_primary), PorterDuff.Mode.SRC_IN);
				}
				recordImage.setVisibility(View.VISIBLE);
				startBlinking();
				startTimer();
				startSeekbar();
				inhibitPinLock(true);
				break;
			case STATE_RECORDING:
				activateSensors(true);
				pauseButton.setImageResource(R.drawable.ic_pause);
				pauseButton.setVisibility(supportsPauseResume() ? View.VISIBLE : View.INVISIBLE);
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
				activateSensors(false);
				pauseButton.setImageResource(R.drawable.ic_record);
				pauseButton.setVisibility(supportsPauseResume() ? View.VISIBLE : View.INVISIBLE);
				pauseButton.setContentDescription(getString(R.string.continue_recording));
				playButton.setImageResource(R.drawable.ic_stop);
				playButton.setContentDescription(getString(R.string.stop));
				recordImage.setVisibility(View.INVISIBLE);
				stopBlinking();
				stopTimer();
				inhibitPinLock(false);
				break;
			case STATE_PLAYING_PAUSED:
				activateSensors(false);
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
		timeDisplayHandler.removeCallbacksAndMessages(null);
		blinkingHandler.removeCallbacksAndMessages(null);
		seekBarHandler.removeCallbacksAndMessages(null);
		activateSensors(false);

		if (isBluetoothEnabled()) {
			logger.debug("stopBluetoothSco");
			try {
				audioManager.stopBluetoothSco();
			} catch (Exception ignored) { }
			if (audioStateChangedReceiver != null) {
				try {
					unregisterReceiver(audioStateChangedReceiver);
				} catch (IllegalArgumentException ignored) {}
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
				returnData();
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

	private void activateSensors(boolean activate) {
		if (preferenceService.isUseProximitySensor()) {
			if (activate) {
				sensorService.registerSensors(SENSOR_TAG_VOICE_RECORDER, this);
			} else {
				sensorService.unregisterSensors(SENSOR_TAG_VOICE_RECORDER);
			}
		}
	}

	@Override
	public void onSensorChanged(String key, boolean value) {
		logger.debug("onSensorChanged: " + value);
	}
}
