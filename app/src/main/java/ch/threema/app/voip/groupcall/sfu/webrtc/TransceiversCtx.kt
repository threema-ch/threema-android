package ch.threema.app.voip.groupcall.sfu.webrtc

import ch.threema.app.voip.groupcall.sfu.MediaKind
import ch.threema.app.voip.groupcall.sfu.ParticipantId
import org.webrtc.RtpTransceiver

typealias Transceivers = MutableMap<MediaKind, RtpTransceiver>

internal class TransceiversCtx(
    var local: Transceivers?,
    var remote: MutableMap<ParticipantId, Transceivers>,
) {
    // Note: We do not need a 'teardown' routine in here since all transceivers will be disposed
    //       by its associated peer connection.
}
