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

package ch.threema.app.voip.groupcall.sfu.webrtc

import androidx.annotation.WorkerThread
import ch.threema.app.webrtc.WrappedPeerConnectionObserver
import ch.threema.base.utils.LoggingUtil
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver

private val logger = LoggingUtil.getThreemaLogger("PeerConnectionCtx")

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
