package ch.threema.storage.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageTypeTest {
    @Suppress("DEPRECATION")
    @Test
    fun `only text and file message types can be edited`() {
        assertTrue(MessageType.TEXT.canBeEdited)
        assertTrue(MessageType.FILE.canBeEdited)

        assertFalse(MessageType.VIDEO.canBeEdited)
        assertFalse(MessageType.VOICEMESSAGE.canBeEdited)
        assertFalse(MessageType.LOCATION.canBeEdited)
        assertFalse(MessageType.CONTACT.canBeEdited)
        assertFalse(MessageType.STATUS.canBeEdited)
        assertFalse(MessageType.BALLOT.canBeEdited)
        assertFalse(MessageType.VOIP_STATUS.canBeEdited)
        assertFalse(MessageType.DATE_SEPARATOR.canBeEdited)
        assertFalse(MessageType.GROUP_CALL_STATUS.canBeEdited)
        assertFalse(MessageType.FORWARD_SECURITY_STATUS.canBeEdited)
        assertFalse(MessageType.GROUP_STATUS.canBeEdited)
    }
}
