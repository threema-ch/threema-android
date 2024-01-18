/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.EnumSet;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import ch.threema.base.utils.LoggingUtil;

/**
 * A wrapper class for {@link android.media.MediaPlayer}.
 * <p>
 * Encapsulates an instance of MediaPlayer, and makes a record of its internal state accessible via a
 * {@link #getState()} accessor.
 * </p>
 */
public class MediaPlayerStateWrapper {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MediaPlayerStateWrapper");

	private final MediaPlayer mediaPlayer;
	private State currentState;
	private final MediaPlayerStateWrapper stateWrapper;
	private StateListener stateListener;

	public MediaPlayerStateWrapper() {
		stateWrapper = this;
		mediaPlayer = new MediaPlayer();
		stateListener = null;
		currentState = State.IDLE;
		mediaPlayer.setOnPreparedListener(onPreparedListener);
		mediaPlayer.setOnCompletionListener(onCompletionListener);
		mediaPlayer.setOnBufferingUpdateListener(onBufferingUpdateListener);
		mediaPlayer.setOnErrorListener(onErrorListener);
		mediaPlayer.setOnInfoListener(onInfoListener);
	}

	public enum State {
		IDLE, ERROR, INITIALIZED, PREPARING, PREPARED, STARTED, STOPPED, PLAYBACK_COMPLETE, PAUSED;
	}

	public void setDataSource(Context context, Uri uri) {
		if (currentState == State.IDLE) {
			try {
				mediaPlayer.setDataSource(context, uri);
				currentState = State.INITIALIZED;
			} catch (IllegalArgumentException|IllegalStateException|IOException e) {
				logger.error("Exception", e);
			}
		}
	}

	public void setDataSource(AssetFileDescriptor afd) {
		if (currentState == State.IDLE) {
			try {
				mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
				currentState = State.INITIALIZED;
			} catch (IllegalArgumentException|IllegalStateException|IOException e) {
				logger.error("Exception", e);
			}
		}
	}

	public void prepareAsync() {
		logger.debug("prepareAsync()");
		if (EnumSet.of(State.INITIALIZED, State.STOPPED).contains(currentState)) {
			mediaPlayer.prepareAsync();
			currentState = State.PREPARING;
		}
	}

	public void prepare() throws IOException, IllegalStateException {
		logger.debug("prepare()");
		if (EnumSet.of(State.INITIALIZED, State.STOPPED).contains(currentState)) {
			currentState = State.PREPARING;
			mediaPlayer.prepare();
			currentState = State.PREPARED;
		}
	}

	public boolean isPlaying() {
		logger.debug("isPlaying()");
		if (currentState != State.ERROR) {
			return mediaPlayer.isPlaying();
		}
		return false;
	}

	public void seekTo(int msec) {
		logger.debug("seekTo()");
		if (EnumSet.of(State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETE).contains(currentState)) {
			mediaPlayer.seekTo(msec);
		}
	}

	public void pause() {
		logger.debug("pause()");
		if (EnumSet.of(State.STARTED, State.PAUSED).contains(currentState)) {
			mediaPlayer.pause();
			currentState = State.PAUSED;
		}
	}

	public void start() {
		logger.debug("start()");
		if (EnumSet.of(State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETE).contains(currentState)) {
			mediaPlayer.start();
			currentState = State.STARTED;
		}
	}

	public void stop() {
		logger.debug("stop()");
		if (EnumSet.of(State.PREPARED, State.STARTED, State.STOPPED, State.PAUSED, State.PLAYBACK_COMPLETE).contains(
				currentState)) {
			mediaPlayer.stop();
			currentState = State.STOPPED;
		}
	}

	public void reset() {
		// Idle, Initialized, Prepared, Started, Paused, Stopped, PlaybackCompleted, Error
		logger.debug("reset()");
		if (EnumSet.of(State.PREPARED, State.STARTED, State.STOPPED, State.PAUSED, State.PLAYBACK_COMPLETE, State.IDLE, State.INITIALIZED).contains(
			currentState)) {
			mediaPlayer.reset();
			currentState = State.IDLE;
		}
	}

	/**
	 * @return The current state of the mediaplayer state machine.
	 */
	public State getState() {
		logger.debug("getState()");
		return currentState;
	}

	public void release() {
		logger.debug("release()");
		mediaPlayer.release();
	}

	/* INTERNAL LISTENERS */
	private MediaPlayer.OnPreparedListener onPreparedListener = new MediaPlayer.OnPreparedListener() {

		@Override
		public void onPrepared(MediaPlayer mp) {
			logger.debug("on prepared");
			if (EnumSet.of(State.PREPARING).contains(currentState)) {
				currentState = State.PREPARED;
				if (stateListener != null) {
					stateListener.onPrepared(mp);
				}
			}
		}
	};

	private MediaPlayer.OnCompletionListener onCompletionListener = new MediaPlayer.OnCompletionListener() {

		@Override
		public void onCompletion(MediaPlayer mp) {
			logger.debug("on completion");
			currentState = State.PLAYBACK_COMPLETE;
			if (stateListener != null) {
				stateListener.onCompletion(mp);
			}
		}
	};

	private MediaPlayer.OnBufferingUpdateListener onBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {

		@Override
		public void onBufferingUpdate(MediaPlayer mp, int percent) {
			logger.debug("on buffering update");
			stateWrapper.onBufferingUpdate(mp, percent);
		}
	};

	private MediaPlayer.OnErrorListener onErrorListener = new MediaPlayer.OnErrorListener() {

		@Override
		public boolean onError(MediaPlayer mp, int what, int extra) {
			logger.debug("on error");
			currentState = State.ERROR;
			stateWrapper.onError(mp, what, extra);
			return false;
		}
	};

	private MediaPlayer.OnInfoListener onInfoListener = new MediaPlayer.OnInfoListener() {

		@Override
		public boolean onInfo(MediaPlayer mp, int what, int extra) {
			logger.debug("on info");
			stateWrapper.onInfo(mp, what, extra);
			return false;
		}
	};


	public void onBufferingUpdate(MediaPlayer mp, int percent) {}

	boolean onError(MediaPlayer mp, int what, int extra) {
		return false;
	}

	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		return false;
	}

	public interface StateListener {
		void onCompletion(MediaPlayer mp);
		void onPrepared(MediaPlayer mp);
	}

	public void setStateListener(StateListener listener) {
		stateListener = listener;
	}

	/* OTHER STUFF */
	public int getCurrentPosition() {
		if (currentState != State.ERROR && currentState != State.IDLE) {
			return mediaPlayer.getCurrentPosition();
		} else {
			return 0;
		}
	}

	public int getDuration() {
		// Prepared, Started, Paused, Stopped, PlaybackCompleted
		if (EnumSet.of(State.PREPARED, State.STARTED, State.PAUSED, State.STOPPED, State.PLAYBACK_COMPLETE).contains(
				currentState)) {
			return mediaPlayer.getDuration();
		} else {
			return 100;
		}
	}

	public void setAudioAttributes(AudioAttributes attributes) {
		// Idle, Initialized, Stopped, Prepared, Started, Paused, PlaybackCompleted
		if (EnumSet.of(State.IDLE, State.INITIALIZED, State.STOPPED, State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETE).contains(
				currentState)) {
			mediaPlayer.setAudioAttributes(attributes);
		}
	}

	public void setAudioStreamType(int streamType) {
		// Idle, Initialized, Stopped, Prepared, Started, Paused, PlaybackCompleted
		if (EnumSet.of(State.IDLE, State.INITIALIZED, State.STOPPED, State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETE).contains(
				currentState)) {
			mediaPlayer.setAudioStreamType(streamType);
		}
	}

	public void setVolume(float leftVolume, float rightVolume) {
		// Idle, Initialized, Stopped, Prepared, Started, Paused, PlaybackCompleted
		if (EnumSet.of(State.IDLE, State.INITIALIZED, State.STOPPED, State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETE).contains(
				currentState)) {
			mediaPlayer.setVolume(leftVolume, rightVolume);
		}
	}

	public void setLooping(boolean looping) {
		if (EnumSet.of(State.IDLE, State.INITIALIZED, State.STOPPED, State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETE).contains(
			currentState)) {
			mediaPlayer.setLooping(looping);
		}
	}

	public void setScreenOnWhilePlaying(boolean screenOn) {
		mediaPlayer.setScreenOnWhilePlaying(screenOn);
	}

	public void setOnPreparedListener(MediaPlayer.OnPreparedListener onPreparedListener) {
		mediaPlayer.setOnPreparedListener(onPreparedListener);
	}

	public void setOnCompletionListener(MediaPlayer.OnCompletionListener onCompletionListener) {
		mediaPlayer.setOnCompletionListener(onCompletionListener);
	}
}
