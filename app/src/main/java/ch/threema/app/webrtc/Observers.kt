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

package ch.threema.app.webrtc

import androidx.annotation.AnyThread
import ch.threema.app.voip.groupcall.GroupCallException
import ch.threema.base.utils.LoggingUtil
import java8.util.concurrent.CompletableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.CameraVideoCapturer
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext

/**
 * Implements the interface but ignores all observed events unless overridden.
 */
@AnyThread
open class DefaultNoopPeerConnectionObserver : PeerConnection.Observer {
    override fun onRenegotiationNeeded() { /* noop */ }
    override fun onSignalingChange(state: PeerConnection.SignalingState?) { /* noop */ }
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) { /* noop */ }
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) { /* noop */ }
    override fun onIceConnectionReceivingChange(receiving: Boolean) { /* noop */ }
    override fun onIceCandidate(candidate: IceCandidate?) { /* noop */ }
    override fun onIceCandidatesRemoved(candidatesRemoved: Array<out IceCandidate>?) { /* noop */ }
    override fun onAddStream(stream: MediaStream?) { /* noop */ }
    override fun onRemoveStream(stream: MediaStream?) { /* noop */ }
    override fun onDataChannel(dataChannel: DataChannel?) { /* noop */ }
}

/**
 * Maps the PeerConnection.Observer into a more sane structure with non-nullable types and some
 * non-standard and irrelevant events removed.
 */
@AnyThread
interface SanePeerConnectionObserver {
    fun onDetach() { /* noop */ }
    fun onRenegotiationNeeded() { /* noop */ }
    fun onSignalingChange(state: PeerConnection.SignalingState) { /* noop */ }
    fun onConnectionChange(state: PeerConnection.PeerConnectionState) { /* noop */ }
    fun onIceGatheringChange(state: PeerConnection.IceGatheringState) { /* noop */ }
    fun onIceConnectionChange(state: PeerConnection.IceConnectionState) { /* noop */ }
    fun onIceCandidate(candidate: IceCandidate) { /* noop */ }
    fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) { /* noop */ }
    fun onDataChannel(channel: DataChannel) { /* noop */ }
    fun onTransceiver(transceiver: RtpTransceiver) { /* noop */ }
}

/**
 * Wraps a PeerConnection.Observer to dispatch to the more SanePeerConnectionObserver.
 *
 * If no observer is attached, all events will be buffered and flushed once an observer is being
 * attached.
 *
 * Since libwebrtc has a... weird thread model, applies a ReentrantLock every time one of
 * the events fires.
 */
@AnyThread
class WrappedPeerConnectionObserver(
    private var observer: SanePeerConnectionObserver? = null,
) : DefaultNoopPeerConnectionObserver() {
    private val lock = ReentrantLock()
    private val events = mutableListOf<Any>()

    fun replace(newObserver: SanePeerConnectionObserver?) {
        lock.withLock {
            observer?.onDetach()
            observer = newObserver
            if (newObserver == null) {
                return@withLock
            }
            events.forEach {
                when (it) {
                    is Unit -> newObserver.onRenegotiationNeeded()
                    is PeerConnection.SignalingState -> newObserver.onSignalingChange(it)
                    is PeerConnection.PeerConnectionState -> newObserver.onConnectionChange(it)
                    is PeerConnection.IceGatheringState -> newObserver.onIceGatheringChange(it)
                    is PeerConnection.IceConnectionState -> newObserver.onIceConnectionChange(it)
                    is IceCandidate -> newObserver.onIceCandidate(it)
                    is CandidatePairChangeEvent -> newObserver.onSelectedCandidatePairChanged(it)
                    is DataChannel -> newObserver.onDataChannel(it)
                    is RtpTransceiver -> newObserver.onTransceiver(it)
                    else -> throw Error("Unexpected peer connection event type: ${it.javaClass}")
                }
            }
            events.clear()
        }
    }

    override fun onRenegotiationNeeded() = lock.withLock {
        if (observer.let { it?.onRenegotiationNeeded() == null }) {
            events.add(Unit)
        }
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
        checkNotNull(state)
        lock.withLock {
            if (observer.let { it?.onSignalingChange(state) == null }) {
                events.add(state)
            }
        }
    }

    override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
        checkNotNull(state)
        lock.withLock {
            if (observer.let { it?.onConnectionChange(state) == null }) {
                events.add(state)
            }
        }
    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
        checkNotNull(state)
        lock.withLock {
            if (observer.let { it?.onIceGatheringChange(state) == null }) {
                events.add(state)
            }
        }
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        checkNotNull(state)
        lock.withLock {
            if (observer.let { it?.onIceConnectionChange(state) == null }) {
                events.add(state)
            }
        }
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        checkNotNull(candidate)
        lock.withLock {
            if (observer.let { it?.onIceCandidate(candidate) == null }) {
                events.add(candidate)
            }
        }
    }

    override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {
        checkNotNull(event)
        lock.withLock {
            if (observer.let { it?.onSelectedCandidatePairChanged(event) == null }) {
                events.add(event)
            }
        }
    }

    override fun onDataChannel(dataChannel: DataChannel?) {
        checkNotNull(dataChannel)
        lock.withLock {
            if (observer.let { it?.onDataChannel(dataChannel) == null }) {
                events.add(dataChannel)
            }
        }
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        checkNotNull(transceiver)
        lock.withLock {
            if (observer.let { it?.onTransceiver(transceiver) == null }) {
                events.add(transceiver)
            }
        }
    }
}

internal class PeerConnectionObserver(
    private val addTransceiver: ((transceiver: RtpTransceiver) -> Unit),
    private val failedSignal: CompletableDeferred<*>,
) : SanePeerConnectionObserver {
    private val logger = LoggingUtil.getThreemaLogger("GroupCall.PeerConnectionObserver")
    private val lock = ReentrantLock()
    private var iceFailedSignal: CompletableFuture<Unit>? = null

    override fun onDetach() = cancelIceFailedTimer()

    // region Event handlers

    override fun onRenegotiationNeeded() = logger.trace("Negotiation needed")
    override fun onSignalingChange(state: PeerConnection.SignalingState) =
        logger.trace("Signaling state: {}", state.name)

    override fun onConnectionChange(state: PeerConnection.PeerConnectionState) =
        logger.debug("Connection state: {}", state.name)

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) =
        logger.trace("ICE gathering state: {}", state.name)

    override fun onIceCandidate(candidate: IceCandidate) =
        logger.trace("ICE candidate: {}", candidate.sdp)

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = lock.withLock {
        logger.debug("ICE connection state: {}", state.name)
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED ->
                    cancelIceFailedTimer()
                PeerConnection.IceConnectionState.DISCONNECTED ->
                    scheduleIceFailedTimer()
                PeerConnection.IceConnectionState.FAILED ->
                    iceFailed(GroupCallException("ICE failed explicitly"))
                else -> { /* noop */ }
            }
    }

    override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) =
        logger.debug("Selected candidate: {} -> {}", event.local.sdp, event.remote.sdp)

    override fun onDataChannel(channel: DataChannel) =
        logger.warn("Unexpected data channel (label='{}')", channel.label())

    override fun onTransceiver(transceiver: RtpTransceiver) = lock.withLock {
        logger.trace("New transceiver (kind='{}', mid='{}')",
            transceiver.mediaType.name, transceiver.mid)
        addTransceiver(transceiver)
    }

    // endregion

    // region ICE failed timer

    private fun cancelIceFailedTimer() {
        if (iceFailedSignal?.cancel(true) == true) {
            logger.trace("ICE failed timer cancelled")
        }
        iceFailedSignal = null
    }

    private fun scheduleIceFailedTimer() {
        cancelIceFailedTimer()
        iceFailedSignal = CompletableFuture.supplyAsync<Unit>({
            throw Error("ICE remained disconnected for 10 seconds")
        }, CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS))
            .exceptionally { error ->
                when (error) {
                    is CancellationException -> { /* noop */ }
                    else -> {
                        logger.trace("ICE failed timer expired")
                        iceFailed(GroupCallException("ICE failed due to timeout"))
                    }
                }
            }
        logger.trace("ICE failed timer started")
    }

    private fun iceFailed(error: GroupCallException) {
        failedSignal.completeExceptionally(error)
    }

    // endregion
}

/**
 * Sane DataChannelObserver.
 */
@AnyThread
interface SaneDataChannelObserver {
    fun onDetach() { /* noop */ }
    fun onBufferedAmountChange(bufferedAmount: ULong) { /* noop */ }
    fun onStateChange(state: DataChannel.State) { /* noop */ }
    fun onMessage(buffer: DataChannel.Buffer) { /* noop */ }
}

/**
 * Wraps a DataChannel.Observer to a SaneDataChannelObserver.
 *
 * If no observer is attached, all events will be buffered and flushed once an observer is being
 * attached.
 *
 * Since libwebrtc has a... weird thread model, applies a ReentrantLock every time one of
 * the events fires.
 */
@AnyThread
class WrappedDataChannelObserver(
    private val state: () -> DataChannel.State,
    private var observer: SaneDataChannelObserver? = null
) : DataChannel.Observer {
    private val lock = ReentrantLock()
    private val events = mutableListOf<Any>()

    fun replace(newObserver: SaneDataChannelObserver?) = lock.withLock {
        observer?.onDetach()
        observer = newObserver
        if (newObserver == null) {
            return
        }
        events.forEach {
            when (it) {
                is ULong -> newObserver.onBufferedAmountChange(it)
                is DataChannel.State -> newObserver.onStateChange(it)
                is DataChannel.Buffer -> newObserver.onMessage(it)
                else -> throw Error("Unexpected data channel event type: ${it.javaClass}")
            }
        }
        events.clear()
    }

    override fun onBufferedAmountChange(bufferedAmountLong: Long) {
        val bufferedAmount = bufferedAmountLong.toULong()
        lock.withLock {
            if (observer.let { it?.onBufferedAmountChange(bufferedAmount) == null }) {
                events.add(bufferedAmount)
            }
        }
    }

    override fun onStateChange() = lock.withLock {
        val state = state()
        if (observer.let { it?.onStateChange(state) == null }) {
            events.add(state)
        }
    }

    override fun onMessage(message: DataChannel.Buffer?) {
        checkNotNull(message)
        lock.withLock {
            if (observer.let { it?.onMessage(message) == null }) {
                // Copy the message since the ByteBuffer will be reused immediately
                val copy = with(ByteBuffer.allocate(message.data.remaining())) {
                    put(message.data)
                    flip()
                    this
                }
                events.add(DataChannel.Buffer(copy, message.binary))
            }
        }
    }
}

suspend fun PeerConnection.setLocalDescription(description: SessionDescription) {
    val future = CompletableDeferred<Unit>()
    this.setLocalDescription(object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {
            future.completeExceptionally(Error("Unexpected onCreateSuccess event"))
        }

        override fun onSetSuccess() {
            future.complete(Unit)
        }

        override fun onCreateFailure(reason: String?) {
            future.completeExceptionally(Error("Unexpected onCreateFailure event"))
        }

        override fun onSetFailure(reason: String?) {
            checkNotNull(reason)
            future.completeExceptionally(Error("Setting remote description failed, reason: $reason"))
        }
    }, description)
    return future.await()
}

suspend fun PeerConnection.setRemoteDescription(description: SessionDescription) {
    val future = CompletableDeferred<Unit>()
    this.setRemoteDescription(object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {
            future.completeExceptionally(GroupCallException("Unexpected onCreateSuccess event"))
        }

        override fun onSetSuccess() {
            future.complete(Unit)
        }

        override fun onCreateFailure(reason: String?) {
            future.completeExceptionally(GroupCallException("Unexpected onCreateFailure event"))
        }

        override fun onSetFailure(reason: String?) {
            checkNotNull(reason)
            future.completeExceptionally(GroupCallException("Setting remote description failed, reason: $reason"))
        }
    }, description)
    return future.await()
}

suspend fun PeerConnection.createAnswer(constraints: MediaConstraints? = null): SessionDescription {
    val future = CompletableDeferred<SessionDescription>()
    this.createAnswer(object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {
            checkNotNull(description)
            future.complete(description)
        }

        override fun onSetSuccess() {
            future.completeExceptionally(Error("Unexpected onSetSuccess event"))
        }

        override fun onCreateFailure(reason: String?) {
            future.completeExceptionally(Error("Creating answer failed, reason: $reason"))
        }

        override fun onSetFailure(reason: String?) {
            future.completeExceptionally(Error("Unexpected onSetFailure event"))
        }
    }, constraints ?: MediaConstraints())
    return future.await()
}

/**
 * Add an ICE candidate and maps the result to a CompletableFuture.
 */
suspend fun PeerConnection.addIceCandidateAsync(candidate: IceCandidate) {
    val future = CompletableDeferred<Unit>()
    this.addIceCandidate(candidate, object : AddIceObserver {
        override fun onAddSuccess() {
            future.complete(Unit)
        }

        override fun onAddFailure(reason: String?) {
            checkNotNull(reason)
            future.completeExceptionally(Error("Unable to add ICE candidate: '$reason'"))
        }
    })
    return future.await()
}

/**
 * Maps the PeerConnection.Observer into a more sane structure with non-nullable types.
 */
interface SaneCameraEventsHandler {
    fun onCameraError(error: String) { /* noop */ }
    fun onCameraDisconnected() { /* noop */ }
    fun onCameraFreeze(error: String) { /* noop */ }
    fun onCameraOpening(cameraName: String) { /* noop */ }
    fun onFirstFrameAvailable() { /* noop */ }
    fun onCameraClosed() { /* noop */ }
}

/**
 * Wraps a CameraVideoCapturer.CameraEventsHandler to a SaneCameraEventsHandler.
 */
class WrappedCameraEventsHandler(
    private val observer: SaneCameraEventsHandler,
) : CameraVideoCapturer.CameraEventsHandler {
    override fun onCameraError(error: String?) {
        observer.onCameraError(checkNotNull(error))
    }

    override fun onCameraDisconnected() {
        observer.onCameraDisconnected()
    }

    override fun onCameraFreezed(error: String?) {
        observer.onCameraFreeze(checkNotNull(error))
    }

    override fun onCameraOpening(cameraName: String?) {
        observer.onCameraOpening(checkNotNull(cameraName))
    }

    override fun onFirstFrameAvailable() {
        observer.onFirstFrameAvailable()
    }

    override fun onCameraClosed() {
        observer.onCameraClosed()
    }
}
