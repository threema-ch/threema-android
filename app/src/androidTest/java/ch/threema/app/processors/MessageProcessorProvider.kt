package ch.threema.app.processors

import android.Manifest
import android.os.Build
import androidx.annotation.CallSuper
import androidx.test.rule.GrantPermissionRule
import ch.threema.KoinTestRule
import ch.threema.app.TestNonceStore
import ch.threema.app.di.SessionScopeContainer
import ch.threema.app.di.SessionScopeContainerHolder
import ch.threema.app.di.modules.sessionScopedModule
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.files.ProfilePictureFileHandleProvider
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.FileService
import ch.threema.app.services.LifetimeService
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.app.testutils.clearDatabaseAndCaches
import ch.threema.app.utils.ForwardSecurityStatusSender
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.common.stateFlowOf
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.ContactModelDataFactory
import ch.threema.data.models.GroupModelDataFactory
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.data.toContactModelData
import ch.threema.domain.fs.DHSession
import ch.threema.domain.helpers.DecryptTaskCodec
import ch.threema.domain.helpers.InMemoryContactStore
import ch.threema.domain.helpers.InMemoryDHSessionStore
import ch.threema.domain.helpers.InMemoryNonceStore
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.Contact
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataInit
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.LeaderState
import ch.threema.domain.taskmanager.QueueSendCompleteState
import ch.threema.domain.taskmanager.ReflectionQueueDryState
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.toCspMessage
import ch.threema.domain.types.Identity
import ch.threema.storage.factories.TaskArchiveFactory
import ch.threema.test.TestIdentityProvider
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import junit.framework.TestCase.assertEquals
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.rules.Timeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

open class MessageProcessorProvider : KoinComponent {
    private val globalEventBuses: GlobalEventBuses by inject()
    private val profilePictureFileHandleProvider: ProfilePictureFileHandleProvider by inject()
    private val sessionScopeContainer: SessionScopeContainer by lazy {
        get<SessionScopeContainerHolder>().sessionScopeContainer!!
    }
    protected val serviceManager: ServiceManager
        get() = sessionScopeContainer.serviceManager

    protected val myContact: TestContact = TestHelpers.TEST_CONTACT
    protected val contactA = TestContact("12345678")
    protected val contactB = TestContact("ABCDEFGH")
    protected val contactC = TestContact("TESTTEST")
    protected val contactD = TestContact("87654321")

    protected val myGroup = TestGroup(
        GroupId(0),
        myContact,
        listOf(myContact, contactA, contactB),
        "MyGroup",
        myContact.identity,
    )
    protected val myGroupWithProfilePicture =
        TestGroup(
            GroupId(1),
            myContact,
            listOf(myContact, contactA),
            "MyGroupWithPicture",
            byteArrayOf(0, 1, 2, 3),
            myContact.identity,
        )
    protected val groupA =
        TestGroup(
            GroupId(2),
            contactA,
            listOf(myContact),
            "GroupA",
            myContact.identity,
        )
    protected val groupB =
        TestGroup(
            GroupId(3),
            contactB,
            listOf(myContact),
            "GroupB",
            myContact.identity,
        )
    protected val groupAB =
        TestGroup(
            GroupId(4),
            contactA,
            listOf(myContact, contactB),
            "GroupAB",
            myContact.identity,
        )
    protected val groupAUnknown =
        TestGroup(
            GroupId(5),
            contactA,
            listOf(myContact, contactB),
            "GroupAUnknown",
            myContact.identity,
        )
    protected val groupALeft =
        TestGroup(
            GroupId(6),
            contactA,
            listOf(contactB),
            "GroupALeft",
            myContact.identity,
        )
    protected val myUnknownGroup =
        TestGroup(
            GroupId(7),
            myContact,
            listOf(contactA),
            "MyUnknownGroup",
            myContact.identity,
        )
    protected val myLeftGroup =
        TestGroup(
            GroupId(8),
            myContact,
            listOf(contactA),
            "MyLeftGroup",
            myContact.identity,
        )
    protected val newAGroup =
        TestGroup(
            GroupId(9),
            contactA,
            listOf(myContact, contactB),
            "NewAGroup",
            myContact.identity,
        )
    protected val newBGroup =
        TestGroup(
            GroupId(10),
            contactB,
            listOf(myContact),
            "NewBGroup",
            myContact.identity,
        )

    private val contactStore: ContactStore = InMemoryContactStore().apply {
        addContact(myContact.contact)
        addContact(contactA.contact)
        addContact(contactB.contact)
        addContact(contactC.contact)
        addContact(contactD.contact)
    }

    private val identityMap = listOf(
        myContact.identity to myContact.identityStore,
        contactA.identity to contactA.identityStore,
        contactB.identity to contactB.identityStore,
        contactC.identity to contactC.identityStore,
        contactD.identity to contactD.identityStore,
    ).toMap()

    private val forwardSecurityStatusListener by lazy {
        object : ForwardSecurityStatusSender(
            serviceManager.contactService,
            serviceManager.messageService,
            APIConnector(
                /* serverAddressProvider = */
                mockk(),
                /* challengeResponseHelper */
                mockk(),
                /* isWork = */
                false,
                /* okHttpClient = */
                OkHttpClient(),
                /* version = */
                Version(),
                /* language = */
                null,
                /* authenticator= */
                null,
            ),
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
    }

    private val forwardSecurityMessageProcessorMap by lazy {
        listOf(
            myContact.identity to serviceManager.forwardSecurityMessageProcessor,
            contactA.identity to ForwardSecurityMessageProcessor(
                InMemoryDHSessionStore(),
                contactStore,
                contactA.identityStore,
                NonceFactory(InMemoryNonceStore()),
                forwardSecurityStatusListener,
            ),
            contactB.identity to ForwardSecurityMessageProcessor(
                InMemoryDHSessionStore(),
                contactStore,
                contactB.identityStore,
                NonceFactory(InMemoryNonceStore()),
                forwardSecurityStatusListener,
            ),
            contactC.identity to ForwardSecurityMessageProcessor(
                InMemoryDHSessionStore(),
                contactStore,
                contactC.identityStore,
                NonceFactory(InMemoryNonceStore()),
                forwardSecurityStatusListener,
            ),
            contactD.identity to ForwardSecurityMessageProcessor(
                InMemoryDHSessionStore(),
                contactStore,
                contactD.identityStore,
                NonceFactory(InMemoryNonceStore()),
                forwardSecurityStatusListener,
            ),
        ).toMap()
    }

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
    private val localTaskCodec by lazy {
        DecryptTaskCodec(contactStore, identityMap, forwardSecurityMessageProcessorMap)
    }

    /**
     * The global task codec is used when new tasks are created.
     */
    private val globalTaskCodec by lazy {
        DecryptTaskCodec(contactStore, identityMap, forwardSecurityMessageProcessorMap)
    }

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

    protected val sentMessagesInsideTask: Queue<AbstractMessage> by lazy {
        localTaskCodec.outboundAbstractMessages
    }

    protected val sentMessagesNewTask: Queue<AbstractMessage> by lazy {
        globalTaskCodec.outboundAbstractMessages
    }

    protected val initialContacts = listOf(myContact, contactA, contactB, contactC, contactD)

    protected val initialGroups =
        listOf(myGroup, myGroupWithProfilePicture, groupA, groupB, groupAB, groupALeft, myLeftGroup)

    @Rule
    @JvmField
    val timeout: Timeout = Timeout.seconds(150)

    @JvmField
    @Rule
    val grantPermissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    @get:Rule
    val koinTestRule = KoinTestRule(
        modules = listOf(sessionScopedModule),
    )

    /**
     * Asserts that the correct identity is set up and fills the database with the initial data.
     */
    @BeforeTest
    @CallSuper
    open fun setup() {
        TestHelpers.setIdentity(serviceManager, TestHelpers.TEST_CONTACT)

        // Delete persisted tasks as they are not needed for tests
        get<TaskArchiveFactory>().deleteAll()

        // Replace original task manager (save a copy of it)
        originalTaskManager = sessionScopeContainer.taskManager
        val mockTaskManager = object : TaskManager {
            override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
                val deferred = CompletableDeferred<R>()
                globalTaskQueue.add(QueueEntry(task, deferred, globalTaskCodec))
                return deferred
            }

            override fun hasPendingTasks(): Boolean = false

            override fun watchQueueSendComplete(): StateFlow<QueueSendCompleteState> = stateFlowOf(QueueSendCompleteState.PENDING)

            override fun watchReflectionQueueDry(): StateFlow<ReflectionQueueDryState> = stateFlowOf(ReflectionQueueDryState.PENDING)

            override fun watchLeaderState(): StateFlow<LeaderState> = stateFlowOf(LeaderState.NOT_LEADER)
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
                it.identityStore,
            ).decode(initMessageBox) as ForwardSecurityEnvelopeMessage
            runBlocking {
                forwardSecurityMessageProcessorMap[it.identity]!!.processInit(
                    myContact.contact,
                    init.data as ForwardSecurityDataInit,
                    globalTaskCodec,
                )
            }

            // Note that we do not need to explicitly process the accept as this is already done by
            // decapsulating the message. The message is decapsulated as soon as it is put into the
            // global task handle. Processing an init immediately sends out an accept message.
        }
    }

    /**
     * Set the original task manager again and wait until the connection has been started again.
     */
    @AfterTest
    fun cleanup() {
        if (this::originalTaskManager.isInitialized) {
            setTaskManager(originalTaskManager)
        }
    }

    private fun clearData() {
        clearDatabaseAndCaches(serviceManager)

        // Delete dh sessions
        initialContacts.forEach {
            serviceManager.dhSessionStore.deleteAllDHSessions(myContact.identity, it.identity)
        }

        // Remove files
        profilePictureFileHandleProvider.deleteAll()

        // Unblock contacts
        val blockedIdentitiesService = serviceManager.blockedIdentitiesService
        blockedIdentitiesService.getAllBlockedIdentities().forEach { blockedIdentity ->
            blockedIdentitiesService.unblockIdentity(blockedIdentity)
        }
    }

    private fun setTaskManager(taskManager: TaskManager) {
        sessionScopeContainer.taskManagerOverride = taskManager
    }

    private fun disableLifetimeService() {
        sessionScopeContainer.lifetimeServiceOverride = mockk<LifetimeService>(relaxed = true) {
            every { isActive } returns true
        }
    }

    /**
     * Fills basic data into the database. This is executed before each test. Override this if other
     * database entries are needed.
     */
    open fun fillDatabase() {
        val databaseService = serviceManager.databaseService
        val contactStore = serviceManager.contactStore
        val fileService = serviceManager.fileService
        val sqliteDatabaseBackend = SqliteDatabaseBackend(
            databaseProvider = mockk {
                every { readableDatabase } returns databaseService.contactModelFactory.readableDatabase
                every { writableDatabase } returns databaseService.contactModelFactory.writableDatabase
            },
            identityProvider = TestIdentityProvider(Identity(myContact.identity)),
        )

        initialContacts
            .filter { it != myContact }
            .forEach { testContact ->
                addGroupContactToDatabase(
                    testContact,
                    sqliteDatabaseBackend,
                    contactStore,
                )
            }

        initialGroups.forEach { addGroupToDatabase(it, sqliteDatabaseBackend, fileService) }
    }

    private fun addGroupContactToDatabase(
        testContact: TestContact,
        sqliteDatabaseBackend: SqliteDatabaseBackend,
        contactStore: ContactStore,
    ) {
        val contactModelData = testContact
            .toBasicContact()
            .toContactModelData()
            .copy(
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
                featureMask = ThreemaFeature.FORWARD_SECURITY.toULong(),
            )
        sqliteDatabaseBackend.createContact(
            ContactModelDataFactory.toDbType(contactModelData),
        )

        contactStore.addCachedContact(contactModelData.toBasicContact())

        // We trigger the event bus to invalidate the cache of the new contact model.
        globalEventBuses.contacts.emit(ContactEvent.ContactUpdated.javaCreate(testContact.identity))
    }

    private fun addGroupToDatabase(
        testGroup: TestGroup,
        sqliteDatabaseBackend: SqliteDatabaseBackend,
        fileService: FileService,
    ) {
        val groupModelData = testGroup.getGroupModelData()
        sqliteDatabaseBackend.createGroup(GroupModelDataFactory.toDbType(groupModelData))
        val groupDatabaseId = sqliteDatabaseBackend.getGroupDatabaseId(groupModelData.groupIdentity)!!
        testGroup.setLocalGroupId(groupDatabaseId.toInt())
        if (testGroup.profilePicture != null) {
            fileService.writeGroupProfilePicture(
                groupModelData.groupIdentity,
                groupDatabaseId,
                ByteArrayInputStream(testGroup.profilePicture),
            )
        }

        // We notify the event bus to invalidate the cache of the new group model.
        val groupIdentity = GroupIdentity(testGroup.groupCreator.identity, testGroup.apiGroupId.toLong())
        globalEventBuses.groups.emit(GroupEvent.GroupUpdated(groupIdentity))
    }

    /**
     * Send a message from a user with the provided identity store.
     */
    protected suspend fun processMessage(
        message: AbstractMessage,
        identityStore: IdentityStore,
    ) {
        val messageBox = createMessageBox(
            message,
            identityStore,
            forwardSecurityMessageProcessorMap[message.fromIdentity]!!,
        )

        // Process the group message
        val messageProcessor = IncomingMessageProcessorImpl(
            serviceManager,
            serverMessageModelRepository = mockk(relaxed = true),
            globalEventBuses,
            typingIndicatorManager = mockk(),
        )

        messageProcessor.processIncomingCspMessage(messageBox, localTaskCodec)

        // Assert that this message has been acked towards the server
        assertEquals(
            message.hasFlag(ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK),
            !localTaskCodec.ackedIncomingMessages.contains(message.messageId),
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
        identityStore: IdentityStore,
        forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    ): MessageBox {
        val nonceFactory = NonceFactory(
            TestNonceStore(),
        )

        val encapsulated = forwardSecurityMessageProcessor.runFsEncapsulationSteps(
            contactStore.getContactForIdentityIncludingCache(
                msg.toIdentity,
            )!!.enhanceToBasicContact(),
            msg,
            nonceFactory.next(NonceScope.CSP),
            nonceFactory,
            globalTaskCodec,
        ).outgoingMessages.last().first

        val messageCoder = MessageCoder(contactStore, identityStore)
        return messageCoder.encode(encapsulated, nonceFactory.next(NonceScope.CSP).bytes)
    }

    private fun Contact.enhanceToBasicContact() = BasicContact(
        identity = identity,
        publicKey = publicKey,
        featureMask = ThreemaFeature.Builder()
            .audio(true)
            .group(true)
            .poll(true)
            .file(true)
            .voip(true)
            .videocalls(true)
            .forwardSecurity(true)
            .groupCalls(true)
            .editMessages(true)
            .deleteMessages(true)
            .build().toULong(),
        identityState = IdentityState.ACTIVE,
        identityType = IdentityType.REGULAR,
        verificationLevel = VerificationLevel.UNVERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        jobTitle = null,
        department = null,
    )
}
