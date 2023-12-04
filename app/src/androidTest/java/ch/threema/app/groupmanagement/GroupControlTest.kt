/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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

package ch.threema.app.groupmanagement

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.rule.GrantPermissionRule
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.HomeActivity
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.FileService
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceStoreInterface
import ch.threema.domain.helpers.InMemoryContactStore
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.connection.MessageQueue
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.GroupCreateMessage
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.models.GroupMemberModel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Field

/**
 * A collection of basic data and utility functions to test group control messages. If the common
 * group receive steps should not be executed for a certain message type, the common group step
 * receive methods should be overridden.
 */
abstract class GroupControlTest<T : AbstractGroupMessage> {

    protected val myContact: TestContact = TestHelpers.TEST_CONTACT
    protected val contactA = TestContact("12345678")
    protected val contactB = TestContact("ABCDEFGH")
    protected val contactC = TestContact("SX96PM5A")

    protected val myGroup = TestGroup(GroupId(0), myContact, listOf(myContact, contactA), "MyGroup")
    protected val myGroupWithProfilePicture = TestGroup(GroupId(1), myContact, listOf(myContact, contactA), "MyGroupWithPicture", byteArrayOf(0, 1, 2, 3))
    protected val groupA = TestGroup(GroupId(2), contactA, listOf(myContact, contactA), "GroupA")
    protected val groupB = TestGroup(GroupId(3), contactB, listOf(myContact, contactB), "GroupB")
    protected val groupAB = TestGroup(GroupId(4), contactA, listOf(myContact, contactA, contactB), "GroupAB")
    protected val groupAUnknown = TestGroup(GroupId(5), contactA, listOf(myContact, contactA, contactB), "GroupAUnknown")
    protected val groupALeft = TestGroup(GroupId(6), contactA, listOf(contactA, contactB), "GroupALeft")
    protected val myUnknownGroup = TestGroup(GroupId(7), myContact, listOf(myContact, contactA), "MyUnknownGroup")
    protected val myLeftGroup = TestGroup(GroupId(8), myContact, listOf(contactA), "MyLeftGroup")
    protected val newAGroup = TestGroup(GroupId(9), contactA, listOf(myContact, contactA, contactB), "NewAGroup")

    protected val serviceManager: ServiceManager = ThreemaApplication.requireServiceManager()
    private val contactStore: ContactStore = InMemoryContactStore().apply {
        addContact(myContact.contact, true)
        addContact(contactA.contact, true)
        addContact(contactB.contact, true)
        addContact(contactC.contact, true)
    }

    private val mutableSentMessages: MutableList<AbstractMessage> = mutableListOf()
    protected val sentMessages: List<AbstractMessage> = mutableSentMessages

    protected val initialContacts = listOf(myContact, contactA, contactB, contactC)

    protected val initialGroups =
        listOf(myGroup, myGroupWithProfilePicture, groupA, groupB, groupAB, groupALeft, myLeftGroup)

    @JvmField
    @Rule
    val grantPermissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    /**
     * Asserts that the correct identity is set up and fills the database with the initial data.
     */
    @Before
    fun setup() {
        assert(myContact.identity == TestHelpers.ensureIdentity(ThreemaApplication.requireServiceManager()))

        setMessageQueue()

        serviceManager.connection.stop()

        cleanup()

        fillDatabase()
    }

    /**
     * Clean the data after the tests. This includes the deletion of the database entries, the
     * avatar files, and the blocked contacts.
     */
    @After
    fun cleanup() {
        // Clear conversations
        serviceManager.conversationService.getAll(true).forEach {
            serviceManager.conversationService.clear(it)
        }

        // Delete database
        serviceManager.databaseServiceNew.apply {
            contactModelFactory.deleteAll()
            messageModelFactory.deleteAll()
            groupCallModelFactory.deleteAll()
            groupInviteModelFactory.deleteAll()
            groupBallotModelFactory.deleteAll()
            groupMessagePendingMessageIdModelFactory.deleteAll()
            groupMemberModelFactory.deleteAll()
            groupMessageModelFactory.deleteAll()
            // Remove group models from group service to empty the group service cache
            serviceManager.groupService.removeAll()
            distributionListModelFactory.deleteAll()
            distributionListMemberModelFactory.deleteAll()
            distributionListMessageModelFactory.deleteAll()
            groupRequestSyncLogModelFactory.deleteAll()
            ballotModelFactory.deleteAll()
            ballotChoiceModelFactory.deleteAll()
            ballotVoteModelFactory.deleteAll()
            identityBallotModelFactory.deleteAll()
            webClientSessionModelFactory.deleteAll()
            conversationTagFactory.deleteAll()
            outgoingGroupJoinRequestModelFactory.deleteAll()
            incomingGroupJoinRequestModelFactory.deleteAll()
            serverMessageModelFactory.deleteAll()
        }

        // Remove files
        serviceManager.fileService.removeAllAvatars()

        // Unblock contacts
        serviceManager.blackListService.removeAll()
    }

    /**
     * Fills basic data into the database. This is executed before each test. Override this if other
     * database entries are needed.
     */
    open fun fillDatabase() {
        val databaseService = serviceManager.databaseServiceNew
        val fileService = serviceManager.fileService

        initialContacts.forEach { addContactToDatabase(it, databaseService, true) }

        initialGroups.forEach { addGroupToDatabase(it, databaseService, fileService) }
    }

    /**
     * Create a message of the tested group message type. This is used to create a message that will
     * be used to test the common group receive steps.
     */
    abstract fun createMessageForGroup(): T

    protected fun startScenario(): ActivityScenario<HomeActivity> {
        Intents.init()

        val scenario = launchActivity<HomeActivity>()

        Thread.sleep(200)

        do {
            var switchedToMessages = false
            try {
                Espresso.onView(ViewMatchers.withId(R.id.messages)).perform(ViewActions.click())
                switchedToMessages = true
            } catch (exception: NoMatchingViewException) {
                Espresso.onView(ViewMatchers.withId(R.id.close_button)).perform(ViewActions.click())
            }
        } while (!switchedToMessages)

        Intents.release()

        Thread.sleep(200)

        return scenario
    }

    /**
     * Send a message from a user with the provided identity store.
     */
    protected fun processMessage(message: AbstractMessage, identityStore: IdentityStoreInterface) {
        val messageBox = createMessageBox(message, identityStore)

        // Process the group message
        val processingResult = serviceManager.messageProcessor.processIncomingMessage(messageBox)
        assertTrue(processingResult.wasProcessed())

        // Give the listeners enough time to fire the event
        Thread.sleep(200)
    }

    /**
     * Check step 2.1 of the common group receive steps: The group could not be found and the user
     * is the creator of the group (as alleged by the message). The message should be discarded.
     */
    @Test
    open fun testCommonGroupReceiveStep2_1() {
        val (message, identityStore) = getMyUnknownGroupMessage()
        setupAndProcessMessage(message, identityStore)

        // Nothing is expected to be sent
        assertEquals(0, sentMessages.size)
    }

    /**
     * Check step 2.2 of the common group receive steps: The group could not be found and the user
     * is not the creator. In this case, a group sync request should be sent.
     */
    @Test
    open fun testCommonGroupReceiveStep2_2() {
        val (message, identityStore) = getUnknownGroupMessage()
        setupAndProcessMessage(message, identityStore)

        assertEquals(1, sentMessages.size)
        val firstMessage = sentMessages.first() as GroupRequestSyncMessage
        assertEquals(message.groupCreator, firstMessage.toIdentity)
        assertEquals(message.toIdentity, firstMessage.fromIdentity)
        assertEquals(message.apiGroupId, firstMessage.apiGroupId)
        assertEquals(message.groupCreator, firstMessage.groupCreator)
    }

    /**
     * Check step 3.1 of the common group receive steps: The group is marked as left and the user is
     * the creator of the group. In this case, a group setup with an empty member list should be
     * sent back to the sender.
     */
    @Test
    open fun testCommonGroupReceiveStep3_1() {
        val (message, identityStore) = getMyLeftGroupMessage()
        setupAndProcessMessage(message, identityStore)

        // Check that empty sync is sent.
        assertEquals(1, sentMessages.size)
        val firstMessage = sentMessages.first() as GroupCreateMessage
        assertEquals(message.fromIdentity, firstMessage.toIdentity)
        assertEquals(myContact.identity, firstMessage.fromIdentity)
        assertEquals(message.apiGroupId, firstMessage.apiGroupId)
        assertEquals(message.groupCreator, firstMessage.groupCreator)
        assertArrayEquals(emptyArray<String>(), firstMessage.members)
    }

    /**
     * Check step 3.2 of the common group receive steps: The group is marked left and the user is
     * not the creator of the group. In this case, a group leave should be sent back to the sender.
     */
    @Test
    open fun testCommonGroupReceiveStep3_2() {
        // First, test the common group receive steps for a message from the group creator
        val (firstIncomingMessage, firstIdentityStore) = getLeftGroupMessageFromCreator()
        setupAndProcessMessage(firstIncomingMessage, firstIdentityStore)

        // Check that a group leave is sent back to the sender
        assertEquals(1, sentMessages.size)
        val firstSentMessage = sentMessages.first() as GroupLeaveMessage
        assertEquals(firstIncomingMessage.fromIdentity, firstSentMessage.toIdentity)
        assertEquals(myContact.identity, firstSentMessage.fromIdentity)
        assertEquals(firstIncomingMessage.apiGroupId, firstSentMessage.apiGroupId)
        assertEquals(firstIncomingMessage.groupCreator, firstSentMessage.groupCreator)

        // Second, test the common group receive steps for a message from a group member
        val (secondIncomingMessage, secondIdentityStore) = getLeftGroupMessage()
        setupAndProcessMessage(secondIncomingMessage, secondIdentityStore)

        // Check that a group leave is sent back to the sender
        assertEquals(2, sentMessages.size)
        val secondSentMessage = sentMessages[1] as GroupLeaveMessage
        assertEquals(secondIncomingMessage.fromIdentity, secondSentMessage.toIdentity)
        assertEquals(myContact.identity, secondSentMessage.fromIdentity)
        assertEquals(secondIncomingMessage.apiGroupId, secondSentMessage.apiGroupId)
        assertEquals(secondIncomingMessage.groupCreator, secondSentMessage.groupCreator)
    }

    /**
     * Check step 4.1 of the common group receive steps: The sender is not a member of the group and
     * the user is the creator of the group. In this case, a group setup with an empty members list
     * should be sent back to the sender.
     */
    @Test
    open fun testCommonGroupReceiveStep4_1() {
        val (message, identityStore) = getSenderNotMemberOfMyGroupMessage()
        setupAndProcessMessage(message, identityStore)

        // Check that a group setup with empty member list is sent back to the sender
        assertEquals(1, sentMessages.size)
        val firstMessage = sentMessages.first() as GroupCreateMessage
        assertEquals(message.fromIdentity, firstMessage.toIdentity)
        assertEquals(myContact.identity, firstMessage.fromIdentity)
        assertEquals(message.apiGroupId, firstMessage.apiGroupId)
        assertEquals(message.groupCreator, firstMessage.groupCreator)
        assertArrayEquals(emptyArray<String>(), firstMessage.members)
    }

    /**
     * Check step 4.2 of the common group receive steps: The sender is not a member of the group and
     * the user is not the creator of the group. The message should be discarded.
     */
    @Test
    open fun testCommonGroupReceiveStep4_2() {
        val (message, identityStore) = getSenderNotMemberMessage()
        setupAndProcessMessage(message, identityStore)

        // Check that no message is sent
        assertEquals(0, sentMessages.size)
    }

    private fun addContactToDatabase(
        testContact: TestContact,
        databaseService: DatabaseServiceNew,
        addHidden: Boolean = false,
    ) {
        databaseService.contactModelFactory.createOrUpdate(
            testContact.contactModel.setIsHidden(addHidden)
        )
    }

    private fun addGroupToDatabase(
        testGroup: TestGroup,
        databaseService: DatabaseServiceNew,
        fileService: FileService,
    ) {
        val groupModel = testGroup.groupModel
        databaseService.groupModelFactory.createOrUpdate(groupModel)
        testGroup.setLocalGroupId(groupModel.id)
        testGroup.members.forEach { member ->
            val memberModel = GroupMemberModel()
                .setGroupId(groupModel.id)
                .setIdentity(member.identity)
            databaseService.groupMemberModelFactory.createOrUpdate(memberModel)
        }
        if (testGroup.profilePicture != null) {
            fileService.writeGroupAvatar(groupModel, testGroup.profilePicture)
        }
    }

    private fun setupAndProcessMessage(
        message: AbstractGroupMessage,
        identityStore: IdentityStoreInterface
    ) {
        // Start home activity and navigate to chat section
        launchActivity<HomeActivity>()

        Espresso.onView(ViewMatchers.withId(R.id.messages)).perform(ViewActions.click())

        processMessage(message, identityStore)
    }

    /**
     * Get a group message where the user is the creator (as alleged by the received message).
     * Common Group Receive Step 2.1
     */
    private fun getMyUnknownGroupMessage() = createMessageForGroup().apply {
        enrich(myUnknownGroup)
    } to myUnknownGroup.groupCreator.identityStore

    /**
     * Get a group message in an unknown group.
     * Common Group Receive Step 2.2
     */
    private fun getUnknownGroupMessage() = createMessageForGroup().apply {
        enrich(groupAUnknown)
    } to groupAUnknown.groupCreator.identityStore

    /**
     * Get a group message that is marked 'left' where the user is the creator.
     * Common Group Receive Step 3.1
     */
    private fun getMyLeftGroupMessage() = createMessageForGroup().apply {
        enrich(myLeftGroup)
        fromIdentity = contactA.identity
    } to contactA.identityStore

    /**
     * Get a group message that is marked 'left' from a group member.
     * Common Group Receive Step 3.2
     */
    private fun getLeftGroupMessage() = createMessageForGroup().apply {
        enrich(groupALeft)
    } to groupALeft.groupCreator.identityStore

    /**
     * Get a group message that is marked 'left' from the group creator.
     * Common Group Receive Step 3.2
     */
    private fun getLeftGroupMessageFromCreator() = createMessageForGroup().apply {
        enrich(groupALeft)
    } to groupALeft.groupCreator.identityStore

    /**
     * Get a group message from a sender that is no member of the group where the user is the
     * creator.
     * Common Group Receive Step 4.1
     */
    private fun getSenderNotMemberOfMyGroupMessage() = createMessageForGroup().apply {
        enrich(myGroup)
        fromIdentity = contactB.identity
    } to contactB.identityStore

    /**
     * Get a group message from a sender that is no member of the group.
     * Common Group Receive Step 4.2
     */
    private fun getSenderNotMemberMessage() = createMessageForGroup().apply {
        enrich(groupA)
        fromIdentity = contactB.identity
    } to contactB.identityStore

    private fun AbstractGroupMessage.enrich(group: TestGroup) {
        apiGroupId = group.apiGroupId
        groupCreator = group.groupCreator.identity
        fromIdentity = group.groupCreator.identity
        toIdentity = myContact.identity
    }

    /**
     * Create a message box from a user with the given identity store.
     */
    private fun createMessageBox(
        msg: AbstractMessage,
        identityStore: IdentityStoreInterface,
    ): MessageBox {
        val nonceFactory = NonceFactory(object : NonceStoreInterface {
            override fun exists(nonce: ByteArray) = false
            override fun store(nonce: ByteArray) = true
        })

        val messageCoder = MessageCoder(contactStore, identityStore)
        return messageCoder.encode(msg, nonceFactory)
    }

    private fun setMessageQueue() {
        val messageQueue = object :
            MessageQueue(contactStore, myContact.identityStore, serviceManager.connection) {
            override fun enqueue(message: AbstractMessage): MessageBox {
                mutableSentMessages.add(message)
                return MessageBox()
            }
        }

        replaceMessageQueue(messageQueue, serviceManager)
        replaceMessageQueue(messageQueue, serviceManager.contactService)
        replaceMessageQueue(messageQueue, serviceManager.messageService)
        replaceMessageQueue(messageQueue, serviceManager.groupJoinResponseService)
        replaceMessageQueue(messageQueue, serviceManager.outgoingGroupJoinRequestService)
        replaceMessageQueue(messageQueue, serviceManager.groupMessagingService)
        serviceManager.forwardSecurityMessageProcessor?.let {
            replaceMessageQueue(messageQueue, it)
        }
    }

    private fun replaceMessageQueue(messageQueue: MessageQueue, c: Any) {
        val messageQueueField: Field = c.javaClass.getDeclaredField("messageQueue")
        messageQueueField.isAccessible = true
        messageQueueField.set(c, messageQueue)
    }

    protected fun <T> Iterable<T>.replace(original: T, new: T) =
        map { if (it == original) new else it }
}
