/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.video.transcoder.audio;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import androidx.annotation.NonNull;
import ch.threema.app.video.transcoder.VideoTranscoder;
import java8.util.Optional;

public abstract class AbstractAudioTranscoder {
	private static final Logger logger = LoggerFactory.getLogger(AbstractAudioTranscoder.class);

	//region Member Variables
	protected final AudioComponent component;
	protected final VideoTranscoder.Stats stats;
	protected long trimStartTimeUs = 0;
	protected final long trimEndTimeUs;

	private @NonNull State state = State.INITIAL;

	protected MediaFormat outputFormat;
	protected MediaMuxer muxer;
	protected @NonNull Optional<Integer> muxerTrack = Optional.empty();

	//endregion

	/**
	 * @param component The audio component that should be transcoded
	 * @param stats Transcoder Statistics
	 * @param trimEndTimeMs Trim time from the end in ms (!)
	 */
	public AbstractAudioTranscoder(AudioComponent component, VideoTranscoder.Stats stats, long trimEndTimeMs) {
		this.component = component;
		this.stats = stats;
		this.trimEndTimeUs = trimEndTimeMs * 1000;
	}

	//region Getter / Setter

	public @NonNull State getState() {
		return this.state;
	}

	protected void setState(@NonNull State state) {
		logger.debug("Setting audio transcoder state to {}", state.name());
		this.state = state;
	}

	//endregion


	/**
	 * @return whether there are frames which did not finished the transcoding process.
	 */
	abstract public boolean hasPendingIntermediateFrames();

	//region Lifecycle

	/**
	 * Initializes the transcoder pipeline
	 *
	 * Changes State from {@link State#INITIAL} to either {@link State#DETECTING_INPUT_FORMAT},
	 * {@link State#DETECTING_OUTPUT_FORMAT} or {@link State#WAITING_ON_MUXER}.
	 *
	 * Should initialize outputFormat of the {@link AbstractAudioTranscoder} class.
	 *
	 * @throws IOException if a codec could not be initialized
	 */
	public abstract void setup() throws IOException, UnsupportedAudioFormatException;

	/**
	 * Trims the media start. Requires {@link AbstractAudioTranscoder#component} to be initialized.
	 *
	 * May only be called after {@link State#INITIAL} and before {@link State#TRANSCODING}.
	 */
	public void trimMediaStartTo(long trimStartTimeUs) {
		if (this.getState() == State.INITIAL || this.getState().ordinal() >= State.TRANSCODING.ordinal()) {
			throw new IllegalStateException(String.format("Trimming may not be done in state %s", this.getState().name()));
		}
		this.trimStartTimeUs = trimStartTimeUs;
		// start sound as soon as possible after provided trimStartTime
		this.component.getMediaExtractor().seekTo(trimStartTimeUs, MediaExtractor.SEEK_TO_NEXT_SYNC);

		logger.debug(
			"Trimmed audio until {}us, the next sync after requested trim time {}us",
			this.component.getMediaExtractor().getSampleTime(),
			trimStartTimeUs
		);
	}

	/**
	 * Transcoding step. Should be repeatedly called until {@link AudioFormatTranscoder#getState()}
	 * returns {@link State#DONE}, but not after the done state is reached.
	 *
	 * May not be called before {@link AudioFormatTranscoder#setup()}.
	 */
	public abstract void step() throws UnsupportedAudioFormatException;

	/**
	 * Injects the audio as track to the muxer and transfers the class state to
	 * {@link State#TRANSCODING}.
	 *
	 * May only be called if {@link AudioFormatTranscoder#getState()} returns
	 * {@link State#WAITING_ON_MUXER}.
	 *
	 */
	public void injectTrackToMuxer(@NonNull MediaMuxer muxer) {
		if(this.state != State.WAITING_ON_MUXER) {
			throw new IllegalStateException("The muxer may not be reconfigured");
		}

		this.muxer = muxer;
		final int trackNumber = muxer.addTrack(this.outputFormat);
		this.muxerTrack = Optional.of(trackNumber);
		logger.debug("Added audio track number {} to muxer with format {}", trackNumber, this.outputFormat);

		this.setState(State.TRANSCODING);
	}

	/**
	 * Cleanup of codecs etc.
	 * May only be called if {@link AudioFormatTranscoder#getState()} returns {@link State#DONE}.
	 */
	public void cleanup() throws Exception {
		if (this.state != State.DONE) {
			throw new IllegalStateException("Cleanup is only permitted after encoding has finished.");
		}
	}

	//endregion

	/**
	 * Current state of the Audio Transcoder.
	 *
	 * States should be changed according to the definition order, but states may be skipped.
	 */
	public enum State {
		/**
		 * Uninitialized state
		 */
		INITIAL,

		/**
		 * Waiting for the input audio format to be configured by the decoder-codec
		 */
		DETECTING_INPUT_FORMAT,

		/**
		 * Waiting for the output format to be configured by the encoder-codec
		 */
		DETECTING_OUTPUT_FORMAT,

		/**
		 * The output format has been detected and we are waiting on the muxer injection.
		 */
		WAITING_ON_MUXER,

		/**
		 * Transcoding the audio.
		 */
		TRANSCODING,

		/**
		 * Transcoding has finished.
		 */
		DONE
	}
}
