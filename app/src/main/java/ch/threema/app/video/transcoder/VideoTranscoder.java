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

package ch.threema.app.video.transcoder;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Surface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.video.transcoder.audio.AbstractAudioTranscoder;
import ch.threema.app.video.transcoder.audio.AudioComponent;
import ch.threema.app.video.transcoder.audio.AudioFormatTranscoder;
import ch.threema.app.video.transcoder.audio.AudioNullTranscoder;
import ch.threema.app.video.transcoder.audio.UnsupportedAudioFormatException;
import java8.util.Optional;

/**
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

@TargetApi(18)
public class VideoTranscoder {
	private static final Logger logger = LoggerFactory.getLogger(VideoTranscoder.class);
	private @NonNull Optional<AbstractAudioTranscoder> audioTranscoder = Optional.empty();
	private @NonNull Optional<Exception> audioTranscoderError = Optional.empty();

	//region Constants

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({FAILURE, SUCCESS, CANCELED})
	public @interface TranscoderResult {}
	public static final int FAILURE = 0;
	public static final int SUCCESS = 1;
	public static final int CANCELED = -1;

	public static final int TRIM_TIME_END = -1;

	private static final int POLLING_SUCCESS = 0;
	private static final int POLLING_ERROR = -1;
	private static final int POLLING_CANCELED = 1;

	private static final String KEY_ROTATION = "rotation";

	/**
	 * How long to wait for the next buffer to become available in microseconds.
	 */
	public static final int TIMEOUT_USEC = 10000;

	//endregion

	//region Properties

	private final Context mContext;
	private final Uri mSrcUri;

	private String mOutputFilePath;

	private boolean mIncludeAudio = true;

	private MediaComponent mInputVideoComponent;
	private AudioComponent mInputAudioComponent;

	private int mOutputVideoWidth;
	private int mOutputVideoHeight;
	private int mOrientationHint;

	private int mOutputVideoBitRate;
	private int mOutputVideoFrameRate;
	private int mOutputVideoIFrameInterval;

	private int mOutputAudioBitRate;

	/**
	 * Approximate start time in Milliseconds.
	 */
	private long mTrimStartTimeMs = 0;
	private long mTrimEndTimeMs = TRIM_TIME_END;

	private MediaFormat mOutputVideoFormat;

	private MediaCodec mVideoEncoder;
	private MediaCodec mVideoDecoder;

	private InputSurface mInputSurface;
	private OutputSurface mOutputSurface;

	private MediaMuxer mMuxer;

	private Stats mStats;
	private int mRetryCount;

	// Buffers
	private ByteBuffer[] mVideoDecoderInputBuffers;
	private ByteBuffer[] mVideoEncoderOutputBuffers;

	// Media Formats from codecs
	private MediaFormat mDecoderOutputVideoFormat;
	private MediaFormat mEncoderOutputVideoFormat;

	private int mOutputVideoTrack = -1;

	private long mStartTime;
	private int progress;
	private Listener listener;
	private boolean isCancelled;

	private long outputDurationUs;
	private long outputStartTimeUs;

	private boolean shouldIncludeAudio() {
		return this.mIncludeAudio;
	}

	private void shouldIncludeAudio(boolean copyAudio) {
		this.mIncludeAudio = copyAudio;
	}


	public boolean hasAudioTranscodingError() {
		return this.audioTranscoderError.isPresent();
	}

	public boolean audioFormatUnsupported() {
		return this.hasAudioTranscodingError() &&
			this.audioTranscoderError.get() instanceof UnsupportedAudioFormatException;
	}

	//endregion

	private VideoTranscoder(Context context, Uri srcUri) {
		mContext = context;
		mSrcUri = srcUri;
	}


	//region Lifecycle

	public void start(@NonNull final Listener listener) {
		if (mContext == null) {
			throw new IllegalStateException("Context cannot be null");
		}

		if (mSrcUri == null) {
			throw new IllegalStateException("Source Uri cannot be null. Make sure to call source()");
		}

		new Thread(() -> {
			final @TranscoderResult int result = startSync(listener);

			RuntimeUtil.runOnUiThread(() -> {
				if (result == SUCCESS) {
					listener.onSuccess(mStats);
				} else if (result == CANCELED) {
					listener.onCanceled();
				} else {
					listener.onFailure();
				}
			});
		}).start();
	}

	public @TranscoderResult int startSync(@NonNull Listener listener) {
		if (mContext == null) {
			throw new IllegalStateException("Context cannot be null");
		}

		if (mSrcUri == null) {
			throw new IllegalStateException("Source Uri cannot be null. Make sure to call source()");
		}

		mStartTime = System.currentTimeMillis();

		this.listener = listener;
		this.progress = 0;
		this.isCancelled = false;

		boolean setupSuccess = false;
		int transcoderResult = FAILURE;
		boolean cleanupSuccess = false;

		try {
			setup();
			setupSuccess = true;
		} catch (Exception ex) {
			logger.error("Failed while setting up VideoTranscoder: {}" , mSrcUri, ex);
		}

		try {
			if (setupSuccess) {
				transcoderResult = transcode();
			}
		} catch (Exception ex) {
			logger.error("Failed while transcoding video: {}", mSrcUri, ex);
		}

		try {
			cleanup();
			cleanupSuccess = true;
		} catch (Exception e) {
			logger.error("Failed while cleaning up transcoder");
		}

		if (setupSuccess && transcoderResult == SUCCESS && cleanupSuccess) {
			this.listener.onSuccess(mStats);
		} else if (transcoderResult == CANCELED) {
			this.listener.onCanceled();
		} else {
			this.listener.onFailure();
		}
		return transcoderResult;
	}

	private void setup() throws IOException {
		mStats = new Stats();
		createComponents();

		setOrientationHint();
		calculateOutputDimensions();

		createOutputFormats();
		createVideoEncoder();
		createVideoDecoder();

		if (shouldIncludeAudio()) {
			final String mimeType = mInputAudioComponent.getTrackFormat().getString(MediaFormat.KEY_MIME);
			if (mimeType.equalsIgnoreCase(Defaults.OUTPUT_AUDIO_MIME_TYPE)) {
				logger.info("Keeping audio track, as in- and output format match");
				audioTranscoder = Optional.of(new AudioNullTranscoder(
					mInputAudioComponent,
					mStats,
					mTrimEndTimeMs
				));
			} else {
				logger.info("Transcoding audio track, as in- and output format differ");
				audioTranscoder = Optional.of(new AudioFormatTranscoder(
					mInputAudioComponent,
					mStats,
					mTrimEndTimeMs,
					mOutputAudioBitRate
				));
			}

			try {
				this.audioTranscoder.get().setup();
			} catch (Exception e) {
				this.handleAudioException(e);
			}
		}

		createMuxer();
	}

	private @TranscoderResult int transcode() {
		listener.onStart();


		boolean videoEncoderDone = false;

		boolean videoDecoderDone = false;

		boolean videoExtractorDone = false;

		boolean muxing = false;

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			mVideoDecoderInputBuffers = mVideoDecoder.getInputBuffers();
			mVideoEncoderOutputBuffers = mVideoEncoder.getOutputBuffers();
		}

		MediaCodec.BufferInfo videoDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
		MediaCodec.BufferInfo videoEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

		trimStart();

		// loop until all the encoding is finished
		while (!videoEncoderDone || (audioTranscoder.isPresent() && audioTranscoder.get().getState() != AbstractAudioTranscoder.State.DONE)) {

			// Extract video from file and feed to decoder.
			// Do not extract video if we have determined the output format but we are not yet
			// ready to mux the frames.
			if (!videoExtractorDone && (mEncoderOutputVideoFormat == null || muxing)) {
				videoExtractorDone = extractAndFeedDecoder(mVideoDecoder, mVideoDecoderInputBuffers, mInputVideoComponent);
			}


			// Poll output frames from the video decoder and feed the encoder
			if (!videoDecoderDone && (mEncoderOutputVideoFormat == null || muxing)) {
				int pollingResult = pollVideoFromDecoderAndFeedToEncoder(videoDecoderOutputBufferInfo);
				videoDecoderDone = pollingResult == POLLING_SUCCESS;

				if (pollingResult == POLLING_CANCELED) {
					return CANCELED;
				}
			}

			// Poll frames from video encoder and send them to the muxer
			if (!videoEncoderDone && (mEncoderOutputVideoFormat == null || muxing)) {
				videoEncoderDone = pollVideoFromEncoderAndFeedToMuxer(videoEncoderOutputBufferInfo);
			}

			if (this.audioTranscoder.isPresent() && this.audioTranscoder.get().getState() != AbstractAudioTranscoder.State.DONE) {
				try {
					this.audioTranscoder.get().step();
				} catch (Exception exception) {
					this.handleAudioException(exception);
				}
			}

			// Setup muxer
			if (!muxing && (audioTranscoder.isEmpty() || audioTranscoder.get().getState() == AbstractAudioTranscoder.State.WAITING_ON_MUXER) && (mEncoderOutputVideoFormat != null)) {
				setupMuxer();
				muxing = true;
			}
		}

		// Basic sanity checks
		sanityChecks();

		return SUCCESS;
	}

	/**
	 *
	 * Trim the start of the video if the trimming time is > 0.
	 *
	 * The audio start trim is moved slightly to fit the video key/sync frames:
	 *
	 * 1. Search video keyframe that happened before the requested mTrimStartTime,
	 *    so that for short trim-sequences the key frame cannot be over mTrimEndTime.
	 *               <<<<¦
	 *    [---------|--------------------] video track
	 *
	 * 2. Search for next audio keyframe after real video cut
	 *              |>>
	 *    [------------‖-----------------] audio track
	 *
	 * (¦ is the time of this variable, | is the real video cut, ‖ the real audio cut)
	 *
	 */
	private void trimStart() {
		if (this.mTrimStartTimeMs > 0) {
			this.mInputVideoComponent.getMediaExtractor().seekTo(
				this.mTrimStartTimeMs * 1000,
				MediaExtractor.SEEK_TO_PREVIOUS_SYNC
			);
			final long exactVideoStartTrimUs = this.mInputVideoComponent.getMediaExtractor().getSampleTime();

			logger.debug(
				"transcoder: trim video decoder start to keyframe at {}us (originally requested {}us)",
				this.mInputVideoComponent.getMediaExtractor().getSampleTime(),
				this.mTrimStartTimeMs * 1000
			);

			this.audioTranscoder.ifPresent(t -> t.trimMediaStartTo(exactVideoStartTrimUs));
		}
	}


	/**
	 * Handle audio transcoder errors the best way possible. If audio transcoding has not yet
	 * started, we skip the audio and continue in video-only mode, or else rethrow the exception.
	 *
	 * @param exception The exception from the audio transcoder
	 */
	private void handleAudioException(Exception exception) {
		final AbstractAudioTranscoder.State state = this.audioTranscoder.get().getState();

		if (
			state == AbstractAudioTranscoder.State.TRANSCODING
			|| state == AbstractAudioTranscoder.State.DONE
		) {
			throw new UnrecoverableVideoTranscoderException(exception);
		}

		this.audioTranscoder = Optional.empty();
		this.audioTranscoderError = Optional.of(exception);
		logger.warn(
			"Audio format is not supported by transcoder",
			exception
		);
		logger.info("Ignoring audio, as transcoding has not yet started");
	}

	/**
	 * Performs a basic checks in an attempt to see if the transcode was successful.
	 * Will throw an IllegalStateException if any checks fail.
	 */
	private void sanityChecks() {
		if (mStats.videoDecodedFrameCount != mStats.videoEncodedFrameCount) {
			logger.info("Frame count mismatch videoDecodedFrameCount: {} videoEncodedFrameCount: {}", mStats.videoDecodedFrameCount, mStats.videoEncodedFrameCount);
		}

		if (mStats.videoDecodedFrameCount > mStats.videoExtractedFrameCount) {
			throw new IllegalStateException("decoded frame count should be less than extracted frame count");
		}

		if (audioTranscoder.isPresent()) {
			if (audioTranscoder.get().hasPendingIntermediateFrames()) {
				throw new IllegalStateException("no frame should be pending");
			}

			logger.debug("audioDecodedFrameCount: {} audioExtractedFrameCount: {}",
				mStats.audioDecodedFrameCount, mStats.audioExtractedFrameCount);
		}
	}

	private void logResults() {
		if (mSrcUri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
			mStats.inputFileSize = Math.round(new File(mSrcUri.getPath()).length() / 1024. / 1000 * 10) / 10.;
		} else {
			Cursor returnCursor =
				mContext.getContentResolver().query(mSrcUri, null, null, null, null);
			int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
			returnCursor.moveToFirst();

			mStats.inputFileSize = Math.round(returnCursor.getLong(sizeIndex) / 1024. / 1000 * 10) / 10.;
			returnCursor.close();
		}

		mStats.outputFileSize = Math.round(new File(mOutputFilePath).length() / 1024. / 1000 * 10) / 10.;
		mStats.timeToTranscode = Math.round(((System.currentTimeMillis() - mStartTime) / 1000.) * 10) / 10.;

		logger.info("Input file: {}MB", mStats.inputFileSize);
		logger.info("Output file: {}MB", mStats.outputFileSize);
		logger.info("Time to encode: {}s", mStats.timeToTranscode);
	}

	private void cleanup() throws Exception {
		logger.info("Releasing extractor, decoder, encoder, and muxer");
		// Try to release everything we acquired, even if one of the releases fails, in which
		// case we save the first exception we got and re-throw at the end (unless something
		// other exception has already been thrown). This guarantees the first exception thrown
		// is reported as the cause of the error, everything is (attempted) to be released, and
		// all other exceptions appear in the logs.
		Exception exception = null;

		try {
			if (mInputVideoComponent != null) {
				mInputVideoComponent.release();
			}
		} catch (Exception e) {
			logger.error("error while releasing videoExtractor", e);
			exception = e;
		}
		try {
			if (mInputAudioComponent != null) {
				mInputAudioComponent.release();
			}
		} catch (Exception e) {
			logger.error("error while releasing audioExtractor", e);
			if (exception == null) {
				exception = e;
			}
		}
		try {
			if (mVideoDecoder != null) {
				mVideoDecoder.stop();
				mVideoDecoder.release();
			}
		} catch (Exception e) {
			logger.error("error while releasing videoDecoder", e);
			if (exception == null) {
				exception = e;
			}
		}
		try {
			if (mOutputSurface != null) {
				mOutputSurface.release();
			}
		} catch (Exception e) {
			logger.error("error while releasing outputSurface", e);
			if (exception == null) {
				exception = e;
			}
		}
		try {
			if (mVideoEncoder != null) {
				mVideoEncoder.stop();
				mVideoEncoder.release();
			}
		} catch (Exception e) {
			logger.error("error while releasing videoEncoder", e);
			if (exception == null) {
				exception = e;
			}
		}
		if (audioTranscoder.isPresent()) {
			try {
				audioTranscoder.get().cleanup();
			} catch(Exception e) {
				if(exception == null) {
					exception = e;
				}
			}
		}
		try {
			if (mMuxer != null) {
				mMuxer.stop();
				mMuxer.release();
			}
		} catch (Exception e) {
			logger.error("error while releasing muxer", e);
			if (exception == null) {
				exception = e;
			}
		}
		try {
			if (mInputSurface != null) {
				mInputSurface.release();
			}
		} catch (Exception e) {
			logger.error("error while releasing inputSurface", e);
			if (exception == null) {
				exception = e;
			}
		}

		if (exception != null) {
			throw exception;
		}

		logResults();
	}

	//endregion

	//region Encoder / Decoders

	/**
	 * Extract and feed to decoder.
	 *
	 * @return Finished. True when it extracts the last frame.
	 */
	private boolean extractAndFeedDecoder(MediaCodec decoder, ByteBuffer[] buffers, MediaComponent component) {
		String type = component.getType() == MediaComponent.COMPONENT_TYPE_VIDEO ? "video" : "audio";

		int decoderInputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
		if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
			logger.debug("no {} decoder input buffer", type);
			return false;
		}

		logger.trace("{} extractor: returned input buffer: {}", type, decoderInputBufferIndex);

		MediaExtractor extractor = component.getMediaExtractor();
		int chunkSize = extractor.readSampleData(
			Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ?
				buffers[decoderInputBufferIndex] :
				decoder.getInputBuffer(decoderInputBufferIndex), 0);

		long sampleTime = extractor.getSampleTime();

		logger.trace("{} extractor: returned buffer of chunkSize {}", type, chunkSize);
		logger.trace("{} extractor: returned buffer for sampleTime {}", type, sampleTime);

		if (mTrimEndTimeMs > 0 && sampleTime > (mTrimEndTimeMs * 1000)) {
			logger.debug("{} extractor: The current sample is over the trim time. Lets stop.", type);
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

			mStats.incrementExtractedFrameCount(component);
		}

		if (!extractor.advance()) {
			logger.debug("{} extractor: EOS", type);
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
				mRetryCount++;
				if (mRetryCount < 5) {
					return this.extractAndFeedDecoder(decoder, buffers, component);
				} else {
					mRetryCount = 0;
					throw e;
				}
			}
			return true;
		}

		return false;
	}

	/**
	 * Extract frame for decoder and feed to encoder.
	 *
	 * @param videoDecoderOutputBufferInfo
	 * @return
	 */
	private int pollVideoFromDecoderAndFeedToEncoder(MediaCodec.BufferInfo videoDecoderOutputBufferInfo) {
		int decoderOutputBufferIndex = mVideoDecoder.dequeueOutputBuffer(videoDecoderOutputBufferInfo, TIMEOUT_USEC);

		if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
			logger.debug("video decoder: no output buffer");
			return POLLING_ERROR;
		}

		if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
			logger.debug("video decoder: output buffers changed");
			return POLLING_ERROR;
		}

		if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
			mDecoderOutputVideoFormat = mVideoDecoder.getOutputFormat();
			logger.debug("video decoder: output format changed: {}", mDecoderOutputVideoFormat);
			return POLLING_ERROR;
		}

		if ((videoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
			logger.debug("video decoder: codec config buffer");
			mVideoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
			return POLLING_ERROR;
		}

		logger.trace("video decoder: returned output buffer: {}", decoderOutputBufferIndex);
		logger.trace("video decoder: returned buffer of size {}", videoDecoderOutputBufferInfo.size);
		logger.trace("video decoder: returned buffer for time {}", videoDecoderOutputBufferInfo.presentationTimeUs);

		int percentage = (int) ((videoDecoderOutputBufferInfo.presentationTimeUs - outputStartTimeUs) * 100 / outputDurationUs);
		if (percentage > progress) {
			progress = percentage;
			if (listener != null) {
				listener.onProgress(progress);
			}
		}
		if (isCancelled) {
			return POLLING_CANCELED;
		}

		boolean render = videoDecoderOutputBufferInfo.size != 0;

		mVideoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, render);

		if (render) {
			mOutputSurface.awaitNewImage();
			mOutputSurface.drawImage(false);
			mInputSurface.setPresentationTime(videoDecoderOutputBufferInfo.presentationTimeUs * 1000);
			mInputSurface.swapBuffers();
			logger.trace("video encoder: notified of new frame");
		}

		if ((videoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
			logger.debug("video decoder: EOS");

			mVideoEncoder.signalEndOfInputStream();
			return POLLING_SUCCESS;
		}

		mStats.videoDecodedFrameCount++;

		return POLLING_ERROR;
	}

	/**
	 * @param videoEncoderOutputBufferInfo
	 * @return
	 */
	private boolean pollVideoFromEncoderAndFeedToMuxer(MediaCodec.BufferInfo videoEncoderOutputBufferInfo) {
		int encoderOutputBufferIndex = mVideoEncoder.dequeueOutputBuffer(videoEncoderOutputBufferInfo, TIMEOUT_USEC);

		if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
			logger.debug("no video encoder output buffer");
			return false;
		}

		if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
			logger.debug("video encoder: output buffers changed");
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
				mVideoEncoderOutputBuffers = mVideoEncoder.getOutputBuffers();
			}
			return false;
		}

		if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
			logger.debug("video encoder: output format changed");
			if (mOutputVideoTrack >= 0) {
				throw new IllegalStateException("Video encoder changed its output format again? What's going on?");
			}
			mEncoderOutputVideoFormat = mVideoEncoder.getOutputFormat();
			return false;
		}

		if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
			logger.debug("video encoder: codec config buffer");
			// Simply ignore codec config buffers.
			mVideoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
			return false;
		}

		logger.trace("video encoder: returned output buffer: {}", encoderOutputBufferIndex);
		logger.trace("video encoder: returned buffer of size {}", videoEncoderOutputBufferInfo.size);
		logger.trace("video encoder: returned buffer for time {}", videoEncoderOutputBufferInfo.presentationTimeUs);

		ByteBuffer encoderOutputBuffer =
			Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ?
				mVideoEncoderOutputBuffers[encoderOutputBufferIndex] :
				mVideoEncoder.getOutputBuffer(encoderOutputBufferIndex);

		if (videoEncoderOutputBufferInfo.size != 0) {
			mMuxer.writeSampleData(mOutputVideoTrack, encoderOutputBuffer, videoEncoderOutputBufferInfo);
		}

		mVideoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);

		mStats.videoEncodedFrameCount++;

		if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
			logger.debug("video encoder: EOS");
			return true;
		}

		return false;
	}

	private void setupMuxer() {
		logger.debug("muxer: adding video track.");
		mOutputVideoTrack = mMuxer.addTrack(mEncoderOutputVideoFormat);


		audioTranscoder.ifPresent(transcoder -> {
			logger.debug("muxer: injecting audio track.");
			transcoder.injectTrackToMuxer(mMuxer);
		});

		logger.debug("muxer: starting");
		mMuxer.setOrientationHint(mOrientationHint);
		mMuxer.start();

	}

	/**
	 * Returns the first codec capable of encoding the specified MIME type,
	 * or null if no match was found.
	 *
	 * @param mimeType specified MIME type
	 * @return
	 */
	public static MediaCodecInfo selectCodec(String mimeType) {
		int numCodecs = MediaCodecList.getCodecCount();
		for (int i = 0; i < numCodecs; i++) {
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

			if (!codecInfo.isEncoder()) {
				continue;
			}

			String[] types = codecInfo.getSupportedTypes();
			for (int j = 0; j < types.length; j++) {
				if (types[j].equalsIgnoreCase(mimeType)) {
					logger.debug("Codec {} found for mime type {}", codecInfo.getName(), mimeType);
					return codecInfo;
				}
			}
		}

		throw new UnrecoverableVideoTranscoderException("Unable to find an appropriate codec for " + mimeType);
	}

	private void createComponents() throws IOException {
		mInputVideoComponent = new MediaComponent(mContext, mSrcUri, MediaComponent.COMPONENT_TYPE_VIDEO);

		MediaFormat inputFormat = mInputVideoComponent.getTrackFormat();
		if (inputFormat.containsKey("rotation-degrees")) {
			// Decoded video is rotated automatically in Android 5.0 lollipop.
			// Turn off here because we don't want to encode rotated one.
			// refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
			inputFormat.setInteger("rotation-degrees", 0);
		}

		if (shouldIncludeAudio()) {
			mInputAudioComponent = new AudioComponent(mContext, mSrcUri);
			if (mInputAudioComponent.getSelectedTrackIndex() == MediaComponent.NO_TRACK_AVAILABLE) {
				shouldIncludeAudio(false);
			}
		}
	}

	private void calculateOutputDimensions() {
		MediaFormat trackFormat = mInputVideoComponent.getTrackFormat();

		int inputWidth = trackFormat.getInteger(MediaFormat.KEY_WIDTH);
		int inputHeight = trackFormat.getInteger(MediaFormat.KEY_HEIGHT);

		// If this is a portrait video taken by a device that supports orientation hints, the resolution will be swapped.
		// If its landscape, a screencap, or a device that doesn't support hints, it won't be.
		if (inputWidth >= inputHeight || mOrientationHint == 0 || mOrientationHint == 180) {

			if (inputWidth > mOutputVideoWidth || inputHeight > mOutputVideoHeight) {
				float ratio = Math.min(mOutputVideoWidth / (float) inputWidth, mOutputVideoHeight / (float) inputHeight);
				mOutputVideoHeight = getRoundedSize(ratio, inputHeight);
				mOutputVideoWidth = getRoundedSize(ratio, inputWidth);
			} else {
				mOutputVideoHeight = inputHeight;
				mOutputVideoWidth = inputWidth;
			}
		} else {
			if (inputHeight > mOutputVideoWidth || inputWidth > mOutputVideoHeight) {
				float ratio = Math.min(mOutputVideoWidth / (float) inputHeight, mOutputVideoHeight / (float) inputWidth);
				mOutputVideoHeight = getRoundedSize(ratio, inputWidth);
				mOutputVideoWidth = getRoundedSize(ratio, inputHeight);
			} else {
				mOutputVideoHeight = inputWidth;
				mOutputVideoWidth = inputHeight;
			}
		}
	}

	private int getRoundedSize(float ratio, int size) {
		// width/height need to be a multiple of 2 otherwise mediacodec encoder will crash
		// with android.media.MediaCodec$CodecException: Error 0xfffffc0e
		return 16 * Math.round(size * ratio / 16);
	}


	private void setOrientationHint() {
		MediaFormat trackFormat = mInputVideoComponent.getTrackFormat();

		if (trackFormat.containsKey(KEY_ROTATION)) {
			mOrientationHint = trackFormat.getInteger(KEY_ROTATION);
		} else {
			// do not use automatic resource management on MediaMetadataRetriever
			final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			try {
				retriever.setDataSource(mContext, mSrcUri);
				String orientation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
				if (!TextUtils.isEmpty(orientation)) {
					mOrientationHint = Integer.parseInt(orientation);
				}
			} finally {
				retriever.release();
			}
		}
	}

	private void createOutputFormats() {
		createVideoOutputFormat();

		// Note: Audio Output formats are set in {@link AudioTranscoder#setup}
	}

	private void createVideoOutputFormat() {
		mOutputVideoFormat = MediaFormat.createVideoFormat(
			Defaults.OUTPUT_VIDEO_MIME_TYPE, mOutputVideoWidth, mOutputVideoHeight);

		// Set some properties. Failing to specify some of these can cause the MediaCodec
		// configure() call to throw an unhelpful exception.
		mOutputVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, getOutputVideoBitRate());
		mOutputVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mOutputVideoFrameRate);
		mOutputVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mOutputVideoIFrameInterval);
		mOutputVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
			MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
	}

	private void createVideoEncoder() throws IOException {
		// Create a MediaCodec for the desired codec, then configure it as an encoder with
		// our desired properties. Request a Surface to use for input.
		AtomicReference<Surface> inputSurfaceReference = new AtomicReference<Surface>();

		mVideoEncoder = MediaCodec.createEncoderByType(Defaults.OUTPUT_VIDEO_MIME_TYPE);
		mVideoEncoder.configure(mOutputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		inputSurfaceReference.set(mVideoEncoder.createInputSurface());
		mVideoEncoder.start();

		mInputSurface = new InputSurface(inputSurfaceReference.get());
		mInputSurface.makeCurrent();

		mOutputSurface = new OutputSurface();
	}

	private void createVideoDecoder() throws IOException {
		MediaFormat inputFormat = mInputVideoComponent.getTrackFormat();

		if (mTrimEndTimeMs == TRIM_TIME_END) {
			outputDurationUs = inputFormat.getLong(MediaFormat.KEY_DURATION);
		} else {
			outputDurationUs = (mTrimEndTimeMs - mTrimStartTimeMs) * 1000;
		}
		outputStartTimeUs = mTrimStartTimeMs * 1000;

		mVideoDecoder = MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat));
		mVideoDecoder.configure(inputFormat, mOutputSurface.getSurface(), null, 0);
		mVideoDecoder.start();
	}

	private void createMuxer() throws IOException {
		mMuxer = new MediaMuxer(mOutputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
		mMuxer.setOrientationHint(mOrientationHint);
	}

	private int getOutputVideoBitRate() {
		int inputBitRate = mOutputVideoBitRate;

		if (mInputVideoComponent.getTrackFormat().containsKey(MediaFormat.KEY_BIT_RATE)) {
			inputBitRate = mInputVideoComponent.getTrackFormat().getInteger(MediaFormat.KEY_BIT_RATE);
		} else {
			final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			try {
				retriever.setDataSource(mContext, mSrcUri);
				String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);

				if (bitrate != null) {
					inputBitRate = Integer.parseInt(bitrate);
				}
			} catch (Exception e) {
				logger.error("Error extracting bitrate", e);
			} finally {
				retriever.release();
			}
		}

		if (false) {
			// broken device
			logger.info("Broken device that cannot properly read a video file's bitrate using MediaMetadataRetriever");
			return mOutputVideoBitRate;
		} else {
			return Math.min(inputBitRate, mOutputVideoBitRate);
		}
	}

	//endregion

	public interface Listener {
		void onSuccess(Stats stats);

		void onProgress(int progress);

		void onCanceled();

		void onFailure();

		void onStart();
	}

	public static final class Defaults {
		static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc";       // H.264 Advanced Video Coding
		public static final String OUTPUT_AUDIO_MIME_TYPE = "audio/MP4A-LATM"; // Advanced Audio Coding

		static final int OUTPUT_VIDEO_BIT_RATE = 5000 * 1024;       // 2 MBps
		static final int OUTPUT_AUDIO_BIT_RATE = 128 * 1024;        // 128 kbps

		static final int OUTPUT_VIDEO_FRAME_RATE = 30;              // 30fps
		static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 10;         // 10 seconds between I-frames

		static final int OUTPUT_MAX_WIDTH = 1920;
		static final int OUTPUT_MAX_HEIGHT = 1920;

		public static final int OUTPUT_AUDIO_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
	}

	public static final class Stats {
		public int videoExtractedFrameCount;
		public int audioExtractedFrameCount;

		public int videoDecodedFrameCount;
		public int audioDecodedFrameCount;
		public int videoEncodedFrameCount;
		public int audioEncodedFrameCount;

		public double timeToTranscode;
		public double inputFileSize;
		public double outputFileSize;

		public void incrementExtractedFrameCount(MediaComponent component) {
			if (component.getType() == MediaComponent.COMPONENT_TYPE_VIDEO) {
				videoExtractedFrameCount++;
			} else {
				audioExtractedFrameCount++;
			}
		}
	}

	public static String getMimeTypeFor(MediaFormat format) {
		return format.getString(MediaFormat.KEY_MIME);
	}

	public static final class Builder {
		private final Uri mSrcUri;
		private final File mDestFile;

		private boolean mIncludeAudio = true;

		private int mMaxFrameWidth = Defaults.OUTPUT_MAX_WIDTH;
		private int mMaxFrameHeight = Defaults.OUTPUT_MAX_HEIGHT;

		private int mVideoBitRate = Defaults.OUTPUT_VIDEO_BIT_RATE;
		private int mVideoFrameRate = Defaults.OUTPUT_VIDEO_FRAME_RATE;
		private int mVideoIFrameInterval = Defaults.OUTPUT_VIDEO_IFRAME_INTERVAL;
		private int mAudioBitRate = Defaults.OUTPUT_AUDIO_BIT_RATE;

		private long mStartTime = 0;
		private long mEndTime = TRIM_TIME_END;

		public Builder(Uri srcUri, File destFile) {
			if (srcUri == null) {
				throw new NullPointerException("srcUri cannot be null");
			}

			if (destFile == null) {
				throw new NullPointerException("destUri cannot be null");
			}

			mSrcUri = srcUri;
			mDestFile = destFile;
		}

		public Builder includeAudio(boolean includeAudio) {
			mIncludeAudio = includeAudio;
			return this;
		}

		public Builder maxFrameWidth(int maxWidth) {
			mMaxFrameWidth = maxWidth;
			return this;
		}

		public Builder maxFrameHeight(int maxHeight) {
			mMaxFrameHeight = maxHeight;
			return this;
		}

		public Builder videoBitRate(int bitRate) {
			mVideoBitRate = bitRate;
			return this;
		}

		public Builder audioBitRate(int bitRate) {
			mAudioBitRate = bitRate;
			return this;
		}

		public Builder frameRate(int frameRate) {
			mVideoFrameRate = frameRate;
			return this;
		}

		public Builder iFrameInterval(int iFrameInterval) {
			mVideoIFrameInterval = iFrameInterval;
			return this;
		}

		public Builder trim(long startTimeMillis, long endTimeMillis) {
			mStartTime = startTimeMillis;
			mEndTime = endTimeMillis;
			return this;
		}

		public VideoTranscoder build(Context context) {
			VideoTranscoder transcoder = new VideoTranscoder(context, mSrcUri);
			transcoder.mIncludeAudio = mIncludeAudio;
			transcoder.mOutputVideoWidth = mMaxFrameWidth;
			transcoder.mOutputVideoHeight = mMaxFrameHeight;
			transcoder.mOutputVideoBitRate = mVideoBitRate;
			transcoder.mOutputAudioBitRate = mAudioBitRate;
			transcoder.mOutputVideoFrameRate = mVideoFrameRate;
			transcoder.mOutputVideoIFrameInterval = mVideoIFrameInterval;
			transcoder.mOutputFilePath = mDestFile.getAbsolutePath();

			if (mStartTime > 0) {
				transcoder.mTrimStartTimeMs = mStartTime;
			}

			if (mEndTime != -1) {
				transcoder.mTrimEndTimeMs = mEndTime;
			}

			return transcoder;
		}
	}

	public void cancel() {
		this.isCancelled = true;
	}
}
