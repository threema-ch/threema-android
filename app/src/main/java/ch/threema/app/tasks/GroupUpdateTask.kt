package ch.threema.app.tasks

import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.profilepicture.RawProfilePicture
import ch.threema.app.protocolsteps.ExpectedProfilePictureChange
import ch.threema.app.protocolsteps.IdentityBlockedSteps
import ch.threema.app.protocolsteps.PredefinedMessageIds
import ch.threema.app.protocolsteps.runActiveGroupUpdateSteps
import ch.threema.app.services.FileService
import ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData.Companion.SerializableExpectedProfilePictureChange
import ch.threema.app.tasks.GroupUpdateTask.GroupUpdateTaskData.Companion.SerializablePredefinedMessageIds
import ch.threema.app.tasks.archive.recovery.TaskRecoveryManager
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.MdD2D
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("GroupUpdateTask")

class GroupUpdateTask(
    private val name: String?,
    private val expectedProfilePictureChange: ExpectedProfilePictureChange?,
    /**
     * Note that this does not contain the creator (and therefore also not the user).
     */
    private val updatedMembers: Set<String>,
    private val addedMembers: Set<String>,
    private val removedMembers: Set<String>,
    private val groupIdentity: GroupIdentity,
    private val predefinedMessageIds: PredefinedMessageIds,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val outgoingCspMessageServices: OutgoingCspMessageServices by inject()
    private val groupCallManager: GroupCallManager by inject()
    private val fileService: FileService by inject()
    private val groupProfilePictureUploader: GroupProfilePictureUploader by inject()
    private val groupModelRepository: GroupModelRepository by inject()

    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }
    private val identityBlockedSteps: IdentityBlockedSteps by inject()

    override val type = "GroupUpdateTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (multiDeviceManager.isMultiDeviceActive) {
            try {
                executeActiveGroupUpdateInTransaction(handle)
            } catch (e: TransactionScope.TransactionException) {
                logger.warn("A group sync race occurred", e)
            }
        } else {
            executeActiveGroupUpdate(handle)
        }
    }

    private suspend fun executeActiveGroupUpdateInTransaction(handle: ActiveTaskCodec) {
        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

        handle.createTransaction(
            multiDeviceProperties.keys,
            MdD2D.TransactionScope.Scope.GROUP_SYNC,
            TRANSACTION_TTL_MAX,
            precondition = {
                groupModelRepository.getByGroupIdentity(groupIdentity)?.data?.let { it.isMember && it.otherMembers.isNotEmpty() } == true
            },
        ).execute {
            executeActiveGroupUpdate(handle)
        }
    }

    private suspend fun executeActiveGroupUpdate(handle: ActiveTaskCodec) {
        val groupModel = groupModelRepository.getByGroupIdentity(groupIdentity)
        val groupModelData = groupModel?.data ?: run {
            logger.warn("Group sync race occurred: Group model does not exist")
            return
        }

        checkForGroupSyncRace(groupModel, groupModelData)
        runActiveGroupUpdateSteps(
            expectedProfilePictureChange = expectedProfilePictureChange,
            addMembers = addedMembers,
            removeMembers = removedMembers,
            predefinedMessageIds = predefinedMessageIds,
            groupModel = groupModel,
            services = outgoingCspMessageServices,
            identityBlockedSteps = identityBlockedSteps,
            groupCallManager = groupCallManager,
            fileService = fileService,
            groupProfilePictureUploader = groupProfilePictureUploader,
            handle = handle,
        )
    }

    private fun checkForGroupSyncRace(groupModel: GroupModel, groupModelData: GroupModelData) {
        if (name != null && groupModelData.name != name) {
            logger.warn("Group sync race occurred: Group name is not equal")
        }

        val persistedGroupProfilePicture = fileService.getGroupProfilePictureBytes(groupModel)?.let { bytes -> RawProfilePicture(bytes) }
        when (expectedProfilePictureChange) {
            is ExpectedProfilePictureChange.Set -> {
                val expectedProfilePicture = expectedProfilePictureChange.profilePicture
                if (persistedGroupProfilePicture == null) {
                    logger.warn("Group sync race occurred: No group profile picture is persisted")
                } else if (!persistedGroupProfilePicture.contentEquals(expectedProfilePicture)) {
                    logger.warn("Group sync race occurred: Different group profile picture is persisted")
                }
            }

            ExpectedProfilePictureChange.Remove -> {
                if (persistedGroupProfilePicture != null) {
                    logger.warn("Group sync race occurred: Group profile picture is persisted")
                }
            }

            null -> Unit
        }

        if (groupModelData.otherMembers != updatedMembers) {
            logger.warn("Group sync race occurred: Group members are not equal")
        }
    }

    override fun serialize(): SerializableTaskData = GroupUpdateTaskData(
        name = name,
        serializableExpectedProfilePictureChange = SerializableExpectedProfilePictureChange.fromExpectedProfilePictureChange(
            expectedProfilePictureChange = expectedProfilePictureChange,
        ),
        updatedMembers = updatedMembers,
        addedMembers = addedMembers,
        removedMembers = removedMembers,
        groupIdentity = groupIdentity,
        serializablePredefinedMessageIds = SerializablePredefinedMessageIds(predefinedMessageIds),
    )

    /**
     * Do not change the class name or any of its fields. Any modification of it will cause the decoding to fail. If it is modified, then a migration
     * handler should be added to the [TaskRecoveryManager] that can recover this task data from the previously persisted encoding.
     */
    @Serializable
    data class GroupUpdateTaskData(
        private val name: String?,
        private val serializableExpectedProfilePictureChange: SerializableExpectedProfilePictureChange,
        private val updatedMembers: Set<String>,
        private val addedMembers: Set<String>,
        private val removedMembers: Set<String>,
        private val groupIdentity: GroupIdentity,
        private val serializablePredefinedMessageIds: SerializablePredefinedMessageIds,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            GroupUpdateTask(
                name = name,
                expectedProfilePictureChange = serializableExpectedProfilePictureChange.getExpectedProfilePictureChange(),
                updatedMembers = updatedMembers,
                addedMembers = addedMembers,
                removedMembers = removedMembers,
                groupIdentity = groupIdentity,
                predefinedMessageIds = serializablePredefinedMessageIds.getPredefinedMessageIds(),
            )

        companion object {
            /**
             * Do not change the class name or any of its fields. Any modification of it will cause the decoding to fail. If it is modified, then a
             * migration handler should be added to the [TaskRecoveryManager] that can recover this task data from the previously persisted encoding.
             */
            @Serializable
            class SerializablePredefinedMessageIds(
                private val messageId1: Long,
                private val messageId2: Long,
                private val messageId3: Long,
                private val messageId4: Long,
            ) {
                constructor(predefinedMessageIds: PredefinedMessageIds) : this(
                    messageId1 = predefinedMessageIds.messageId1.messageIdLong,
                    messageId2 = predefinedMessageIds.messageId2.messageIdLong,
                    messageId3 = predefinedMessageIds.messageId3.messageIdLong,
                    messageId4 = predefinedMessageIds.messageId4.messageIdLong,
                )

                fun getPredefinedMessageIds(): PredefinedMessageIds =
                    PredefinedMessageIds(
                        messageId1 = MessageId(messageId1),
                        messageId2 = MessageId(messageId2),
                        messageId3 = MessageId(messageId3),
                        messageId4 = MessageId(messageId4),
                    )
            }

            /**
             * Do not change the interface name or any of its fields or subclasses. Any modification of it will cause the decoding to fail. If it is
             * modified, then a migration handler should be added to the [TaskRecoveryManager] that can recover this task data from the previously
             * persisted encoding.
             */
            @Serializable
            sealed interface SerializableExpectedProfilePictureChange {

                fun getExpectedProfilePictureChange(): ExpectedProfilePictureChange?

                @Serializable
                class Set(
                    val profilePictureBytes: ByteArray,
                    val blobId: ByteArray,
                    val encryptionKey: ByteArray,
                    val size: Int,
                ) : SerializableExpectedProfilePictureChange {
                    override fun getExpectedProfilePictureChange() = ExpectedProfilePictureChange.Set.WithUpload(
                        profilePictureUploadResultSuccess = GroupProfilePictureUploader.GroupProfilePictureUploadResult.Success(
                            profilePicture = RawProfilePicture(profilePictureBytes),
                            blobId = blobId,
                            encryptionKey = encryptionKey,
                            size = size,
                        ),
                    )
                }

                @Serializable
                class SetWithoutUpload(
                    val profilePictureBytes: ByteArray,
                ) : SerializableExpectedProfilePictureChange {
                    override fun getExpectedProfilePictureChange() = ExpectedProfilePictureChange.Set.WithoutUpload(
                        profilePicture = RawProfilePicture(profilePictureBytes),
                    )
                }

                @Serializable
                object Remove : SerializableExpectedProfilePictureChange {
                    override fun getExpectedProfilePictureChange() = ExpectedProfilePictureChange.Remove
                }

                @Serializable
                object NoChange : SerializableExpectedProfilePictureChange {
                    override fun getExpectedProfilePictureChange() = null
                }

                companion object {
                    fun fromExpectedProfilePictureChange(
                        expectedProfilePictureChange: ExpectedProfilePictureChange?,
                    ): SerializableExpectedProfilePictureChange = when (expectedProfilePictureChange) {
                        is ExpectedProfilePictureChange.Set.WithUpload -> Set(
                            profilePictureBytes = expectedProfilePictureChange.profilePictureUploadResultSuccess.profilePicture.bytes,
                            blobId = expectedProfilePictureChange.profilePictureUploadResultSuccess.blobId,
                            encryptionKey = expectedProfilePictureChange.profilePictureUploadResultSuccess.encryptionKey,
                            size = expectedProfilePictureChange.profilePictureUploadResultSuccess.size,
                        )

                        is ExpectedProfilePictureChange.Set.WithoutUpload -> SetWithoutUpload(
                            profilePictureBytes = expectedProfilePictureChange.profilePicture.bytes,
                        )

                        is ExpectedProfilePictureChange.Remove -> Remove

                        null -> NoChange
                    }
                }
            }
        }
    }
}
