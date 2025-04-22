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
import ch.threema.app.protocol.Contact
import ch.threema.app.protocol.Init
import ch.threema.app.protocol.Invalid
import ch.threema.app.protocol.SpecialContact
import ch.threema.app.protocol.UserContact
import ch.threema.app.protocol.runValidContactsLookupSteps
import ch.threema.app.tasks.ReflectGroupSyncUpdateImmediateTask
import ch.threema.app.tasks.ReflectionFailed
import ch.threema.app.tasks.ReflectionPreconditionFailed
import ch.threema.app.tasks.ReflectionSuccess
import ch.threema.app.tasks.toFullSyncContact
import ch.threema.app.tasks.toGroupSync
import ch.threema.app.voip.groupcall.localGroupId
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.now
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactReflectException
import ch.threema.data.repositories.ContactStoreException
import ch.threema.data.repositories.GroupCreateException
import ch.threema.data.repositories.UnexpectedContactException
import ch.threema.domain.models.IdentityState
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.taskmanager.awaitReflectAck
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedContactSyncCreate
import ch.threema.domain.taskmanager.getEncryptedGroupSyncCreate
import ch.threema.domain.taskmanager.getEncryptedGroupSyncUpdate
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.MdD2D.GroupSync.Update.MemberStateChange
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.group
import ch.threema.protobuf.groupIdentity
import ch.threema.protobuf.identities
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel.UserState

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupSetupTask")

class IncomingGroupSetupTask(
    message: GroupSetupMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupSetupMessage>(message, triggerSource, serviceManager) {
    // Services
    private val userService by lazy { serviceManager.userService }
    private val groupService by lazy { serviceManager.groupService }
    private val groupCallManager by lazy { serviceManager.groupCallManager }
    private val contactStore by lazy { serviceManager.contactStore }
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val apiConnector by lazy { serviceManager.apiConnector }
    private val licenseService by lazy { serviceManager.licenseService }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val conversationService by lazy { serviceManager.conversationService }
    private val conversationCategoryService by lazy { serviceManager.conversationCategoryService }
    private val ringtoneService by lazy { serviceManager.ringtoneService }

    // Repositories
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }

    // Properties
    private val myIdentity by lazy { userService.identity }
    private val groupIdentity by lazy {
        GroupIdentity(
            message.groupCreator,
            message.apiGroupId.toLong(),
        )
    }

    // Note that the properties are null if and only if MD is not active.
    private val multiDeviceProperties by lazy {
        if (multiDeviceManager.isMultiDeviceActive) {
            multiDeviceManager.propertiesProvider.get()
        } else {
            null
        }
    }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        // Let members be the given member list. Remove all duplicate entries from members.
        // Remove the sender from members if present.
        val senderIdentity = message.fromIdentity
        val members = message.members.filter { it != senderIdentity }.toSet()

        // Look up the group
        val group = groupModelRepository.getByGroupIdentity(groupIdentity)

        // Abort if there is no change
        if (group != null && !hasChange(members, group)) {
            logger.info("There is no change contained in the group-setup message")
            return ReceiveStepsResult.DISCARD
        }

        // Determine whether the user will be part of the group or not
        return if (members.contains(myIdentity)) {
            handleSetupContainingUser(senderIdentity, members, group, handle)
        } else {
            handleSetupWithoutUser(group, handle)
        }
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.info("Discarding group-setup from sync")
        return ReceiveStepsResult.DISCARD
    }

    private fun hasChange(members: Set<String>, group: GroupModel): Boolean {
        val data = group.data.value
        if (data == null || !data.isMember) {
            if (members.isEmpty()) {
                return false
            }
        } else {
            val currentMembersIncludingUser = data.otherMembers + myIdentity
            if (currentMembersIncludingUser == members) {
                return false
            }
        }
        return true
    }

    private suspend fun handleSetupContainingUser(
        senderIdentity: String,
        members: Set<String>,
        group: GroupModel?,
        handle: ActiveTaskCodec,
    ): ReceiveStepsResult {
        val detectedChanges = if (multiDeviceManager.isMultiDeviceActive) {
            handle.createTransaction(
                multiDeviceManager.propertiesProvider.get().keys,
                MdD2D.TransactionScope.Scope.GROUP_SYNC,
                TRANSACTION_TTL_MAX,
            ) {
                contactModelRepository.getByIdentity(senderIdentity)?.data?.value != null
            }.execute {
                detectAndReflectChangesIfMdEnabled(members, group, handle)
            }
        } else {
            detectAndReflectChangesIfMdEnabled(members, group, handle)
        }

        persistChanges(detectedChanges)

        return ReceiveStepsResult.SUCCESS
    }

    private suspend fun detectAndReflectChangesIfMdEnabled(
        members: Set<String>,
        group: GroupModel?,
        handle: ActiveTaskCodec,
    ): GroupChanges {
        val validMembersLookupResult = runValidContactsLookupSteps(
            members,
            myIdentity,
            contactStore,
            contactModelRepository,
            licenseService,
            apiConnector,
        ).filter { (_, contactOrInit) ->
            when (contactOrInit) {
                // Invalid contacts cannot be part of a group
                is Invalid -> false
                // The user itself is not added to the member list
                is UserContact -> false
                // Do not include contacts that are present but not valid
                is Contact -> contactOrInit.contactModel.data.value?.activityState != IdentityState.INVALID
                is Init -> true
                is SpecialContact -> true
            }
        }

        val newContactModelData = validMembersLookupResult.asSequence()
            .filter { it.key != myIdentity }
            .map { it.value }
            .filterIsInstance<Init>()
            .map { it.contactModelData }
            .map { it.copy(acquaintanceLevel = ContactModel.AcquaintanceLevel.GROUP) }
            .toSet()

        newContactModelData.reflectAsNewContactsIfMdEnabled(handle)

        val validMembers = validMembersLookupResult.map { it.key }.toSet()

        val groupModelData = group?.data?.value

        return if (group == null || groupModelData == null) {
            val now = now()
            val newGroupModelData = GroupModelData(
                groupIdentity = groupIdentity,
                name = "",
                createdAt = now,
                synchronizedAt = null,
                lastUpdate = now,
                isArchived = false,
                groupDescription = null,
                groupDescriptionChangedAt = null,
                otherMembers = validMembers + groupIdentity.creatorIdentity,
                userState = UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
            )
            newGroupModelData.reflectAsNewGroupIfMdEnabled(handle)
            NewGroup(newContactModelData, newGroupModelData)
        } else {
            val currentMembers =
                groupModelData.otherMembers - groupModelData.groupIdentity.creatorIdentity
            val addedMembers = validMembers - currentMembers
            val removedMembers = currentMembers - validMembers

            val memberStateChanges = addedMembers.associateWith { MemberStateChange.ADDED } +
                removedMembers.associateWith { MemberStateChange.KICKED }

            validMembers.reflectAsGroupUpdateIfMdEnabled(memberStateChanges, handle)
            ModifiedGroup(newContactModelData, group, addedMembers, removedMembers)
        }
    }

    private fun persistChanges(changes: GroupChanges) {
        // Persist newly created contacts
        changes.newContacts.forEach {
            try {
                contactModelRepository.persistGroupContactFromRemote(it)
            } catch (e: ContactCreateException) {
                when (e) {
                    // The contact must not exists yet
                    is ContactStoreException -> logger.error(
                        "Contact {} already exists",
                        it.identity,
                    )
                    // This should never happen
                    is UnexpectedContactException -> throw e
                    // This should never happen
                    is ContactReflectException -> throw e
                }
            }
        }

        when (changes) {
            is NewGroup -> {
                try {
                    groupModelRepository.persistNewGroup(changes.groupModelData)
                } catch (e: GroupCreateException) {
                    logger.error("Could not store the group", e)
                }
            }

            is ModifiedGroup -> {
                // TODO(ANDR-3262): Remove 'removed-members' from group call if a group call exists.
                //  Note that according to the protocol, this should happen before persisting the
                //  newly added contacts.

                changes.groupModel.persistMemberChanges(changes.addedMembers, changes.removedMembers)

                changes.groupModel.persistUserState(UserState.MEMBER)

                if (changes.addedMembers.isNotEmpty() || changes.removedMembers.isNotEmpty()) {
                    groupService.runRejectedMessagesRefreshSteps(changes.groupModel)
                }
            }
        }
    }

    private suspend fun handleSetupWithoutUser(
        group: GroupModel?,
        handle: ActiveTaskCodec,
    ): ReceiveStepsResult {
        if (group == null) {
            logger.info("User is not part of the unknown group")
            return ReceiveStepsResult.DISCARD
        }

        if (multiDeviceManager.isMultiDeviceActive) {
            val reflectionResult = ReflectGroupSyncUpdateImmediateTask.ReflectUserKicked(
                group,
                nonceFactory,
                multiDeviceManager,
            ).reflect(handle)
            when (reflectionResult) {
                is ReflectionFailed -> {
                    logger.error("Reflection of group update failed", reflectionResult.exception)
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionPreconditionFailed -> {
                    logger.error(
                        "Group sync race: Transaction precondition failed",
                        reflectionResult.transactionException,
                    )
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionSuccess -> logger.info("Reflected user kicked update")
            }
        }

        // If the user is currently participating in a group call of this group, trigger leaving the
        // call.
        if (groupCallManager.hasJoinedCall(group.localGroupId)) {
            logger.info("Group call is running in a group where the user just has been kicked")
            groupCallManager.abortCurrentCall()
        }

        group.persistUserState(UserState.KICKED)

        groupService.runRejectedMessagesRefreshSteps(group)

        return ReceiveStepsResult.SUCCESS
    }

    private suspend fun Collection<ContactModelData>.reflectAsNewContactsIfMdEnabled(handle: ActiveTaskCodec) {
        val multiDeviceProperties = this@IncomingGroupSetupTask.multiDeviceProperties ?: return
        map {
            val encryptedEnvelopeResult = getEncryptedContactSyncCreate(
                it.toFullSyncContact(
                    conversationService,
                    conversationCategoryService,
                    ringtoneService,
                ),
                multiDeviceProperties,
            )

            handle.reflect(encryptedEnvelopeResult) to encryptedEnvelopeResult.nonce
        }.forEach { (reflectAck, nonce) ->
            handle.awaitReflectAck(reflectAck)
            nonceFactory.store(NonceScope.D2D, nonce)
        }
    }

    private suspend fun GroupModelData.reflectAsNewGroupIfMdEnabled(handle: ActiveTaskCodec) {
        val multiDeviceProperties = this@IncomingGroupSetupTask.multiDeviceProperties ?: return

        val groupSync = toGroupSync(false, MdD2DSync.ConversationVisibility.NORMAL)
        val encryptedEnvelopeResult = getEncryptedGroupSyncCreate(groupSync, multiDeviceProperties)
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult,
            true,
            nonceFactory,
        )
    }

    private suspend fun Collection<String>.reflectAsGroupUpdateIfMdEnabled(
        memberStateChanges: Map<String, MemberStateChange>,
        handle: ActiveTaskCodec,
    ) {
        val multiDeviceProperties = this@IncomingGroupSetupTask.multiDeviceProperties ?: return

        val groupSync = group {
            groupIdentity = groupIdentity {
                creatorIdentity = this@IncomingGroupSetupTask.groupIdentity.creatorIdentity
                groupId = this@IncomingGroupSetupTask.groupIdentity.groupId
            }
            userState = MdD2DSync.Group.UserState.MEMBER
            memberIdentities = identities {
                identities.addAll(this@reflectAsGroupUpdateIfMdEnabled)
            }
        }
        val encryptedEnvelopeResult =
            getEncryptedGroupSyncUpdate(groupSync, memberStateChanges, multiDeviceProperties)
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult,
            true,
            nonceFactory,
        )
    }
}

private sealed class GroupChanges(val newContacts: Set<ContactModelData>)

private class NewGroup(
    newContacts: Set<ContactModelData>,
    val groupModelData: GroupModelData,
) : GroupChanges(newContacts)

private class ModifiedGroup(
    newContacts: Set<ContactModelData>,
    val groupModel: GroupModel,
    val addedMembers: Set<String>,
    val removedMembers: Set<String>,
) : GroupChanges(newContacts)
