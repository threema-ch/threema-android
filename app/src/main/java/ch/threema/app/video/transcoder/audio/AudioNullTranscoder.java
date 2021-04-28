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

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import ch.threema.app.video.transcoder.VideoTranscoder;

/**
 * Keep audio input track and return it unchanged to the muxer
 */
public class AudioNullTranscoder extends AbstractAudioTranscoder {
	private static final Logger logger = LoggerFactory.getLogger(AudioNullTranscoder.class);

	/**
	 * Time of the previously muxed sample.
	 */
	private long previousSampleTime;

	private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
	private ByteBuffer buffer;


	/**
	 * @param component The audio component that should be transcoded
	 * @param stats Transcoder Statistics
	 * @param trimEndTimeMs Trim time from the end in ms (!)
	 */
	public AudioNullTranscoder(AudioComponent component, VideoTranscoder.Stats stats, long trimEndTimeMs) {
		super(component, stats, trimEndTimeMs);
	}

	@Override
	public boolean hasPendingIntermediateFrames() {
		// We don't have any intermediate frames which could be pending when done.
		return this.getState() != State.DONE;
	}

	@Override
	public void setup() {
		if(this.getState() != State.INITIAL) {
			throw new IllegalStateException("Setup may only be called on initialization");
		}

		this.outputFormat = this.component.getTrackFormat();
		this.buffer = ByteBuffer.allocate(this.outputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));

		this.setState(State.WAITING_ON_MUXER);
	}

	@Override
	public void step() {
		if (this.getState() == State.INITIAL || this.getState() == State.DONE) {
			throw new IllegalStateException(String.format("Calling an audio transcoding step is not allowed in state %s", this.getState()));
		}

		if (this.getState() == State.WAITING_ON_MUXER) {
			logger.debug("Skipping transcoding step, waiting for muxer to be injected.");
			return;
		}

		MediaExtractor extractor = this.component.getMediaExtractor();

		final int sampleSize = extractor.readSampleData(this.buffer, 0);
		this.bufferInfo.set(
		 	0,
			 sampleSize,
			 extractor.getSampleTime(),
			 extractor.getSampleFlags()
		 );

		logger.trace("audio extractor: returned buffer of chunkSize {}", sampleSize);
		logger.trace("audio extractor: returned buffer for sampleTime {}", this.bufferInfo.presentationTimeUs);

		if (this.trimEndTimeUs > 0 && this.bufferInfo.presentationTimeUs > this.trimEndTimeUs) {
			logger.debug("audio extractor: The current sample is over the trim time. Lets stop.");
			this.setState(State.DONE);
			return;
		}

		if (sampleSize >= 0) {
			this.stats.incrementExtractedFrameCount(this.component);

			if (this.bufferInfo.presentationTimeUs >= this.previousSampleTime) {
				this.previousSampleTime = this.bufferInfo.presentationTimeUs;
				this.muxer.writeSampleData(this.muxerTrack.get(), this.buffer, this.bufferInfo);
			} else {
				// skip old audio, as this only results in quality reduction.
				logger.debug("audio muxer: presentationTimeUs {} < previousPresentationTime {}",
					this.bufferInfo.presentationTimeUs, this.previousSampleTime);
			}
		}

		if (!extractor.advance()) {
			this.setState(State.DONE);
		}
	}
}
