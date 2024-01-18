/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.webrtc

import android.content.Context
import androidx.annotation.AnyThread
import ch.threema.annotation.SameThread
import ch.threema.app.voip.groupcall.GroupCallException
import ch.threema.app.voip.groupcall.sfu.webrtc.FactoryCtx
import ch.threema.app.voip.util.VideoCapturerUtil
import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.CompletableDeferred
import org.webrtc.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = LoggingUtil.getThreemaLogger("VideoContext")

@AnyThread
data class VideoCaptureSettings(
    val width: UShort,
    val height: UShort,
    val fps: UShort,
)
typealias VideoCaptureSettingsFn = () -> VideoCaptureSettings

@SameThread
sealed class Camera(
    val name: String,
    val facing: Facing,
) {
    enum class Facing {
        FRONT,
        BACK,
    }

    companion object {
        fun from(name: String, facing: Facing) = when (facing) {
            Facing.FRONT -> Front(name)
            Facing.BACK -> Back(name)
        }
    }

    class Front(name: String) : Camera(name, Facing.FRONT)
    class Back(name: String) : Camera(name, Facing.BACK)
}

@SameThread
private data class CameraVideoCapturerState(
    var capturer: org.webrtc.CameraVideoCapturer?,
    var currentCamera: Camera?,
    val frontCameras: List<Camera.Front>,
    val backCameras: List<Camera.Back>,
) {
    companion object {
        fun create(
            context: Context,
            observer: CapturerObserver,
            surfaceTextureHelper: SurfaceTextureHelper,
            cameraEventsHandler: WrappedCameraEventsHandler,
        ): CameraVideoCapturerState {
            // Create capturer, if possible
            //
            // Note: This is a tad ugly since there's a slight mismatch between Kotlin `Pair`
            //       and androidx `Pair`, so we cannot destructure easily. :/
            var capturer: org.webrtc.CameraVideoCapturer? = null
            var currentCamera: Camera? = null


            VideoCapturerUtil.createVideoCapturer(context, cameraEventsHandler)?.let {
                capturer = it.first
                currentCamera = Camera.from(it.second.first, it.second.second)
            }

            // Initialise capturer
            capturer?.initialize(surfaceTextureHelper, context, observer)

            // Enumerate all cameras. Each first item is supposed to be the primary device of
            // its type (_front_ or _back_ facing).
            //
            // Note: Right now, we only have one front and one back facing camera. But the
            //       code is ready for more cameras, when desired.
            val (front, back) = VideoCapturerUtil.getPrimaryCameraNames(context).let {
                Pair(
                    listOfNotNull(it.first?.let { name -> Camera.Front(name) }),
                    listOfNotNull(it.second?.let { name -> Camera.Back(name) }),
                )
            }

            // Sanity-check
            assert((capturer == null && front.size + back.size == 0)
                || (capturer != null && front.size + back.size > 0))

            // Done
            return CameraVideoCapturerState(capturer, currentCamera, front, back)

        }
    }
}

// TODO(ANDR-1957): Has been altered to use suspend function which do not play well with locks,
//  thus there are races to mitigate here!
@AnyThread
internal class CameraVideoCapturer private constructor(
    private val state: CameraVideoCapturerState,
) {
    companion object {
        @AnyThread
        fun create(
            context: Context,
            observer: CapturerObserver,
            surfaceTextureHelper: SurfaceTextureHelper,
            cameraEventsHandler: WrappedCameraEventsHandler,
        ): CameraVideoCapturer {
            val capturerState = CameraVideoCapturerState.create(
                context = context,
                observer = observer,
                surfaceTextureHelper = surfaceTextureHelper,
                cameraEventsHandler = cameraEventsHandler,
            )
            return CameraVideoCapturer(capturerState)
        }
    }

    private val lock = ReentrantLock()

    var capturing: Boolean = false
        get() = lock.withLock { field }
        private set // May only be called while holding the lock
    val currentCamera: Camera?
        get() = lock.withLock { state.currentCamera }
    val frontCameras: List<Camera.Front>
        get() = lock.withLock { state.frontCameras }
    val backCameras: List<Camera.Back>
        get() = lock.withLock { state.backCameras }

    @AnyThread
    fun startCapturing(settings: VideoCaptureSettings): Camera = lock.withLock {
        // Note: This implicitly starts capturing and it will also work to change the
        //       capture format on-the-fly, so it's our best match to KISS.
        state.capturer?.changeCaptureFormat(
            settings.width.toInt(),
            settings.height.toInt(),
            settings.fps.toInt(),
        )
        capturing = true
        checkNotNull(currentCamera)
    }

    @AnyThread
    fun stopCapturing() = lock.withLock {
        state.capturer?.stopCapture()
        capturing = false
    }

    @AnyThread
    suspend fun switchTo(camera: Camera) {
        val done = CompletableDeferred<Unit>()
        lock.withLock {
            state.capturer?.switchCamera(object : org.webrtc.CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(front: Boolean) {
                    lock.withLock {
                        state.currentCamera = camera
                        done.complete(Unit)
                    }
                }

                override fun onCameraSwitchError(error: String?) {
                    lock.withLock {
                        done.completeExceptionally(GroupCallException(
                            "Unable to switch to camera '${camera.name}', reason: $error"))
                    }
                }
            }, camera.name)
        }
        done.await()
    }

    @AnyThread
    fun teardown() = lock.withLock {
        state.capturer?.stopCapture()
        state.capturer?.dispose()
        state.capturer = null
    }
}

typealias DetachSinkFn = () -> Unit

abstract class VideoContext(
    track: VideoTrack,
) {
    private var _track: VideoTrack? = track

    protected val lock = ReentrantLock()
    protected val track: VideoTrack? get() = _track.also {
        if (it == null) {
            logger.warn("Video track already disposed")
        }
    }
    private var sink: VideoSink? = null

    @AnyThread
    fun renderTo(sink: VideoSink): DetachSinkFn = lock.withLock {
        track?.let { videoTrack ->
            this.sink?.let { videoTrack.removeSink(it) }
            videoTrack.addSink(sink)
            this.sink = sink
            // Return DetachSinkFn
            {
                lock.withLock {
                    videoTrack.removeSink(sink)
                    if (this.sink == sink) {
                        this.sink = null
                    }
                }
            }
        } ?: {}
    }

    @AnyThread
    internal open fun teardown() = lock.withLock {
        _track?.let { if (sink != null) it.removeSink(sink) }
        _track?.dispose()
        _track = null
    }
}

class RemoteVideoContext private constructor(
    track: VideoTrack,
) : VideoContext(
    track = track,
) {
    companion object {
        @AnyThread
        fun create(
            transceiver: RtpTransceiver
        ): RemoteVideoContext {
            if (transceiver.mediaType != MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                throw Error("Invalid transceiver kind for remote video context: '${transceiver.mediaType.name}")
            }
            if (transceiver.direction != RtpTransceiver.RtpTransceiverDirection.RECV_ONLY &&
                transceiver.direction != RtpTransceiver.RtpTransceiverDirection.SEND_RECV) {
                throw Error("Invalid transceiver direction for remote video context: '${transceiver.direction.name}")
            }

            // Extract track
            val track = transceiver.receiver.track().let {
                when (it) {
                    is VideoTrack -> it
                    null -> throw Error("Missing track on transceiver")
                    else -> throw Error("Invalid track type for remote video context: '$it'")
                }
            }
            return RemoteVideoContext(track)
        }
    }

    var active: Boolean
        get() = lock.withLock { track?.enabled() ?: false }
        set(enabled) = lock.withLock { track?.setEnabled(enabled) }
}

abstract class LocalVideoContext(
    track: VideoTrack,
) : VideoContext(
    track = track,
) {
    @AnyThread
    internal fun sendTo(transceiver: RtpTransceiver) = lock.withLock {
        if (transceiver.mediaType != MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
            throw Error("Invalid transceiver kind for local video context: '${transceiver.mediaType.name}")
        }
        if (transceiver.direction != RtpTransceiver.RtpTransceiverDirection.SEND_ONLY &&
            transceiver.direction != RtpTransceiver.RtpTransceiverDirection.SEND_RECV) {
            throw Error("Invalid transceiver direction for local video context: '${transceiver.direction.name}")
        }

        // Note: We do explicitly request to not take ownership because that will dispose of tracks
        //       when fetching all transceivers.
        transceiver.sender.setTrack(track, false)
    }
}

// TODO(ANDR-1957): Has been altered to use suspend function which do not play well with locks,
//  thus there are races to mitigate here!
@AnyThread
class LocalCameraVideoContext private constructor(
    track: VideoTrack,
    private val capturer: CameraVideoCapturer,
    private val settings: VideoCaptureSettingsFn,
) : LocalVideoContext(
    track = track,
) {
    companion object {
        @AnyThread
        fun create(
            context: Context,
            factory: FactoryCtx,
            settings: VideoCaptureSettingsFn,
        ): LocalCameraVideoContext {
            var source: VideoSource? = null
            var track: VideoTrack? = null

            try {
                // Create a new video source and track.
                //
                // Note: This will do nothing on any peer connection unless the track is applied to it.
                source = factory.factory.createVideoSource(false, false)
                track = factory.factory.createVideoTrack("local-video", source).also { it.setEnabled(false) }
                val capturer = CameraVideoCapturer.create(
                    context = context,
                    observer = source.capturerObserver,
                    surfaceTextureHelper = factory.surfaceTextureHelper,
                    cameraEventsHandler = WrappedCameraEventsHandler(object : SaneCameraEventsHandler {
                        // TODO(ANDR-1978): Handle camera events
                    })
                )

                return LocalCameraVideoContext(track, capturer, settings)
            } catch (e: Exception) {
                source?.dispose()
                track?.dispose()
                throw e
            }
        }
    }

    val active: Boolean
        get() = lock.withLock { capturer.capturing }
    val currentCamera: Camera?
        get() = lock.withLock { capturer.currentCamera }
    val frontCameras: List<Camera>
        get() = lock.withLock { capturer.frontCameras }
    val backCameras: List<Camera>
        get() = lock.withLock { capturer.backCameras }

    @AnyThread
    fun startCapturing(): Camera {
        return capturer.startCapturing(settings()).also {
            lock.withLock {
                track?.setEnabled(true)
            }
        }
    }

    @AnyThread
    fun stopCapturing() {
        lock.withLock {
            track?.setEnabled(false)
        }
        capturer.stopCapturing()
    }

    @AnyThread
    suspend fun flipCamera() {
        val otherCamera = currentCamera?.let {
            when (it.facing) {
                Camera.Facing.FRONT -> backCameras.firstOrNull()
                Camera.Facing.BACK -> frontCameras.firstOrNull()
            }
        }
        if (otherCamera != null) {
            capturer.switchTo(otherCamera)
        } else {
            throw GroupCallException("Cannot switch camera, no other camera available")
        }
    }

    @AnyThread
    override fun teardown() = lock.withLock {
        capturer.teardown()
        super.teardown()
    }
}
