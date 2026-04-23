package ch.threema.app.processors.reflectedd2dsync

import ch.threema.app.managers.ListenerManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DeadlineListService.DEADLINE_INDEFINITE
import ch.threema.app.services.DeadlineListService.DEADLINE_INDEFINITE_EXCEPT_MENTIONS
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.app.utils.AppVersionProvider
import ch.threema.app.utils.ExifInterface
import ch.threema.app.utils.GroupUtil
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupAlreadyExistsException
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.data.repositories.GroupStoreException
import ch.threema.domain.models.UserState
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.common.Blob
import ch.threema.protobuf.common.DeltaImage
import ch.threema.protobuf.d2d.GroupSync
import ch.threema.protobuf.d2d.sync.ConversationCategory
import ch.threema.protobuf.d2d.sync.ConversationVisibility
import ch.threema.protobuf.d2d.sync.Group
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.ConversationTag
import java.util.Collections
import java.util.Date
import okhttp3.OkHttpClient

private val logger = getThreemaLogger("ReflectedGroupSyncTask")

class ReflectedGroupSyncTask(
    private val groupSync: GroupSync,
    private val groupModelRepository: GroupModelRepository,
    private val groupService: GroupService,
    private val fileService: FileService,
    private val okHttpClient: OkHttpClient,
    private val serverAddressProvider: ServerAddressProvider,
    private val symmetricEncryptionService: SymmetricEncryptionService,
    private val multiDeviceManager: MultiDeviceManager,
    private val conversationCategoryService: ConversationCategoryService,
    private val conversationService: ConversationService,
    private val preferenceService: PreferenceService,
    userService: UserService,
) {
    private val myIdentity by lazy { userService.identity }

    fun run() {
        when (groupSync.actionCase) {
            GroupSync.ActionCase.CREATE -> handleGroupCreate(groupSync.create)
            GroupSync.ActionCase.UPDATE -> handleGroupUpdate(groupSync.update)
            GroupSync.ActionCase.DELETE -> handleGroupDelete(groupSync.delete)
            GroupSync.ActionCase.ACTION_NOT_SET -> logger.warn("No action set for group sync")
            null -> logger.warn("Action is null for contact sync")
        }
    }

    private fun handleGroupCreate(groupCreate: GroupSync.Create) {
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

    private fun handleGroupUpdate(groupUpdate: GroupSync.Update) {
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

    private fun handleGroupDelete(groupDelete: GroupSync.Delete) {
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

                DeltaImage.ImageCase.UPDATED -> group.profilePicture.updated.blob.loadGroupProfilePictureAndMarkAsDone(groupModel)

                DeltaImage.ImageCase.IMAGE_NOT_SET -> logger.warn("Profile picture image case not set")

                null -> logger.warn("Profile picture image case is null")
            }
        }
    }

    private fun applyMembers(
        group: Group,
        memberStateMap: Map<String, GroupSync.Update.MemberStateChange>,
        groupModel: GroupModel,
    ) {
        // Abort if the member identities are not set
        if (!group.hasMemberIdentities()) {
            if (memberStateMap.isNotEmpty()) {
                logger.warn("Received member state changes but no updated member identities")
            }
            return
        }

        // Note that the member list should not contain the user and the creator.
        if (group.memberIdentities.identitiesList.contains(myIdentity!!)) {
            // TODO(ANDR-4545): Log if this happens
            logger.error("Member identities of a group should not contain the user identity")
        }
        if (group.memberIdentities.identitiesList.contains(group.groupIdentity.creatorIdentity)) {
            // TODO(ANDR-4545): Log if this happens
            logger.error("Member identities of a group should not contain the group creator")
        }
        val updatedMembers = (group.memberIdentities.identitiesList - myIdentity!! - group.groupIdentity.creatorIdentity).toSet()

        val oldMembers = groupModel.data?.otherMembers ?: run {
            logger.error("Group model data is null")
            return
        }

        groupModel.setMembersFromSync(updatedMembers)

        memberStateMap.forEach { (identity, state) ->
            when (state) {
                GroupSync.Update.MemberStateChange.ADDED -> {
                    when {
                        oldMembers.contains(identity) -> logger.error(
                            "Group already contains {}",
                            identity,
                        )

                        !updatedMembers.contains(identity) -> logger.error(
                            "New member set does not contain {}",
                            identity,
                        )

                        else -> notifyDeprecatedOnNewMemberListeners(
                            groupModel.groupIdentity,
                            identity,
                        )
                    }
                }

                GroupSync.Update.MemberStateChange.LEFT, GroupSync.Update.MemberStateChange.KICKED -> {
                    when {
                        !oldMembers.contains(identity) -> logger.error(
                            "Member {} was not present in group",
                            identity,
                        )

                        updatedMembers.contains(identity) -> logger.error(
                            "Member {} still contained in group",
                            identity,
                        )

                        state == GroupSync.Update.MemberStateChange.LEFT -> notifyDeprecatedOnMemberLeaveListeners(
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

                GroupSync.Update.MemberStateChange.UNRECOGNIZED -> logger.warn("Member state change unrecognized")
            }
        }
    }

    private fun applyConversationCategory(group: Group, groupModel: GroupModel) {
        if (!group.hasConversationCategory()) {
            return
        }

        when (group.conversationCategory) {
            ConversationCategory.DEFAULT -> conversationCategoryService.persistDefaultChat(GroupUtil.getUniqueIdString(groupModel))
            ConversationCategory.PROTECTED -> conversationCategoryService.persistPrivateChat(GroupUtil.getUniqueIdString(groupModel))
            ConversationCategory.UNRECOGNIZED -> unrecognizedValue("Group.conversationCategory")
            null -> nullValue("Group.conversationCategory")
        }
    }

    private fun applyConversationVisibility(group: Group, groupModel: GroupModel) {
        if (group.hasConversationVisibility()) {
            when (group.conversationVisibility) {
                ConversationVisibility.NORMAL -> {
                    val archivedConversationModel = getArchivedConversationModel(groupModel.getDatabaseId())
                    if (archivedConversationModel != null) {
                        conversationService.unarchive(listOf(archivedConversationModel), TriggerSource.SYNC)
                    }
                    val conversationModel = getConversationModel(groupModel.getDatabaseId())
                    if (conversationModel != null) {
                        conversationService.untag(conversationModel, ConversationTag.PINNED, TriggerSource.SYNC)
                    } else {
                        logger.error("The conversation intended to have normal visibility was not found.")
                    }
                }

                ConversationVisibility.ARCHIVED -> {
                    val conversationModel = getConversationModel(groupModel.getDatabaseId())
                    if (conversationModel != null) {
                        conversationService.untag(conversationModel, ConversationTag.PINNED, TriggerSource.SYNC)
                        conversationService.archive(conversationModel, TriggerSource.SYNC)
                    } else if (getArchivedConversationModel(groupModel.getDatabaseId()) != null) {
                        logger.warn("Conversation already is archived")
                    } else {
                        logger.error("The conversation intended to be archived was not found.")
                    }
                }

                ConversationVisibility.PINNED -> {
                    val archivedConversationModel = getArchivedConversationModel(groupModel.getDatabaseId())
                    if (archivedConversationModel != null) {
                        conversationService.unarchive(listOf(archivedConversationModel), TriggerSource.SYNC)
                    }
                    val conversationModel = getConversationModel(groupModel.getDatabaseId())
                    if (conversationModel != null) {
                        conversationService.tag(conversationModel, ConversationTag.PINNED, TriggerSource.SYNC)
                    } else {
                        logger.error("The conversation intended to be pinned was not found.")
                    }
                }

                ConversationVisibility.UNRECOGNIZED -> unrecognizedValue("conversation visibility")

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
        return conversationService.getArchived().find { it.group?.id?.toLong() == localGroupDatabaseId }
    }

    /**
     * Synchronously notify new group member listeners.
     */
    private fun notifyDeprecatedOnNewMemberListeners(
        groupIdentity: GroupIdentity,
        newIdentity: IdentityString,
    ) {
        ListenerManager.groupListeners.handle { it.onNewMember(groupIdentity, newIdentity) }
    }

    /**
     * Synchronously notify group member left listeners.
     */
    private fun notifyDeprecatedOnMemberLeaveListeners(
        groupIdentity: GroupIdentity,
        leftIdentity: IdentityString,
    ) {
        ListenerManager.groupListeners.handle { it.onMemberLeave(groupIdentity, leftIdentity) }
    }

    /**
     * Synchronously notify group member kicked listeners.
     */
    private fun notifyDeprecatedOnMemberKickedListeners(
        groupIdentity: GroupIdentity,
        kickedIdentity: IdentityString,
    ) {
        ListenerManager.groupListeners.handle { it.onMemberKicked(groupIdentity, kickedIdentity) }
    }

    private fun removeGroupAvatar(groupModel: GroupModel) {
        if (fileService.hasGroupProfilePicture(groupModel)) {
            fileService.removeGroupProfilePicture(groupModel)
            ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel.groupIdentity) }
            ShortcutUtil.updateShareTargetShortcut(
                groupService.createReceiver(groupModel),
                preferenceService.getContactNameFormat(),
            )
        }
    }

    private fun Blob.loadGroupProfilePictureAndMarkAsDone(groupModel: GroupModel) {
        val blobLoadingResult = loadAndMarkAsDone(
            okHttpClient = okHttpClient,
            version = AppVersionProvider.appVersion,
            serverAddressProvider = serverAddressProvider,
            multiDevicePropertyProvider = multiDeviceManager.propertiesProvider,
            symmetricEncryptionService = symmetricEncryptionService,
            fallbackNonce = ProtocolDefines.GROUP_PHOTO_NONCE,
            downloadBlobScope = BlobScope.Local,
            markAsDoneBlobScope = BlobScope.Local,
        )
        when (blobLoadingResult) {
            is ReflectedBlobDownloader.BlobLoadingResult.Success -> {
                if (!ExifInterface.isJpegFormat(blobLoadingResult.blobBytes)) {
                    logger.warn("Received group profile picture that is not a jpeg")
                }

                fileService.writeGroupProfilePicture(groupModel, blobLoadingResult.blobBytes)
                ListenerManager.groupListeners.handle { it.onUpdatePhoto(groupModel.groupIdentity) }
                ShortcutUtil.updateShareTargetShortcut(
                    groupService.createReceiver(groupModel),
                    preferenceService.getContactNameFormat(),
                )
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobMirrorNotAvailable -> {
                logger.warn("Cannot download blob because blob mirror is not available", blobLoadingResult.exception)
                throw ProtocolException("Blob mirror not available")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.DecryptionFailed -> {
                logger.error("Could not decrypt group profile picture blob", blobLoadingResult.exception)
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobNotFound -> {
                logger.error("Could not download group profile picture because the blob was not found")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobDownloadCancelled -> {
                logger.error("Could not download profile picture because the download was cancelled")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.Other -> {
                logger.error("Could not download profile picture because of an exception", blobLoadingResult.exception)
            }
        }
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
        otherMembers = Collections.unmodifiableSet(getMembers() - groupIdentity.creatorIdentity - myIdentity),
        userState = userState.convert() ?: UserState.MEMBER,
        notificationTriggerPolicyOverride = notificationTriggerPolicyOverride.convert(),
    )

    private fun Group.getMembers(): Set<String> = memberIdentities.identitiesList.toSet()

    private fun Group.UserState?.convert() = when (this) {
        Group.UserState.MEMBER -> UserState.MEMBER
        Group.UserState.LEFT -> UserState.LEFT
        Group.UserState.KICKED -> UserState.KICKED
        Group.UserState.UNRECOGNIZED -> unrecognizedValue("Group.UserState")
        null -> nullValue("Group.UserState")
    }

    private fun ch.threema.protobuf.common.GroupIdentity.convert() = GroupIdentity(
        creatorIdentity = creatorIdentity,
        groupId = groupId,
    )

    private fun Group.NotificationTriggerPolicyOverride.convert(): Long? =
        when (overrideCase) {
            Group.NotificationTriggerPolicyOverride.OverrideCase.DEFAULT -> null
            Group.NotificationTriggerPolicyOverride.OverrideCase.POLICY -> {
                when (policy.policy) {
                    Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.MENTIONED -> DEADLINE_INDEFINITE_EXCEPT_MENTIONS
                    Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER -> {
                        if (policy.hasExpiresAt()) {
                            policy.expiresAt
                        } else {
                            DEADLINE_INDEFINITE
                        }
                    }

                    Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.UNRECOGNIZED -> unrecognizedValue(
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
