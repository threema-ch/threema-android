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

package ch.threema.app.processors.reflectedd2dsync

import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ApiService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.ConversationTagService
import ch.threema.app.services.DeadlineListService.DEADLINE_INDEFINITE
import ch.threema.app.services.DeadlineListService.DEADLINE_INDEFINITE_EXCEPT_MENTIONS
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.app.utils.GroupUtil
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupAlreadyExistsException
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.data.repositories.GroupStoreException
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.taskmanager.catchAllExceptNetworkException
import ch.threema.protobuf.Common
import ch.threema.protobuf.Common.Blob
import ch.threema.protobuf.Common.DeltaImage
import ch.threema.protobuf.d2d.MdD2D.GroupSync
import ch.threema.protobuf.d2d.MdD2D.GroupSync.ActionCase.ACTION_NOT_SET
import ch.threema.protobuf.d2d.MdD2D.GroupSync.ActionCase.CREATE
import ch.threema.protobuf.d2d.MdD2D.GroupSync.ActionCase.DELETE
import ch.threema.protobuf.d2d.MdD2D.GroupSync.ActionCase.UPDATE
import ch.threema.protobuf.d2d.MdD2D.GroupSync.Create
import ch.threema.protobuf.d2d.MdD2D.GroupSync.Delete
import ch.threema.protobuf.d2d.MdD2D.GroupSync.Update
import ch.threema.protobuf.d2d.MdD2D.GroupSync.Update.MemberStateChange
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group.UserState
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.ConversationTag
import java.util.Collections
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("ReflectedGroupSyncTask")

class ReflectedGroupSyncTask(
    private val groupSync: GroupSync,
    private val groupModelRepository: GroupModelRepository,
    private val groupService: GroupService,
    private val fileService: FileService,
    private val apiService: ApiService,
    private val symmetricEncryptionService: SymmetricEncryptionService,
    private val conversationCategoryService: ConversationCategoryService,
    private val conversationService: ConversationService,
    private val conversationTagService: ConversationTagService,
    userService: UserService,
) {
    private val myIdentity by lazy { userService.identity }

    fun run() {
        when (groupSync.actionCase) {
            CREATE -> handleGroupCreate(groupSync.create)
            UPDATE -> handleGroupUpdate(groupSync.update)
            DELETE -> handleGroupDelete(groupSync.delete)
            ACTION_NOT_SET -> logger.warn("No action set for group sync")
            null -> logger.warn("Action is null for contact sync")
        }
    }

    private fun handleGroupCreate(groupCreate: Create) {
        logger.info("Processing reflected group create")

        val groupModelData = groupCreate.group.toNewGroupModelData()

        val groupModel = try {
            groupModelRepository.createFromSync(groupModelData)
        } catch (e: Exception) {
            when (e) {
                is GroupStoreException -> logger.error("Could not store group", e)
                is GroupAlreadyExistsException -> logger.error("Group already exists")
                else -> throw e
            }
            return
        }

        applyProfilePicture(groupCreate.group, groupModel)

        logger.info("New group successfully created from sync")
    }

    private fun handleGroupUpdate(groupUpdate: Update) {
        logger.info("Processing reflected group update")

        val group = groupUpdate.group
        val groupModel = groupModelRepository.getByGroupIdentity(group.groupIdentity.convert())
        if (groupModel == null) {
            logger.error("Received group update for unknown group")
            return
        }

        applyName(group, groupModel)
        applyUserState(group, groupModel)
        applyNotificationTriggerPolicyOverride(group, groupModel)
        applyProfilePicture(group, groupModel)
        applyMembers(group, groupUpdate.memberStateChangesMap, groupModel)
        applyConversationCategory(group, groupModel)
        applyConversationVisibility(group, groupModel)
    }

    private fun handleGroupDelete(groupDelete: Delete) {
        logger.info("Processing reflected group delete")

        val groupIdentity = groupDelete.groupIdentity.convert()
        val groupModel = groupModelRepository.getByGroupIdentity(groupIdentity) ?: run {
            logger.error("Cannot delete unknown group")
            return
        }

        groupService.removeGroupBelongings(groupModel, TriggerSource.SYNC)
        groupModelRepository.persistRemovedGroup(groupIdentity)

        logger.info("Deleted group")
    }

    private fun applyName(group: Group, groupModel: GroupModel) {
        if (group.hasName()) {
            groupModel.setNameFromSync(group.name)
        }
    }

    private fun applyUserState(group: Group, groupModel: GroupModel) {
        if (group.hasUserState()) {
            val userState = group.userState.convert() ?: return

            groupModel.setUserStateFromSync(userState)
        }
    }

    private fun applyNotificationTriggerPolicyOverride(group: Group, groupModel: GroupModel) {
        if (group.hasNotificationTriggerPolicyOverride()) {
            groupModel.setNotificationTriggerPolicyOverrideFromSync(
                group.notificationTriggerPolicyOverride.convert(),
            )
        }
    }

    private fun applyProfilePicture(group: Group, groupModel: GroupModel) {
        if (group.hasProfilePicture()) {
            when (group.profilePicture.imageCase) {
                DeltaImage.ImageCase.REMOVED -> removeGroupAvatar(groupModel)

                DeltaImage.ImageCase.UPDATED -> loadAndPersistBlob(
                    groupModel,
                    group.profilePicture.updated.blob,
                )

                DeltaImage.ImageCase.IMAGE_NOT_SET -> logger.warn("Profile picture image case not set")

                null -> logger.warn("Profile picture image case is null")
            }
        }
    }

    private fun applyMembers(
        group: Group,
        memberStateMap: Map<String, MemberStateChange>,
        groupModel: GroupModel,
    ) {
        // Abort if the member identities are not set
        if (!group.hasMemberIdentities()) {
            if (memberStateMap.isNotEmpty()) {
                logger.warn("Received member state changes but no updated member identities")
            }
            return
        }

        val members =
            (group.memberIdentities.identitiesList + groupModel.groupIdentity.creatorIdentity)
                .filter { it != myIdentity }
                .toSet()
        val oldMembers = groupModel.data.value?.otherMembers ?: run {
            logger.error("Group model data is null")
            return
        }

        groupModel.setMembersFromSync(members)

        memberStateMap.forEach { (identity, state) ->
            when (state) {
                MemberStateChange.ADDED -> {
                    when {
                        oldMembers.contains(identity) -> logger.error(
                            "Group already contains {}",
                            identity,
                        )

                        !members.contains(identity) -> logger.error(
                            "New member set does not contain {}",
                            identity,
                        )

                        else -> notifyDeprecatedOnNewMemberListeners(
                            groupModel.groupIdentity,
                            identity,
                        )
                    }
                }

                MemberStateChange.LEFT, MemberStateChange.KICKED -> {
                    when {
                        !oldMembers.contains(identity) -> logger.error(
                            "Member {} was not present in group",
                            identity,
                        )

                        members.contains(identity) -> logger.error(
                            "Member {} still contained in group",
                            identity,
                        )

                        state == MemberStateChange.LEFT -> notifyDeprecatedOnMemberLeaveListeners(
                            groupModel.groupIdentity,
                            identity,
                        )

                        else ->
                            notifyDeprecatedOnMemberKickedListeners(
                                groupModel.groupIdentity,
                                identity,
                            )
                    }
                }

                MemberStateChange.UNRECOGNIZED -> logger.warn("Member state change unrecognized")
            }
        }
    }

    private fun applyConversationCategory(group: Group, groupModel: GroupModel) {
        if (!group.hasConversationCategory()) {
            return
        }

        when (group.conversationCategory) {
            MdD2DSync.ConversationCategory.DEFAULT -> conversationCategoryService.persistDefaultChat(GroupUtil.getUniqueIdString(groupModel))
            MdD2DSync.ConversationCategory.PROTECTED -> conversationCategoryService.persistPrivateChat(GroupUtil.getUniqueIdString(groupModel))
            MdD2DSync.ConversationCategory.UNRECOGNIZED -> unrecognizedValue("Group.conversationCategory")
            null -> nullValue("Group.conversationCategory")
        }
    }

    private fun applyConversationVisibility(group: Group, groupModel: GroupModel) {
        if (group.hasConversationVisibility()) {
            when (group.conversationVisibility) {
                MdD2DSync.ConversationVisibility.NORMAL -> {
                    val archivedConversationModel = getArchivedConversationModel(groupModel.getDatabaseId())
                    if (archivedConversationModel != null) {
                        conversationService.unarchive(listOf(archivedConversationModel), TriggerSource.SYNC)
                    }
                    val conversationModel = getConversationModel(groupModel.getDatabaseId())
                    if (conversationModel != null) {
                        conversationTagService.removeTagAndNotify(
                            conversationModel,
                            ConversationTag.PINNED,
                            TriggerSource.SYNC,
                        )
                        conversationModel.setIsPinTagged(false)
                    } else {
                        logger.error("The conversation intended to have normal visibility was not found.")
                    }
                }

                MdD2DSync.ConversationVisibility.ARCHIVED -> {
                    val conversationModel = getConversationModel(groupModel.getDatabaseId())
                    if (conversationModel != null) {
                        conversationTagService.removeTagAndNotify(conversationModel, ConversationTag.PINNED, TriggerSource.SYNC)
                        conversationModel.setIsPinTagged(false)
                        conversationService.archive(conversationModel, TriggerSource.SYNC)
                    } else if (getArchivedConversationModel(groupModel.getDatabaseId()) != null) {
                        logger.warn("Conversation already is archived")
                    } else {
                        logger.error("The conversation intended to be archived was not found.")
                    }
                }

                MdD2DSync.ConversationVisibility.PINNED -> {
                    val archivedConversationModel = getArchivedConversationModel(groupModel.getDatabaseId())
                    if (archivedConversationModel != null) {
                        conversationService.unarchive(listOf(archivedConversationModel), TriggerSource.SYNC)
                    }
                    val conversationModel = getConversationModel(groupModel.getDatabaseId())
                    if (conversationModel != null) {
                        conversationTagService.addTagAndNotify(conversationModel, ConversationTag.PINNED, TriggerSource.SYNC)
                        conversationModel.setIsPinTagged(true)
                    } else {
                        logger.error("The conversation intended to be pinned was not found.")
                    }
                }

                MdD2DSync.ConversationVisibility.UNRECOGNIZED -> unrecognizedValue("conversation visibility")

                null -> nullValue("conversation visibility")
            }
        }
    }

    private fun getConversationModel(localGroupDatabaseId: Long): ConversationModel? {
        // We need load the conversations from the database. This is due to a race condition in the conversation service when the user pins an
        // archived group.
        return conversationService.getAll(true).find { it.group?.id?.toLong() == localGroupDatabaseId }
    }

    private fun getArchivedConversationModel(localGroupDatabaseId: Long): ConversationModel? {
        return conversationService.getArchived(null).find { it.group?.id?.toLong() == localGroupDatabaseId }
    }

    /**
     * Synchronously notify new group member listeners.
     */
    private fun notifyDeprecatedOnNewMemberListeners(
        groupIdentity: GroupIdentity,
        newIdentity: String,
    ) {
        ListenerManager.groupListeners.handle { it.onNewMember(groupIdentity, newIdentity) }
    }

    /**
     * Synchronously notify group member left listeners.
     */
    private fun notifyDeprecatedOnMemberLeaveListeners(
        groupIdentity: GroupIdentity,
        leftIdentity: String,
    ) {
        ListenerManager.groupListeners.handle { it.onMemberLeave(groupIdentity, leftIdentity) }
    }

    /**
     * Synchronously notify group member kicked listeners.
     */
    private fun notifyDeprecatedOnMemberKickedListeners(
        groupIdentity: GroupIdentity,
        kickedIdentity: String,
    ) {
        ListenerManager.groupListeners.handle { it.onMemberKicked(groupIdentity, kickedIdentity) }
    }

    private fun removeGroupAvatar(groupModel: GroupModel) {
        if (fileService.hasGroupAvatarFile(groupModel)) {
            fileService.removeGroupAvatar(groupModel)
            ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel.groupIdentity) }
            ShortcutUtil.updateShareTargetShortcut(groupService.createReceiver(groupModel))
        }
    }

    private fun loadAndPersistBlob(groupModel: GroupModel, blob: Blob) {
        val blobId = blob.id.toByteArray()
        val blobLoader = apiService.createLoader(blobId)
        val encryptedBlob = {
            blobLoader.load(BlobScope.Local)
        }.catchAllExceptNetworkException { e: Exception ->
            logger.error("Could not download blob", e)
            return
        }
        val profilePicture = symmetricEncryptionService.decrypt(
            encryptedBlob,
            blob.key.toByteArray(),
            blob.nonce.toByteArray().let { nonceBytes ->
                if (nonceBytes.isEmpty()) {
                    ProtocolDefines.GROUP_PHOTO_NONCE
                } else {
                    nonceBytes
                }
            },
        )
        if (profilePicture == null) {
            logger.error("Could not load profile picture blob")
            return
        }

        fileService.writeGroupAvatar(groupModel, profilePicture)
        ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel.groupIdentity) }
        ShortcutUtil.updateShareTargetShortcut(groupService.createReceiver(groupModel))

        blobLoader.markAsDone(blobId, BlobScope.Local)
    }

    private fun Group.toNewGroupModelData(): GroupModelData = GroupModelData(
        groupIdentity = groupIdentity.convert(),
        name = name,
        createdAt = Date(createdAt),
        synchronizedAt = null,
        lastUpdate = Date(),
        isArchived = false,
        groupDescription = null,
        groupDescriptionChangedAt = null,
        otherMembers = Collections.unmodifiableSet(getMembers() + groupIdentity.creatorIdentity - myIdentity),
        userState = userState.convert() ?: ch.threema.storage.models.GroupModel.UserState.MEMBER,
        notificationTriggerPolicyOverride = notificationTriggerPolicyOverride.convert(),
    )

    private fun Group.getMembers(): Set<String> = memberIdentities.identitiesList.toSet()

    private fun UserState?.convert() = when (this) {
        UserState.MEMBER -> ch.threema.storage.models.GroupModel.UserState.MEMBER
        UserState.LEFT -> ch.threema.storage.models.GroupModel.UserState.LEFT
        UserState.KICKED -> ch.threema.storage.models.GroupModel.UserState.KICKED
        UserState.UNRECOGNIZED -> unrecognizedValue("Group.UserState")
        null -> nullValue("Group.UserState")
    }

    private fun Common.GroupIdentity.convert() = GroupIdentity(
        creatorIdentity = creatorIdentity,
        groupId = groupId,
    )

    private fun Group.NotificationTriggerPolicyOverride.convert(): Long? =
        when (overrideCase) {
            Group.NotificationTriggerPolicyOverride.OverrideCase.DEFAULT -> null
            Group.NotificationTriggerPolicyOverride.OverrideCase.POLICY -> {
                when (policy.policy) {
                    NotificationTriggerPolicy.MENTIONED -> DEADLINE_INDEFINITE_EXCEPT_MENTIONS
                    NotificationTriggerPolicy.NEVER -> {
                        if (policy.hasExpiresAt()) {
                            policy.expiresAt
                        } else {
                            DEADLINE_INDEFINITE
                        }
                    }

                    NotificationTriggerPolicy.UNRECOGNIZED -> unrecognizedValue(
                        "Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy",
                    )

                    null -> nullValue("Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy")
                }
            }

            Group.NotificationTriggerPolicyOverride.OverrideCase.OVERRIDE_NOT_SET -> null
            null -> null
        }

    private fun unrecognizedValue(valueName: String): Nothing? {
        logger.warn("Unrecognized {}", valueName)
        return null
    }

    private fun nullValue(valueName: String): Nothing? {
        logger.warn("Value {} is null", valueName)
        return null
    }
}
