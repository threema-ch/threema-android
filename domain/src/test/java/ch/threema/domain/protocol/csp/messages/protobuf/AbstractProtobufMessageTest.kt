/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.protobuf

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals

class AbstractProtobufMessageTest {
    @Test
    fun testGetBody() {
        val bytes = byteArrayOf(0, 1, 2)
        val protobufDataStub = mockk<ProtobufDataInterface<*>> {
            every { toProtobufBytes() } returns bytes
        }
        val message = object : AbstractProtobufMessage<ProtobufDataInterface<*>>(3, protobufDataStub) {
            override fun getType() = 0

            override fun getMinimumRequiredForwardSecurityVersion() = null

            override fun allowUserProfileDistribution() = false

            override fun exemptFromBlocking() = false

            override fun createImplicitlyDirectContact() = false

            override fun protectAgainstReplay() = false

            override fun reflectIncoming() = false

            override fun reflectOutgoing() = false

            override fun reflectSentUpdate() = false

            override fun sendAutomaticDeliveryReceipt() = false

            override fun bumpLastUpdate() = false
        }

        assertContentEquals(bytes, message.body)
    }
}
