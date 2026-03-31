package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.protocolsteps.Contact
import ch.threema.app.protocolsteps.Init
import ch.threema.app.protocolsteps.Invalid
import ch.threema.app.protocolsteps.SpecialContact
import ch.threema.app.protocolsteps.UserContact
import ch.threema.app.protocolsteps.ValidContactsLookupSteps
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.ReflectGroupSyncUpdateImmediateTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.tasks.toFullSyncContact
import ch.threema.app.tasks.toGroupSync
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.now
import ch.threema.data.datatypes.localGroupId
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.ContactReflectException
import ch.threema.data.repositories.ContactStoreException
import ch.threema.data.repositories.GroupCreateException
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.data.repositories.InvalidContactException
import ch.threema.data.repositories.UnexpectedContactException
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.UserState
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.taskmanager.awaitReflectAck
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedContactSyncCreate
import ch.threema.domain.taskmanager.getEncryptedGroupSyncCreate
import ch.threema.domain.taskmanager.getEncryptedGroupSyncUpdate
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.MdD2D.GroupSync.Update.MemberStateChange
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.group
import ch.threema.protobuf.groupIdentity
import ch.threema.protobuf.identities
import ch.threema.storage.models.ContactModel
import java.util.Collections
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("IncomingGroupSetupTask")

class IncomingGroupSetupTask(
    message: GroupSetupMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupSetupMessage>(message, triggerSource, serviceManager), KoinComponent {
    // Services
    private val userService: UserService by inject()
    private val groupService: GroupService by inject()
    private val groupCallManager: GroupCallManager by inject()
    private val nonceFactory: NonceFactory by inject()
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val conversationService: ConversationService by inject()
    private val conversationCategoryService: ConversationCategoryService by inject()
    private val ringtoneService: RingtoneService by inject()

    // Repositories
    private val contactModelRepository: ContactModelRepository by inject()
    private val groupModelRepository: GroupModelRepository by inject()

    // Steps
    private val validContactsLookupSteps: ValidContactsLookupSteps by inject()

    // Properties
    private val myIdentity by lazy { userService.identity!! }
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
        logger.info("Processing incoming group-setup message for group with id {}", message.apiGroupId)

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

        if (group == null) {
            logger.info("Group does not yet exist")
        } else {
            logger.info("Group exists and changes are detected")
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

    /**
     * Check whether the [members] of the group setup message imply a change to the given [group].
     *
     * The [members] are expected to contain the user's identity but not the group creator identity. If this is not the case, this method may indicate
     * that there is a change despite there isn't any change.
     */
    private fun hasChange(members: Set<String>, group: GroupModel): Boolean {
        val groupModelData = group.data
        if (groupModelData == null || !groupModelData.isMember) {
            if (members.isEmpty()) {
                return false
            }
        } else {
            val allMembersWithoutCreator = groupModelData.getAllMembers(myIdentity) - group.groupIdentity.creatorIdentity
            if (allMembersWithoutCreator == members) {
                return false
            }
        }
        return true
    }

    private suspend fun handleSetupContainingUser(
        senderIdentity: IdentityString,
        members: Set<String>,
        group: GroupModel?,
        handle: ActiveTaskCodec,
    ): ReceiveStepsResult {
        val detectedChanges = if (multiDeviceManager.isMultiDeviceActive) {
            handle.createTransaction(
                keys = multiDeviceManager.propertiesProvider.get().keys,
                scope = MdD2D.TransactionScope.Scope.GROUP_SYNC,
                ttl = TRANSACTION_TTL_MAX,
                precondition = {
                    contactModelRepository.getByIdentity(senderIdentity)?.data != null
                },
            ).execute {
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
        val validMembersLookupResult = validContactsLookupSteps.run(identities = members)
            .filter { (_, contactOrInit) ->
                when (contactOrInit) {
                    // Invalid contacts cannot be part of a group
                    is Invalid -> false
                    // The user itself is not added to the member list
                    is UserContact -> false
                    // Do not include contacts that are present but not valid
                    is Contact -> contactOrInit.contactModel.data?.activityState != IdentityState.INVALID
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

        val groupModelData = group?.data

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
                otherMembers = Collections.unmodifiableSet(validMembers),
                userState = UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
            )
            newGroupModelData.reflectAsNewGroupIfMdEnabled(handle)
            NewGroup(newContactModelData, newGroupModelData)
        } else {
            val currentMembers = groupModelData.otherMembers
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
                    is InvalidContactException -> throw e
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
                groupIdentity = group.groupIdentity,
            ).reflect(handle)
            when (reflectionResult) {
                is ReflectionResult.Failed -> {
                    logger.error("Reflection of group update failed", reflectionResult.exception)
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.PreconditionFailed -> {
                    logger.error(
                        "Group sync race: Transaction precondition failed",
                        reflectionResult.transactionException,
                    )
                    return ReceiveStepsResult.DISCARD
                }

                is ReflectionResult.MultiDeviceNotActive -> {
                    // Note that this is an edge case that should never happen as deactivating md and processing incoming messages is both running in
                    // tasks. However, if it happens nevertheless, we can simply log a warning and continue processing the message.
                    logger.warn("Reflection failed because multi device is not active")
                }

                is ReflectionResult.Success -> logger.info("Reflected user kicked update")
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
