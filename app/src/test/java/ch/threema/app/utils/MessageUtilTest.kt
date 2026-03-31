package ch.threema.app.utils

import ch.threema.app.AppConstants
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.common.minus
import ch.threema.common.now
import ch.threema.common.plus
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.domain.protocol.csp.messages.file.FileData.RenderingType
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.FileDataModel
import ch.threema.storage.models.group.GroupMessageModel
import ch.threema.testhelpers.nonSecureRandomArray
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import testdata.TestData

class MessageUtilTest {
    private lateinit var contactMessageModelInbox: MessageModel
    private lateinit var contactMessageModelInboxUserAcknowledged: MessageModel
    private lateinit var contactMessageModelInboxUserDeclined: MessageModel
    private lateinit var contactMessageModelOutbox: MessageModel
    private lateinit var contactMessageModelOutboxUserAcknowledged: MessageModel
    private lateinit var contactMessageModelOutboxUserDeclined: MessageModel

    private lateinit var businessContactMessageModelInbox: MessageModel
    private lateinit var businessContactMessageModelInboxUserAcknowledged: MessageModel
    private lateinit var businessContactMessageModelInboxUserDeclined: MessageModel
    private lateinit var businessContactMessageModelOutbox: MessageModel
    private lateinit var businessContactMessageModelOutboxUserAcknowledged: MessageModel
    private lateinit var businessContactMessageModelOutboxUserDeclined: MessageModel

    private lateinit var groupMessageModelInbox: GroupMessageModel
    private lateinit var groupMessageModelOutbox: GroupMessageModel

    private lateinit var distributionListMessageModelOutbox: DistributionListMessageModel

    private val multiDeviceManagerMock: MultiDeviceManager = mockk<MultiDeviceManager>()
    private val taskManagerMock: TaskManager = mockk<TaskManager>()
    private val coreServiceManagerMock: CoreServiceManager = mockk<CoreServiceManager> {
        every { multiDeviceManager } returns multiDeviceManagerMock
        every { taskManager } returns taskManagerMock
        every { identityStore } returns mockk<IdentityStore> {
            every { getIdentityString() } returns TestData.Identities.ME.value
        }
    }

    private val blockedIdentitiesServiceMock = mockk<BlockedIdentitiesService>()
    private val serviceManagerMock: ServiceManager = mockk<ServiceManager> {
        every { multiDeviceManager } returns multiDeviceManagerMock
        every { taskManager } returns taskManagerMock
    }

    private val databaseBackendMock: DatabaseBackend = mockk<DatabaseBackend>()
    private val contactModelRepositoryMock: ContactModelRepository = mockk<ContactModelRepository>()

    @Suppress("DEPRECATION")
    @BeforeTest
    fun setUp() {
        mockkStatic(ConfigUtils::class)

        val contactThreemaId = AppConstants.ECHO_USER_IDENTITY
        val businessContactThreemaId = "*THREEMA"

        // mock object
        this.contactMessageModelInbox = MessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
        }

        this.contactMessageModelInboxUserAcknowledged = MessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
            state = MessageState.USERACK
        }

        this.contactMessageModelInboxUserDeclined = MessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
            state = MessageState.USERDEC
        }

        this.contactMessageModelOutbox = MessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
        }

        this.contactMessageModelOutboxUserAcknowledged = MessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
            state = MessageState.USERACK
        }

        this.contactMessageModelOutboxUserDeclined = MessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
            state = MessageState.USERDEC
        }

        this.businessContactMessageModelInbox = MessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
        }

        this.businessContactMessageModelInboxUserAcknowledged = MessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
            state = MessageState.USERACK
        }

        this.businessContactMessageModelInboxUserDeclined = MessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
            state = MessageState.USERDEC
        }

        this.businessContactMessageModelOutbox = MessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
        }

        this.businessContactMessageModelOutboxUserAcknowledged = MessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
            state = MessageState.USERACK
        }

        this.businessContactMessageModelOutboxUserDeclined = MessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
            state = MessageState.USERDEC
        }

        this.groupMessageModelInbox = GroupMessageModel().apply {
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
        }

        this.groupMessageModelOutbox = GroupMessageModel().apply {
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
        }

        this.distributionListMessageModelOutbox = DistributionListMessageModel().apply {
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
        }
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(ConfigUtils::class)
    }

    @Suppress("DEPRECATION")
    @Test
    fun hasDataFile() {
        assertFalse(MessageUtil.hasDataFile(messageModelWithType(MessageType.TEXT)))
        assertTrue(MessageUtil.hasDataFile(messageModelWithType(MessageType.IMAGE)))
        assertTrue(MessageUtil.hasDataFile(messageModelWithType(MessageType.VIDEO)))
        assertTrue(MessageUtil.hasDataFile(messageModelWithType(MessageType.VOICEMESSAGE)))
        assertFalse(MessageUtil.hasDataFile(messageModelWithType(MessageType.LOCATION)))
        assertFalse(MessageUtil.hasDataFile(messageModelWithType(MessageType.CONTACT)))
        assertFalse(MessageUtil.hasDataFile(messageModelWithType(MessageType.STATUS)))
        assertFalse(MessageUtil.hasDataFile(messageModelWithType(MessageType.BALLOT)))
        assertTrue(MessageUtil.hasDataFile(messageModelWithType(MessageType.FILE)))
    }

    @Suppress("DEPRECATION")
    @Test
    fun hasThumbnailFile() {
        assertFalse(MessageUtil.canHaveThumbnailFile(messageModelWithType(MessageType.TEXT)))
        assertTrue(MessageUtil.canHaveThumbnailFile(messageModelWithType(MessageType.IMAGE)))
        assertTrue(MessageUtil.canHaveThumbnailFile(messageModelWithType(MessageType.VIDEO)))
        assertFalse(MessageUtil.canHaveThumbnailFile(messageModelWithType(MessageType.VOICEMESSAGE)))
        assertFalse(MessageUtil.canHaveThumbnailFile(messageModelWithType(MessageType.LOCATION)))
        assertFalse(MessageUtil.canHaveThumbnailFile(messageModelWithType(MessageType.CONTACT)))
        assertFalse(MessageUtil.canHaveThumbnailFile(messageModelWithType(MessageType.STATUS)))
        assertFalse(MessageUtil.canHaveThumbnailFile(messageModelWithType(MessageType.BALLOT)))
        assertTrue(MessageUtil.canHaveThumbnailFile(messageModelWithType(MessageType.FILE)))
    }

    @Suppress("DEPRECATION")
    @Test
    fun fileTypes() {
        assertFalse(MessageUtil.getFileTypes().contains(MessageType.TEXT))
        assertFalse(MessageUtil.getFileTypes().contains(MessageType.BALLOT))
        assertFalse(MessageUtil.getFileTypes().contains(MessageType.CONTACT))
        assertFalse(MessageUtil.getFileTypes().contains(MessageType.LOCATION))
        assertFalse(MessageUtil.getFileTypes().contains(MessageType.STATUS))
        assertTrue(MessageUtil.getFileTypes().contains(MessageType.IMAGE))
        assertTrue(MessageUtil.getFileTypes().contains(MessageType.VOICEMESSAGE))
        assertTrue(MessageUtil.getFileTypes().contains(MessageType.VIDEO))
        assertTrue(MessageUtil.getFileTypes().contains(MessageType.FILE))
    }

    @Test
    fun canSendDeliveryReceipt() {
        assertTrue(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertTrue(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelInboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertFalse(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelOutbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertFalse(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelOutboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertTrue(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertTrue(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelInboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertFalse(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelOutbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertFalse(
            MessageUtil.canSendDeliveryReceipt(
                this.businessContactMessageModelOutboxUserAcknowledged,
                ProtocolDefines.DELIVERYRECEIPT_MSGREAD,
            ),
        )
        assertFalse(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))

        if (ConfigUtils.isGroupAckEnabled()) {
            assertTrue(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK))
            assertTrue(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC))
        } else {
            assertFalse(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK))
            assertFalse(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC))
        }

        assertFalse(MessageUtil.canSendDeliveryReceipt(this.distributionListMessageModelOutbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
    }

    /**
     * User acknowledge can be sent following message setups:
     * - User, Inbox, Not acknowledged
     * - Business, Inbox acknowledged
     */
    @Test
    fun canSendUserAcknowledge() {
        // contact message (inbox) can be acknowledge by the user
        assertTrue(MessageUtil.canSendUserAcknowledge(this.contactMessageModelInbox))

        // contact message (outbox) can be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.contactMessageModelOutbox))

        // contact message (inbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.contactMessageModelInboxUserAcknowledged))

        // contact message (outbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.contactMessageModelOutboxUserAcknowledged))

        // business contact message (inbox) can be acknowledge by the user
        assertTrue(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelInbox))

        // business contact message (outbox) can be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelOutbox))

        // business contact message (inbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelInboxUserAcknowledged))

        // business contact message (outbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelOutboxUserAcknowledged))

        // group message (inbox) can be acknowledged by the user
        assertTrue(MessageUtil.canSendUserAcknowledge(this.groupMessageModelInbox))

        // group message (outbox) can be acknowledged by the user
        assertTrue(MessageUtil.canSendUserAcknowledge(this.groupMessageModelOutbox))

        // group message (outbox) can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.distributionListMessageModelOutbox))
    }

    /**
     * User decline can be sent following message setups:
     * - User, Inbox, Not acknowledged
     * - Business, Inbox acknowledged
     */
    @Test
    fun canSendUserDecline() {
        // contact message (inbox) can be decline by the user
        assertTrue(MessageUtil.canSendUserDecline(this.contactMessageModelInbox))

        // contact message (outbox) can be decline by the user
        assertFalse(MessageUtil.canSendUserDecline(this.contactMessageModelOutbox))

        // contact message (inbox) with state UserDec can not be decline by the user
        assertFalse(MessageUtil.canSendUserDecline(this.contactMessageModelInboxUserDeclined))

        // contact message (outbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.contactMessageModelOutboxUserDeclined))

        // business contact message (inbox) can be acknowledge by the user
        assertTrue(MessageUtil.canSendUserDecline(this.businessContactMessageModelInbox))

        // business contact message (outbox) can be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.businessContactMessageModelOutbox))

        // business contact message (inbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.businessContactMessageModelInboxUserDeclined))

        // business contact message (outbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.businessContactMessageModelOutboxUserDeclined))

        // group message (inbox) can be declined by the user
        assertTrue(MessageUtil.canSendUserDecline(this.groupMessageModelInbox))

        // group message (outbox) can be declined by the user
        assertTrue(MessageUtil.canSendUserDecline(this.groupMessageModelOutbox))

        // group message (outbox) can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.distributionListMessageModelOutbox))
    }

    /**
     * status icons show for following message setups:
     * - User, Outbox
     * - User, Inbox, User acknowledged
     * - Business, Inbox, User acknowledged
     * - Business|Group|Distribution List, outbox, send failed|pending|sending
     */
    @Test
    fun showStatusIcon() {
        assertFalse(MessageUtil.showStatusIcon(this.contactMessageModelInbox))
        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.contactMessageModelInboxUserAcknowledged))
        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutboxUserAcknowledged))

        assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelInbox))
        assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelInboxUserAcknowledged))
        assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelOutboxUserAcknowledged))

        assertFalse(MessageUtil.showStatusIcon(this.groupMessageModelInbox))
        assertFalse(MessageUtil.showStatusIcon(this.groupMessageModelOutbox))

        assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox))

        // all types in state sending
        contactMessageModelOutbox.state = MessageState.SENDING
        businessContactMessageModelOutbox.state = MessageState.SENDING
        groupMessageModelOutbox.state = MessageState.SENDING
        distributionListMessageModelOutbox.state = MessageState.SENDING

        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.groupMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox))

        // all types in state failed
        contactMessageModelOutbox.state = MessageState.SENDFAILED
        businessContactMessageModelOutbox.state = MessageState.SENDFAILED
        groupMessageModelOutbox.state = MessageState.SENDFAILED
        distributionListMessageModelOutbox.state = MessageState.SENDFAILED

        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.groupMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox))

        // all types in state pending
        contactMessageModelOutbox.state = MessageState.PENDING
        businessContactMessageModelOutbox.state = MessageState.PENDING
        groupMessageModelOutbox.state = MessageState.PENDING
        distributionListMessageModelOutbox.state = MessageState.PENDING

        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.groupMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox))
    }

    @Test
    fun isUnread() {
        assertTrue(
            MessageUtil.isUnread(
                MessageModel().apply {
                    isOutbox = false
                    isStatusMessage = false
                },
            ),
        )
        assertFalse(
            MessageUtil.isUnread(
                MessageModel().apply {
                    isOutbox = true
                    isStatusMessage = false
                },
            ),
        )

        assertFalse(
            MessageUtil.isUnread(
                MessageModel().apply {
                    isOutbox = false
                    isStatusMessage = true
                },
            ),
        )

        assertFalse(MessageUtil.isUnread(null))
    }

    @Test
    fun canMarkAsRead() {
        assertTrue(
            MessageUtil.canMarkAsRead(
                MessageModel().apply {
                    isRead = false
                    isOutbox = false
                },
            ),
        )
        assertFalse(
            MessageUtil.canMarkAsRead(
                MessageModel().apply {
                    isRead = true
                    isOutbox = false
                },
            ),
        )
        assertFalse(
            MessageUtil.canMarkAsRead(
                MessageModel().apply {
                    isRead = false
                    isOutbox = true
                },
            ),
        )

        assertFalse(
            MessageUtil.canMarkAsRead(
                MessageModel().apply {
                    isRead = true
                    isOutbox = true
                },
            ),
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun autoGenerateThumbnail() {
        assertFalse(MessageUtil.autoGenerateThumbnail(messageModelWithType(MessageType.TEXT)))
        assertTrue(MessageUtil.autoGenerateThumbnail(messageModelWithType(MessageType.IMAGE)))
        assertFalse(MessageUtil.autoGenerateThumbnail(messageModelWithType(MessageType.VIDEO)))
        assertFalse(MessageUtil.autoGenerateThumbnail(messageModelWithType(MessageType.VOICEMESSAGE)))
        assertFalse(MessageUtil.autoGenerateThumbnail(messageModelWithType(MessageType.CONTACT)))
        assertFalse(MessageUtil.autoGenerateThumbnail(messageModelWithType(MessageType.STATUS)))
        assertFalse(MessageUtil.autoGenerateThumbnail(messageModelWithType(MessageType.BALLOT)))
        assertFalse(MessageUtil.autoGenerateThumbnail(messageModelWithType(MessageType.FILE)))
    }

    @Test
    fun allReceivers_message_without_affected_receivers() {
        val contactMessageReceiver = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )

        // Message receiver without affected receivers
        val allReceivers: List<MessageReceiver<*>> = MessageUtil.getAllReceivers(contactMessageReceiver)
        assertEquals(1, allReceivers.size)
        assertEquals(contactMessageReceiver, allReceivers[0])
    }

    @Test
    fun allReceivers_message_with_affected_receivers() {
        val identities = listOf(TestData.Identities.OTHER_1, TestData.Identities.OTHER_2, TestData.Identities.OTHER_3)
        val distributionListReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = identities.map { identity ->
                identity to nonSecureRandomArray(32)
            },
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )
        val allReceivers: List<MessageReceiver<*>> = MessageUtil.getAllReceivers(distributionListReceiver)
        assertEquals(4, allReceivers.size)
        assertEquals(distributionListReceiver, allReceivers[0])
        identities.forEachIndexed { index: Int, identity ->
            assertEquals(identity.value, allReceivers[index + 1].identities[0])
        }
    }

    @Test
    fun addDistributionListReceivers_empty_message_receivers_list() {
        assertEquals(0, MessageUtil.addDistributionListReceivers(arrayOfNulls(0)).size)
    }

    @Test
    fun addDistributionListReceivers_must_contain_passed_receivers() {
        val contactMessageReceiver: MessageReceiver<*> = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val distributionListReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = emptyList(),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )
        val resolvedReceivers = MessageUtil.addDistributionListReceivers(
            arrayOf(
                contactMessageReceiver,
                distributionListReceiver,
            ),
        )
        assertEquals(2, resolvedReceivers.size)
        assertEquals(contactMessageReceiver, resolvedReceivers[0])
        assertEquals(distributionListReceiver, resolvedReceivers[1])
    }

    @Test
    fun addDistributionListReceivers_must_preserve_order_of_receivers() {
        val contactMessageReceiver1: MessageReceiver<*> = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            publicKey = nonSecureRandomArray(32),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val contactMessageReceiver2: MessageReceiver<*> = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_2,
            publicKey = nonSecureRandomArray(32),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val contactMessageReceiver3: MessageReceiver<*> = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_3,
            publicKey = nonSecureRandomArray(32),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val emptyDistributionListMessageReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = emptyList(),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )
        val distributionListMessageReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 2L,
            identitiesWithPublicKey = listOf(
                TestData.Identities.OTHER_4 to TestData.publicKeyAllZeros,
                TestData.Identities.OTHER_5 to TestData.publicKeyAllZeros,
            ),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )

        val receivers = arrayOf(
            contactMessageReceiver1,
            emptyDistributionListMessageReceiver,
            contactMessageReceiver2,
            distributionListMessageReceiver,
            contactMessageReceiver3,
        )

        val resolvedReceivers = MessageUtil.addDistributionListReceivers(receivers)

        assertEquals(7, resolvedReceivers.size)
        assertEquals(contactMessageReceiver1, resolvedReceivers[0])
        assertEquals(emptyDistributionListMessageReceiver, resolvedReceivers[1])
        assertEquals(contactMessageReceiver2, resolvedReceivers[2])
        assertEquals(distributionListMessageReceiver, resolvedReceivers[3])
        assertEquals(TestData.Identities.OTHER_4.value, resolvedReceivers[4].identities[0])
        assertEquals(TestData.Identities.OTHER_5.value, resolvedReceivers[5].identities[0])
        assertEquals(contactMessageReceiver3, resolvedReceivers[6])
    }

    @Test
    fun addDistributionListReceivers_must_preserve_order_of_receivers_and_remove_duplicates() {
        val contactMessageReceiver1 = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val contactMessageReceiver2 = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_2,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val contactMessageReceiver3 = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_3,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )

        val duplicate1 = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )

        val emptyDistributionListMessageReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = emptyList(),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )
        val distributionListMessageReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = listOf(
                TestData.Identities.OTHER_4 to TestData.publicKeyAllZeros,
                TestData.Identities.OTHER_5 to TestData.publicKeyAllZeros,
                TestData.Identities.OTHER_2 to TestData.publicKeyAllZeros,
                TestData.Identities.OTHER_3 to TestData.publicKeyAllZeros,
            ),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )

        val receivers = arrayOf(
            contactMessageReceiver1,
            duplicate1,
            emptyDistributionListMessageReceiver,
            contactMessageReceiver2,
            distributionListMessageReceiver,
            contactMessageReceiver3,
            distributionListMessageReceiver,
        )

        val resolvedReceivers = MessageUtil.addDistributionListReceivers(receivers)

        assertEquals(7, resolvedReceivers.size)
        assertEquals(contactMessageReceiver1, resolvedReceivers[0])
        assertEquals(emptyDistributionListMessageReceiver, resolvedReceivers[1])
        assertEquals(contactMessageReceiver2, resolvedReceivers[2])
        assertEquals(distributionListMessageReceiver, resolvedReceivers[3])
        assertEquals(TestData.Identities.OTHER_4.value, resolvedReceivers[4].identities[0])
        assertEquals(TestData.Identities.OTHER_5.value, resolvedReceivers[5].identities[0])
        assertEquals(TestData.Identities.OTHER_3.value, resolvedReceivers[6].identities[0])
    }

    @Test
    fun hasFileWithDefaultRendering() {
        assertFalse(MessageUtil.hasFileWithDefaultRendering(messageModelWithType(MessageType.TEXT)))

        val messageWithImage: AbstractMessageModel = messageModelWithType(MessageType.FILE)
        val image: FileDataModel = createFileDataModel(FileData.RENDERING_MEDIA)
        messageWithImage.fileData = image
        assertFalse(MessageUtil.hasFileWithDefaultRendering(messageWithImage))

        val messageWithImageAsFile: AbstractMessageModel = messageModelWithType(MessageType.FILE)
        val imageAsFile: FileDataModel = createFileDataModel(FileData.RENDERING_DEFAULT)
        messageWithImageAsFile.fileData = imageAsFile
        assertTrue(MessageUtil.hasFileWithDefaultRendering(messageWithImageAsFile))
    }

    @Test
    fun `text message can be edited`() {
        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isOutbox = true,
            createdAt = now(),
            postedAt = now(),
        )

        assertTrue(message.canBeEdited())
    }

    @Test
    fun `file message can be edited`() {
        val message = createAbstractMessageModel(
            type = MessageType.FILE,
            isOutbox = true,
            createdAt = now(),
            postedAt = now(),
        )

        assertTrue(message.canBeEdited())
    }

    @Test
    fun `message can be edited if it is less than 6 hours old`() {
        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isOutbox = true,
            createdAt = now() - 5.9.hours,
            postedAt = now(),
        )

        assertTrue(message.canBeEdited())
    }

    @Test
    fun `message can be edited if it is less than 6 hours older than custom editTime`() {
        val editTime = now() - 4.hours

        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isOutbox = true,
            createdAt = editTime - 5.9.hours,
            postedAt = now(),
        )

        assertTrue(message.canBeEdited(editTime = editTime))
    }

    @Test
    fun `message can not be edited if it is more than 6 hours old`() {
        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isOutbox = true,
            createdAt = now() - 6.1.hours,
            postedAt = now(),
        )

        assertFalse(message.canBeEdited())
    }

    @Test
    fun `message in notes group can still be edited even if it is older than 6 hours`() {
        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isOutbox = true,
            createdAt = now() - 6.1.hours,
            postedAt = now(),
        )

        assertTrue(message.canBeEdited(belongsToNotesGroup = true))
    }

    @Test
    fun `status message can not be edited`() {
        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isStatusMessage = true,
            isOutbox = true,
            createdAt = now(),
            postedAt = now(),
        )

        assertFalse(message.canBeEdited())
    }

    @Test
    fun `message from other user can not be edited`() {
        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isOutbox = false,
            createdAt = now(),
            postedAt = now(),
        )

        assertFalse(message.canBeEdited())
    }

    @Test
    fun `location message can not be edited`() {
        val message = createAbstractMessageModel(
            type = MessageType.LOCATION,
            isOutbox = true,
            createdAt = now(),
            postedAt = now(),
        )

        assertFalse(message.canBeEdited())
    }

    @Test
    fun `deleted message from other user can not be edited`() {
        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isOutbox = true,
            createdAt = now(),
            postedAt = now(),
            isDeleted = true,
        )

        assertFalse(message.canBeEdited())
    }

    @Test
    fun `unposted message can not be edited`() {
        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isOutbox = true,
            createdAt = now(),
            postedAt = null,
        )

        assertFalse(message.canBeEdited())
    }

    @Test
    fun `unposted message can not be edited if it failed to send`() {
        val message = createAbstractMessageModel(
            type = MessageType.TEXT,
            isOutbox = true,
            createdAt = now(),
            postedAt = null,
            state = MessageState.SENDFAILED,
        )

        assertTrue(message.canBeEdited())
    }

    @Test
    fun `group message can be edited (conditions apply)`() {
        val message = mockk<GroupMessageModel> {
            every { type } returns MessageType.TEXT
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { isOutbox } returns true
            every { createdAt } returns now()
            every { postedAt } returns now()
        }

        assertTrue(message.canBeEdited())
    }

    @Test
    fun `distribution list message can not be edited`() {
        val message = mockk<DistributionListMessageModel> {
            every { type } returns MessageType.TEXT
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { isOutbox } returns true
            every { createdAt } returns now()
            every { postedAt } returns now()
        }

        assertFalse(message.canBeEdited())
    }

    @Test
    fun `canDeleteRemotely respects message age for contact receiver`() {
        // arrange
        val contactReceiver: ContactMessageReceiver = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val messageCreatedAtTooOld: Date = Date()
            .minus(duration = DeleteMessage.DELETE_MESSAGES_MAX_AGE.milliseconds)
            .minus(duration = 1.days)
        val message: AbstractMessageModel = createAbstractMessageModel(
            type = MessageType.TEXT,
            isStatusMessage = false,
            isDeleted = false,
            isOutbox = true,
            createdAt = messageCreatedAtTooOld,
            postedAt = messageCreatedAtTooOld.plus(100.milliseconds),
            state = MessageState.SENT,
        )

        // act
        val result: Boolean = MessageUtil.canDeleteRemotely(message, contactReceiver)

        // assert
        assertFalse(result)
    }

    @Test
    fun `canDeleteRemotely respects old message age for group receiver`() {
        // arrange
        val ownIdentity = TestData.Identities.ME
        val creatorIdentity = TestData.Identities.OTHER_1
        val groupMessageReceiver: GroupMessageReceiver = TestData.createAndMockGroupMessageReceiver(
            groupDatabaseId = 1L,
            ownIdentity = ownIdentity,
            creatorIdentity = creatorIdentity,
            otherMembers = emptySet(),
            databaseBackendMock = databaseBackendMock,
            contactModelRepositoryMock = contactModelRepositoryMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val messageCreatedAtTooOld: Date = Date()
            .minus(duration = DeleteMessage.DELETE_MESSAGES_MAX_AGE.milliseconds)
            .minus(duration = 1.days)
        val message: AbstractMessageModel = createAbstractMessageModel(
            type = MessageType.TEXT,
            isStatusMessage = false,
            isDeleted = false,
            isOutbox = true,
            createdAt = messageCreatedAtTooOld,
            postedAt = messageCreatedAtTooOld.plus(100.milliseconds),
            state = MessageState.SENT,
        )

        // act
        val result: Boolean = MessageUtil.canDeleteRemotely(message, groupMessageReceiver)

        // assert
        assertFalse(result)
    }

    @Test
    fun `canDeleteRemotely returns true for young message age for group receiver`() {
        // arrange
        val ownIdentity = TestData.Identities.ME
        val creatorIdentity = TestData.Identities.OTHER_1
        val groupMessageReceiver: GroupMessageReceiver = TestData.createAndMockGroupMessageReceiver(
            groupDatabaseId = 1L,
            ownIdentity = ownIdentity,
            creatorIdentity = creatorIdentity,
            otherMembers = emptySet(),
            databaseBackendMock = databaseBackendMock,
            contactModelRepositoryMock = contactModelRepositoryMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val messageCreatedAtYoungEnough: Date = Date()
            .minus(duration = (DeleteMessage.DELETE_MESSAGES_MAX_AGE / 2).milliseconds)
        val message: AbstractMessageModel = createAbstractMessageModel(
            type = MessageType.TEXT,
            isStatusMessage = false,
            isDeleted = false,
            isOutbox = true,
            createdAt = messageCreatedAtYoungEnough,
            postedAt = messageCreatedAtYoungEnough.plus(100.milliseconds),
            state = MessageState.SENT,
        )

        // act
        val result: Boolean = MessageUtil.canDeleteRemotely(message, groupMessageReceiver)

        // assert
        assertTrue(result)
    }

    @Test
    fun `canDeleteRemotely ignores message age for notes group`() {
        // arrange
        val ownIdentity = TestData.Identities.ME
        val groupMessageReceiver: GroupMessageReceiver = TestData.createAndMockGroupMessageReceiver(
            groupDatabaseId = 1L,
            ownIdentity = ownIdentity,
            creatorIdentity = ownIdentity,
            otherMembers = emptySet(),
            databaseBackendMock = databaseBackendMock,
            contactModelRepositoryMock = contactModelRepositoryMock,
            serviceManagerMock = serviceManagerMock,
            coreServiceManagerMock = coreServiceManagerMock,
        )
        val messageCreatedAtTooOld: Date = Date()
            .minus(duration = DeleteMessage.DELETE_MESSAGES_MAX_AGE.milliseconds)
            .minus(duration = 1.days)
        val message: AbstractMessageModel = createAbstractMessageModel(
            type = MessageType.TEXT,
            isStatusMessage = false,
            isDeleted = false,
            isOutbox = true,
            createdAt = messageCreatedAtTooOld,
            postedAt = messageCreatedAtTooOld.plus(100.milliseconds),
            state = MessageState.SENT,
        )

        // act
        val result: Boolean = MessageUtil.canDeleteRemotely(message, groupMessageReceiver)

        // assert
        assertTrue(result)
    }

    private fun messageModelWithType(messageType: MessageType) = MessageModel().apply {
        type = messageType
    }

    companion object {
        private fun createFileDataModel(@RenderingType renderingType: Int) = FileDataModel(
            /* mimeType = */
            "image/jpeg",
            /* thumbnailMimeType = */
            "image/jpeg",
            /* fileSize = */
            100,
            /* fileName = */
            null,
            /* renderingType = */
            renderingType,
            /* caption = */
            "A photo without name",
            /* isDownloaded = */
            true,
            /* metaData = */
            HashMap(),
        )

        private fun createAbstractMessageModel(
            type: MessageType,
            isStatusMessage: Boolean = false,
            isDeleted: Boolean = false,
            isOutbox: Boolean,
            createdAt: Date?,
            postedAt: Date? = null,
            state: MessageState? = null,
        ): AbstractMessageModel {
            return mockk<MessageModel> {
                val message = this
                every { message.type } returns type
                every { message.isStatusMessage } returns isStatusMessage
                every { message.isDeleted } returns isDeleted
                every { message.isOutbox } returns isOutbox
                every { message.createdAt } returns createdAt
                every { message.postedAt } returns postedAt
                every { message.state } returns state
            }
        }
    }
}
