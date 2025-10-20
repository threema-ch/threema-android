/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.utils.toBasicContacts
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.Identity
import java.util.Date

abstract class OutgoingCspGroupControlMessageTask(serviceManager: ServiceManager) :
    OutgoingCspMessageTask(serviceManager) {
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val blockedIdentitiesService by lazy { serviceManager.blockedIdentitiesService }
    private val apiConnector by lazy { serviceManager.apiConnector }

    protected abstract val messageId: MessageId
    protected abstract val creatorIdentity: Identity
    protected abstract val groupId: GroupId
    protected abstract val recipientIdentities: Set<Identity>
    protected open val date: Date = Date()

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val recipients = recipientIdentities
            .toBasicContacts(contactModelRepository, contactStore, apiConnector)
            .toSet()

        val messageCreator = OutgoingCspGroupMessageCreator(
            messageId,
            date,
            groupId,
            creatorIdentity,
        ) { createGroupMessage() }

        val outgoingCspMessageHandle = OutgoingCspMessageHandle(
            recipients,
            messageCreator,
        )

        handle.runBundledMessagesSendSteps(
            outgoingCspMessageHandle,
            OutgoingCspMessageServices(
                forwardSecurityMessageProcessor,
                identityStore,
                userService,
                contactStore,
                contactService,
                contactModelRepository,
                groupService,
                nonceFactory,
                blockedIdentitiesService,
                preferenceService,
                multiDeviceManager,
            ),
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
