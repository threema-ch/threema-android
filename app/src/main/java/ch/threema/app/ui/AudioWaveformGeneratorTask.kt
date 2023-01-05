/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.ui

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import ch.threema.app.ThreemaApplication
import ch.threema.app.video.transcoder.MediaComponent
import ch.threema.app.video.transcoder.VideoTranscoder
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.AbstractMessageModel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Waveform generator for voice messages. Accepts 16bit sampled audio data only
 *
 * @param messageModel The AbstractMessageModel of a voice message
 * @param requestedSamplesCount How many points to expect to be drawn in the end
 */
class AudioWaveformGeneratorTask(private val messageModel: AbstractMessageModel, private val requestedSamplesCount: Int, private val listener: AudioWaveformGeneratorListener) : Runnable {
    private val logger = LoggingUtil.getThreemaLogger("AudioWaveFormGenerator")

    private var sampleRate = 0
    private var channels = 1
    private val sampleData = ArrayList<Float>()
    var canceled: AtomicBoolean = AtomicBoolean(false)

    interface AudioWaveformGeneratorListener {
        fun onDataReady(newMessageModel: AbstractMessageModel, sampleData : List<Float>)
        fun onError(errorMessageModel: AbstractMessageModel, errorMessage: String?)
        fun onCanceled(cancelMessageModel: AbstractMessageModel)
    }

    override fun run() {
        if (canceled.get()) {
            listener.onCanceled(messageModel)
            return
        }

        var decoder: MediaCodec? = null
        var inputAudioComponent: MediaComponent? = null
        try {
            val file = ThreemaApplication.getServiceManager()?.fileService?.getDecryptedMessageFile(messageModel)
            if (file == null || !file.exists()) {
                listener.onError(messageModel, "Unable to open audio file")
                return
            }

            inputAudioComponent = MediaComponent(ThreemaApplication.getAppContext(), Uri.fromFile(file), MediaComponent.COMPONENT_TYPE_AUDIO)
            if (inputAudioComponent.mimeType != null) {
                val mediaFormat = inputAudioComponent.trackFormat
                if (mediaFormat != null) {
                    val durationMs = inputAudioComponent.durationUs / 1000
                    if (durationMs > 0) {
                        sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                        decoder = MediaCodec.createDecoderByType(inputAudioComponent.mimeType!!)
                        decoder.configure(mediaFormat, null, null, 0)
                        decoder.start()
                        extractSamples(inputAudioComponent.mediaExtractor, decoder, inputAudioComponent.durationUs)

                        if (!canceled.get()) {
                            listener.onDataReady(messageModel, sampleData)
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Media failure", e)
        } finally {
            inputAudioComponent?.release()
            decoder?.let {
                try {
                    it.stop()
                    it.release()
                } catch (e: Exception) {
                    logger.error("Failed to stop decoder", e)
                }
            }
        }
        if (canceled.get()) {
            listener.onCanceled(messageModel)
        } else {
            listener.onError(messageModel,  "Audio waveform generating failed or interrupted")
        }
    }

    private fun extractSamples(extractor: MediaExtractor, decoder: MediaCodec, duration: Long) {
        val info = MediaCodec.BufferInfo()
        var outputDone = false
        var inputDone = false
        var samplesExtracted = 0
        var sampleIndex = 0
        while (!outputDone && !canceled.get()) {
            if (!inputDone) {
                val decoderInputBufferIndex = decoder.dequeueInputBuffer(VideoTranscoder.TIMEOUT_USEC.toLong())
                if (decoderInputBufferIndex >= 0) {
                    val chunkSize = extractor.readSampleData(decoder.getInputBuffer(decoderInputBufferIndex)!!, 0)
                    if (chunkSize < 0 || samplesExtracted >= requestedSamplesCount) {
                        decoder.queueInputBuffer(decoderInputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(decoderInputBufferIndex, 0, chunkSize, extractor.sampleTime, 0)
                        samplesExtracted++
                        extractor.seekTo(duration * samplesExtracted / requestedSamplesCount, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    }
                }
            }
            val outputBufIndex = decoder.dequeueOutputBuffer(info, VideoTranscoder.TIMEOUT_USEC.toLong())
            if (outputBufIndex >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
                val shouldRender = info.size != 0
                if (shouldRender) {
                    if (sampleIndex < requestedSamplesCount) {
                        decoder.getOutputBuffer(outputBufIndex)?.let { buf ->
                            val size = info.size
                            var sampleSum = 0.0

                            buf.position(info.offset)
                            repeat(size / if (channels == 2) 4 else 2) {
                                // voice messages are one channel only
                                val a = buf.get().toInt()
                                val b = buf.get().toInt() shl 8
                                val value = (a or b) / 32768f
                                if (channels == 2) {
                                    // skip right channel, if there is one
                                    buf.get()
                                    buf.get()
                                }
                                sampleSum += value.toDouble().pow(2.0)
                            }

                            val rms = sqrt(sampleSum / size) * 15
                            sampleData.add(rms.toFloat())
                        }
                    }
                    sampleIndex++
                }
                decoder.releaseOutputBuffer(outputBufIndex, shouldRender)
            }
        }
    }

    fun cancel() {
        canceled.set(true)
    }

    fun getMessageId() : Int {
        return messageModel.id
    }
}
