package ch.threema.app.voip.groupcall.sfu.messages

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class P2PMessagesTest {

    @Test
    internal fun `screen sharing must be active if startedAt is provided`() {
        val screenShareCaptureState = P2PMessageContent.CaptureState.Screen(Date())
        assertTrue(screenShareCaptureState.active)
    }

    @Test
    internal fun `screen sharing must be inactive if startedAt is not provided`() {
        val screenShareCaptureState = P2PMessageContent.CaptureState.Screen(null)
        assertFalse(screenShareCaptureState.active)
    }

    @Test
    internal fun `screen share convenience factory methods yield correct state`() {
        val screenShareOn = P2PMessageContent.CaptureState.Screen.on(Date())
        assertTrue(screenShareOn.active)

        val screenShareOff = P2PMessageContent.CaptureState.Screen.off()
        assertFalse(screenShareOff.active)
    }
}
