/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.reaction

import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.domain.protocol.csp.messages.ReactionMessageData
import ch.threema.protobuf.csp.e2e.Reaction
import ch.threema.protobuf.csp.e2e.reaction
import ch.threema.testutils.willThrow
import ch.threema.testutils.withMessage
import com.google.protobuf.kotlin.toByteStringUtf8
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupReactionMessageTest {
    private val bytesCreatorIdentity: ByteArray = "00000000".toByteArray()
    private val bytesApiGroupId: ByteArray = "00000000".toByteArray()

    private val dragonEmoji: String = Character.toString(128_009) // 🐉

    private val testMessageId = MessageId()
    private val testReactionMessageApplying: Reaction = reaction {
        this.messageId = testMessageId.messageIdLong
        this.apply = dragonEmoji.toByteStringUtf8()
    }

    @Test
    fun shouldThrowWhenDataIsTooShort() {
        // arrange
        val shortData = byteArrayOf(0, 0, 0, 0)

        // act
        val codeUnderTest = {
            GroupReactionMessage.fromByteArray(
                data = shortData,
                offset = 0,
                length = shortData.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class withMessage Regex("^Bad length")
    }

    @Test
    fun shouldThrowWhenOffsetIsTooSmall() {
        // arrange
        val data =
            bytesCreatorIdentity + bytesApiGroupId + testReactionMessageApplying.toByteArray()

        // act
        val codeUnderTest = {
            GroupReactionMessage.fromByteArray(
                data = data,
                offset = -1,
                length = data.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class withMessage Regex("^Bad offset")
    }

    @Test
    fun shouldThrowWhenDataIsSmallerThanLengthAndOffset() {
        // arrange
        val data =
            bytesCreatorIdentity + bytesApiGroupId + testReactionMessageApplying.toByteArray()

        // act
        val codeUnderTest = {
            GroupReactionMessage.fromByteArray(
                data = data,
                offset = 5,
                length = data.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class withMessage Regex("^Invalid byte array length")
    }

    @Test
    fun shouldThrowWhenCreatorIdentityIsMissing() {
        // arrange
        val data = bytesApiGroupId + testReactionMessageApplying.toByteArray()

        // act
        val codeUnderTest = {
            GroupReactionMessage.fromByteArray(
                data = data,
                offset = 0,
                length = data.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowWhenApiGroupIdIsMissing() {
        // arrange
        val data = bytesCreatorIdentity + testReactionMessageApplying.toByteArray()

        // act
        val codeUnderTest = {
            GroupReactionMessage.fromByteArray(
                data = data,
                offset = 0,
                length = data.size,
            )
        }

        // assert
        codeUnderTest willThrow BadMessageException::class
    }

    @Test
    fun shouldParseSuccessfully() {
        // arrange
        val data =
            bytesCreatorIdentity + bytesApiGroupId + testReactionMessageApplying.toByteArray()

        // act
        val actualGroupReactionMessage = GroupReactionMessage.fromByteArray(
            data = data,
            offset = 0,
            length = data.size,
        )

        // assert
        assertEquals(
            ReactionMessageData.Apply(
                messageId = testMessageId.messageIdLong,
                emojiSequenceBytes = dragonEmoji.toByteStringUtf8(),
            ),
            actualGroupReactionMessage.data,
        )
        assertEquals(
            GroupId(bytesApiGroupId),
            actualGroupReactionMessage.apiGroupId,
        )
        assertEquals(
            String(bytesCreatorIdentity),
            actualGroupReactionMessage.groupCreator,
        )
    }
}
