/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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
