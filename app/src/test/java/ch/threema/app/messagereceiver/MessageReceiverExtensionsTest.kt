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

package ch.threema.app.messagereceiver

import ch.threema.storage.models.ContactModel
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageReceiverExtensionsTest {
    @Test
    fun `direct chat with gateway contact is considered a gateway chat`() {
        val messageReceiverMock = mockk<ContactMessageReceiver>()
        val contactModel = ContactModel.create("*TESTING", MOCK_PUBLIC_KEY)
        every { messageReceiverMock.contact } returns contactModel

        assertTrue(messageReceiverMock.isGatewayChat())
    }

    @Test
    fun `direct chat with non-gateway contact is not considered a gateway chat`() {
        val messageReceiverMock = mockk<ContactMessageReceiver>()
        val contactModel = ContactModel.create("TESTUSER", MOCK_PUBLIC_KEY)
        every { messageReceiverMock.contact } returns contactModel

        assertFalse(messageReceiverMock.isGatewayChat())
    }

    @Test
    fun `group chat is not considered a gateway chat`() {
        val messageReceiverMock = mockk<GroupMessageReceiver>()

        assertFalse(messageReceiverMock.isGatewayChat())
    }

    @Test
    fun `distribution list chat is not considered a gateway chat`() {
        val messageReceiverMock = mockk<DistributionListMessageReceiver>()

        assertFalse(messageReceiverMock.isGatewayChat())
    }

    companion object {
        private val MOCK_PUBLIC_KEY = ByteArray(32)
    }
}
