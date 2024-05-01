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

import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.filterValid
import ch.threema.app.utils.sendMessageToReceivers
import ch.threema.app.utils.toKnownContactModels
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import java.util.Date

abstract class OutgoingCspGroupControlMessageTask(serviceManager: ServiceManager) :
    OutgoingCspMessageTask(serviceManager) {
    private val contactService by lazy { serviceManager.contactService }
    private val forwardSecurityMessageProcessor by lazy { serviceManager.forwardSecurityMessageProcessor }
    private val identityStore by lazy { serviceManager.identityStore }
    private val contactStore by lazy { serviceManager.contactStore }
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val taskCreator by lazy { serviceManager.taskCreator }
    private val blackListService by lazy { serviceManager.blackListService }

    protected abstract val messageId: MessageId
    protected abstract val creatorIdentity: String
    protected abstract val groupId: GroupId
    protected abstract val recipientIdentities: Set<String>
    protected open val date: Date = Date()

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val recipients = recipientIdentities
            .toSet()
            .toKnownContactModels(contactService)
            .filterValid()
            .toSet()

        val messageCreator = OutgoingCspGroupMessageCreator(
            messageId,
            groupId,
            creatorIdentity
        ) { createGroupMessage() }

        // Note that the given recipients may no longer be part of the group. Therefore we must use
        // sendMessageToReceivers instead of sendGroupMessage.
        handle.sendMessageToReceivers(
            messageCreator,
            recipients,
            forwardSecurityMessageProcessor,
            identityStore,
            contactStore,
            nonceFactory,
            blackListService,
            taskCreator
        )
    }

    /**
     * Get the group message that will be sent. Note that this message must contain all the message
     * specific information. The message id, group id, creator identity, to identity, and the date
     * will be added before sending the message.
     *
     * Every invocation of this method must create a new instance of the message.
     */
    abstract fun createGroupMessage(): AbstractGroupMessage
}
