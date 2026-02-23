package ch.threema.app.voip.groupcall.sfu.webrtc

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.webrtc.LocalCameraVideoContext
import ch.threema.app.webrtc.LocalMicrophoneAudioContext
import ch.threema.app.webrtc.VideoCaptureSettings
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("LocalCtx")

@WorkerThread
class LocalCtx private constructor(
    val microphoneAudioContext: LocalMicrophoneAudioContext,
    val cameraVideoContext: LocalCameraVideoContext,
    // Note: This is the place to add a screenshare context when desired
) {
    companion object {
        @WorkerThread
        internal fun create(context: Context, factory: FactoryCtx): LocalCtx {
            GroupCallThreadUtil.assertDispatcherThread()

            return LocalCtx(
                microphoneAudioContext = LocalMicrophoneAudioContext.create(factory),
                cameraVideoContext = LocalCameraVideoContext.create(context, factory) {
                    // TODO(ANDR-1952): Refine parameters
                    VideoCaptureSettings(width = 1280u, height = 960u, fps = 30u)
                },
            ).also {
                // microphone active by default
                it.microphoneAudioContext.active = true
            }
        }
    }

    @AnyThread
    fun teardown() {
        logger.trace("Teardown: Local")

        logger.trace("Teardown: LocalMicrophoneAudioContext")
        microphoneAudioContext.teardown()

        logger.trace("Teardown: LocalCameraVideoContext")
        cameraVideoContext.teardown()

        logger.trace("Teardown: /Local")
    }
}
