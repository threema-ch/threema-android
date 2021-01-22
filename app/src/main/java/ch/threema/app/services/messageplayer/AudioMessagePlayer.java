/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import androidx.core.content.ContextCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.listeners.SensorListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SensorService;
import ch.threema.app.utils.MediaPlayerStateWrapper;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.logging.ThreemaLogger;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

import static android.media.AudioManager.STREAM_MUSIC;

public class AudioMessagePlayer extends MessagePlayer implements AudioManager.OnAudioFocusChangeListener, SensorListener {
	private final Logger logger = LoggerFactory.getLogger(AudioMessagePlayer.class);
	private final String UID;

	private final int SEEKBAR_UPDATE_FREQUENCY = 500;
	private MediaPlayerStateWrapper mediaPlayer;
	private File decryptedFile = null;
	private int duration = 0;
	private int position = 0;
	private Thread mediaPositionListener;
	private AudioManager audioManager;
	private int streamType = STREAM_MUSIC;
	private int audioStreamType = STREAM_MUSIC;
	private PreferenceService preferenceService;
	private SensorService sensorService;
	private FileService fileService;
	private boolean micPermission;

	protected AudioMessagePlayer(Context context,
	                             MessageService messageService,
	                             FileService fileService,
	                             PreferenceService preferenceService,
	                             MessageReceiver messageReceiver,
	                             AbstractMessageModel messageModel) {
		super(context, messageService, fileService, messageReceiver, messageModel);

		this.preferenceService = preferenceService;
		this.audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
		this.sensorService = ThreemaApplication.getServiceManager().getSensorService();
		this.fileService = fileService;
		this.mediaPlayer = null;
		this.micPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

		this.UID = messageModel.getUid();
		logger.info("New MediaPlayer instance: {}", this.UID);

		// Set logger prefix
		if (logger instanceof ThreemaLogger) {
			((ThreemaLogger) logger).setPrefix(String.valueOf(this.UID));
		}
	}

	/**
	 * Get default volume level. Reduce level if mic permission has not been granted.
	 * Workaround for Android bug that causes the OS to play extra loud through earpiece.
	 * @return volume level
	 */
	private float getDefaultVolumeLevel() {
		if (streamType ==  AudioManager.STREAM_VOICE_CALL) {
			return micPermission ? 0.7f : 0.1f;
		}
		return 1.0f;
	}

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

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private AudioAttributes getAudioAttributes(int stream) {
		if (stream == AudioManager.STREAM_VOICE_CALL) {
			return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build();
		} else {
			return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();
		}
	}

	private void open(File decryptedFile, final boolean resume) {
		this.decryptedFile = decryptedFile;
		final Uri uri = fileService.getShareFileUri(decryptedFile, null);
		this.position = 0;

		logger.debug("open uri = {}", uri);

		if (mediaPlayer != null) {
			logger.debug("stopping existing player {}", Thread.currentThread().getId());

			if (resume && isPlaying()) {
				position = mediaPlayer.getCurrentPosition();
			}
			releasePlayer();
			abandonFocus(resume);
		}

		logger.debug("starting new player {}", Thread.currentThread().getId());
		mediaPlayer = new MediaPlayerStateWrapper();

		try {
			logger.debug("starting prepare - streamType = {}", streamType);
			setOutputStream(streamType);
			mediaPlayer.setDataSource(getContext(), uri);
			mediaPlayer.prepare();
			prepared(mediaPlayer, resume);
		} catch (Exception e) {
			if (e instanceof IllegalArgumentException) {
				showError(getContext().getString(R.string.file_is_not_audio));
			}
			logger.error("Could not prepare media player", e);
			stop();
		}
	}

	private void setOutputStream(int streamType) {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
			mediaPlayer.setAudioAttributes(getAudioAttributes(streamType));
		} else {
			mediaPlayer.setAudioStreamType(streamType);
		}

		logger.info("Speakerphone state = {} newStreamType = {}",
			this.audioManager.isSpeakerphoneOn(), streamType);

		this.audioManager.setSpeakerphoneOn(false);
/*
		if (streamType == STREAM_VOICE_CALL) {
			this.audioManager.setBluetoothScoOn(false);
			this.audioManager.setSpeakerphoneOn(false);
		} else {
			this.audioManager.setSpeakerphoneOn(true);
		}
*/	}

	@Override
	protected void open(File decryptedFile) {
		open(decryptedFile, false);
	}

	/**
	 * called, if the media player prepared
	 */
	private void prepared(MediaPlayerStateWrapper mp, boolean resume) {
		logger.debug("prepared");

		//do not play if state is changed! (not playing)
		if (this.getState() != State_PLAYING) {
			logger.debug("not in playing state");
			return;
		}

		if (mp != this.mediaPlayer) {
			//another mediaplayer
			logger.debug("another player instance");
			return;
		}

		duration = mediaPlayer.getDuration();

		if (duration == 0) {
			MediaMessageDataInterface d = this.getData();
			if (d instanceof AudioDataModel) {
				duration = ((AudioDataModel) d).getDuration();
			} else if (d instanceof FileDataModel) {
				duration = (int) ((FileDataModel) d).getDuration();
			}
		}
		logger.debug("duration = {}", duration);

		if (this.position != 0) {
			this.mediaPlayer.seekTo(this.position);
		}

		logger.debug("play from position {}", this.position);

		if (requestFocus(resume)) {
			logger.debug("request focus done");

			if (this.mediaPlayer != null) {
				this.mediaPlayer.setVolume(getDefaultVolumeLevel(), getDefaultVolumeLevel());
				this.mediaPlayer.start();
				initPositionListener(true);
			}
		}
	}

	private void initPositionListener(boolean hard) {
		logger.debug("initPositionListener hard = {}", hard);

		if (!hard && this.mediaPositionListener != null && this.mediaPositionListener.isAlive()) {
			return;
		}

		if (this.mediaPositionListener != null) {
			this.mediaPositionListener.interrupt();
		}

		this.mediaPositionListener = new Thread(new Runnable() {
			@Override
			public void run() {
				boolean cont = true;
				while (cont) {
					try {
						cont = false;
						Thread.sleep(SEEKBAR_UPDATE_FREQUENCY);

						if (mediaPlayer != null && getState() == State_PLAYING && isPlaying()) {
							position = mediaPlayer.getCurrentPosition();
							AudioMessagePlayer.this.updatePlayState();
							cont = !Thread.interrupted();
						}
					} catch (Exception e) {
						cont = false;
					}
				}
			}
		});

		this.mediaPositionListener.start();

		//reset old listeners
		this.mediaPlayer.setStateListener(null);
		//configure new one
		this.mediaPlayer.setStateListener(new MediaPlayerStateWrapper.StateListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				stop();
				ListenerManager.messagePlayerListener.handle(listener -> listener.onAudioPlayEnded(getMessageModel()));
			}

			@Override
			public void onPrepared(MediaPlayer mp) {
			}
		});
	}

	@Override
	protected void makePause(int source) {
		logger.info("makePause");

		if (source != SOURCE_LIFECYCLE) {
			if (this.mediaPlayer != null) {
				playerPause();
				if (this.getState() != State_INTERRUPTED_PLAY) {
					abandonFocus(false);
				}
			}
		} else {
			// the app has been put to the background
			if (preferenceService.isUseProximitySensor()) {
				sensorService.unregisterSensors(this.getMessageModel().getUid());
			}
			if (audioStreamType != STREAM_MUSIC) {
				ListenerManager.messagePlayerListener.handle(listener -> listener.onAudioStreamChanged(STREAM_MUSIC));
				changeAudioOutput(STREAM_MUSIC);
				audioStreamType = STREAM_MUSIC;
			}
		}
	}

	@Override
	protected void makeResume(int source) {
		logger.debug("makeResume");

		if (source != SOURCE_LIFECYCLE) {
			if (this.mediaPlayer != null) {
				if (this.duration > 0 && !isPlaying()) {
					if (requestFocus(false)) {
						playerStart();
						initPositionListener(true);
					}
				}
			} else {
				this.stop();
				this.open();
			}
		} else {
			// the app was brought to the foreground
			if (this.mediaPlayer != null) {
				initPositionListener(false);
				if (preferenceService.isUseProximitySensor()) {
					sensorService.registerSensors(this.getMessageModel().getUid(), this);
				}
			}
		}
	}

	private void releasePlayer() {
		logger.debug("releasePlayer");

		if (mediaPositionListener != null) {
			logger.debug("mediaPositionListener.interrupt()");
			mediaPositionListener.interrupt();
			mediaPositionListener = null;
		}

		if (mediaPlayer != null) {

			try {
				logger.debug("stop");
				mediaPlayer.stop();
			} catch (Exception e) {
				logger.error("Could not stop media player", e);
			}

			try {
				logger.debug("reset");
				mediaPlayer.reset();
			} catch (Exception e) {
				logger.error("Could not reset media player", e);
			}

			try {
				logger.debug("release");
				mediaPlayer.release();
				mediaPlayer = null;
			} catch (Exception e) {
				logger.error("Could not release media player", e);
			}
		}
		logger.debug("Player released");
	}

	@Override
	public boolean stop() {
		logger.debug("Stop player called from stop() {}", Thread.currentThread().getId());
		releasePlayer();
		abandonFocus(false);

		return super.stop();
	}

	@Override
	public void seekTo(int pos) {
		logger.debug("seekTo");

		if (pos >= 0) {
			if (this.mediaPlayer != null) {
				this.mediaPlayer.seekTo(pos);
				this.position = this.mediaPlayer.getCurrentPosition();
			}
			this.updatePlayState();
		}
	}

	@Override
	public int getDuration() {
		if (this.mediaPlayer != null) {
			return this.duration;
		}
		return 0;
	}

	@Override
	public int getPosition() {
		if (this.getState() == State_PLAYING || this.getState() == State_PAUSE || this.getState() == State_INTERRUPTED_PLAY) {
			return this.position;
		}

		return 0;
	}

	private void changeAudioOutput(int streamType) {
		logger.debug("changeAudioOutput");

		this.streamType = streamType;
		if (this.mediaPlayer != null && isPlaying()) {
			if (this.decryptedFile != null) {
				this.open(this.decryptedFile, true);
			} else {
				logger.debug("decrypted file not available");
			}
		}
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		switch (focusChange) {
			case AudioManager.AUDIOFOCUS_GAIN:
				// resume playback
				logger.debug("AUDIOFOCUS_GAIN");
				this.resume(SOURCE_AUDIOFOCUS);
				if (mediaPlayer != null) {
					mediaPlayer.setVolume(getDefaultVolumeLevel(), getDefaultVolumeLevel());
				}
				break;

			case AudioManager.AUDIOFOCUS_LOSS:
				// Lost focus for an unbounded amount of time: stop playback and release media player
				logger.debug("AUDIOFOCUS_LOSS");
				this.stop();
				break;

			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
				// Lost focus for a short time, but we have to stop
				// playback. We don't release the media player because playback
				// is likely to resume
				logger.info("AUDIOFOCUS_LOSS_TRANSIENT");
				this.pause(true, SOURCE_AUDIOFOCUS);
				break;

			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				// Lost focus for a short time, but it's ok to keep playing
				// at an attenuated level
				logger.debug("AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
				if (mediaPlayer != null) {
					mediaPlayer.setVolume(0.2f, 0.2f);
				}
				break;
		}
	}

	private boolean requestFocus(boolean resume) {
		logger.debug("requestFocus resume = {} streamType = {}", resume, streamType);

		if (audioManager.requestAudioFocus(this, this.streamType, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			if (preferenceService.isUseProximitySensor() && !resume) {
				sensorService.registerSensors(this.getMessageModel().getUid(), this);
			}
			return true;
		} else {
			logger.debug("Focus request not granted");
		}
		return false;
	}

	private void abandonFocus(boolean resume) {
		logger.debug("abandonFocus resume = {}", resume);
		if (!resume) {
			audioManager.abandonAudioFocus(this);
			if (preferenceService.isUseProximitySensor()) {
				sensorService.unregisterSensors(this.getMessageModel().getUid());
				ListenerManager.messagePlayerListener.handle(listener -> listener.onAudioStreamChanged(STREAM_MUSIC));
			}
		}
	}

	@Override
	public void onSensorChanged(String key, boolean value) {
		logger.info("SensorService onSensorChanged: {}", value);

		RuntimeUtil.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (key.equals(SensorListener.keyIsNear)) {

					int newStreamType = value ?
							AudioManager.STREAM_VOICE_CALL :
							AudioManager.STREAM_MUSIC;

					if (newStreamType != audioStreamType) {
						logger.info("New Audio stream: {}", newStreamType);

						ListenerManager.messagePlayerListener.handle(listener -> listener.onAudioStreamChanged(newStreamType));
						changeAudioOutput(newStreamType);

						audioStreamType = newStreamType;
					}
				}
			}
		});
	}

	private boolean isPlaying() {
		boolean isPlaying = false;

		if (this.mediaPlayer != null) {
			isPlaying = this.mediaPlayer.isPlaying();
		}

		return isPlaying;
	}

	private void playerStart() {
		if (this.mediaPlayer != null) {
			this.mediaPlayer.start();
		}
	}

	private void playerPause() {
		if (this.mediaPlayer != null) {
			this.mediaPlayer.pause();
		}
	}
}

