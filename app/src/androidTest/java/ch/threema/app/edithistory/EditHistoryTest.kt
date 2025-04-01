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

package ch.threema.app.edithistory

import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ch.threema.app.DangerousTest
import ch.threema.app.activities.HomeActivity
import ch.threema.app.asynctasks.AndroidContactLinkPolicy
import ch.threema.app.asynctasks.ContactSyncPolicy
import ch.threema.app.asynctasks.DeleteContactServices
import ch.threema.app.asynctasks.EmptyOrDeleteConversationsAsyncTask
import ch.threema.app.asynctasks.MarkContactAsDeletedBackgroundTask
import ch.threema.app.processors.MessageProcessorProvider
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.data.repositories.ContactModelRepository
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
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.factories.MessageModelFactory
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class EditHistoryTest : MessageProcessorProvider() {

    private val messageService: MessageService by lazy { serviceManager.messageService }
    private val contactService: ContactService by lazy { serviceManager.contactService }
    private val groupService: GroupService by lazy { serviceManager.groupService }
    private val databaseService: DatabaseServiceNew by lazy { serviceManager.databaseServiceNew }
    private val messageModelFactory: MessageModelFactory by lazy { databaseService.messageModelFactory }
    private val groupMessageModelFactory: GroupMessageModelFactory by lazy { databaseService.groupMessageModelFactory }
    private val editHistoryDao: EditHistoryDao by lazy { EditHistoryDaoImpl(databaseService) }
    private val contactModelRepository: ContactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val deleteContactServices: DeleteContactServices by lazy {
        DeleteContactServices(
            serviceManager.userService,
            contactService,
            serviceManager.conversationService,
            serviceManager.ringtoneService,
            serviceManager.mutedChatsListService,
            serviceManager.hiddenChatsListService,
            serviceManager.profilePicRecipientsService,
            serviceManager.wallpaperService,
            serviceManager.fileService,
            serviceManager.excludedSyncIdentitiesService,
            serviceManager.dhSessionStore,
            serviceManager.notificationService,
            databaseService,
        )
    }

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
                setOf(messageModel.identity),
                contactModelRepository,
                deleteContactServices,
                ContactSyncPolicy.INCLUDE,
                AndroidContactLinkPolicy.KEEP,
            )
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

        groupService.leaveOrDissolveAndRemoveFromLocal(groupA.groupModel)

        messageModel.assertHistorySize(0)
    }

    private suspend fun AbstractMessageModel.emptyOrDeleteChat(mode: EmptyOrDeleteConversationsAsyncTask.Mode) {
        val receiver = messageService.getMessageReceiver(this)
        val deferred = CompletableDeferred<Unit>()
        @Suppress("DEPRECATION")
        EmptyOrDeleteConversationsAsyncTask(
            mode,
            arrayOf(receiver),
            serviceManager.conversationService,
            serviceManager.groupService,
            serviceManager.distributionListService,
            null, null
        ) { deferred.complete(Unit) }.execute()
        deferred.await()
    }

    private suspend fun receiveTextMessage(): MessageModel {
        val message = TextMessage().apply {
            text = "Original Text"
            fromIdentity = contactA.identity
            toIdentity = myContact.identity
            messageId = MessageId()
        }

        processMessage(message, contactA.identityStore)

        return messageModelFactory.getByApiMessageIdAndIdentity(
            message.messageId,
            message.fromIdentity
        )!!
    }

    private suspend fun MessageModel.receiveEdit() {
        val editMessage = EditMessage(
            EditMessageData(
                MessageId.fromString(apiMessageId).messageIdLong,
                "$body Edited"
            )
        ).apply {
            fromIdentity = identity
            toIdentity = myContact.identity
        }
        processMessage(editMessage, contactA.identityStore)
    }

    private suspend fun MessageModel.receiveDelete() {
        val deleteMessage = DeleteMessage(
            DeleteMessageData(MessageId.fromString(apiMessageId).messageIdLong)
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
            messageId = MessageId()
        }

        processMessage(message, contactA.identityStore)

        return groupMessageModelFactory.getByApiMessageIdAndIdentity(
            message.messageId,
            message.fromIdentity
        )!!
    }

    private suspend fun GroupMessageModel.receiveEdit() {
        val editMessage = GroupEditMessage(
            EditMessageData(
                MessageId.fromString(apiMessageId).messageIdLong,
                "$body Edited"
            )
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
            DeleteMessageData(MessageId.fromString(apiMessageId).messageIdLong)
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
            editHistoryDao.findAllByMessageUid(uid).size
        )
    }
}
