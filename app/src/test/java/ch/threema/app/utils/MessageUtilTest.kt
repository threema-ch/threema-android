/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.app.utils

import ch.threema.app.AppConstants
import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.common.minus
import ch.threema.common.now
import ch.threema.common.plus
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.domain.protocol.csp.messages.file.FileData.RenderingType
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.Identity
import ch.threema.storage.DatabaseService
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.FileDataModel
import ch.threema.testhelpers.nonSecureRandomArray
import ch.threema.testhelpers.randomIdentity
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
    }

    private val blockedIdentitiesServiceMock = mockk<BlockedIdentitiesService>()
    private val serviceManagerMock: ServiceManager = mockk<ServiceManager> {
        every { multiDeviceManager } returns multiDeviceManagerMock
        every { taskManager } returns taskManagerMock
    }

    private val databaseBackendMock: DatabaseBackend = mockk<DatabaseBackend>()
    private val contactModelRepositoryMock: ContactModelRepository = mockk<ContactModelRepository>()
    private val groupModelRepositoryMock: GroupModelRepository = mockk<GroupModelRepository>()

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
        val contactMessageReceiver = createContactMessageReceiver()

        // Message receiver without affected receivers
        val allReceivers: List<MessageReceiver<*>> = MessageUtil.getAllReceivers(contactMessageReceiver)
        assertEquals(1, allReceivers.size)
        assertEquals(contactMessageReceiver, allReceivers[0])
    }

    @Test
    fun allReceivers_message_with_affected_receivers() {
        val identities = listOf("ABCDEFG1", "ABCDEFG2", "ABCDEFG3")
        val distributionListReceiver = createDistributionListMessageReceiver(
            identities.map { it to nonSecureRandomArray(32) },
        )
        val allReceivers: List<MessageReceiver<*>> = MessageUtil.getAllReceivers(distributionListReceiver)
        assertEquals(4, allReceivers.size)
        assertEquals(distributionListReceiver, allReceivers[0])
        identities.forEachIndexed { index: Int, identity ->
            assertEquals(identity, allReceivers[index + 1].identities[0])
        }
    }

    @Test
    fun addDistributionListReceivers_empty_message_receivers_list() {
        assertEquals(0, MessageUtil.addDistributionListReceivers(arrayOfNulls(0)).size)
    }

    @Test
    fun addDistributionListReceivers_must_contain_passed_receivers() {
        val contactMessageReceiver: MessageReceiver<*> = createContactMessageReceiver()
        val distributionListReceiver = createDistributionListMessageReceiver(emptyList())

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
        val contactMessageReceiver1: MessageReceiver<*> = createContactMessageReceiver("ABCDEFG1", nonSecureRandomArray(32))
        val contactMessageReceiver2: MessageReceiver<*> = createContactMessageReceiver("ABCDEFG2", nonSecureRandomArray(32))
        val contactMessageReceiver3: MessageReceiver<*> = createContactMessageReceiver("ABCDEFG3", nonSecureRandomArray(32))
        val identity4 = "ABCDEFG4"
        val identity5 = "ABCDEFG5"
        val emptyDistributionListMessageReceiver = createDistributionListMessageReceiver(emptyList())
        val distributionListMessageReceiver = createDistributionListMessageReceiver(
            listOf(
                identity4 to nonSecureRandomArray(32),
                identity5 to nonSecureRandomArray(32),
            ),
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
        assertEquals(identity4, resolvedReceivers[4].identities[0])
        assertEquals(identity5, resolvedReceivers[5].identities[0])
        assertEquals(contactMessageReceiver3, resolvedReceivers[6])
    }

    @Test
    fun addDistributionListReceivers_must_preserve_order_of_receivers_and_remove_duplicates() {
        val publicKey1 = nonSecureRandomArray(32)
        val publicKey2 = nonSecureRandomArray(32)
        val publicKey3 = nonSecureRandomArray(32)
        val publicKey4 = nonSecureRandomArray(32)
        val publicKey5 = nonSecureRandomArray(32)

        val contactMessageReceiver1 = createContactMessageReceiver("ABCDEFG1", publicKey1)
        val contactMessageReceiver2 = createContactMessageReceiver("ABCDEFG2", publicKey2)
        val contactMessageReceiver3 = createContactMessageReceiver("ABCDEFG3", publicKey3)

        val duplicate1 = createContactMessageReceiver("ABCDEFG1", publicKey1)

        val identity2 = "ABCDEFG2"
        val identity3 = "ABCDEFG3"
        val identity4 = "ABCDEFG4"
        val identity5 = "ABCDEFG5"

        val emptyDistributionListMessageReceiver = createDistributionListMessageReceiver(emptyList())
        val distributionListMessageReceiver = createDistributionListMessageReceiver(
            listOf(
                identity4 to publicKey4,
                identity5 to publicKey5,
                identity2 to publicKey2,
                identity3 to publicKey3,
            ),
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
        assertEquals(identity4, resolvedReceivers[4].identities[0])
        assertEquals(identity5, resolvedReceivers[5].identities[0])
        assertEquals(identity3, resolvedReceivers[6].identities[0])
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
        val contactReceiver: ContactMessageReceiver = createContactMessageReceiver(
            identity = randomIdentity(),
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
        val ownIdentity: String = randomIdentity()
        val creatorIdentity: String = randomIdentity()
        val groupMessageReceiver: GroupMessageReceiver = createGroupMessageReceiver(
            ownIdentity = ownIdentity,
            creatorIdentity = creatorIdentity,
            otherMembers = setOf(creatorIdentity),
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
        val ownIdentity: String = randomIdentity()
        val creatorIdentity: String = randomIdentity()
        val groupMessageReceiver: GroupMessageReceiver = createGroupMessageReceiver(
            ownIdentity = ownIdentity,
            creatorIdentity = creatorIdentity,
            otherMembers = setOf(creatorIdentity),
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
        val ownIdentity: String = randomIdentity()
        val groupMessageReceiver: GroupMessageReceiver = createGroupMessageReceiver(
            ownIdentity = ownIdentity,
            creatorIdentity = ownIdentity,
            otherMembers = emptySet(),
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

    private fun createContactMessageReceiver(
        identity: Identity = randomIdentity(),
        publicKey: ByteArray = nonSecureRandomArray(32),
    ): ContactMessageReceiver {
        val contactModel = ContactModel.create(identity, publicKey)
        every {
            contactModelRepositoryMock.getByIdentity(any())
        } returns ch.threema.data.models.ContactModel(
            identity = identity,
            data = ContactModelData(
                identity = identity,
                publicKey = publicKey,
                createdAt = Date(),
                firstName = "firstname",
                lastName = "lastname",
                nickname = "nickname",
                idColor = IdColor(0),
                verificationLevel = VerificationLevel.FULLY_VERIFIED,
                workVerificationLevel = WorkVerificationLevel.NONE,
                identityType = IdentityType.NORMAL,
                acquaintanceLevel = ContactModel.AcquaintanceLevel.DIRECT,
                activityState = IdentityState.ACTIVE,
                featureMask = 1u,
                syncState = ContactSyncState.INITIAL,
                readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                isArchived = false,
                androidContactLookupInfo = AndroidContactLookupInfo("androidcontactlookupkey", 42),
                localAvatarExpires = Date(),
                isRestored = false,
                profilePictureBlobId = byteArrayOf(0),
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
            ),
            databaseBackendMock,
            contactModelRepositoryMock,
            coreServiceManagerMock,
        )

        return ContactMessageReceiver(
            /* contact = */
            contactModel,
            /* contactService = */
            null,
            /* serviceManager = */
            serviceManagerMock,
            /* databaseService = */
            null,
            /* identityStore = */
            null,
            /* blockedIdentitiesService = */
            blockedIdentitiesServiceMock,
            /* contactModelRepository = */
            contactModelRepositoryMock,
        )
    }

    /**
     *  Create a GroupMessageReceiver and mock the [contactModelRepository] to return it.
     *
     *  @param otherMembers has to follow the rules defined in [GroupModelData.otherMembers].
     */
    private fun createGroupMessageReceiver(
        ownIdentity: Identity,
        creatorIdentity: Identity,
        otherMembers: Set<Identity>,
        groupService: GroupService = mockk(),
        databaseService: DatabaseService = mockk(),
        databaseBackend: DatabaseBackend = databaseBackendMock,
        contactService: ContactService = mockk(),
        contactModelRepository: ContactModelRepository = contactModelRepositoryMock,
        serviceManager: ServiceManager = serviceManagerMock,
        coreServiceManager: CoreServiceManager = coreServiceManagerMock,
        groupModelRepository: GroupModelRepository = groupModelRepositoryMock,
    ): GroupMessageReceiver {
        val localGroupDbId = 0
        val groupId = GroupId(nonSecureRandomArray(ProtocolDefines.GROUP_ID_LEN))
        val groupIdentity = GroupIdentity(
            creatorIdentity = creatorIdentity,
            groupId = groupId.toLong(),
        )

        val groupModel: GroupModel = GroupModel().apply {
            this.id = localGroupDbId
            this.apiGroupId = groupId
            this.creatorIdentity = creatorIdentity
        }

        val newGroupModel = ch.threema.data.models.GroupModel(
            groupIdentity = groupIdentity,
            data = GroupModelData(
                groupIdentity = groupIdentity,
                name = null,
                createdAt = Date(),
                synchronizedAt = null,
                lastUpdate = null,
                isArchived = false,
                precomputedIdColor = IdColor.invalid(),
                groupDescription = null,
                groupDescriptionChangedAt = null,
                otherMembers = otherMembers,
                userState = GroupModel.UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
            ),
            databaseBackend = databaseBackend,
            coreServiceManager = coreServiceManager,
        )

        every {
            groupModelRepository.getByCreatorIdentityAndId(
                creatorIdentity = creatorIdentity,
                groupId = groupId,
            )
        } returns newGroupModel

        every {
            groupModelRepository.getByGroupIdentity(
                groupIdentity = GroupIdentity(
                    creatorIdentity = creatorIdentity,
                    groupId = groupId.toLong(),
                ),
            )
        } returns newGroupModel

        every {
            groupModelRepository.getByLocalGroupDbId(
                localGroupDbId = localGroupDbId.toLong(),
            )
        } returns newGroupModel

        every {
            coreServiceManager.identityStore.getIdentity()
        } returns ownIdentity

        return GroupMessageReceiver(
            /* group = */
            groupModel,
            /* groupService = */
            groupService,
            /* databaseService = */
            databaseService,
            /* contactService = */
            contactService,
            /* contactModelRepository = */
            contactModelRepository,
            /* groupModelRepository = */
            groupModelRepository,
            /* serviceManager = */
            serviceManager,
        )
    }

    private fun createDistributionListMessageReceiver(
        identitiesWithPublicKey: List<Pair<String, ByteArray>>,
    ): MessageReceiver<*> {
        val distributionListServiceMock = mockk<DistributionListService>()
        val contacts: List<ContactModel> = identitiesWithPublicKey.map { ContactModel.create(it.first, it.second) }

        every { distributionListServiceMock.getMembers(any()) } returns contacts

        val contactServiceMock = mockk<ContactService>()

        every { contactModelRepositoryMock.getByIdentity(any()) } answers { call ->

            val identity = call.invocation.args.first() as String

            ch.threema.data.models.ContactModel(
                identity = identity,
                data = ContactModelData(
                    identity = identity,
                    publicKey = contacts.first { it.identity == identity }.publicKey,
                    createdAt = Date(),
                    firstName = "firstname",
                    lastName = "lastname",
                    nickname = "nickname",
                    idColor = IdColor(0),
                    verificationLevel = VerificationLevel.FULLY_VERIFIED,
                    workVerificationLevel = WorkVerificationLevel.NONE,
                    identityType = IdentityType.NORMAL,
                    acquaintanceLevel = ContactModel.AcquaintanceLevel.DIRECT,
                    activityState = IdentityState.ACTIVE,
                    featureMask = 1u,
                    syncState = ContactSyncState.INITIAL,
                    readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                    typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                    isArchived = false,
                    androidContactLookupInfo = AndroidContactLookupInfo("androidcontactlookupkey", 42),
                    localAvatarExpires = Date(),
                    isRestored = false,
                    profilePictureBlobId = byteArrayOf(0),
                    department = null,
                    jobTitle = null,
                    notificationTriggerPolicyOverride = null,
                ),
                databaseBackendMock,
                contactModelRepositoryMock,
                coreServiceManagerMock,
            )
        }

        every {
            contactServiceMock.createReceiver(any() as ContactModel)
        } answers { call ->
            val contactModel = call.invocation.args.first() as ContactModel
            ContactMessageReceiver(
                /* contact = */
                contactModel,
                /* contactService = */
                null,
                /* serviceManager = */
                serviceManagerMock,
                /* databaseService = */
                null,
                /* identityStore = */
                null,
                /* blockedIdentitiesService = */
                blockedIdentitiesServiceMock,
                /* contactModelRepository = */
                contactModelRepositoryMock,
            )
        }

        return DistributionListMessageReceiver(
            /* databaseService = */
            null,
            /* contactService = */
            contactServiceMock,
            /* distributionListModel = */
            null,
            /* distributionListService = */
            distributionListServiceMock,
        )
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
