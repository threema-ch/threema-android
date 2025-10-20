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

import ch.threema.app.groupflows.DisbandGroupFlow
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.OutgoingCspMessageServices.Companion.getOutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.utils.toBasicContacts
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.storage.models.GroupModel
import java.util.Date
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OutgoingGroupDisbandTask")

/**
 * This task is used to send out an empty [GroupSetupMessage] to each member of the given group.
 * Note that this task should only be scheduled by the [DisbandGroupFlow] as it only handles csp
 * messages.
 */
class OutgoingGroupDisbandTask(
    private val groupIdentity: GroupIdentity,
    private val members: Set<String>,
    private val messageId: MessageId,
    private val groupModelRepository: GroupModelRepository,
    private val apiConnector: APIConnector,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
) : ActiveTask<Unit>, PersistableTask {
    override val type = "OutgoingGroupDisbandTask"

    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (multiDeviceManager.isMultiDeviceActive) {
            try {
                sendEmptySetupMessagesInTransaction(handle)
            } catch (e: TransactionScope.TransactionException) {
                logger.warn("A group sync race occurred", e)
            }
        } else {
            sendEmptySetupMessages(handle)
        }
    }

    private suspend fun sendEmptySetupMessagesInTransaction(handle: ActiveTaskCodec) {
        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

        handle.createTransaction(
            keys = multiDeviceProperties.keys,
            scope = MdD2D.TransactionScope.Scope.GROUP_SYNC,
            ttl = TRANSACTION_TTL_MAX,
            precondition = {
                val groupModelData = groupModelRepository.getByGroupIdentity(groupIdentity)?.data
                if (groupModelData != null) {
                    // If the group exists, then the user state must be left
                    val isLeft = groupModelData.userState == GroupModel.UserState.LEFT

                    if (!isLeft) {
                        logger.error("A major group state inconsistency detected: group must be left")
                    }

                    isLeft
                } else {
                    // It is fine if the group does not exist
                    true
                }
            },
        ).execute {
            sendEmptySetupMessages(handle)
        }
    }

    private suspend fun sendEmptySetupMessages(handle: ActiveTaskCodec) {
        val receivers = members.toBasicContacts(
            outgoingCspMessageServices.contactModelRepository,
            outgoingCspMessageServices.contactStore,
            apiConnector,
        ).toSet()

        handle.runBundledMessagesSendSteps(
            OutgoingCspMessageHandle(
                receivers,
                OutgoingCspGroupMessageCreator(
                    messageId,
                    Date(),
                    groupIdentity,
                ) {
                    GroupSetupMessage().also {
                        it.members = emptyArray()
                    }
                },
            ),
            outgoingCspMessageServices,
        )
    }

    override fun serialize() =
        OutgoingGroupDisbandTaskData(groupIdentity, members, messageId.messageId)

    @Serializable
    class OutgoingGroupDisbandTaskData(
        private val groupIdentity: GroupIdentity,
        private val members: Set<String>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupDisbandTask(
                groupIdentity,
                members,
                MessageId(messageId),
                serviceManager.modelRepositories.groups,
                serviceManager.apiConnector,
                serviceManager.getOutgoingCspMessageServices(),
            )
    }
}
