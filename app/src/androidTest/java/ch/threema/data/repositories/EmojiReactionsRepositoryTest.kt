package ch.threema.data.repositories

import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.mockUser
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.EmojiReactionsRepository.ReactionMessageIdentifier
import ch.threema.data.storage.EmojiReactionsDao
import ch.threema.data.storage.EmojiReactionsDaoImpl
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import ch.threema.storage.TestDatabaseProvider
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.group.GroupMessageModel
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class EmojiReactionsRepositoryTest {
    private lateinit var testCoreServiceManager: TestCoreServiceManager
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var emojiReactionsRepository: EmojiReactionsRepository
    private lateinit var emojiReactionDao: EmojiReactionsDao

    @BeforeTest
    fun before() {
        databaseProvider = TestDatabaseProvider()
        val identityProviderMock: IdentityProvider = mockk {
            every { getIdentity() } returns Identity(TestHelpers.TEST_CONTACT.identity)
            every { getIdentityString() } returns TestHelpers.TEST_CONTACT.identity
        }
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns Identity(TestHelpers.TEST_CONTACT.identity)
            every { getIdentityString() } returns TestHelpers.TEST_CONTACT.identity
        }
        testCoreServiceManager = TestCoreServiceManager(
            databaseProvider = databaseProvider,
            identityProvider = identityProviderMock,
            preferenceStore = mockk {
                mockUser(TestHelpers.TEST_CONTACT)
            },
            encryptedPreferenceStore = mockk {
                mockUser(TestHelpers.TEST_CONTACT)
            },
            taskManager = TestTaskManager(UnusedTaskCodec()),
            identityStore = identityStoreMock,
        )

        emojiReactionsRepository = ModelRepositories(testCoreServiceManager, mockk()).emojiReaction
        emojiReactionDao = EmojiReactionsDaoImpl(databaseProvider)
    }

    @Test
    fun testEmojiReactionForeignKeyConstraint() {
        val contactMessage = MessageModel().enrich()

        assertFailsWith<EmojiReactionEntryCreateException> {
            emojiReactionsRepository.createEntry(contactMessage, "ABCDEFGH", "\uD83C\uDFC8")
        }

        testCoreServiceManager.databaseService.messageModelFactory.create(contactMessage)

        contactMessage.assertEmojiReactionSize(0)

        contactMessage.body = "reacted"

        emojiReactionsRepository.createEntry(contactMessage, "ABCDEFGH", "⚽")
        testCoreServiceManager.databaseService.messageModelFactory.update(contactMessage)

        contactMessage.assertEmojiReactionSize(1)

        testCoreServiceManager.databaseService.messageModelFactory.delete(contactMessage)

        contactMessage.assertEmojiReactionSize(0)
    }

    @Test
    fun testGroupEmojiReactionForeignKeyConstraint() {
        val groupMessage = GroupMessageModel().enrich()

        assertFailsWith<EmojiReactionEntryCreateException> {
            emojiReactionsRepository.createEntry(groupMessage, "ABCDEFGH", "⚾")
        }

        testCoreServiceManager.databaseService.groupMessageModelFactory.create(groupMessage)

        groupMessage.assertEmojiReactionSize(0)

        groupMessage.body = "Reacted"

        emojiReactionsRepository.createEntry(groupMessage, "ABCDEFGH", "⚽")
        testCoreServiceManager.databaseService.groupMessageModelFactory.update(groupMessage)

        groupMessage.assertEmojiReactionSize(1)

        testCoreServiceManager.databaseService.groupMessageModelFactory.delete(groupMessage)

        groupMessage.assertEmojiReactionSize(0)
    }

    @Test
    fun testEmojiReactionUniqueness() {
        val message = MessageModel().enrich()
        testCoreServiceManager.databaseService.messageModelFactory.create(message)

        message.assertEmojiReactionSize(0)
        message.body = "reacted"

        emojiReactionsRepository.createEntry(message, "ABCDEFGH", "⚽")
        testCoreServiceManager.databaseService.messageModelFactory.update(message)

        message.assertEmojiReactionSize(1)

        assertFailsWith<EmojiReactionEntryCreateException> {
            emojiReactionsRepository.createEntry(message, "ABCDEFGH", "⚽")
        }

        message.assertEmojiReactionSize(1)

        val reactions = emojiReactionsRepository.getReactionsByMessage(message)
        assertNotNull(reactions)

        val reaction = reactions.data!![0]
        assertEquals("⚽", reaction.emojiSequence)

        testCoreServiceManager.databaseService.messageModelFactory.delete(message)
    }

    @Test
    fun testContactAndGroupReactionsNotMixedUp() {
        val contactMessage = MessageModel().enrich()
        val groupMessage = GroupMessageModel().enrich()

        testCoreServiceManager.databaseService.messageModelFactory.create(contactMessage)
        testCoreServiceManager.databaseService.groupMessageModelFactory.create(groupMessage)

        assertEquals(1, contactMessage.id)
        assertEquals(1, groupMessage.id)

        contactMessage.assertEmojiReactionSize(0)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(contactMessage, "ABCD1234", "⚾")

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(groupMessage, "ABCD1234", "⛵")

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(1)

        val contactReaction =
            emojiReactionsRepository.getReactionsByMessage(contactMessage)!!.data!![0]
        val groupReaction =
            emojiReactionsRepository.getReactionsByMessage(groupMessage)!!.data!![0]

        assertEquals("⚾", contactReaction.emojiSequence)
        assertEquals("⛵", groupReaction.emojiSequence)
    }

    @Test
    fun testContactAndGroupReactionsNotMixedUpWhenRemoved() {
        val reactionSequence = "⛵"

        val contactMessage = MessageModel().enrich()
        val groupMessage = GroupMessageModel().enrich()

        testCoreServiceManager.databaseService.messageModelFactory.create(contactMessage)
        testCoreServiceManager.databaseService.groupMessageModelFactory.create(groupMessage)

        assertEquals(1, contactMessage.id)
        assertEquals(1, groupMessage.id)

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
        testCoreServiceManager.databaseService.messageModelFactory.create(contactMessage)

        // Test successful creation of reaction-message-identifier
        val reactionMessageIdentifier = ReactionMessageIdentifier.fromMessageModel(contactMessage)
        assertNotNull(reactionMessageIdentifier)

        // Test unsuccessful creation of reaction-message-identifier
        val reactionMessageIdentifierNull = ReactionMessageIdentifier.fromMessageModel(
            messageModel = DistributionListMessageModel(),
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
            reactedAt = Instant.now(),
        )
        val emojiReactionsModel = EmojiReactionsModel(
            data = listOf(emojiReactionData),
            coreServiceManager = testCoreServiceManager,
        )
        cachedEntry = testEmojiCache.getOrCreate(reactionMessageIdentifier) { emojiReactionsModel }
        assertContentEquals(listOf(emojiReactionData), cachedEntry!!.data)

        // Test should read the cached value
        testEmojiCache.getOrCreate(reactionMessageIdentifier) {
            fail("Should not call this miss() function")
        }
        assertNotNull(testEmojiCache.get(reactionMessageIdentifier))

        // Test removing from cache
        val removedEmojiReactionsModel = testEmojiCache.remove(reactionMessageIdentifier)
        assertContentEquals(listOf(emojiReactionData), removedEmojiReactionsModel!!.data)
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
            messageType = ReactionMessageIdentifier.TargetMessageType.ONE_TO_ONE,
        )
        val reactionMessageIdentifierGroup = ReactionMessageIdentifier(
            messageId = groupMessageId,
            messageType = ReactionMessageIdentifier.TargetMessageType.GROUP,
        )
        // Add only the emoji reaction of the 1:1 message to the cache
        val emojiReactionDataForContactMessage = EmojiReactionData(
            messageId = contactMessageId,
            senderIdentity = "ABCD1234",
            emojiSequence = "⛵",
            reactedAt = Instant.now(),
        )
        val emojiReactionsModelContact = EmojiReactionsModel(
            data = listOf(emojiReactionDataForContactMessage),
            coreServiceManager = testCoreServiceManager,
        )

        val cachedEntryContact =
            testEmojiCache.getOrCreate(reactionMessageIdentifierContact) { emojiReactionsModelContact }

        assertContentEquals(
            listOf(emojiReactionDataForContactMessage),
            cachedEntryContact!!.data,
        )
        assertNull(testEmojiCache.get(reactionMessageIdentifierGroup))

        testEmojiCache.remove(reactionMessageIdentifierGroup)
        assertNotNull(testEmojiCache.get(reactionMessageIdentifierContact))
    }

    private fun AbstractMessageModel.assertEmojiReactionSize(expectedSize: Int) {
        val actualSize = emojiReactionDao.findAllByMessage(this).size

        assertEquals(expectedSize, actualSize)
    }

    private fun <T : AbstractMessageModel> T.enrich(text: String = "Text"): T {
        type = MessageType.TEXT
        uid = UUID.randomUUID().toString()
        body = text
        return this
    }
}
