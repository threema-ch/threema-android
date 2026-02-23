package ch.threema.app.voip.groupcall.sfu.webrtc

import androidx.annotation.WorkerThread
import ch.threema.app.webrtc.WrappedPeerConnectionObserver
import ch.threema.base.utils.getThreemaLogger
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver

private val logger = getThreemaLogger("PeerConnectionCtx")

internal class PeerConnectionCtx(
    val pc: PeerConnection,
    val observer: WrappedPeerConnectionObserver,
) {
    private val _transceivers = mutableMapOf<String, RtpTransceiver>()

    internal val transceivers: Map<String, RtpTransceiver>
        get() = _transceivers

    internal fun gatherInitialTransceivers(): MutableMap<String, RtpTransceiver> {
        if (_transceivers.isNotEmpty()) {
            throw Error("gatherInitialTransceivers may only be called once when mapping all local transceivers for the first time!")
        }
        return pc.transceivers.associateBy { transceiver ->
            if (transceiver.mid == null) {
                throw Error("Invalid transceiver, MID is null")
            }
            _transceivers[transceiver.mid] = transceiver
            transceiver.mid
        }.toMutableMap()
    }

    internal fun removeInactiveTransceiver(mid: String) {
        _transceivers.remove(mid)
    }

    internal fun addTransceiverFromEvent(transceiver: RtpTransceiver) {
        // CAUTION: We do not dispatch here because libwebrtc would deadlock us.
        // The thread dump looks like this:
        //
        // Dispatcher: setRemoteDescription(...)
        //             ^ this keeps Dispatcher locked and dispatches to an internal signaling thread
        //
        // signaling:  onTransceiver(...) -> addTransceiverFromEvent (...)
        //
        // Because our Dispatcher is currently locked and no other thread is expected to access
        // `transceivers`, having no lock or dispatch routine here is okay.
        if (_transceivers.contains(transceiver.mid)) {
            throw Error("Newly announced transceiver already existed in transceiver map")
        }
        _transceivers[transceiver.mid] = transceiver
    }

    /**
     * IMPORTANT: Make sure this is executed in the ConnectionCtx-Worker
     */
    @WorkerThread
    fun teardown() {
        logger.trace("Teardown: PeerConnectionCtx")

        logger.trace("Teardown: Dispose PeerConnection")
        // RtpTransceivers are disposed when the underlying PeerConnection is closed.
        // When disposing the PeerConnection it is also closed.
        pc.dispose()

        logger.trace("Teardown: /PeerConnectionCtx")
    }
}
