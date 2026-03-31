package ch.threema.app.voip.groupcall.sfu

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class RtpHeaderExtensionIdsTest {
    @Test
    fun `fallbacks used when no ids in join response present`() {
        val rtpHeaderExtensionIds = RtpHeaderExtensionIds.createFromJoinResponse(
            mockk {
                every { hasRtpHeaderExtensionIds() } returns false
            },
        )

        assertEquals(
            mapOf(
                1u to "urn:ietf:params:rtp-hdrext:sdes:mid",
                4u to "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time",
                5u to "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01",
            ),
            rtpHeaderExtensionIds.microphone,
        )
        assertEquals(
            mapOf(
                1u to "urn:ietf:params:rtp-hdrext:sdes:mid",
                2u to "urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id",
                3u to "urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id",
                4u to "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time",
                5u to "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01",
                11u to "urn:3gpp:video-orientation",
                12u to "urn:ietf:params:rtp-hdrext:toffset",
            ),
            rtpHeaderExtensionIds.cameraAndScreen,
        )
    }

    @Test
    fun `ids are used to create mappings`() {
        val rtpHeaderExtensionIds = RtpHeaderExtensionIds.createFromJoinResponse(
            mockk {
                every { hasRtpHeaderExtensionIds() } returns true
                every { rtpHeaderExtensionIds } returns mockk {
                    every { mid } returns 1001
                    every { rtpStreamId } returns 1002
                    every { repairedRtpStreamId } returns 1003
                    every { absoluteSendTime } returns 1004
                    every { transportWideCongestionControl01 } returns 1005
                    every { videoOrientation } returns 1011
                    every { timeOffset } returns 1012
                }
            },
        )

        assertEquals(
            mapOf(
                1001u to "urn:ietf:params:rtp-hdrext:sdes:mid",
                1004u to "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time",
                1005u to "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01",
            ),
            rtpHeaderExtensionIds.microphone,
        )
        assertEquals(
            mapOf(
                1001u to "urn:ietf:params:rtp-hdrext:sdes:mid",
                1002u to "urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id",
                1003u to "urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id",
                1004u to "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time",
                1005u to "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01",
                1011u to "urn:3gpp:video-orientation",
                1012u to "urn:ietf:params:rtp-hdrext:toffset",
            ),
            rtpHeaderExtensionIds.cameraAndScreen,
        )
    }
}
