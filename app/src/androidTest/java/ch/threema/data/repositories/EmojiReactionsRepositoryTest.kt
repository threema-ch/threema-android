/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.data.repositories

import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.ThreemaApplication
import ch.threema.data.ModelTypeCache
import ch.threema.data.TestDatabaseService
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.EmojiReactionsRepository.ReactionMessageIdentifier
import ch.threema.data.storage.EmojiReactionsDao
import ch.threema.data.storage.EmojiReactionsDaoImpl
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class EmojiReactionsRepositoryTest {
    private lateinit var testCoreServiceManager: TestCoreServiceManager
    private lateinit var databaseService: TestDatabaseService
    private lateinit var emojiReactionsRepository: EmojiReactionsRepository
    private lateinit var emojiReactionDao: EmojiReactionsDao

    @Before
    fun before() {
        databaseService = TestDatabaseService()
        testCoreServiceManager = TestCoreServiceManager(
            version = ThreemaApplication.getAppVersion(),
            databaseService = databaseService,
            preferenceStore = ThreemaApplication.requireServiceManager().preferenceStore,
            taskManager = TestTaskManager(UnusedTaskCodec())
        )

        emojiReactionsRepository = ModelRepositories(testCoreServiceManager).emojiReaction
        emojiReactionDao = EmojiReactionsDaoImpl(databaseService)
    }

    @Test
    fun testEmojiReactionForeignKeyConstraint() {
        val contactMessage = MessageModel().enrich()

        Assert.assertThrows(EmojiReactionEntryCreateException::class.java) {
            emojiReactionsRepository.createEntry(contactMessage, "ABCDEFGH", "\uD83C\uDFC8")
        }

        databaseService.messageModelFactory.create(contactMessage)

        contactMessage.assertEmojiReactionSize(0)

        contactMessage.body = "reacted"

        emojiReactionsRepository.createEntry(contactMessage, "ABCDEFGH", "⚽")
        databaseService.messageModelFactory.update(contactMessage)

        contactMessage.assertEmojiReactionSize(1)

        databaseService.messageModelFactory.delete(contactMessage)

        contactMessage.assertEmojiReactionSize(0)
    }

    @Test
    fun testGroupEmojiReactionForeignKeyConstraint() {
        val groupMessage = GroupMessageModel().enrich()

        Assert.assertThrows(EmojiReactionEntryCreateException::class.java) {
            emojiReactionsRepository.createEntry(groupMessage, "ABCDEFGH", "⚾")
        }

        databaseService.groupMessageModelFactory.create(groupMessage)

        groupMessage.assertEmojiReactionSize(0)

        groupMessage.body = "Reacted"

        emojiReactionsRepository.createEntry(groupMessage, "ABCDEFGH", "⚽")
        databaseService.groupMessageModelFactory.update(groupMessage)

        groupMessage.assertEmojiReactionSize(1)

        databaseService.groupMessageModelFactory.delete(groupMessage)

        groupMessage.assertEmojiReactionSize(0)
    }

    @Test
    fun testEmojiReactionUniqueness() {
        val message = MessageModel().enrich()
        databaseService.messageModelFactory.create(message)

        message.assertEmojiReactionSize(0)
        message.body = "reacted"

        emojiReactionsRepository.createEntry(message, "ABCDEFGH", "⚽")
        databaseService.messageModelFactory.update(message)

        message.assertEmojiReactionSize(1)

        Assert.assertThrows(EmojiReactionEntryCreateException::class.java) {
            emojiReactionsRepository.createEntry(message, "ABCDEFGH", "⚽")
        }

        message.assertEmojiReactionSize(1)

        val reactions = emojiReactionsRepository.getReactionsByMessage(message)
        Assert.assertNotNull(reactions)

        val reaction = reactions!!.data.value!![0]
        Assert.assertEquals("⚽", reaction.emojiSequence)

        databaseService.messageModelFactory.delete(message)
    }

    @Test
    fun testContactAndGroupReactionsNotMixedUp() {
        val contactMessage = MessageModel().enrich()
        val groupMessage = GroupMessageModel().enrich()

        databaseService.messageModelFactory.create(contactMessage)
        databaseService.groupMessageModelFactory.create(groupMessage)

        Assert.assertEquals(1, contactMessage.id)
        Assert.assertEquals(1, groupMessage.id)

        contactMessage.assertEmojiReactionSize(0)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(contactMessage, "ABCD1234", "⚾")

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(groupMessage, "ABCD1234", "⛵")

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(1)

        val contactReaction =
            emojiReactionsRepository.getReactionsByMessage(contactMessage)!!.data.value!![0]
        val groupReaction =
            emojiReactionsRepository.getReactionsByMessage(groupMessage)!!.data.value!![0]

        Assert.assertEquals("⚾", contactReaction.emojiSequence)
        Assert.assertEquals("⛵", groupReaction.emojiSequence)
    }

    @Test
    fun testContactAndGroupReactionsNotMixedUpWhenRemoved() {
        val reactionSequence = "⛵"

        val contactMessage = MessageModel().enrich()
        val groupMessage = GroupMessageModel().enrich()

        databaseService.messageModelFactory.create(contactMessage)
        databaseService.groupMessageModelFactory.create(groupMessage)

        Assert.assertEquals(1, contactMessage.id)
        Assert.assertEquals(1, groupMessage.id)

        contactMessage.assertEmojiReactionSize(0)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(contactMessage, "ABCD1234", reactionSequence)

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(groupMessage, "ABCD1234", reactionSequence)

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(1)

        emojiReactionsRepository.removeEntry(contactMessage, "ABCD1234", reactionSequence)

        contactMessage.assertEmojiReactionSize(0)
        groupMessage.assertEmojiReactionSize(1)

        emojiReactionsRepository.removeEntry(groupMessage, "ABCD1234", reactionSequence)

        contactMessage.assertEmojiReactionSize(0)
        groupMessage.assertEmojiReactionSize(0)
    }

    @Test
    fun testEmojiReactionsModelCaching() {

        val testEmojiCache = ModelTypeCache<ReactionMessageIdentifier, EmojiReactionsModel>()

        val contactMessage = MessageModel().enrich()
        databaseService.messageModelFactory.create(contactMessage)

        // Test successful creation of reaction-message-identifier
        val reactionMessageIdentifier = ReactionMessageIdentifier.fromMessageModel(contactMessage)
        assertNotNull(reactionMessageIdentifier)

        // Test unsuccessful creation of reaction-message-identifier
        val reactionMessageIdentifierNull = ReactionMessageIdentifier.fromMessageModel(
            messageModel = DistributionListMessageModel()
        )
        assertNull(reactionMessageIdentifierNull)

        // Test reading empty cache
        var cachedEntry: EmojiReactionsModel? = testEmojiCache.get(reactionMessageIdentifier)
        assertNull(cachedEntry)

        // Test reading empty cache but creating entity
        val emojiReactionData = EmojiReactionData(
            contactMessage.id,
            senderIdentity = "ABCD1234",
            emojiSequence = "⛵",
            reactedAt = Date()
        )
        val emojiReactionsModel = EmojiReactionsModel(
            data = listOf(emojiReactionData),
            coreServiceManager = testCoreServiceManager
        )
        cachedEntry = testEmojiCache.getOrCreate(reactionMessageIdentifier) { emojiReactionsModel }
        assertContentEquals(listOf(emojiReactionData), cachedEntry!!.data.value)

        // Test should read the cached value
        testEmojiCache.getOrCreate(reactionMessageIdentifier) {
            fail("Should not call this miss() function")
        }
        assertNotNull(testEmojiCache.get(reactionMessageIdentifier))

        // Test removing from cache
        val removedEmojiReactionsModel = testEmojiCache.remove(reactionMessageIdentifier)
        assertContentEquals(listOf(emojiReactionData), removedEmojiReactionsModel!!.data.value)
        assertNull(testEmojiCache.get(reactionMessageIdentifier))
    }

    @Test
    fun testCacheCollision() {

        // arrange
        val testEmojiCache = ModelTypeCache<ReactionMessageIdentifier, EmojiReactionsModel>()
        val contactMessageId = 1
        val groupMessageId = 1
        val reactionMessageIdentifierContact = ReactionMessageIdentifier(
            messageId = contactMessageId,
            messageType = ReactionMessageIdentifier.TargetMessageType.ONE_TO_ONE
        )
        val reactionMessageIdentifierGroup = ReactionMessageIdentifier(
            messageId = groupMessageId,
            messageType = ReactionMessageIdentifier.TargetMessageType.GROUP
        )
        // Add only the emoji reaction of the 1:1 message to the cache
        val emojiReactionDataForContactMessage = EmojiReactionData(
            messageId = contactMessageId,
            senderIdentity = "ABCD1234",
            emojiSequence = "⛵",
            reactedAt = Date()
        )
        val emojiReactionsModelContact = EmojiReactionsModel(
            data = listOf(emojiReactionDataForContactMessage),
            coreServiceManager = testCoreServiceManager
        )

        val cachedEntryContact =
            testEmojiCache.getOrCreate(reactionMessageIdentifierContact) { emojiReactionsModelContact }

        assertContentEquals(
            listOf(emojiReactionDataForContactMessage),
            cachedEntryContact!!.data.value
        )
        assertNull(testEmojiCache.get(reactionMessageIdentifierGroup))

        testEmojiCache.remove(reactionMessageIdentifierGroup)
        assertNotNull(testEmojiCache.get(reactionMessageIdentifierContact))
    }

    private fun AbstractMessageModel.assertEmojiReactionSize(expectedSize: Int) {
        val actualSize = emojiReactionDao.findAllByMessage(this).size

        Assert.assertEquals(expectedSize, actualSize)
    }

    private fun <T : AbstractMessageModel> T.enrich(text: String = "Text"): T {
        type = MessageType.TEXT
        uid = UUID.randomUUID().toString()
        body = text
        return this
    }
}
