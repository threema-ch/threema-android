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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.protocol.ProfilePictureChange
import ch.threema.app.protocol.RemoveProfilePicture
import ch.threema.app.protocol.SetProfilePicture
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationTagService
import ch.threema.app.services.FileService
import ch.threema.app.utils.ConversationUtil
import ch.threema.app.utils.contentEquals
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.models.ModelDeletedException
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.getEncryptedGroupSyncUpdate
import ch.threema.protobuf.Common
import ch.threema.protobuf.blob
import ch.threema.protobuf.d2d.MdD2D.GroupSync.Update.MemberStateChange
import ch.threema.protobuf.d2d.sync.GroupKt
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group
import ch.threema.protobuf.d2d.sync.group
import ch.threema.protobuf.deltaImage
import ch.threema.protobuf.identities
import ch.threema.protobuf.image
import ch.threema.protobuf.unit
import ch.threema.storage.models.ConversationTag
import com.google.protobuf.kotlin.toByteString
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("ReflectGroupSyncUpdateTask")

abstract class ReflectGroupSyncUpdateBaseTask<TransactionResult, TaskResult>(
    protected val groupModel: GroupModel,
    private val nonceFactory: NonceFactory,
    multiDeviceManager: MultiDeviceManager,
) : ReflectGroupSyncTask<TransactionResult, TaskResult>(multiDeviceManager) {
    /**
     * The task type. This is just used for debugging.
     */
    protected abstract val type: String

    /**
     * This method is called as part of the transaction's precondition. Specific sub classes may
     * define their own precondition based on the current group model data by implementing this
     * method.
     */
    protected abstract fun checkInPrecondition(currentData: GroupModelData): Boolean

    /**
     * Get the group sync that contains the delta updates.
     */
    abstract fun getGroupSync(): Group

    /**
     * Get the member state changes.
     */
    abstract fun getMemberStateChanges(): Map<String, MemberStateChange>

    /**
     * As a precondition for an update task, the group with the given group identity must exist and
     * the changed data must differ from the current data.
     */
    final override val runPrecondition: () -> Boolean = {
        // The group must exist and the task specific check must be successful
        groupModel.data.value?.let {
            checkInPrecondition(it)
        } ?: false
    }

    protected suspend fun reflectGroupSync(handle: ActiveTaskCodec) {
        logger.info("Reflecting group sync update of type {}", type)

        val encryptedEnvelopeResult = getEncryptedGroupSyncUpdate(
            group = getGroupSync().also { groupSync: Group ->
                check(
                    groupSync.groupIdentity.creatorIdentity == groupModel.groupIdentity.creatorIdentity &&
                        groupSync.groupIdentity.groupId == groupModel.groupIdentity.groupId,
                ) { "Group identity must match" }
            },
            memberStateChanges = getMemberStateChanges(),
            multiDeviceProperties = mdProperties,
        )

        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }
}

/**
 * This task must be run *before* the changes have been persisted. It cannot be run using the task
 * manager as it must be run immediately inside another task.
 */
abstract class ReflectGroupSyncUpdateImmediateTask(
    groupModel: GroupModel,
    nonceFactory: NonceFactory,
    multiDeviceManager: MultiDeviceManager,
) : ReflectGroupSyncUpdateBaseTask<Unit, Unit>(
    groupModel,
    nonceFactory,
    multiDeviceManager,
) {
    suspend fun reflect(handle: ActiveTaskCodec): ReflectionResult<Unit> {
        return runTransaction(handle)
    }

    /**
     * This method is run inside the transaction before the sync messages are sent.
     */
    protected abstract fun checkForDataRaces(currentData: GroupModelData)

    /**
     * The default precondition for immediate tasks is checking that the group is not deleted and
     * the user state is member.
     */
    override fun checkInPrecondition(currentData: GroupModelData): Boolean {
        return currentData.isMember
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        val groupModelData = groupModel.data.value
        check(groupModelData != null) { "Group model data cannot be null at this point" }
        checkForDataRaces(groupModelData)

        reflectGroupSync(handle)
    }

    override fun getMemberStateChanges(): Map<String, MemberStateChange> = emptyMap()

    override val runAfterSuccessfulTransaction: (transactionResult: Unit) -> Unit = {
        // Nothing to do
    }

    /**
     * This reflection task can be used to reflect that the user has been kicked.
     */
    class ReflectUserKicked(
        groupModel: GroupModel,
        nonceFactory: NonceFactory,
        multiDeviceManager: MultiDeviceManager,
    ) : ReflectGroupSyncUpdateImmediateTask(
        groupModel,
        nonceFactory,
        multiDeviceManager,
    ) {
        override val type = "ReflectUserKicked"

        override fun checkForDataRaces(currentData: GroupModelData) {
            // Nothing to check here as this check is already done in the precondition
        }

        override fun getGroupSync(): Group = group {
            groupIdentity = groupModel.groupIdentity.toProtobuf()
            userState = Group.UserState.KICKED
        }
    }

    class ReflectMemberLeft(
        private val leftMemberIdentity: String,
        groupModel: GroupModel,
        nonceFactory: NonceFactory,
        multiDeviceManager: MultiDeviceManager,
    ) : ReflectGroupSyncUpdateImmediateTask(
        groupModel,
        nonceFactory,
        multiDeviceManager,
    ) {
        override val type = "ReflectMemberLeft"

        override fun checkForDataRaces(currentData: GroupModelData) {
            if (!currentData.otherMembers.contains(leftMemberIdentity)) {
                logger.warn("Group race occurred: Left member has already been removed from data")
            }
        }

        override fun getGroupSync(): Group {
            val groupModelData = groupModel.data.value
                ?: throw IllegalStateException("Group model data is null")

            val updatedMembers = groupModelData.otherMembers.filter { it != leftMemberIdentity }

            return group {
                groupIdentity = groupModel.groupIdentity.toProtobuf()
                memberIdentities = identities {
                    this.identities.clear()
                    this.identities.addAll(updatedMembers)
                }
            }
        }

        override fun getMemberStateChanges(): Map<String, MemberStateChange> {
            return mapOf(leftMemberIdentity to MemberStateChange.LEFT)
        }
    }

    class ReflectGroupName(
        private val newGroupName: String,
        groupModel: GroupModel,
        nonceFactory: NonceFactory,
        multiDeviceManager: MultiDeviceManager,
    ) : ReflectGroupSyncUpdateImmediateTask(
        groupModel,
        nonceFactory,
        multiDeviceManager,
    ) {
        override val type = "ReflectGroupName"

        override fun checkForDataRaces(currentData: GroupModelData) {
            if (newGroupName == currentData.name) {
                logger.warn("Group race occurred: The name did not change")
            }
        }

        override fun getGroupSync(): Group = group {
            groupIdentity = this@ReflectGroupName.groupModel.groupIdentity.toProtobuf()
            name = newGroupName
        }
    }

    class ReflectGroupSetProfilePicture(
        private val blobId: ByteArray,
        private val encryptionKey: ByteArray,
        private val blobNonce: ByteArray,
        groupModel: GroupModel,
        private val profilePictureBlob: ByteArray,
        private val fileService: FileService,
        nonceFactory: NonceFactory,
        multiDeviceManager: MultiDeviceManager,
    ) : ReflectGroupSyncUpdateImmediateTask(
        groupModel,
        nonceFactory,
        multiDeviceManager,
    ) {
        override val type = "ReflectGroupDeleteProfilePicture"

        override fun checkForDataRaces(currentData: GroupModelData) {
            if (fileService.getGroupAvatarStream(groupModel).contentEquals(profilePictureBlob)) {
                logger.warn("Group race occurred: The profile picture did not change")
            }
        }

        override fun getGroupSync(): Group = group {
            groupIdentity =
                this@ReflectGroupSetProfilePicture.groupModel.groupIdentity.toProtobuf()
            profilePicture = deltaImage {
                updated = image {
                    blob = blob {
                        id = blobId.toByteString()
                        key = encryptionKey.toByteString()
                        nonce = blobNonce.toByteString()
                    }
                }
            }
        }
    }

    class ReflectGroupDeleteProfilePicture(
        groupModel: GroupModel,
        private val fileService: FileService,
        nonceFactory: NonceFactory,
        multiDeviceManager: MultiDeviceManager,
    ) : ReflectGroupSyncUpdateImmediateTask(
        groupModel,
        nonceFactory,
        multiDeviceManager,
    ) {
        override val type = "ReflectGroupDeleteProfilePicture"

        override fun checkForDataRaces(currentData: GroupModelData) {
            if (!fileService.hasGroupAvatarFile(groupModel)) {
                logger.warn("Group race occurred: There is no group profile picture for this group")
            }
        }

        override fun getGroupSync(): Group = group {
            groupIdentity =
                this@ReflectGroupDeleteProfilePicture.groupModel.groupIdentity.toProtobuf()
            profilePicture = deltaImage {
                removed = unit { }
            }
        }
    }
}

abstract class ReflectGroupSyncUpdateTask(
    groupModel: GroupModel,
    multiDeviceManager: MultiDeviceManager,
    nonceFactory: NonceFactory,
) : ReflectGroupSyncUpdateBaseTask<Unit, Unit>(
    groupModel = groupModel,
    nonceFactory = nonceFactory,
    multiDeviceManager = multiDeviceManager,
),
    ActiveTask<Unit>,
    PersistableTask {
    /**
     * Return true if the change that should be reflected still matches the current data. Note that
     * if a task performs several changes, then *all* of the new values must be equal to
     * [currentData].
     */
    protected abstract fun isChangeValid(currentData: GroupModelData): Boolean

    final override fun checkInPrecondition(currentData: GroupModelData): Boolean = isChangeValid(currentData)

    /**
     *  Not changing any member states in [ReflectGroupSyncUpdateTask].
     *  Use the tasks of [ReflectGroupSyncUpdateImmediateTask] for that.
     */
    final override fun getMemberStateChanges(): Map<String, MemberStateChange> = emptyMap()

    final override fun getGroupSync(): Group = group {
        this.groupIdentity = groupModel.groupIdentity.toProtobuf()
        this.buildGroupSyncChanges()
    }

    protected abstract val buildGroupSyncChanges: GroupKt.Dsl.() -> Unit

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        reflectGroupSync(handle)
    }

    override val runAfterSuccessfulTransaction: (transactionResult: Unit) -> Unit = {
        // Nothing to do
    }

    final override suspend fun invoke(handle: ActiveTaskCodec) {
        runTransaction(handle)
    }

    class ReflectNotificationTriggerPolicyOverrideUpdate(
        private val newNotificationTriggerPolicyOverride: NotificationTriggerPolicyOverride,
        groupModel: GroupModel,
        nonceFactory: NonceFactory,
        multiDeviceManager: MultiDeviceManager,
    ) : ReflectGroupSyncUpdateTask(
        groupModel = groupModel,
        nonceFactory = nonceFactory,
        multiDeviceManager = multiDeviceManager,
    ) {
        override val type: String = "ReflectNotificationTriggerPolicyOverrideUpdate"

        override fun isChangeValid(currentData: GroupModelData): Boolean =
            currentData.currentNotificationTriggerPolicyOverride == newNotificationTriggerPolicyOverride

        override val buildGroupSyncChanges: GroupKt.Dsl.() -> Unit = {
            this.notificationTriggerPolicyOverride = GroupKt.notificationTriggerPolicyOverride {
                when (newNotificationTriggerPolicyOverride) {
                    NotificationTriggerPolicyOverride.NotMuted -> default = unit {}
                    NotificationTriggerPolicyOverride.MutedIndefinite ->
                        policy = GroupKt.NotificationTriggerPolicyOverrideKt.policy {
                            policy = Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
                        }

                    NotificationTriggerPolicyOverride.MutedIndefiniteExceptMentions ->
                        policy = GroupKt.NotificationTriggerPolicyOverrideKt.policy {
                            policy = Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.MENTIONED
                        }

                    is NotificationTriggerPolicyOverride.MutedUntil ->
                        policy = GroupKt.NotificationTriggerPolicyOverrideKt.policy {
                            policy = Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
                            expiresAt = newNotificationTriggerPolicyOverride.utcMillis
                        }
                }
            }
        }

        override fun serialize(): SerializableTaskData =
            ReflectNotificationTriggerPolicyOverrideUpdateData(
                newNotificationTriggerPolicyOverride = newNotificationTriggerPolicyOverride,
                groupIdentity = groupModel.groupIdentity,
            )

        @Serializable
        data class ReflectNotificationTriggerPolicyOverrideUpdateData(
            private val newNotificationTriggerPolicyOverride: NotificationTriggerPolicyOverride,
            private val groupIdentity: GroupIdentity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
                val groupModel =
                    serviceManager.modelRepositories.groups.getByGroupIdentity(groupIdentity)
                        ?: throw ModelDeletedException(
                            modelName = "GroupModel",
                            methodName = "ReflectNotificationTriggerPolicyOverrideUpdateData.createTask",
                        )
                return ReflectNotificationTriggerPolicyOverrideUpdate(
                    newNotificationTriggerPolicyOverride = newNotificationTriggerPolicyOverride,
                    groupModel = groupModel,
                    nonceFactory = serviceManager.nonceFactory,
                    multiDeviceManager = serviceManager.multiDeviceManager,
                )
            }
        }
    }

    /**
     * Note that this task currently just reflects the current conversation category state of the group as the conversation category is not part of
     * the group model.
     */
    class ReflectGroupConversationCategoryUpdateTask(
        groupModel: GroupModel,
        private val isPrivateChat: Boolean,
        nonceFactory: NonceFactory,
        private val conversationCategoryService: ConversationCategoryService,
        multiDeviceManager: MultiDeviceManager,
    ) : ReflectGroupSyncUpdateTask(groupModel, multiDeviceManager, nonceFactory) {
        override val type: String = "ReflectGroupConversationCategoryUpdateTask"

        override fun isChangeValid(currentData: GroupModelData): Boolean {
            // The change must be correct, otherwise we must not reflect that change
            return conversationCategoryService.isPrivateGroupChat(groupModel.getDatabaseId()) == isPrivateChat
        }

        override val buildGroupSyncChanges: GroupKt.Dsl.() -> Unit = {
            conversationCategory = if (isPrivateChat) {
                MdD2DSync.ConversationCategory.PROTECTED
            } else {
                MdD2DSync.ConversationCategory.DEFAULT
            }
        }

        override fun serialize(): SerializableTaskData {
            return ReflectGroupConversationCategoryData(
                groupIdentity = groupModel.groupIdentity,
                isPrivateChat = isPrivateChat,
            )
        }

        @Serializable
        data class ReflectGroupConversationCategoryData(
            private val groupIdentity: GroupIdentity,
            private val isPrivateChat: Boolean,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
                val groupModel =
                    serviceManager.modelRepositories.groups.getByGroupIdentity(groupIdentity)
                        ?: throw ModelDeletedException(
                            modelName = "GroupModel",
                            methodName = "ReflectGroupConversationCategoryData.createTask",
                        )
                return ReflectGroupConversationCategoryUpdateTask(
                    groupModel = groupModel,
                    isPrivateChat = isPrivateChat,
                    nonceFactory = serviceManager.nonceFactory,
                    conversationCategoryService = serviceManager.conversationCategoryService,
                    multiDeviceManager = serviceManager.multiDeviceManager,
                )
            }
        }
    }

    /**
     * Reflect a new conversation visibility regarding the archive option.
     *
     * TODO(ANDR-3721): There should only be one task that reflects the conversation visibility.
     */
    class ReflectGroupConversationVisibilityArchiveUpdate(
        private val isArchived: Boolean,
        groupModel: GroupModel,
        nonceFactory: NonceFactory,
        multiDeviceManager: MultiDeviceManager,
    ) : ReflectGroupSyncUpdateTask(
        groupModel = groupModel,
        nonceFactory = nonceFactory,
        multiDeviceManager = multiDeviceManager,
    ) {
        override val type: String = "ReflectGroupConversationVisibilityArchiveUpdate"

        override fun isChangeValid(currentData: GroupModelData): Boolean =
            currentData.isArchived == isArchived

        override val buildGroupSyncChanges: GroupKt.Dsl.() -> Unit = {
            this.conversationVisibility = if (isArchived) {
                MdD2DSync.ConversationVisibility.ARCHIVED
            } else {
                MdD2DSync.ConversationVisibility.NORMAL
            }
        }

        override fun serialize(): SerializableTaskData =
            ReflectGroupConversationVisibilityArchiveUpdateData(
                isArchived = isArchived,
                groupIdentity = groupModel.groupIdentity,
            )

        @Serializable
        data class ReflectGroupConversationVisibilityArchiveUpdateData(
            private val isArchived: Boolean,
            private val groupIdentity: GroupIdentity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
                val groupModel =
                    serviceManager.modelRepositories.groups.getByGroupIdentity(groupIdentity)
                        ?: throw ModelDeletedException(
                            modelName = "GroupModel",
                            methodName = "ReflectGroupConversationVisibilityArchiveUpdateData.createTask",
                        )
                return ReflectGroupConversationVisibilityArchiveUpdate(
                    isArchived = isArchived,
                    groupModel = groupModel,
                    nonceFactory = serviceManager.nonceFactory,
                    multiDeviceManager = serviceManager.multiDeviceManager,
                )
            }
        }
    }

    /**
     * Reflect a new conversation visibility regarding the pin option.
     *
     * TODO(ANDR-3721): There should only be one task that reflects the conversation visibility.
     */
    class ReflectGroupConversationVisibilityPinnedUpdate(
        private val isPinned: Boolean,
        groupModel: GroupModel,
        private val conversationTagService: ConversationTagService,
        nonceFactory: NonceFactory,
        multiDeviceManager: MultiDeviceManager,
    ) : ReflectGroupSyncUpdateTask(
        groupModel = groupModel,
        nonceFactory = nonceFactory,
        multiDeviceManager = multiDeviceManager,
    ) {
        override val type: String = "ReflectGroupConversationVisibilityPinnedUpdate"

        override fun isChangeValid(currentData: GroupModelData): Boolean =
            conversationTagService.isTaggedWith(
                ConversationUtil.getGroupConversationUid(groupModel.getDatabaseId()),
                ConversationTag.PINNED,
            ) == isPinned

        override val buildGroupSyncChanges: GroupKt.Dsl.() -> Unit = {
            this.conversationVisibility = if (isPinned) {
                MdD2DSync.ConversationVisibility.PINNED
            } else {
                MdD2DSync.ConversationVisibility.NORMAL
            }
        }

        override fun serialize(): SerializableTaskData =
            ReflectGroupConversationVisibilityPinnedUpdateData(
                isPinned = isPinned,
                groupIdentity = groupModel.groupIdentity,
            )

        @Serializable
        data class ReflectGroupConversationVisibilityPinnedUpdateData(
            private val isPinned: Boolean,
            private val groupIdentity: GroupIdentity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
                val groupModel =
                    serviceManager.modelRepositories.groups.getByGroupIdentity(groupIdentity)
                        ?: throw ModelDeletedException(
                            modelName = "GroupModel",
                            methodName = "ReflectGroupConversationVisibilityPinnedUpdateData.createTask",
                        )
                return ReflectGroupConversationVisibilityPinnedUpdate(
                    isPinned = isPinned,
                    groupModel = groupModel,
                    conversationTagService = serviceManager.conversationTagService,
                    nonceFactory = serviceManager.nonceFactory,
                    multiDeviceManager = serviceManager.multiDeviceManager,
                )
            }
        }
    }
}

abstract class ReflectGroupSyncUpdateFromLocal<T>(
    groupModel: GroupModel,
    nonceFactory: NonceFactory,
    multiDeviceManager: MultiDeviceManager,
) : ReflectGroupSyncUpdateBaseTask<T, ReflectionResult<Unit>>(
    groupModel,
    nonceFactory,
    multiDeviceManager,
),
    ActiveTask<ReflectionResult<Unit>> {
    override suspend fun invoke(handle: ActiveTaskCodec): ReflectionResult<Unit> {
        try {
            return reflectSync(handle)
        } catch (e: TransactionScope.TransactionException) {
            logger.error("Could not reflect as the precondition failed", e)
            return ReflectionPreconditionFailed(e)
        }
    }
}

class ReflectLocalGroupUpdate(
    private val updatedName: String?,
    private val addMembers: Set<String>,
    private val removeMembers: Set<String>,
    private val profilePictureChange: ProfilePictureChange?,
    private val uploadGroupPhoto: (ProfilePictureChange?) -> GroupPhotoUploadResult?,
    private val finishGroupUpdate: (GroupPhotoUploadResult?) -> Unit,
    groupModel: GroupModel,
    nonceFactory: NonceFactory,
    private val contactModelRepository: ContactModelRepository,
    multiDeviceManager: MultiDeviceManager,
) : ReflectGroupSyncUpdateFromLocal<Unit>(
    groupModel,
    nonceFactory,
    multiDeviceManager,
) {
    override val type = "ReflectGroupLocalUpdate"

    private var groupPhotoUploadResult: GroupPhotoUploadResult? = null

    override fun checkInPrecondition(currentData: GroupModelData): Boolean {
        return addMembers.all { contactModelRepository.getByIdentity(it) != null } &&
            removeMembers.all { contactModelRepository.getByIdentity(it) != null }
    }

    override fun getGroupSync(): Group {
        val groupModelData = groupModel.data.value ?: run {
            throw IllegalStateException("Group model data cannot be null at this point")
        }

        val updatedMembers = groupModelData.otherMembers + addMembers - removeMembers -
            groupModelData.groupIdentity.creatorIdentity

        return group {
            groupIdentity = this@ReflectLocalGroupUpdate.groupModel.groupIdentity.toProtobuf()
            updatedName?.let {
                name = it
            }
            memberIdentities = identities {
                identities.addAll(updatedMembers)
            }
            when (profilePictureChange) {
                is SetProfilePicture -> {
                    groupPhotoUploadResult?.let {
                        profilePicture = deltaImage {
                            updated = image {
                                type = Common.Image.Type.JPEG
                                blob = blob {
                                    id = it.blobId.toByteString()
                                    nonce = ProtocolDefines.GROUP_PHOTO_NONCE.toByteString()
                                    key = it.encryptionKey.toByteString()
                                }
                            }
                        }
                    } ?: {
                        logger.error("Not reflecting group picture as upload failed")
                    }
                }

                is RemoveProfilePicture -> {
                    profilePicture = deltaImage {
                        removed = unit { }
                    }
                }

                null -> Unit
            }
        }
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        groupPhotoUploadResult = uploadGroupPhoto(profilePictureChange)
        reflectGroupSync(handle)
    }

    override val runAfterSuccessfulTransaction: (transactionResult: Unit) -> ReflectionResult<Unit> =
        {
            finishGroupUpdate(groupPhotoUploadResult)
            ReflectionSuccess(Unit)
        }

    override fun getMemberStateChanges(): Map<String, MemberStateChange> {
        val memberStateChanges: MutableMap<String, MemberStateChange> = mutableMapOf()

        addMembers.forEach { memberStateChanges[it] = MemberStateChange.ADDED }
        removeMembers.forEach { memberStateChanges[it] = MemberStateChange.KICKED }

        return memberStateChanges
    }
}

class ReflectLocalGroupLeaveOrDisband(
    groupModel: GroupModel,
    nonceFactory: NonceFactory,
    multiDeviceManager: MultiDeviceManager,
) : ReflectGroupSyncUpdateFromLocal<Unit>(
    groupModel,
    nonceFactory,
    multiDeviceManager,
) {
    override val type = "ReflectLocalGroupLeave"

    override fun checkInPrecondition(currentData: GroupModelData) = currentData.isMember

    override fun getGroupSync() = group {
        groupIdentity = groupModel.groupIdentity.toProtobuf()
        userState = Group.UserState.LEFT
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        reflectGroupSync(handle)
    }

    override val runAfterSuccessfulTransaction: (transactionResult: Unit) -> ReflectionResult<Unit> =
        { ReflectionSuccess(Unit) }

    override fun getMemberStateChanges(): Map<String, MemberStateChange> = emptyMap()
}
