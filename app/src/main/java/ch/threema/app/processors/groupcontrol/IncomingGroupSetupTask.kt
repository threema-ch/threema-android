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

package ch.threema.app.processors.groupcontrol

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.IncomingCspMessageSubTask
import ch.threema.app.processors.ReceiveStepsResult
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.filterValid
import ch.threema.app.utils.sendMessageToReceivers
import ch.threema.app.utils.toContactModels
import ch.threema.app.voip.groupcall.localGroupId
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.storage.models.GroupModel
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupSetupTask")

class IncomingGroupSetupTask(
    private val groupSetupMessage: GroupSetupMessage,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask(serviceManager) {
    private val userService = serviceManager.userService
    private val contactService = serviceManager.contactService
    private val groupService = serviceManager.groupService
    private val blackListService = serviceManager.blackListService
    private val groupCallManager = serviceManager.groupCallManager
    private val databaseService = serviceManager.databaseServiceNew
    private val contactStore = serviceManager.contactStore
    private val identityStore = serviceManager.identityStore
    private val nonceFactory = serviceManager.nonceFactory
    private val apiConnector by lazy { serviceManager.apiConnector }
    private val taskCreator by lazy { serviceManager.taskCreator }
    private val preferenceService = serviceManager.preferenceService
    private val forwardSecurityMessageProcessor by lazy { serviceManager.forwardSecurityMessageProcessor }

    override suspend fun run(handle: ActiveTaskCodec): ReceiveStepsResult {
        // 1. Let members be the given member list. Remove all duplicate entries from members.
        // Remove the sender from members if present.
        val sender = groupSetupMessage.fromIdentity
        val members = groupSetupMessage.members.filter { it != sender }.toSet()
        val myIdentity = userService.identity

        // 2. Look up the group
        var group = groupService.getByGroupMessage(groupSetupMessage)
        val previousMemberCount = if (group != null) {
            groupService.countMembers(group)
        } else {
            0
        }

        val newGroup = group == null

        // 3. If the group could not be found:
        if (group == null) {
            // 3.1 If the user is not present in members, abort these steps
            if (!members.contains(myIdentity)) {
                logger.info("Dismissing group setup message for unknown group without the user as member")
                return ReceiveStepsResult.DISCARD
            }
            // 3.2 If the sender is blocked, send a group-leave message to the sender and all
            // provided members (including those who are blocked) and abort these steps.
            if (isBlocked(sender)) {
                logger.info("Sending a leave message to the creator of a new group that is blocked")
                sendLeave(handle, groupSetupMessage.apiGroupId, sender, members)
                return ReceiveStepsResult.SUCCESS
            }
        }

        // 4. If the group could be found and members is empty or does not include the user:
        if (group != null && !members.contains(myIdentity)) {
            // 4.1 If the user is currently participating in a group call of this group, trigger
            // leaving the call.
            if (groupCallManager.hasJoinedCall(group.localGroupId)) {
                logger.info("Group call is running in a group where the user just has been kicked")
                groupCallManager.abortCurrentCall()
            }

            // 4.2 Mark the group as left and abort these steps.
            groupService.removeMemberFromGroup(group, myIdentity)

            ListenerManager.groupListeners.handle {
                it.onMemberKicked(
                    group,
                    myIdentity,
                    previousMemberCount,
                )
            }

            return ReceiveStepsResult.SUCCESS
        }

        // 5. For each member of members, create a contact with acquaintance level group
        // if not already present in the contact list.
        val unknownContacts = members.filter { contactService.getByIdentity(it) == null }
        contactService.createGroupContactsByIdentities(unknownContacts)

        // 6. Create or update the group with the given members plus the sender (creator).
        val now = Date()
        if (group == null) {
            group = GroupModel().apply {
                apiGroupId = groupSetupMessage.apiGroupId
                creatorIdentity = groupSetupMessage.groupCreator
                createdAt = now
                lastUpdate = now
            }
            databaseService.groupModelFactory.create(group)
        } else if (group.isDeleted || !groupService.isGroupMember(group)) {
            group.isDeleted = false
            group.lastUpdate = now
            databaseService.groupModelFactory.update(group)
        }
        updateMembers(group, members, previousMemberCount)

        // If the group is new, then fire the listener.
        if (newGroup) {
            ListenerManager.groupListeners.handle { it.onCreate(group) }
        }

        // 7. If the group was previously marked as left, remove the left mark.
        // Note that this step is already handled in step 6, because a group is defined to be left,
        // if the user is not part of the members.

        // 8. Run the rejected messages refresh steps for the group
        groupService.runRejectedMessagesRefreshSteps(group)

        // 9. If the user is currently participating in a group call of this group and there are
        // group call participants that are no longer members of the group, remove these
        // participants from the group call (handle them as if they left the call).
        groupCallManager.updateAllowedCallParticipants(group)

        return ReceiveStepsResult.SUCCESS
    }

    private suspend fun sendLeave(
        handle: ActiveTaskCodec,
        apiGroupId: GroupId,
        creatorIdentity: String,
        members: Set<String>,
    ) {
        val messageId = MessageId()
        val myIdentity = userService.identity

        val recipients = (members + creatorIdentity - myIdentity)
            .toContactModels(contactService, apiConnector)
            .filterValid()
            .toSet()

        handle.sendMessageToReceivers(
            OutgoingCspGroupMessageCreator(messageId, apiGroupId, creatorIdentity) { GroupLeaveMessage() },
            recipients,
            forwardSecurityMessageProcessor,
            identityStore,
            contactStore,
            nonceFactory,
            blackListService,
            taskCreator
        )
    }

    private fun updateMembers(group: GroupModel, members: Set<String>, previousMemberCount: Int) {
        // Delete all local group members that are not a member of the updated group. Don't delete
        // the group creator.
        val localMembersToDelete = groupService.getGroupMemberModels(group)
            .filter { group.creatorIdentity != it.identity && !members.contains(it.identity) }

        localMembersToDelete.forEach { memberModel ->
            // Remove member from group
            groupService.removeMemberFromGroup(group, memberModel.identity)

            // Notify listeners that the member has been removed
            ListenerManager.groupListeners.handle {
                it.onMemberKicked(
                    group,
                    memberModel.identity,
                    previousMemberCount
                )
            }
        }

        // All members that are already added (including the group creator)
        val addedMembers = groupService.getGroupMemberModels(group).map { it.identity }

        // All members including the creator that should be part of the group
        val allMembers = (listOf(group.creatorIdentity) + members).toSet()

        // Add all members to the group that are not already in the group
        allMembers.filter { !addedMembers.contains(it) }.forEach { memberIdentity ->
            if (groupService.addMemberToGroup(group, memberIdentity)) {
                ListenerManager.groupListeners.handle {
                    it.onNewMember(group, memberIdentity, previousMemberCount)
                }
            }
        }
    }

    private fun isBlocked(identity: String): Boolean =
        blackListService.has(identity) ||
                (contactService.getByIdentity(identity) == null && preferenceService.isBlockUnknown)
}
