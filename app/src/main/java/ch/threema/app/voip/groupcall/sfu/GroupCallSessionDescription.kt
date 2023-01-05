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

package ch.threema.app.voip.groupcall.sfu

import ch.threema.app.voip.groupcall.sfu.webrtc.SessionParameters
import org.webrtc.RtpParameters

// From grammar for SDP 'token':
// https://www.rfc-editor.org/rfc/rfc4566#section-9
private val SDP_TOKEN_RANGE = listOf(
    '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
    'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
    'U', 'V', 'W', 'X', 'Y', 'Z', '^', '_', '`', 'a',
    'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k',
    'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u',
    'v', 'w', 'x', 'y', 'z', '{', '|', '}', '~'
)
private val MIDS: List<String> = listOf<String>() +
    SDP_TOKEN_RANGE.map { "$it" } +
    SDP_TOKEN_RANGE.map { left -> SDP_TOKEN_RANGE.map { right -> "$left$right" } }.flatten()

private const val MIDS_PER_PARTICIPANT = 8

val MIDS_MAX = MIDS.size / MIDS_PER_PARTICIPANT

@JvmInline
value class Mid(val mid: String)

data class Mids(
    val microphone: Mid,
    val camera: Mid,
    val _r1: Mid,
    val _r2: Mid,
    val _r3: Mid,
    val _r4: Mid,
    val _r5: Mid,
    val data: Mid,
) {
    companion object {
        fun fromParticipantId(participantId: ParticipantId): Mids {
            var offset = participantId.id.toInt() * MIDS_PER_PARTICIPANT
            return Mids(
                microphone = Mid(MIDS[offset++]),
                camera = Mid(MIDS[offset++]),
                _r1 = Mid(MIDS[offset++]),
                _r2 = Mid(MIDS[offset++]),
                _r3 = Mid(MIDS[offset++]),
                _r4 = Mid(MIDS[offset++]),
                _r5 = Mid(MIDS[offset++]),
                data = Mid(MIDS[offset]),
            )
        }
    }

    fun toMap(): Map<MediaKind, Mid> = mapOf(
        MediaKind.AUDIO to this.microphone,
        MediaKind.VIDEO to this.camera,
    )
}

private class Codec(
    val payloadType: UShort,
    val parameters: List<ULong>,
    val feedback: List<String>? = null,
    val fmtp: List<String>? = null,
)

private val MICROPHONE_CODECS = mapOf(
    "opus" to Codec(
        payloadType = 111u,
        parameters = listOf(48_000_u, 2u),
        feedback = listOf("transport-cc"),
        fmtp = listOf("minptime=10", "useinbandfec=1", "usedtx=1"),
    )
)

private val CAMERA_CODECS = mapOf(
    "VP8" to Codec(
        payloadType = 96u,
        parameters = listOf(90_000u),
        feedback = listOf("transport-cc", "ccm fir", "nack", "nack pli", "goog-remb"),
    ),
    "rtx" to Codec(
        payloadType = 97u,
        parameters = listOf(90_000u),
        fmtp = listOf("apt=96"),
    ),
)

private val MICROPHONE_HEADER_EXTENSIONS = mapOf(
    1u to "urn:ietf:params:rtp-hdrext:sdes:mid",
    4u to "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time",
    5u to "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01",
    // TODO(SE-257): Disabled until we can use cryptex
    // 10u to "urn:ietf:params:rtp-hdrext:ssrc-audio-level",
)

private val CAMERA_HEADER_EXTENSIONS = mapOf(
    1u to "urn:ietf:params:rtp-hdrext:sdes:mid",
    2u to "urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id",
    3u to "urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id",
    4u to "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time",
    5u to "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01",
    11u to "urn:3gpp:video-orientation",
    12u to "urn:ietf:params:rtp-hdrext:toffset",
)

enum class ScalabilityMode(val temporalLayers: Int) {
    L1T3(3)
}

class SendEncoding(
    val rid: String,
    val maxBitrateBps: ULong,
    val scalabilityMode: ScalabilityMode,
    val scaleResolutionDownBy: UShort? = null,
) {
    fun toRtcEncoding(): RtpParameters.Encoding {
        val encoding = RtpParameters.Encoding(rid, true, 1.0)
        encoding.maxBitrateBps = maxBitrateBps.toInt()
        encoding.numTemporalLayers = scalabilityMode.temporalLayers
        encoding.scaleResolutionDownBy = scaleResolutionDownBy?.toDouble()
        return encoding
    }
}

// TODO(ANDR-1952): Refine parameters
val CAMERA_SEND_ENCODINGS = listOf(
    SendEncoding(
        rid = "l",
        maxBitrateBps = 100_000_u,
        scalabilityMode = ScalabilityMode.L1T3,
        scaleResolutionDownBy = 4u,
    ),
    SendEncoding(
        rid = "m",
        maxBitrateBps = 250_000_u,
        scalabilityMode = ScalabilityMode.L1T3,
        scaleResolutionDownBy = 2u,
    ),
    SendEncoding(
        rid = "h",
        maxBitrateBps = 1_200_000_u,
        scalabilityMode = ScalabilityMode.L1T3,
    ),
)

internal class RemoteSessionDescriptionInit(
    val parameters: SessionParameters,
    val remoteParticipants: Set<ParticipantId>,
)

private class SessionState(
    var id: ULong,
    var version: ULong,
    val localParticipantId: ParticipantId,
) {
    /**
     * Tracks the SDP m-line order. Local first, then all remote participants in
     * **insertion order**, including those which have already left the call.
     *
     * IMPORTANT: This **must** maintain the insertion order, hence `LinkedHashSet`!
     */
    val mLineOrder = LinkedHashSet<ParticipantId>(listOf(localParticipantId))
}

private enum class DirectionType {
    LOCAL,
    REMOTE,
}

enum class MediaKind(val sdpKind: String) {
    AUDIO(sdpKind = "audio"),
    VIDEO(sdpKind = "video"),
}

internal class GroupCallSessionDescription(
    localParticipantId: ParticipantId
) {
    private val state: SessionState = SessionState(1u, 1u, localParticipantId)

    val mLineOrder: Set<ParticipantId>
        get() = state.mLineOrder

    fun addParticipantToMLineOrder(participantId: ParticipantId) {
        state.mLineOrder.add(participantId)
    }

    fun generateRemoteDescription(init: RemoteSessionDescriptionInit): String {
        // List of all bundled MIDs
        val bundle = mutableListOf<Mid>()

        // Generated remote lines
        var lines = mutableListOf<String>()

        // Add local media lines
        val mLineOrder = state.mLineOrder.toMutableList()
        run {
            if (mLineOrder.removeFirst() != state.localParticipantId) {
                throw Error("Expected local participant ID to be first in mLineOrder")
            }
            val mids = Mids.fromParticipantId(state.localParticipantId)

            // Add RTP media lines
            bundle.add(mids.microphone)
            lines += createRtpMediaLines(
                type = DirectionType.LOCAL,
                kind = MediaKind.AUDIO,
                active = true,
                extensions = MICROPHONE_HEADER_EXTENSIONS,
                codecs = MICROPHONE_CODECS,
                mid = mids.microphone,
            )
            bundle.add(mids.camera)
            lines += createRtpMediaLines(
                type = DirectionType.LOCAL,
                kind = MediaKind.VIDEO,
                active = true,
                extensions = CAMERA_HEADER_EXTENSIONS,
                codecs = CAMERA_CODECS,
                mid = mids.camera,
                simulcast = CAMERA_SEND_ENCODINGS,
            )

            // Add SCTP media line
            bundle.add(mids.data)
            lines += createSctpMediaLines(mids.data)
        }

        // Add remote media lines
        val remoteParticipants = init.remoteParticipants.toMutableSet()
        for (participantId in mLineOrder) {
            val mids = Mids.fromParticipantId(participantId)

            // Check if the remote participant is active
            val active = remoteParticipants.remove(participantId)

            // Add RTP media lines
            bundle.add(mids.microphone)
            lines += createRtpMediaLines(
                type = DirectionType.REMOTE,
                kind = MediaKind.AUDIO,
                active = active,
                extensions = MICROPHONE_HEADER_EXTENSIONS,
                codecs = MICROPHONE_CODECS,
                mid = mids.microphone,
            )
            bundle.add(mids.camera)
            lines += createRtpMediaLines(
                type = DirectionType.REMOTE,
                kind = MediaKind.VIDEO,
                active = active,
                extensions = CAMERA_HEADER_EXTENSIONS,
                codecs = CAMERA_CODECS,
                mid = mids.camera,
            )
        }

        // Sanity-check
        if (remoteParticipants.isNotEmpty()) {
            throw Error("Expected a remote media line to be present for each remote participant ID")
        }

        // Prepend session lines
        lines = (createSessionLines(init, bundle) + lines).toMutableList()

        // Generate description
        lines.add("")
        return lines.joinToString("\r\n")
    }

    fun patchLocalDescription(sdp: String): String {
        val opus = MICROPHONE_CODECS["opus"]!!

        // Ensure correct Opus settings
        return sdp.replace(Regex("a=fmtp:${opus.payloadType} .*"), "a=fmtp:${opus.payloadType} ${opus.fmtp!!.joinToString(";")}")
    }

    private fun createRtpMediaLines(
        type: DirectionType,
        kind: MediaKind,
        active: Boolean,
        extensions: Map<UInt, String>,
        codecs: Map<String, Codec>,
        mid: Mid,
        simulcast: List<SendEncoding>? = null,
    ): List<String> {
        val payloadTypes = codecs.map { (_, codec) -> codec.payloadType }

        // Determine direction
        val direction = when {
            !active -> "inactive"
            type == DirectionType.LOCAL -> "recvonly"
            type == DirectionType.REMOTE -> "sendonly"
            else -> throw Error("Unable to determine direction")
        }

        // Add media-specific lines
        val lines = mutableListOf(
            "m=${kind.sdpKind} ${if (active) "9" else "0"} UDP/TLS/RTP/SAVPF ${payloadTypes.joinToString(" ")}",
            "c=IN IP4 0.0.0.0",
            "a=rtcp:9 IN IP4 0.0.0.0",
            "a=mid:${mid.mid}"
        )
        lines += extensions.map { (id, uri) -> "a=extmap:$id $uri" }
        lines += listOf(
            "a=$direction",
            "a=rtcp-mux",
        )
        if (kind == MediaKind.VIDEO) {
            lines.add("a=rtcp-rsize")
        }

        // Add msid if remote participant
        if (type == DirectionType.REMOTE) {
            lines.add("a=msid:- ${mid.mid}")
        }

        // Add codec-specific lines
        for ((name, codec) in codecs) {
            lines.add("a=rtpmap:${codec.payloadType} ${name}/${codec.parameters.joinToString("/")}")
            lines += codec.feedback?.map { feedback -> "a=rtcp-fb:${codec.payloadType} $feedback" }
                ?: listOf()
            if (codec.fmtp != null) {
                lines.add("a=fmtp:${codec.payloadType} ${codec.fmtp.joinToString(";")}")
            }
        }

        // Add simulcast lines, if necessary
        if (active && simulcast != null) {
            if (kind != MediaKind.VIDEO) {
                throw Error("Can only do simulcast for 'video'")
            }
            if (type != DirectionType.LOCAL) {
                throw Error("Can only do simulcast for local media")
            }
            val rids = simulcast.map { it.rid }
            lines += rids.map { rid -> "a=rid:$rid recv" }
            lines.add("a=simulcast:recv ${rids.joinToString(";")}")
        }

        return lines
    }

    private fun createSctpMediaLines(mid: Mid): List<String> {
        return listOf(
            "m=application 9 UDP/DTLS/SCTP webrtc-datachannel",
            "c=IN IP4 0.0.0.0",
            "a=mid:${mid.mid}",
            "a=sctp-port:5000",
            "a=max-message-size:131072",
        )
    }

    private fun createSessionLines(init: RemoteSessionDescriptionInit, bundle: List<Mid>): List<String> {
        return listOf(
            "v=0",
            "o=- ${state.id} ${state.version++} IN IP4 127.0.0.1",
            "s=-",
            "t=0 0",
            "a=group:BUNDLE ${bundle.joinToString(" ") { it.mid }}",
            "a=ice-ufrag:${init.parameters.iceParameters.usernameFragment}",
            "a=ice-pwd:${init.parameters.iceParameters.password}",
            "a=ice-options:trickle",
            "a=ice-lite",
            "a=fingerprint:sha-256 ${init.parameters.dtlsParameters.fingerprintToString()}",
            "a=setup:passive",
        )
    }
}
