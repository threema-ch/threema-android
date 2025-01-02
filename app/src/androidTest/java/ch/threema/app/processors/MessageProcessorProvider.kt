/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.processors

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import ch.threema.app.TestCoreServiceManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManagerImpl
import ch.threema.app.services.FileService
import ch.threema.app.services.LifetimeService
import ch.threema.app.tasks.TaskArchiverImpl
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ForwardSecurityStatusSender
import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.crypto.NonceStore
import ch.threema.domain.fs.DHSession
import ch.threema.domain.helpers.DecryptTaskCodec
import ch.threema.domain.helpers.InMemoryContactStore
import ch.threema.domain.helpers.InMemoryDHSessionStore
import ch.threema.domain.helpers.InMemoryNonceStore
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.Contact
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataInit
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.QueueSendCompleteListener
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.toCspMessage
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.GroupMemberModel
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.Timeout
import java.io.File
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

open class MessageProcessorProvider {

    protected val myContact: TestContact = TestHelpers.TEST_CONTACT
    protected val contactA = TestContact("12345678")
    protected val contactB = TestContact("ABCDEFGH")
    protected val contactC = TestContact("TESTTEST")

    protected val myGroup = TestGroup(GroupId(0), myContact, listOf(myContact, contactA, contactB), "MyGroup", myContact.identity)
    protected val myGroupWithProfilePicture =
        TestGroup(
            GroupId(1),
            myContact,
            listOf(myContact, contactA),
            "MyGroupWithPicture",
            byteArrayOf(0, 1, 2, 3),
            myContact.identity
        )
    protected val groupA =
        TestGroup(GroupId(2), contactA, listOf(myContact, contactA), "GroupA", myContact.identity)
    protected val groupB =
        TestGroup(GroupId(3), contactB, listOf(myContact, contactB), "GroupB", myContact.identity)
    protected val groupAB =
        TestGroup(GroupId(4), contactA, listOf(myContact, contactA, contactB), "GroupAB", myContact.identity)
    protected val groupAUnknown =
        TestGroup(GroupId(5), contactA, listOf(myContact, contactA, contactB), "GroupAUnknown", myContact.identity)
    protected val groupALeft =
        TestGroup(GroupId(6), contactA, listOf(contactA, contactB), "GroupALeft", myContact.identity)
    protected val myUnknownGroup =
        TestGroup(GroupId(7), myContact, listOf(myContact, contactA), "MyUnknownGroup", myContact.identity)
    protected val myLeftGroup =
        TestGroup(GroupId(8), myContact, listOf(contactA), "MyLeftGroup", myContact.identity)
    protected val newAGroup =
        TestGroup(GroupId(9), contactA, listOf(myContact, contactA, contactB), "NewAGroup", myContact.identity)

    protected val serviceManager: ServiceManager = ThreemaApplication.requireServiceManager()
    private val contactStore: ContactStore = InMemoryContactStore().apply {
        addContact(myContact.contact)
        addContact(contactA.contact)
        addContact(contactB.contact)
        addContact(contactC.contact)
    }

    private val identityMap = listOf(
        myContact.identity to myContact.identityStore,
        contactA.identity to contactA.identityStore,
        contactB.identity to contactB.identityStore,
        contactC.identity to contactC.identityStore,
    ).toMap()

    private val forwardSecurityStatusListener = object : ForwardSecurityStatusSender(
        serviceManager.contactService,
        serviceManager.messageService,
        APIConnector(
            false,
            null,
            false
        ) { host -> ConfigUtils.getSSLSocketFactory(host) },
        serviceManager.userService,
        serviceManager.modelRepositories.contacts,
    ) {
        override fun messageWithoutFSReceived(
            contact: Contact,
            session: DHSession,
            message: AbstractMessage,
        ) {
            throw AssertionError("We do not accept messages without forward security")
        }
    }

    private val forwardSecurityMessageProcessorMap = listOf(
        myContact.identity to serviceManager.forwardSecurityMessageProcessor,
        contactA.identity to ForwardSecurityMessageProcessor(
            InMemoryDHSessionStore(),
            contactStore,
            contactA.identityStore,
            NonceFactory(InMemoryNonceStore()),
            forwardSecurityStatusListener
        ),
        contactB.identity to ForwardSecurityMessageProcessor(
            InMemoryDHSessionStore(),
            contactStore,
            contactB.identityStore, NonceFactory(InMemoryNonceStore()),
            forwardSecurityStatusListener
        ),
        contactC.identity to ForwardSecurityMessageProcessor(
            InMemoryDHSessionStore(),
            contactStore,
            contactC.identityStore,
            NonceFactory(InMemoryNonceStore()),
            forwardSecurityStatusListener
        ),
    ).toMap()

    /**
     * Do not use this field in tests! This is only to restore the original task manager in the
     * service manager after the test.
     */
    private lateinit var originalTaskManager: TaskManager

    /**
     * The local task codec is used for running tasks directly in the tests. We can use this to
     * check that messages are being sent inside the directly run task. Note that the test task
     * codec automatically enqueues server acks for outgoing message and decrypts outgoing
     * forward security messages.
     */
    private val localTaskCodec =
        DecryptTaskCodec(contactStore, identityMap, forwardSecurityMessageProcessorMap)

    /**
     * The global task codec is used when new tasks are created.
     */
    private val globalTaskCodec =
        DecryptTaskCodec(contactStore, identityMap, forwardSecurityMessageProcessorMap)

    private val globalTaskQueue: Queue<QueueEntry<*>> = ConcurrentLinkedQueue()

    private data class QueueEntry<R>(
        private val task: Task<R, TaskCodec>,
        private val done: CompletableDeferred<R>,
        private val taskCodec: TaskCodec,
    ) {
        fun run() = runBlocking {
            done.complete(task.invoke(taskCodec))
        }
    }

    protected val sentMessagesInsideTask: Queue<AbstractMessage> =
        localTaskCodec.outboundAbstractMessages

    protected val sentMessagesNewTask: Queue<AbstractMessage> =
        globalTaskCodec.outboundAbstractMessages

    protected val initialContacts = listOf(myContact, contactA, contactB, contactC)

    protected val initialGroups =
        listOf(myGroup, myGroupWithProfilePicture, groupA, groupB, groupAB, groupALeft, myLeftGroup)

    @Rule
    @JvmField
    val timeout: Timeout = Timeout.seconds(300)

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

        // Delete persisted tasks as they are not needed for tests
        serviceManager.databaseServiceNew.taskArchiveFactory.deleteAll()

        // Then stop connection
        serviceManager.connection.stop()

        // Replace original task manager (save a copy of it)
        originalTaskManager = serviceManager.taskManager

        val mockTaskManager = object : TaskManager {
            override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
                val deferred = CompletableDeferred<R>()
                globalTaskQueue.add(QueueEntry(task, deferred, globalTaskCodec))
                return deferred
            }

            override fun hasPendingTasks(): Boolean = false

            override fun addQueueSendCompleteListener(listener: QueueSendCompleteListener) {
                // Nothing to do
            }

            override fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener) {
                // Nothing to do
            }
        }

        setTaskManager(mockTaskManager)

        disableLifetimeService()

        clearData()

        fillDatabase()

        val nonceFactory = NonceFactory(InMemoryNonceStore())

        val myForwardSecurityMessageProcessor = serviceManager.forwardSecurityMessageProcessor
        initialContacts.filter { it != myContact }.forEach {
            val textMessage = TextMessage().apply {
                toIdentity = it.identity
                fromIdentity = myContact.identity
                text = "Text"
            }
            // Making the message triggers an fs init message. We do not need to send the
            // encapsulated message as we only want to initiate a new fs session. Therefore we just
            // need to send the first message, which is the init.
            val result =
                myForwardSecurityMessageProcessor.runFsEncapsulationSteps(
                    it.toBasicContact(),
                    textMessage,
                    nonceFactory.next(NonceScope.CSP),
                    nonceFactory,
                    globalTaskCodec,
                )

            // Commit the dh session state
            myForwardSecurityMessageProcessor.commitSessionState(result)

            // Process the init message
            val (initMessage, initNonce) = result.outgoingMessages.first()

            initMessage.toIdentity = it.contact.identity
            val initCspMessage =
                initMessage.toCspMessage(myContact.identityStore, contactStore, initNonce)

            val initMessageBox = MessageBox.parseBinary(initCspMessage.toOutgoingMessageData().data)
            val init = MessageCoder(
                contactStore,
                it.identityStore
            ).decode(initMessageBox) as ForwardSecurityEnvelopeMessage
            runBlocking {
                forwardSecurityMessageProcessorMap[it.identity]!!.processInit(
                    myContact.contact,
                    init.data as ForwardSecurityDataInit,
                    globalTaskCodec
                )
            }

            // Note that we do not need to explicitly process the accept as this is already done by
            // decapsulating the message. The message is decapsulated as soon as it is put into the
            // global task handle. Processing an init immediately sends out an accept message.
        }
    }

    /**
     * Clean the data after the tests. This includes the deletion of the database entries, the
     * avatar files, and the blocked contacts.
     */
    @After
    fun cleanup() {
        clearData()

        if (this::originalTaskManager.isInitialized) {
            setTaskManager(originalTaskManager)
        }

        // We need to start the connection again, as some tests require a running connection
        serviceManager.connection.start()

        // Wait until the connection has been established. If we do not wait for the connection, the
        // next test may fail due to a race condition that occurs when the connection is started and
        // almost immediately stopped again.
        while (serviceManager.connection.connectionState != ConnectionState.LOGGEDIN) {
            Thread.sleep(50)
        }
    }

    private fun clearData() {
        // Clear conversations
        serviceManager.conversationService.getAll(true).forEach {
            serviceManager.conversationService.empty(it, true)
        }

        // Delete database
        serviceManager.databaseServiceNew.apply {
            contactModelFactory.deleteAll()
            messageModelFactory.deleteAll()
            groupCallModelFactory.deleteAll()
            groupInviteModelFactory.deleteAll()
            groupBallotModelFactory.deleteAll()
            groupMemberModelFactory.deleteAll()
            groupMessageModelFactory.deleteAll()
            // Remove group models from group service to empty the group service cache
            serviceManager.groupService.removeAll()
            distributionListModelFactory.deleteAll()
            distributionListMemberModelFactory.deleteAll()
            distributionListMessageModelFactory.deleteAll()
            outgoingGroupSyncRequestLogModelFactory.deleteAll()
            incomingGroupSyncRequestLogModelFactory.deleteAll()
            ballotModelFactory.deleteAll()
            ballotChoiceModelFactory.deleteAll()
            ballotVoteModelFactory.deleteAll()
            identityBallotModelFactory.deleteAll()
            webClientSessionModelFactory.deleteAll()
            conversationTagFactory.deleteAll()
            outgoingGroupJoinRequestModelFactory.deleteAll()
            incomingGroupJoinRequestModelFactory.deleteAll()
            serverMessageModelFactory.deleteAll()
            taskArchiveFactory.deleteAll()
        }

        // Delete dh sessions
        initialContacts.forEach {
            serviceManager.dhSessionStore.deleteAllDHSessions(myContact.identity, it.identity)
        }

        // Remove files
        serviceManager.fileService.removeAllAvatars()
        serviceManager.fileService.remove(
            File(
                InstrumentationRegistry.getInstrumentation().context.filesDir,
                "taskArchive"
            ), true
        )

        // Unblock contacts
        serviceManager.blockedContactsService.removeAll()
    }

    private fun setTaskManager(taskManager: TaskManager) {
        val serviceManager = ThreemaApplication.requireServiceManager()
        val coreServiceManager = TestCoreServiceManager(
            ThreemaApplication.getAppVersion(),
            serviceManager.databaseServiceNew,
            serviceManager.preferenceStore,
            TaskArchiverImpl(serviceManager.databaseServiceNew.taskArchiveFactory),
            serviceManager.deviceCookieManager,
            taskManager,
            serviceManager.multiDeviceManager as MultiDeviceManagerImpl,
            serviceManager.identityStore,
            serviceManager.nonceFactory,
        )

        val field = ServiceManager::class.java.getDeclaredField("coreServiceManager")
        field.isAccessible = true
        field.set(ThreemaApplication.getServiceManager(), coreServiceManager)
    }

    private fun disableLifetimeService() {
        val field = ServiceManager::class.java.getDeclaredField("lifetimeService")
        field.isAccessible = true
        field.set(ThreemaApplication.getServiceManager(), object : LifetimeService {
            override fun acquireConnection(sourceTag: String, unpauseable: Boolean) = Unit
            override fun acquireConnection(source: String) = Unit
            override fun acquireUnpauseableConnection(source: String) = Unit
            override fun releaseConnection(sourceTag: String) = Unit
            override fun releaseConnectionLinger(sourceTag: String, timeoutMs: Long) = Unit
            override fun ensureConnection() = Unit
            override fun alarm(intent: Intent?) = Unit
            override fun isActive(): Boolean = true
            override fun pause() = Unit
            override fun unpause() = Unit
            override fun addListener(listener: LifetimeService.LifetimeServiceListener?) = Unit
        })
    }

    /**
     * Fills basic data into the database. This is executed before each test. Override this if other
     * database entries are needed.
     */
    open fun fillDatabase() {
        val databaseService = serviceManager.databaseServiceNew
        val contactStore = serviceManager.contactStore
        val fileService = serviceManager.fileService

        initialContacts.forEach {
            addContactToDatabase(
                it,
                databaseService,
                contactStore,
                AcquaintanceLevel.GROUP
            )
        }

        initialGroups.forEach { addGroupToDatabase(it, databaseService, fileService) }
    }

    private fun addContactToDatabase(
        testContact: TestContact,
        databaseService: DatabaseServiceNew,
        contactStore: ContactStore,
        acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
    ) {
        databaseService.contactModelFactory.createOrUpdate(
            testContact.contactModel.setAcquaintanceLevel(acquaintanceLevel)
                .setFeatureMask(ThreemaFeature.FORWARD_SECURITY)
        )

        contactStore.addCachedContact(testContact.toBasicContact())

        // We trigger the listeners to invalidate the cache of the new contact model.
        ListenerManager.contactListeners.handle { it.onModified(testContact.identity) }
    }

    private fun addGroupToDatabase(
        testGroup: TestGroup,
        databaseService: DatabaseServiceNew,
        fileService: FileService,
    ) {
        val groupModel = testGroup.groupModel
        databaseService.groupModelFactory.createOrUpdate(groupModel)
        testGroup.setLocalGroupId(groupModel.id)
        testGroup.members.filter { it.identity != myContact.identity }.forEach { member ->
            val memberModel = GroupMemberModel()
                .setGroupId(groupModel.id)
                .setIdentity(member.identity)
            databaseService.groupMemberModelFactory.createOrUpdate(memberModel)
        }
        if (testGroup.profilePicture != null) {
            fileService.writeGroupAvatar(groupModel, testGroup.profilePicture)
        }
    }

    /**
     * Send a message from a user with the provided identity store.
     */
    protected suspend fun processMessage(
        message: AbstractMessage,
        identityStore: IdentityStoreInterface,
    ) {
        val messageBox = createMessageBox(
            message,
            identityStore,
            forwardSecurityMessageProcessorMap[message.fromIdentity]!!
        )

        // Process the group message
        val messageProcessor = IncomingMessageProcessorImpl(serviceManager)

        messageProcessor.processIncomingCspMessage(messageBox, localTaskCodec)

        // Assert that this message has been acked towards the server
        assertEquals(
            message.hasFlags(ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK),
            !localTaskCodec.ackedIncomingMessages.contains(message.messageId)
        )

        while (globalTaskQueue.isNotEmpty()) {
            globalTaskQueue.poll()?.run()
        }
    }

    /**
     * Run a task with the local task codec.
     */
    protected fun <T> runTask(task: ActiveTask<T>): T = runBlocking {
        task.invoke(localTaskCodec)
    }

    /**
     * Create a message box from a user with the given identity store.
     */
    private fun createMessageBox(
        msg: AbstractMessage,
        identityStore: IdentityStoreInterface,
        forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    ): MessageBox {
        val nonceFactory = NonceFactory(object : NonceStore {
            override fun exists(scope: NonceScope, nonce: Nonce) = false
            override fun store(scope: NonceScope, nonce: Nonce) = true
            override fun getAllHashedNonces(scope: NonceScope) = listOf<HashedNonce>()
            override fun getCount(scope: NonceScope) = 0L
            override fun addHashedNoncesChunk(scope: NonceScope, chunkSize: Int, offset: Int, nonces: MutableList<HashedNonce>) {}
            override fun insertHashedNonces(scope: NonceScope, nonces: List<HashedNonce>) = true
        })

        val encapsulated = forwardSecurityMessageProcessor.runFsEncapsulationSteps(
            contactStore.getContactForIdentityIncludingCache(
                msg.toIdentity
            )!!.enhanceToBasicContact(),
            msg,
            nonceFactory.next(NonceScope.CSP),
            nonceFactory,
            globalTaskCodec
        ).outgoingMessages.last().first

        val messageCoder = MessageCoder(contactStore, identityStore)
        return messageCoder.encode(encapsulated, nonceFactory.next(NonceScope.CSP).bytes)
    }

    private fun Contact.enhanceToBasicContact() = BasicContact(
        identity,
        publicKey,
        ThreemaFeature.Builder()
            .audio(true)
            .group(true)
            .ballot(true)
            .file(true)
            .voip(true)
            .videocalls(true)
            .forwardSecurity(true)
            .groupCalls(true)
            .editMessages(true)
            .deleteMessages(true)
            .build().toULong(),
        IdentityState.ACTIVE,
        IdentityType.NORMAL,
    )

}
