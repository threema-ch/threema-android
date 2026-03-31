package ch.threema.app.edithistory

import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ch.threema.app.DangerousTest
import ch.threema.app.asynctasks.AndroidContactLinkPolicy
import ch.threema.app.asynctasks.ContactSyncPolicy
import ch.threema.app.asynctasks.DeleteContactServices
import ch.threema.app.asynctasks.EmptyOrDeleteConversationsAsyncTask
import ch.threema.app.asynctasks.MarkContactAsDeletedBackgroundTask
import ch.threema.app.di.injectNonBinding
import ch.threema.app.groupflows.GroupLeaveIntent
import ch.threema.app.home.HomeActivity
import ch.threema.app.processors.MessageProcessorProvider
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.data.models.GroupIdentity
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.data.storage.EditHistoryDao
import ch.threema.data.storage.EditHistoryDaoImpl
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.protocol.csp.messages.DeleteMessageData
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.protocol.csp.messages.EditMessageData
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.factories.MessageModelFactory
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.group.GroupMessageModel
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class EditHistoryTest : MessageProcessorProvider(), KoinComponent {
    private val groupModelRepository: GroupModelRepository by injectNonBinding()
    private val groupFlowDispatcher: GroupFlowDispatcher by injectNonBinding()
    private val conversationService: ConversationService by injectNonBinding()
    private val distributionListService: DistributionListService by injectNonBinding()
    private val messageService: MessageService by injectNonBinding()
    private val contactService: ContactService by injectNonBinding()
    private val groupService: GroupService by injectNonBinding()
    private val messageModelFactory: MessageModelFactory by injectNonBinding()
    private val groupMessageModelFactory: GroupMessageModelFactory by injectNonBinding()
    private val editHistoryDao: EditHistoryDao
        get() = EditHistoryDaoImpl(databaseProvider = get())
    private val contactModelRepository: ContactModelRepository by injectNonBinding()
    private val deleteContactServices: DeleteContactServices
        get() = DeleteContactServices(
            userService = get(),
            contactService = get(),
            conversationService = get(),
            ringtoneService = get(),
            conversationCategoryService = get(),
            profilePictureRecipientsService = get(),
            wallpaperService = get(),
            fileService = get(),
            excludedSyncIdentitiesService = get(),
            dhSessionStore = get(),
            notificationService = get(),
            contactModelFactory = get(),
        )

    @Test
    fun testHistoryDeletedOnContactMessageDelete() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        messageService.remove(messageModel)

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnGroupMessageDelete() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveGroupTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        messageService.remove(messageModel)

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnIncomingContactMessageRemoteDelete() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        messageModel.receiveDelete()

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnIncomingGroupMessageRemoteDelete() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveGroupTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        messageModel.receiveDelete()

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnOutgoingContactMessageRemoteDelete() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = sendContactTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.sendEdit()

        messageModel.assertHistorySize(1)

        messageModel.sendDelete()

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnOutgoingGroupMessageRemoteDelete() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = sendGroupTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.sendEdit()

        messageModel.assertHistorySize(1)

        messageModel.sendDelete()

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnEmptyContactChat() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        messageModel.emptyOrDeleteChat(EmptyOrDeleteConversationsAsyncTask.Mode.EMPTY)

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnEmptyGroupChat() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveGroupTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        messageModel.emptyOrDeleteChat(EmptyOrDeleteConversationsAsyncTask.Mode.EMPTY)

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnDeleteContactChat() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        messageModel.emptyOrDeleteChat(EmptyOrDeleteConversationsAsyncTask.Mode.DELETE)

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnDeleteGroupChat() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveGroupTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        messageModel.emptyOrDeleteChat(EmptyOrDeleteConversationsAsyncTask.Mode.DELETE)

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnContactDelete() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        BackgroundExecutor().executeDeferred(
            MarkContactAsDeletedBackgroundTask(
                setOf(messageModel.identity!!),
                contactModelRepository,
                deleteContactServices,
                ContactSyncPolicy.INCLUDE,
                AndroidContactLinkPolicy.KEEP,
            ),
        ).await()

        messageModel.assertHistorySize(0)
    }

    @Test
    fun testHistoryDeletedOnGroupDelete() = runTest {
        launchActivity<HomeActivity>()

        val messageModel = receiveGroupTextMessage()

        messageModel.assertHistorySize(0)

        messageModel.receiveEdit()

        messageModel.assertHistorySize(1)

        val groupModel = groupModelRepository.getByGroupIdentity(
            GroupIdentity(
                creatorIdentity = groupA.groupCreator.identity,
                groupId = groupA.apiGroupId.toLong(),
            ),
        )
        assertNotNull(groupModel)
        groupFlowDispatcher.runLeaveGroupFlow(
            intent = GroupLeaveIntent.LEAVE_AND_REMOVE,
            groupModel = groupModel,
        ).await()

        messageModel.assertHistorySize(0)
    }

    private suspend fun AbstractMessageModel.emptyOrDeleteChat(mode: EmptyOrDeleteConversationsAsyncTask.Mode) {
        val receiver = messageService.getMessageReceiver(this)
        val deferred = CompletableDeferred<Unit>()
        @Suppress("DEPRECATION")
        EmptyOrDeleteConversationsAsyncTask(
            mode,
            arrayOf(receiver),
            conversationService,
            distributionListService,
            groupModelRepository,
            groupFlowDispatcher,
            myContact.identity,
            null,
            null,
        ) { deferred.complete(Unit) }.execute()
        deferred.await()
    }

    private suspend fun receiveTextMessage(): MessageModel {
        val message = TextMessage().apply {
            text = "Original Text"
            fromIdentity = contactA.identity
            toIdentity = myContact.identity
            messageId = MessageId.random()
        }

        processMessage(message, contactA.identityStore)

        return messageModelFactory.getByApiMessageIdAndIdentity(
            message.messageId,
            message.fromIdentity,
        )!!
    }

    private suspend fun MessageModel.receiveEdit() {
        val editMessage = EditMessage(
            EditMessageData(
                MessageId.fromString(apiMessageId).messageIdLong,
                "$body Edited",
            ),
        ).apply {
            fromIdentity = identity
            toIdentity = myContact.identity
        }
        processMessage(editMessage, contactA.identityStore)
    }

    private suspend fun MessageModel.receiveDelete() {
        val deleteMessage = DeleteMessage(
            DeleteMessageData(MessageId.fromString(apiMessageId).messageIdLong),
        ).apply {
            fromIdentity = identity
            toIdentity = myContact.identity
        }
        processMessage(deleteMessage, contactA.identityStore)
    }

    private suspend fun receiveGroupTextMessage(): GroupMessageModel {
        val message = GroupTextMessage().apply {
            text = "Original Text"
            apiGroupId = groupA.apiGroupId
            groupCreator = groupA.groupCreator.identity
            fromIdentity = contactA.identity
            toIdentity = myContact.identity
            messageId = MessageId.random()
        }

        processMessage(message, contactA.identityStore)

        return groupMessageModelFactory.getByApiMessageIdAndIdentity(
            message.messageId,
            message.fromIdentity,
        )!!
    }

    private suspend fun GroupMessageModel.receiveEdit() {
        val editMessage = GroupEditMessage(
            EditMessageData(
                MessageId.fromString(apiMessageId).messageIdLong,
                "$body Edited",
            ),
        ).apply {
            apiGroupId = groupA.apiGroupId
            groupCreator = groupA.groupCreator.identity
            fromIdentity = identity
            toIdentity = myContact.identity
        }
        processMessage(editMessage, contactA.identityStore)
    }

    private suspend fun GroupMessageModel.receiveDelete() {
        val deleteMessage = GroupDeleteMessage(
            DeleteMessageData(MessageId.fromString(apiMessageId).messageIdLong),
        ).apply {
            apiGroupId = groupA.apiGroupId
            groupCreator = groupA.groupCreator.identity
            fromIdentity = identity
            toIdentity = myContact.identity
        }
        processMessage(deleteMessage, contactA.identityStore)
    }

    private fun sendContactTextMessage(): MessageModel {
        val receiver = contactService.createReceiver(contactA.contactModel)
        val message = messageService.sendText("Text", receiver) as MessageModel
        return message
    }

    private fun AbstractMessageModel.sendEdit() {
        val receiver = messageService.getMessageReceiver(this)
        messageService.sendEditedMessageText(this, "$body Edited", Date(), receiver)
    }

    private fun AbstractMessageModel.sendDelete() {
        val receiver = messageService.getMessageReceiver(this)
        messageService.sendDeleteMessage(this, receiver)
    }

    private fun sendGroupTextMessage(): GroupMessageModel {
        val receiver = groupService.createReceiver(groupA.groupModel)
        val message = messageService.sendText("Text", receiver) as GroupMessageModel

        return message
    }

    private fun <T : AbstractMessageModel> T.assertHistorySize(size: Int) {
        assertEquals(
            size,
            editHistoryDao.findAllByMessageUid(uid!!).size,
        )
    }
}
