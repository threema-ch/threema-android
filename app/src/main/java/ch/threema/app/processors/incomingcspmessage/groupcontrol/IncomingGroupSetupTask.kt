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

package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.asynctasks.getIdentityState
import ch.threema.app.asynctasks.getIdentityType
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices.Companion.getOutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.utils.toBasicContacts
import ch.threema.app.voip.groupcall.localGroupId
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.ContactModelData.Companion.getIdColorIndex
import ch.threema.data.repositories.ContactCreateException
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.NetworkException
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupSetupTask")

class IncomingGroupSetupTask(
    message: GroupSetupMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupSetupMessage>(message, triggerSource, serviceManager) {
    private val userService by lazy { serviceManager.userService }
    private val contactService by lazy { serviceManager.contactService }
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val groupService by lazy { serviceManager.groupService }
    private val blockedContactsService by lazy { serviceManager.blockedContactsService }
    private val groupCallManager by lazy { serviceManager.groupCallManager }
    private val databaseService by lazy { serviceManager.databaseServiceNew }
    private val contactStore by lazy { serviceManager.contactStore }
    private val apiConnector by lazy { serviceManager.apiConnector }
    private val preferenceService by lazy { serviceManager.preferenceService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        // 1. Let members be the given member list. Remove all duplicate entries from members.
        // Remove the sender from members if present.
        val sender = message.fromIdentity
        val members = message.members.filter { it != sender }.toSet()
        val myIdentity = userService.identity

        // 2. Look up the group
        var group = groupService.getByGroupMessage(message)

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
                sendLeave(handle, message.apiGroupId, sender, members)
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

            // If we are not a member anyway, we do not have to do anything. Especially, we should
            // not call the listener as this would trigger a status message each time.
            if (!groupService.isGroupMember(group)) {
                return ReceiveStepsResult.SUCCESS
            }

            // 4.2 Mark the group as left and abort these steps.
            // Note that we just set the user state to kicked as the user is not stored as a member
            // in the database.
            if (group.userState == GroupModel.UserState.MEMBER) {
                group.userState = GroupModel.UserState.KICKED
                groupService.save(group)
            }

            ListenerManager.groupListeners.handle {
                it.onMemberKicked(group, myIdentity)
            }

            return ReceiveStepsResult.SUCCESS
        }

        // 5. For each member of members, create a contact with acquaintance level group
        // if not already present in the contact list.
        val unknownContacts = members
            .filter { contactModelRepository.getByIdentity(it) == null }
            .filter { it != myIdentity }
        createGroupMemberContacts(unknownContacts, handle)

        // 6. Create or update the group with the given members plus the sender (creator).
        val now = Date()
        if (group == null) {
            group = GroupModel().apply {
                apiGroupId = message.apiGroupId
                creatorIdentity = message.groupCreator
                createdAt = now
                lastUpdate = now
            }
            databaseService.groupModelFactory.create(group)
        } else if (group.isDeleted || !groupService.isGroupMember(group)) {
            group.isDeleted = false
            group.lastUpdate = now
            databaseService.groupModelFactory.update(group)
        }
        updateMembers(group, members)

        // If the group is new, then fire the listener.
        if (newGroup) {
            ListenerManager.groupListeners.handle { it.onCreate(group) }
        }

        // 7. If the group was previously marked as left, remove the left mark.
        if (group.userState != GroupModel.UserState.MEMBER) {
            group.userState = GroupModel.UserState.MEMBER
            databaseService.groupModelFactory.update(group)
            ListenerManager.groupListeners.handle { it.onNewMember(group, myIdentity) }
        }

        // 8. Run the rejected messages refresh steps for the group
        groupService.runRejectedMessagesRefreshSteps(group)

        // 9. If the user is currently participating in a group call of this group and there are
        // group call participants that are no longer members of the group, remove these
        // participants from the group call (handle them as if they left the call).
        groupCallManager.updateAllowedCallParticipants(group)

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        // TODO(ANDR-2741): Support group synchronization
        return ReceiveStepsResult.DISCARD
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
            .toBasicContacts(contactModelRepository, contactStore, apiConnector)
            .toSet()

        val messageCreator = OutgoingCspGroupMessageCreator(
            messageId,
            Date(),
            apiGroupId,
            creatorIdentity
        ) { GroupLeaveMessage() }

        val outgoingCspMessageHandle = OutgoingCspMessageHandle(
            recipients,
            messageCreator,
        )

        handle.runBundledMessagesSendSteps(
            outgoingCspMessageHandle,
            serviceManager.getOutgoingCspMessageServices(),
        )
    }

    private suspend fun createGroupMemberContacts(
        unknownIdentities: List<String>,
        handle: ActiveTaskCodec,
    ) {
        if (unknownIdentities.isEmpty()) {
            return
        }

        val date = Date()
        val fetchResults = try {
            apiConnector.fetchIdentities(unknownIdentities)
        } catch (e: Exception) {
            when (e) {
                is NetworkException -> throw ProtocolException(
                    e.message ?: "Could not fetch identities"
                )

                is ThreemaException -> throw ProtocolException(
                    e.message ?: "Could not fetch server url"
                )

                else -> throw e
            }
        }
        fetchResults.forEach { fetchResult ->
            try {
                contactModelRepository.createFromRemote(
                    ContactModelData(
                        identity = fetchResult.identity,
                        publicKey = fetchResult.publicKey,
                        createdAt = date,
                        firstName = "",
                        lastName = "",
                        nickname = null,
                        colorIndex = getIdColorIndex(fetchResult.identity),
                        verificationLevel = VerificationLevel.UNVERIFIED,
                        workVerificationLevel = WorkVerificationLevel.NONE,
                        identityType = fetchResult.getIdentityType(),
                        acquaintanceLevel = ContactModel.AcquaintanceLevel.GROUP,
                        activityState = fetchResult.getIdentityState(),
                        syncState = ContactSyncState.INITIAL,
                        featureMask = fetchResult.featureMask.toULong(),
                        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                        androidContactLookupKey = null,
                        localAvatarExpires = null,
                        isRestored = false,
                        profilePictureBlobId = null,
                        jobTitle = null,
                        department = null,
                    ),
                    handle
                )
            } catch (e: ContactCreateException) {
                logger.error("Could not create contact {}", fetchResult.identity)
                // In case the contact could not be created because it already exists, we just
                // continue. Otherwise, we throw a protocol exception to restart processing the
                // incoming group setup message.
                if (contactModelRepository.getByIdentity(fetchResult.identity) == null) {
                    throw ProtocolException("Could not create contact ${fetchResult.identity}")
                }
            }
        }
    }

    private fun updateMembers(group: GroupModel, members: Set<String>) {
        // Delete all local group members that are not a member of the updated group. Don't delete
        // the group creator.
        val localMembersToDelete = groupService.getGroupIdentities(group)
            .filter { group.creatorIdentity != it && !members.contains(it) }

        localMembersToDelete.forEach { identity ->
            // Remove member from group
            groupService.removeMemberFromGroup(group, identity)

            // Notify listeners that the member has been removed
            ListenerManager.groupListeners.handle {
                it.onMemberKicked(group, identity)
            }
        }

        // All members that are already added (including the group creator and user)
        val addedMembers = groupService.getGroupIdentities(group)

        // All members including the creator that should be part of the group
        val allMembers = (listOf(group.creatorIdentity) + members).toSet()

        // Add all members to the group that are not already in the group
        allMembers.filter { !addedMembers.contains(it) }.forEach { memberIdentity ->
            if (groupService.addMemberToGroup(group, memberIdentity)) {
                ListenerManager.groupListeners.handle {
                    it.onNewMember(group, memberIdentity)
                }
            }
        }
    }

    private fun isBlocked(identity: String): Boolean =
        blockedContactsService.has(identity) ||
            (contactService.getByIdentity(identity) == null && preferenceService.isBlockUnknown)
}
