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
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import ch.threema.app.video.transcoder.VideoTranscoder;
import ch.threema.app.video.transcoder.MediaComponent;
import java8.util.Optional;


/**
 * Transcode an audio track to another format.
 *
 * Based on https://github.com/groupme/android-video-kit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@SuppressWarnings( "deprecation" ) // we use various deprecated methods to support older versions.
public class AudioFormatTranscoder extends AbstractAudioTranscoder {
	private static final Logger logger = LoggerFactory.getLogger(AudioFormatTranscoder.class);

	private static final int TIMEOUT_USEC = VideoTranscoder.TIMEOUT_USEC;


	//region Member Variables

	/**
	 * Requested output format for the transcoder.
	 */
	private final int outputAudioBitrate;

	private MediaCodec encoder;
	private MediaCodec decoder;

	/**
	 * Decoder input buffer access for for Android before {@link Build.VERSION_CODES#LOLLIPOP}
	 */
	private ByteBuffer[] decoderInputBuffers;

	/**
	 * Decoder output buffer access for for Android before {@link Build.VERSION_CODES#LOLLIPOP}
	 */
	private ByteBuffer[] decoderOutputBuffers;

	/**
	 * Encoder input buffer access for for Android before {@link Build.VERSION_CODES#LOLLIPOP}
	 */
	private ByteBuffer[] encoderInputBuffers;

	/**
	 * Encoder output buffer access for for Android before {@link Build.VERSION_CODES#LOLLIPOP}
	 */
	private ByteBuffer[] encoderOutputBuffers;

	/**
	 * Information about the last decoder output buffer that was made available.
	 */
	private MediaCodec.BufferInfo decoderOutputBufferInfo;

	/**
	 * Information about the last encoder output buffer that was made available.
	 */
	private MediaCodec.BufferInfo encoderOutputBufferInfo;

	private boolean extractorDone;

	/**
	 * Next decoder output buffer that should be encoded
	 */
	private @NonNull Optional<Integer> decoderOutputBufferNextIndex = Optional.empty();

	private boolean encoderDone = false;
	private int resendRetryCount = 0;

	/**
	 * Keeps track of the last appended audio time, so that we do not append out-of-order audio.
	 */
	private long previousPresentationTime = -1;

	//endregion

	@Override
	public boolean hasPendingIntermediateFrames() {
		return this.decoderOutputBufferNextIndex.isPresent();
	}

	//region Setup

	/**
	 * @param component The audio component that should be transcoded
	 * @param stats Transcoder Statistics
	 * @param trimEndTimeMs Trim time from the end in ms (!)
	 * @param outputAudioBitrate Target bitrate for the output audio
	 */
	public AudioFormatTranscoder(
		AudioComponent component,
		VideoTranscoder.Stats stats,
		long trimEndTimeMs,
		int outputAudioBitrate
	) {
		super(component, stats, trimEndTimeMs);
		this.outputAudioBitrate = outputAudioBitrate;
	}

	@Override
	public void setup() throws IOException, UnsupportedAudioFormatException {
		if(this.getState() != State.INITIAL) {
			throw new IllegalStateException("Setup may only be called on initialization");
		}

		MediaFormat inputFormat = this.component.getTrackFormat();

		// Setup De/Encoder
		this.setupAudioDecoder(inputFormat);
		this.setupAudioEncoder(inputFormat);

		this.setState(State.DETECTING_INPUT_FORMAT);
	}

	private void setupAudioDecoder(MediaFormat inputFormat) throws IOException, UnsupportedAudioFormatException {
		logger.debug("audio decoder: set sample rate to {}", inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));

		if (logger.isDebugEnabled() && inputFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
			logger.debug("audio decoder: set bit rate to {}", inputFormat.getInteger(MediaFormat.KEY_BIT_RATE));
		} else {
			logger.debug("audio decoder: decoding unknown bit rate");
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			this.decoder = this.getDecoderFor(inputFormat);
		} else {
			this.decoder = MediaCodec.createDecoderByType(VideoTranscoder.getMimeTypeFor(inputFormat));
		}

		this.decoder.configure(inputFormat, null, null, 0);
		this.decoder.start();

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			this.decoderInputBuffers = this.decoder.getInputBuffers();
			this.decoderOutputBuffers = this.decoder.getOutputBuffers();
		}
		this.decoderOutputBufferInfo = new MediaCodec.BufferInfo();
	}

	private void setupAudioEncoder(MediaFormat inputFormat) throws IOException {
		int sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
		int channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

		this.outputFormat = MediaFormat.createAudioFormat(VideoTranscoder.Defaults.OUTPUT_AUDIO_MIME_TYPE,
			sampleRate, channelCount);

		this.outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, this.outputAudioBitrate);
		this.outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, VideoTranscoder.Defaults.OUTPUT_AUDIO_AAC_PROFILE);

		MediaCodecInfo codecInfo = VideoTranscoder.selectCodec(VideoTranscoder.Defaults.OUTPUT_AUDIO_MIME_TYPE);
		logger.debug("audio encoder: set sample rate to {}", this.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
		logger.debug("audio encoder: set bit rate to {}", this.outputFormat.getInteger(MediaFormat.KEY_BIT_RATE));

		if (this.encoder == null) {
			this.encoder = MediaCodec.createByCodecName(codecInfo.getName());
		} else {
			this.encoder.stop();
		}
		this.encoder.configure(this.outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		this.encoder.start();

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			this.encoderInputBuffers = this.encoder.getInputBuffers();
			this.encoderOutputBuffers = this.encoder.getOutputBuffers();
		}
		this.encoderOutputBufferInfo = new MediaCodec.BufferInfo();
	}

	/**
	 * Detect the most optimal decoder. This method is only available with
	 * Android SDK >= {@link Build.VERSION_CODES#LOLLIPOP}
	 *
	 * @throws UnsupportedAudioFormatException if there is no decoder for this format available.
	 * @throws IOException If the codec creation failed.
	 */
	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private MediaCodec getDecoderFor(MediaFormat inputFormat) throws UnsupportedAudioFormatException, IOException {

		final MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);

		if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
			// Workaround for Framework bug, see {@link MediaCodecList#findDecoderForFormat)
			inputFormat.setString(MediaFormat.KEY_FRAME_RATE, null);
		}

		@Nullable final String codec = mediaCodecList.findDecoderForFormat(inputFormat);

		if (codec == null) {
			logger.warn("Could not find a codec for input format {}", inputFormat);
			throw new UnsupportedAudioFormatException(inputFormat);
		}

		return MediaCodec.createByCodecName(codec);
	}

	//endregion

	@Override
	public void step() throws UnsupportedAudioFormatException {
		if (this.getState() == State.INITIAL || this.getState() == State.DONE) {
			throw new IllegalStateException(String.format("Calling an audio transcoding step is not allowed in state %s", this.getState()));
		}

		if (this.getState() == State.WAITING_ON_MUXER) {
			logger.debug("Skipping transcoding step, waiting for muxer to be injected.");
			return;
		}

		// Extract audio from file and feed to decoder.
		// Do not extract audio if we have determined the output format but we are not yet
		// ready to mux the frames.
		if (!this.extractorDone) {
			this.extractorDone = this.pipeExtractorFrameToDecoder(this.decoder, this.decoderInputBuffers, this.component);
		}

		// Poll output frames from the audio decoder.
		if (this.decoderOutputBufferNextIndex.isEmpty()) {
			this.pollAudioFromDecoder(this.decoderOutputBufferInfo);
		}

		// Feed the pending audio buffer to the audio encoder
		if (this.decoderOutputBufferNextIndex.isPresent()) {
			this.pipeDecoderFrameToEncoder(this.decoderOutputBufferInfo);
		}

		// Poll frames from audio encoder and send them to the muxer
		if (!this.encoderDone) {
			this.encoderDone = this.pipeEncoderFrameToMuxer(this.encoderOutputBufferInfo);
			if (this.encoderDone) {
				this.setState(State.DONE);
			}
		}
	}

	//region Transcoding

	/**
	 * Extract and feed to decoder.
	 *
	 * @return Finished. True when it extracts the last frame.
	 */
	private boolean pipeExtractorFrameToDecoder(MediaCodec decoder, ByteBuffer[] buffers, MediaComponent component) {
		final int decoderInputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);

		if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
			logger.debug("no audio decoder input buffer");
			return false;
		}

		logger.trace("audio extractor: returned input buffer: {}", decoderInputBufferIndex);

		MediaExtractor extractor = component.getMediaExtractor();
		int chunkSize = extractor.readSampleData(
			Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ?
				buffers[decoderInputBufferIndex] :
				decoder.getInputBuffer(decoderInputBufferIndex), 0);

		long sampleTime = extractor.getSampleTime();

		logger.trace("audio extractor: returned buffer of chunkSize {}", chunkSize);
		logger.trace("audio extractor: returned buffer for sampleTime {}", sampleTime);

		if (this.trimEndTimeUs > 0 && sampleTime > this.trimEndTimeUs) {
			logger.debug("audio extractor: The current sample is over the trim time. Lets stop.");
			decoder.queueInputBuffer(
				decoderInputBufferIndex,
				0,
				0,
				0,
				MediaCodec.BUFFER_FLAG_END_OF_STREAM);
			return true;
		}

		if (chunkSize >= 0) {
			decoder.queueInputBuffer(
				decoderInputBufferIndex,
				0,
				chunkSize,
				sampleTime,
				extractor.getSampleFlags());

			this.stats.incrementExtractedFrameCount(component);
		}

		if (!extractor.advance()) {
			logger.debug("audio extractor: EOS");
			try {
				decoder.queueInputBuffer(
					decoderInputBufferIndex,
					0,
					0,
					0,
					MediaCodec.BUFFER_FLAG_END_OF_STREAM);
			} catch (Exception e) {
				// On some Android versions, queueInputBuffers' native code throws an exception if
				// BUFFER_FLAG_END_OF_STREAM is set on non-empty buffers.
				this.resendRetryCount++;
				if (this.resendRetryCount < 5) {
					return this.pipeExtractorFrameToDecoder(decoder, buffers, component);
				} else {
					this.resendRetryCount = 0;
					throw e;
				}
			}
			return true;
		}

		return false;
	}

	private void pollAudioFromDecoder(MediaCodec.BufferInfo audioDecoderOutputBufferInfo) throws UnsupportedAudioFormatException {
		final int decoderOutputBufferIndex;

		try {
			decoderOutputBufferIndex = this.decoder.dequeueOutputBuffer(audioDecoderOutputBufferInfo, TIMEOUT_USEC);
		} catch(IllegalStateException exception) {
			// We cannot determine the exact cause of the Exception, as it is only reported in the
			// system's log. However, the most likely cause is an unsupported format/extension by
			// the codec.
			logger.warn("Decoder input buffer could not be dequeued.");
			throw new UnsupportedAudioFormatException("Decoder error: " + exception.getMessage(), exception);
		}

		if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
			logger.debug("audio decoder: no output buffer");
			return;
		}


		if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
			logger.debug("audio decoder: output buffers changed");
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
				this.decoderOutputBuffers = this.decoder.getOutputBuffers();
			}
			return;
		}

		if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
			MediaFormat decoderOutputFormat = this.decoder.getOutputFormat();
			logger.debug("audio decoder: output format changed: {}", decoderOutputFormat);
			try {
				this.setupAudioEncoder(decoderOutputFormat);
				this.setState(State.DETECTING_OUTPUT_FORMAT);
			} catch (IOException e) {
				logger.error("Reconfiguring encoder media format failed");
			}
			return;
		}

		logger.trace("audio decoder: returned output buffer: {}", decoderOutputBufferIndex);
		logger.trace("audio decoder: returned buffer of size {}", audioDecoderOutputBufferInfo.size);

		if ((audioDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
			logger.debug("audio decoder: codec config buffer");
			this.decoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
			return;
		}

		logger.trace("audio decoder: returned buffer for time {}", audioDecoderOutputBufferInfo.presentationTimeUs);
		logger.trace("audio decoder: output buffer is now pending: {}", decoderOutputBufferIndex);

		this.decoderOutputBufferNextIndex = Optional.of(decoderOutputBufferIndex);
		this.stats.audioDecodedFrameCount++;
	}

	private void pipeDecoderFrameToEncoder(MediaCodec.BufferInfo audioDecoderOutputBufferInfo) {
		logger.trace("audio decoder: attempting to process pending buffer: {}", this.decoderOutputBufferNextIndex.get());

		int encoderInputBufferIndex = this.encoder.dequeueInputBuffer(TIMEOUT_USEC);

		if (encoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
			logger.debug("no audio encoder input buffer");
			return;
		}

		logger.trace("audio encoder: returned input buffer: {}", encoderInputBufferIndex);

		ByteBuffer encoderInputBuffer =
			Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ?
				this.encoderInputBuffers[encoderInputBufferIndex] :
				this.encoder.getInputBuffer(encoderInputBufferIndex);

		int chunkSize = Math.min(audioDecoderOutputBufferInfo.size, encoderInputBuffer.capacity());
	    long presentationTime = audioDecoderOutputBufferInfo.presentationTimeUs;

		logger.trace("audio decoder: processing pending buffer: {}", this.decoderOutputBufferNextIndex.get());
		logger.trace("audio decoder: pending buffer of size {}", chunkSize);
		logger.trace("audio decoder: pending buffer for time {}", presentationTime);

		if (chunkSize >= 0) {
			ByteBuffer decoderOutputBuffer = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ?
				this.decoderOutputBuffers[this.decoderOutputBufferNextIndex.get()].duplicate() :
				this.decoder.getOutputBuffer(this.decoderOutputBufferNextIndex.get()).duplicate();
			decoderOutputBuffer.position(audioDecoderOutputBufferInfo.offset);
			decoderOutputBuffer.limit(audioDecoderOutputBufferInfo.offset + chunkSize);
			encoderInputBuffer.position(0);
			encoderInputBuffer.put(decoderOutputBuffer);

			this.encoder.queueInputBuffer(
				encoderInputBufferIndex,
				0,
				chunkSize,
				presentationTime,
				audioDecoderOutputBufferInfo.flags);
		}

		this.decoder.releaseOutputBuffer(this.decoderOutputBufferNextIndex.get(), false);
		this.decoderOutputBufferNextIndex = Optional.empty();

		if ((audioDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
			logger.debug("audio decoder: EOS");
		}

	}

	private boolean pipeEncoderFrameToMuxer(MediaCodec.BufferInfo audioEncoderOutputBufferInfo) {
		int encoderOutputBufferIndex = this.encoder.dequeueOutputBuffer(audioEncoderOutputBufferInfo, TIMEOUT_USEC);

		if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
			logger.debug("no audio encoder output buffer");
			return false;
		}

		if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
			logger.debug("audio encoder: output buffers changed");
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
				this.encoderOutputBuffers = this.encoder.getOutputBuffers();
			}
			return false;
		}

		if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
			if (this.muxer != null) {
				throw new IllegalStateException("audio encoder format may not be changed after muxer is initialized");
			}
			this.outputFormat = this.encoder.getOutputFormat();
			logger.debug("audio encoder: output format changed to {}", this.outputFormat);
			if (this.getState() == State.DETECTING_OUTPUT_FORMAT) {
				this.setState(State.WAITING_ON_MUXER);
			} else {
				logger.debug("audio encoder: preliminary output format change detected, not switching state");
			}
			return false;
		}

		if ((audioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
			logger.debug("audio encoder: codec config buffer");
			// Simply ignore codec config buffers.
			this.encoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
			return false;
		}

		logger.trace("audio encoder: returned output buffer: {}", encoderOutputBufferIndex);
		logger.trace("audio encoder: returned buffer of size {}", audioEncoderOutputBufferInfo.size);
		logger.trace("audio encoder: returned buffer for time {}", audioEncoderOutputBufferInfo.presentationTimeUs);

		if (audioEncoderOutputBufferInfo.size != 0) {
			ByteBuffer encoderOutputBuffer = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ?
				this.encoderOutputBuffers[encoderOutputBufferIndex] :
				this.encoder.getOutputBuffer(encoderOutputBufferIndex);
			if (audioEncoderOutputBufferInfo.presentationTimeUs >= this.previousPresentationTime) {
				this.previousPresentationTime = audioEncoderOutputBufferInfo.presentationTimeUs;
				this.muxer.writeSampleData(this.muxerTrack.get(), encoderOutputBuffer, audioEncoderOutputBufferInfo);
			} else {
				// skip old audio, as this only results in quality reduction.
				logger.debug("audio encoder: presentationTimeUs {} < previousPresentationTime {}",
					audioEncoderOutputBufferInfo.presentationTimeUs, this.previousPresentationTime);
			}
		}

		this.encoder.releaseOutputBuffer(encoderOutputBufferIndex, false);

		if ((audioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
			logger.debug("audio encoder: EOS");
			return true;
		}

		this.stats.audioEncodedFrameCount++;

		return false;
	}

	//endregion

	@Override
	public void cleanup() throws Exception {
		super.cleanup();

		Exception firstException = null; // Collect root cause exception without aborting cleanup

		try {
			if (this.decoder != null) {
				this.decoder.stop();
				this.decoder.release();
			}
		} catch (Exception e) {
			logger.error("error while releasing decoder", e);
			firstException = e;
		}

		try {
			if (this.encoder != null) {
				this.encoder.stop();
				this.encoder.release();
			}
		} catch (Exception e) {
			logger.error("error while releasing encoder", e);
			if (firstException == null) {
				firstException = e;
			}
		}

		if (firstException != null) {
			throw firstException;
		}
	}

}
