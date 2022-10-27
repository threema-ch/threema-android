/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2022 Threema GmbH
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

package ch.threema.app.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.opengl.GLES20
import ch.threema.app.ui.MediaItem
import ch.threema.app.video.transcoder.MediaComponent
import ch.threema.app.video.transcoder.OutputSurface
import ch.threema.app.video.transcoder.VideoTranscoder
import ch.threema.app.video.transcoder.VideoTranscoderUtil
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VideoTimelineThumbnailTask(private val context: Context, private val mediaItem: MediaItem, private val thumbnailCount: Int, private val targetWidth: Int, private val targetHeight: Int, private val listener: VideoTimelineListener) : Runnable {
    private val logger = LoggingUtil.getThreemaLogger("VideoTimelineThumbnailTask")

    interface VideoTimelineListener {
        fun onMetadataReady()
        fun onError(errorMessage: String?)
        fun setThumbnail(column: Int, thumbnail: Bitmap?): Boolean
    }

    override fun run() {
        var decoder: MediaCodec? = null
        var outputSurface: OutputSurface? = null
        var inputVideoComponent: MediaComponent? = null
        try {
            inputVideoComponent = MediaComponent(context, mediaItem.uri, MediaComponent.COMPONENT_TYPE_VIDEO)
            val mediaFormat = inputVideoComponent.trackFormat
            if (mediaFormat != null) {
                if (mediaItem.startTimeMs < 0) {
                    mediaItem.startTimeMs = 0
                }
                if (mediaItem.endTimeMs == MediaItem.TIME_UNDEFINED) {
                    mediaItem.endTimeMs = inputVideoComponent.durationUs / 1000
                }
                mediaItem.durationMs = inputVideoComponent.durationUs / 1000
                listener.onMetadataReady()
                val orientationHint = VideoTranscoderUtil.getOrientationHint(context, inputVideoComponent, mediaItem.uri)
                val outputDimensions = VideoTranscoderUtil.calculateOutputDimensions(inputVideoComponent, targetWidth, targetHeight)
                if (outputDimensions != null) {
                    if (orientationHint % 180 == 90) {
                        val height = outputDimensions.height
                        outputDimensions.height = outputDimensions.width
                        outputDimensions.width = height
                    }
                    outputSurface = OutputSurface(outputDimensions.width, outputDimensions.height, 0)
                    decoder = MediaCodec.createDecoderByType(inputVideoComponent.mimeType!!)
                    decoder.configure(mediaFormat, outputSurface.surface, null, 0)
                    decoder.start()
                    extractThumbnails(inputVideoComponent.mediaExtractor, decoder, outputSurface, outputDimensions.width, outputDimensions.height, inputVideoComponent.durationUs)
                }
            }
        } catch (e: Exception) {
            logger.error("Media failure", e)
            listener.onError(e.message)
        } finally {
            inputVideoComponent?.release()
            outputSurface?.release()
            if (decoder != null) {
                try {
                    decoder.stop()
                    decoder.release()
                } catch (e: Exception) {
                    logger.error("Failed to stop decoder", e)
                }
            }
        }
    }

    @Throws(ThreemaException::class)
    private fun extractThumbnails(extractor: MediaExtractor, decoder: MediaCodec, outputSurface: OutputSurface, outputWidth: Int, outputHeight: Int, duration: Long) {
        val info = MediaCodec.BufferInfo()
        val byteBuffer = ByteBuffer.allocateDirect(outputWidth * outputHeight * 4)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        var outputDone = false
        var inputDone = false
        var samplesExtracted = 0
        var thumbnailIndex = 0
        while (!outputDone) {
            if (!inputDone) {
                val decoderInputBufferIndex = decoder.dequeueInputBuffer(VideoTranscoder.TIMEOUT_USEC.toLong())
                if (decoderInputBufferIndex >= 0) {
                    val chunkSize = extractor.readSampleData(
                            decoder.getInputBuffer(decoderInputBufferIndex)!!, 0)
                    if (chunkSize < 0 || samplesExtracted >= thumbnailCount) {
                        decoder.queueInputBuffer(decoderInputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(decoderInputBufferIndex, 0, chunkSize, extractor.sampleTime, 0)
                        samplesExtracted++
                        extractor.seekTo(duration * samplesExtracted / thumbnailCount, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    }
                }
            }
            val outputBufIndex = decoder.dequeueOutputBuffer(info, VideoTranscoder.TIMEOUT_USEC.toLong())
            if (outputBufIndex >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
                val shouldRender = info.size != 0
                decoder.releaseOutputBuffer(outputBufIndex, shouldRender)
                if (shouldRender) {
                    outputSurface.awaitNewImage()
                    outputSurface.drawImage(false)
                    if (thumbnailIndex < thumbnailCount) {
                        byteBuffer.rewind()
                        GLES20.glReadPixels(0, 0, outputWidth, outputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer)
                        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
                        byteBuffer.rewind()
                        bitmap.copyPixelsFromBuffer(byteBuffer)
                        if (!listener.setThumbnail(thumbnailIndex, bitmap)) {
                            break
                        }
                    }
                    thumbnailIndex++
                }
            }
        }
    }
}
