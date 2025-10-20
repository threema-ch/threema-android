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

package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.ReflectGroupSyncUpdateImmediateTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.BasicContact
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.Identity

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupLeaveTask")

class IncomingGroupLeaveTask(
    message: GroupLeaveMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupLeaveMessage>(message, triggerSource, serviceManager) {
    private val groupService by lazy { serviceManager.groupService }
    private val userService by lazy { serviceManager.userService }
    private val groupCallManager by lazy { serviceManager.groupCallManager }
    private val contactStore by lazy { serviceManager.contactStore }
    private val contactService by lazy { serviceManager.contactService }
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val nonceFactory by lazy { serviceManager.nonceFactory }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        val creatorIdentity = message.groupCreator
        val senderIdentity = message.fromIdentity

        // If the sender is the creator of the group, abort these steps
        if (senderIdentity == creatorIdentity) {
            logger.warn("Discarding group leave message from group creator")
            return ReceiveStepsResult.DISCARD
        }

        val groupIdentity = GroupIdentity(message.groupCreator, message.apiGroupId.toLong())

        // Look up the group
        val groupModel = groupModelRepository.getByGroupIdentity(groupIdentity)

        val groupModelData = groupModel?.data

        if (groupModelData == null || !groupModelData.isMember) {
            if (userService.identity == creatorIdentity) {
                logger.info("The user is the creator of the group")
                return ReceiveStepsResult.DISCARD
            }
            val creatorContact = fetchCreatorContact(creatorIdentity) ?: run {
                logger.error("Could not get creator contact")
                return ReceiveStepsResult.DISCARD
            }
            runGroupSyncRequestSteps(
                creatorContact,
                groupIdentity,
                serviceManager,
                handle,
            )
            return ReceiveStepsResult.DISCARD
        }

        if (!groupModelData.otherMembers.contains(senderIdentity)) {
            logger.info("Sender is not a member")
            return ReceiveStepsResult.DISCARD
        }

        if (multiDeviceManager.isMultiDeviceActive) {
            val reflectionResult = ReflectGroupSyncUpdateImmediateTask.ReflectMemberLeft(
                senderIdentity,
                groupModel,
                nonceFactory,
                multiDeviceManager,
            ).reflect(handle)
            when (reflectionResult) {
                is ReflectionResult.PreconditionFailed -> {
                    logger.warn(
                        "Group sync race: Could not reflect contact leave",
                        reflectionResult.transactionException,
                    )
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.Failed -> {
                    logger.error("Could not reflect contact leave", reflectionResult.exception)
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.MultiDeviceNotActive -> {
                    // Note that this is an edge case that should never happen as deactivating md and processing incoming messages is both running in
                    // tasks. However, if it happens nevertheless, we can simply log a warning and continue processing the message.
                    logger.warn("Reflection failed because multi device is not active")
                }

                is ReflectionResult.Success -> Unit
            }
        }

        // If the user and the sender are participating in a group call of this group, remove the
        // sender from the group call (handle it as if the sender left the call)
        groupCallManager.removeGroupCallParticipants(setOf(senderIdentity), groupModel)

        // Remove the member from the group
        groupModel.removeLeftMemberFromRemote(senderIdentity)

        groupService.resetCache(groupModel.getDatabaseId().toInt())

        // Run the rejected messages refresh steps for the group
        groupService.runRejectedMessagesRefreshSteps(groupModel)

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.info("Discarding group leave from sync")
        return ReceiveStepsResult.DISCARD
    }

    private fun fetchCreatorContact(identity: Identity): BasicContact? {
        // Fetch and cache the contact. Note that the contact is only fetched from the server if the
        // contact is not already known.
        contactService.fetchAndCacheContact(identity)
        val basicContact =
            // If the contact is unknown, it should be cached at this point.
            contactStore.getCachedContact(identity)
                // If the contact is not cached, then this implies that the contact must be known.
                ?: contactModelRepository.getByIdentity(identity)?.data?.toBasicContact()

        return basicContact
    }
}
