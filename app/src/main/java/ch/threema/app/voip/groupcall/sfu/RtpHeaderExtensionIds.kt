package ch.threema.app.voip.groupcall.sfu

import ch.threema.protobuf.groupcall.SfuHttpResponse

data class RtpHeaderExtensionIds(
    val microphone: Map<UInt, String>,
    val cameraAndScreen: Map<UInt, String>,
) {
    companion object {
        fun createFromJoinResponse(response: SfuHttpResponse.Join): RtpHeaderExtensionIds {
            if (!response.hasRtpHeaderExtensionIds()) {
                return RtpHeaderExtensionIds(
                    microphone = FALLBACK_MICROPHONE,
                    cameraAndScreen = FALLBACK_CAMERA_AND_SCREEN,
                )
            }

            return with(response.rtpHeaderExtensionIds) {
                RtpHeaderExtensionIds(
                    microphone = mapOf(
                        mid.toUInt() to MID,
                        absoluteSendTime.toUInt() to ABSOLUTE_SEND_TIME,
                        transportWideCongestionControl01.toUInt() to TRANSPORT_WIDE_CONGESTION_CONTROL_01,
                    ),
                    cameraAndScreen = mapOf(
                        mid.toUInt() to MID,
                        rtpStreamId.toUInt() to RTP_STREAM_ID,
                        repairedRtpStreamId.toUInt() to REPAIRED_RTP_STREAM_ID,
                        absoluteSendTime.toUInt() to ABSOLUTE_SEND_TIME,
                        transportWideCongestionControl01.toUInt() to TRANSPORT_WIDE_CONGESTION_CONTROL_01,
                        videoOrientation.toUInt() to VIDEO_ORIENTATION,
                        timeOffset.toUInt() to TIME_OFFSET,
                    ),
                )
            }
        }

        private const val MID = "urn:ietf:params:rtp-hdrext:sdes:mid"
        private const val RTP_STREAM_ID = "urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id"
        private const val REPAIRED_RTP_STREAM_ID = "urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id"
        private const val ABSOLUTE_SEND_TIME = "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time"
        private const val TRANSPORT_WIDE_CONGESTION_CONTROL_01 = "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01"
        private const val VIDEO_ORIENTATION = "urn:3gpp:video-orientation"
        private const val TIME_OFFSET = "urn:ietf:params:rtp-hdrext:toffset"

        private val FALLBACK_MICROPHONE = mapOf(
            1u to MID,
            4u to ABSOLUTE_SEND_TIME,
            5u to TRANSPORT_WIDE_CONGESTION_CONTROL_01,
        )

        private val FALLBACK_CAMERA_AND_SCREEN = mapOf(
            1u to MID,
            2u to RTP_STREAM_ID,
            3u to REPAIRED_RTP_STREAM_ID,
            4u to ABSOLUTE_SEND_TIME,
            5u to TRANSPORT_WIDE_CONGESTION_CONTROL_01,
            11u to VIDEO_ORIENTATION,
            12u to TIME_OFFSET,
        )
    }
}
