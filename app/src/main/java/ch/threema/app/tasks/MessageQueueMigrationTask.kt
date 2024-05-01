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

package ch.threema.app.tasks

import android.content.Context
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.utils.FileUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.NetworkException
import ch.threema.domain.taskmanager.waitForServerAck
import ch.threema.localcrypto.MasterKey
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.factories.MessageModelFactory
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("MessageQueueMigrationTask")

// TODO(ANDR-2626): Remove this task as soon as the file has been deleted for most users
/**
 * This task reads the legacy message queue and sends the messages if there are some. Afterwards,
 * the message queue file is deleted.
 */
class MessageQueueMigrationTask(
    private val context: Context,
    private val myIdentity: String?,
    private val messageService: MessageService,
    private val groupService: GroupService,
    private val messageModelFactory: MessageModelFactory,
    private val groupMessageModelFactory: GroupMessageModelFactory,
) : ActiveTask<Unit> {
    private val messageQueueSaveFile = "msgqueue.ser"

    override val type: String = "MessageQueueMigrationTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val messageQueueFile = File(context.filesDir, messageQueueSaveFile)
        if (!messageQueueFile.exists()) {
            logger.debug("No message queue file found")
            return
        }

        val masterKey = ThreemaApplication.getMasterKey()
        if (masterKey == null) {
            logger.warn("No master key. Deleting the message queue file")
            FileUtil.deleteFileOrWarn(messageQueueFile, "message queue file", logger)
            return
        }

        if (masterKey.isLocked) {
            logger.warn("Could not restore messages from message queue, as the master key was locked")
            FileUtil.deleteFileOrWarn(messageQueueFile, "message queue file", logger)
            return
        }

        try {
            // After 30 seconds, we give up (to prevent waiting forever)
            withTimeout(30000) {
                sendMessages(masterKey, messageQueueFile, handle)
            }
        } catch (e: Exception) {
            when (e) {
                is NetworkException -> throw e
                else -> logger.error("Could not send queued messages", e)
            }
        }

        logger.info("Deleting message queue file")
        FileUtil.deleteFileOrWarn(messageQueueFile, "Remove message queue", logger)
    }

    private suspend fun sendMessages(masterKey: MasterKey, file: File, handle: ActiveTaskCodec) {
        withContext(Dispatchers.IO) {
            return@withContext MessageBoxInputStream(masterKey.getCipherInputStream(FileInputStream(file))).use {
                val messages = mutableListOf<MessageBox>()
                while (true) {
                    if (!coroutineContext.isActive) {
                        break
                    }

                    try {
                        val messageBox = it.readObject() as MessageBox

                        if (myIdentity != messageBox.fromIdentity) {
                            logger.warn(
                                "Skipping outgoing message from wrong identity ({}) in queue",
                                messageBox.fromIdentity
                            )
                            continue
                        }

                        logger.info(
                            "Sending message {} from message queue file",
                            messageBox.messageId
                        )

                        messages.add(messageBox)
                    } catch (e: Exception) {
                        when (e) {
                            is EOFException -> {
                                logger.info("Finished reading message queue file")
                                break
                            }
                            else -> {
                                logger.error("Error while reading message from queue file", e)
                                // Abort as the stream may be corrupt and therefore never succeeds
                                break
                            }
                        }
                    }
                }
                return@use messages
            }
        }.forEach { sendMessage(it, handle) }
    }

    private suspend fun sendMessage(messageBox: MessageBox, handle: ActiveTaskCodec) {
        // Send message
        handle.write(messageBox.creatCspMessage())

        val messageId = messageBox.messageId
        val recipientIdentity = messageBox.toIdentity

        // Wait until message has been sent
        handle.waitForServerAck(messageId, recipientIdentity)

        // Update state if message model found for message
        updateMessageModel(messageId, recipientIdentity)
    }

    private fun updateMessageModel(messageId: MessageId, identity: String) {
        val date = Date()
        val messageModels = getMatchingMessageModels(messageId, identity).filterNotNull()
        // We update the state for each message model that fits the message id and identity. Note
        // that for group messages, the message state is set to sent too early. Since this is only
        // needed for the migration to the task manager queue, this is acceptable.
        messageModels.forEach { messageService.updateMessageState(it, MessageState.SENT, date) }
    }

    private fun getMatchingMessageModels(
        messageId: MessageId,
        identity: String,
    ): List<AbstractMessageModel?> {
        return groupService.getGroupsByIdentity(identity).mapNotNull {
            groupMessageModelFactory.getByApiMessageIdAndGroupId(messageId, it.id)
        } + messageModelFactory.getByApiMessageIdAndIdentity(messageId, identity)
    }

}

private class MessageBoxInputStream(input: InputStream) : ObjectInputStream(input) {
    override fun readClassDescriptor(): ObjectStreamClass {
        val readClassDescriptor = super.readClassDescriptor()
        val localClass = Class.forName(readClassDescriptor.name)
        val localClassDescriptor = ObjectStreamClass.lookup(localClass)
        if (localClassDescriptor != null && localClassDescriptor.serialVersionUID != readClassDescriptor.serialVersionUID) {
            // Note that this is dangerous if the MessageBox class changes. However, this is only
            // migration code and new message boxes won't be deserialized here.
            return localClassDescriptor
        }
        return readClassDescriptor
    }
}
