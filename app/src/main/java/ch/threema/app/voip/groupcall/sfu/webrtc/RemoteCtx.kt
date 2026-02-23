package ch.threema.app.voip.groupcall.sfu.webrtc

import androidx.annotation.UiThread
import ch.threema.app.voip.groupcall.sfu.MediaKind
import ch.threema.app.webrtc.RemoteAudioContext
import ch.threema.app.webrtc.RemoteVideoContext

@UiThread
class RemoteCtx private constructor(
    val microphoneAudioContext: RemoteAudioContext,
    val cameraVideoContext: RemoteVideoContext,
    // Note: This is the place to add a screenshare context when desired
) {
    companion object {
        internal fun fromTransceiverMap(transceivers: Transceivers): RemoteCtx {
            val microphoneAudio = checkNotNull(transceivers[MediaKind.AUDIO]) {
                "Expected remote audio transceiver to be set"
            }
            val cameraVideo = checkNotNull(transceivers[MediaKind.VIDEO]) {
                "Expected remote video transceiver to be set"
            }
            return RemoteCtx(
                microphoneAudioContext = RemoteAudioContext.create(microphoneAudio),
                cameraVideoContext = RemoteVideoContext.create(cameraVideo),
            )
        }
    }
}
