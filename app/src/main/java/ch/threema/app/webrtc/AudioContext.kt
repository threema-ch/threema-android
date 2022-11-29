/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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
import ch.threema.app.voip.groupcall.sfu.webrtc.FactoryCtx
import org.webrtc.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// TODO(ANDR-1956): replace completable futures with suspend..

abstract class AudioContext(
    track: AudioTrack,
) {
    private var _track: AudioTrack? = track

    protected val lock = ReentrantLock()
    protected val track: AudioTrack get() = checkNotNull(_track) { "Audio track already disposed" }

    internal fun teardown() = lock.withLock {
        _track?.dispose()
        _track = null
    }
}

class RemoteAudioContext private constructor(
    track: AudioTrack,
) : AudioContext(
    track = track,
) {
    companion object {
        fun create(
            transceiver: RtpTransceiver,
        ): RemoteAudioContext {
            if (transceiver.mediaType != MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                throw Error("Invalid transceiver kind for remote audio context: '${transceiver.mediaType.name}")
            }
            if (transceiver.direction != RtpTransceiver.RtpTransceiverDirection.RECV_ONLY &&
                transceiver.direction != RtpTransceiver.RtpTransceiverDirection.SEND_RECV) {
                throw Error("Invalid transceiver direction for remote audio context: '${transceiver.direction.name}")
            }

            // Extract track
            val track = transceiver.receiver.track().let {
                when (it) {
                    is AudioTrack -> it
                    null -> throw Error("Missing track on transceiver")
                    else -> throw Error("Invalid track type for remote audio context: '$it")
                }
            }
            return RemoteAudioContext(track)
        }
    }

    var active: Boolean
        get() = lock.withLock { track.enabled() }
        set(enabled) = lock.withLock { track.setEnabled(enabled) }
}

abstract class LocalAudioContext(
    track: AudioTrack,
) : AudioContext(
    track = track,
) {
    internal fun sendTo(transceiver: RtpTransceiver) = lock.withLock {
        if (transceiver.mediaType != MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
            throw Error("Invalid transceiver kind for local audio context: '${transceiver.mediaType.name}")
        }
        if (transceiver.direction != RtpTransceiver.RtpTransceiverDirection.SEND_ONLY &&
            transceiver.direction != RtpTransceiver.RtpTransceiverDirection.SEND_RECV) {
            throw Error("Invalid transceiver direction for local audio context: '${transceiver.direction.name}")
        }

        // Note: We do explicitly request to not take ownership because that will dispose of tracks
        //       when fetching all transceivers.
        transceiver.sender.setTrack(track, false)
    }
}

@AnyThread
class LocalMicrophoneAudioContext private constructor(
    track: AudioTrack,
) : LocalAudioContext(
    track = track
) {
    companion object {
        @AnyThread
        fun create(
            factory: FactoryCtx,
        ): LocalMicrophoneAudioContext {
            var source: AudioSource? = null
            var track: AudioTrack? = null

            try {
                // Create a new audio source and track.
                //
                // Note: This will do nothing on any peer connection unless the track is applied to it.
                val constraints = MediaConstraints()
                source = factory.factory.createAudioSource(constraints)
                track = factory.factory.createAudioTrack("local-audio", source)
                return track.let {
                    it.setEnabled(false)
                    LocalMicrophoneAudioContext(it)
                }
            } catch (e: Exception) {
                track?.dispose()
                source?.dispose()
                throw e
            }
        }
    }

    var active: Boolean
        get() = lock.withLock { track.enabled() }
        set(enabled) = lock.withLock { track.setEnabled(enabled) }
}
