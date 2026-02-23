package ch.threema.app.tasks

import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.protocol.PreGeneratedMessageIds
import ch.threema.app.protocol.runActiveGroupStateResyncSteps
import ch.threema.app.services.FileService
import ch.threema.app.services.UserService
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.toBasicContacts
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.storage.DatabaseService

private val logger = getThreemaLogger("ActiveGroupStateResyncTask")

class ActiveGroupStateResyncTask(
    private val groupModel: GroupModel,
    private val contactModelRepository: ContactModelRepository,
    private val contactStore: ContactStore,
    private val apiConnector: APIConnector,
    private val userService: UserService,
    private val groupProfilePictureUploader: GroupProfilePictureUploader,
    private val fileService: FileService,
    private val groupCallManager: GroupCallManager,
    private val databaseService: DatabaseService,
    private val outgoingCspMessageServices: OutgoingCspMessageServices,
) : ActiveTask<Boolean> {
    override val type = "ActiveGroupStateResyncTask"

    override suspend fun invoke(handle: ActiveTaskCodec): Boolean {
        val multiDeviceManager = outgoingCspMessageServices.multiDeviceManager

        return if (multiDeviceManager.isMultiDeviceActive) {
            val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

            handle.createTransaction(
                keys = multiDeviceProperties.keys,
                scope = MdD2D.TransactionScope.Scope.GROUP_SYNC,
                ttl = TRANSACTION_TTL_MAX,
                precondition = {
                    getGroupModelData()?.isMember == true
                },
            ).execute {
                runActiveGroupStateResyncSteps(handle)
            }
        } else {
            runActiveGroupStateResyncSteps(handle)
        }
    }

    private fun getGroupModelData(): GroupModelData? {
        val groupModelData = groupModel.data ?: run {
            logger.warn("Group model data is null: cannot resync group")
            null
        }

        return groupModelData
    }

    private suspend fun runActiveGroupStateResyncSteps(handle: ActiveTaskCodec): Boolean {
        val groupModelData = getGroupModelData() ?: return false

        runActiveGroupStateResyncSteps(
            groupModel,
            groupModelData.otherMembers.toBasicContacts(
                contactModelRepository,
                contactStore,
                apiConnector,
            ).toSet(),
            PreGeneratedMessageIds(
                firstMessageId = MessageId.random(),
                secondMessageId = MessageId.random(),
                thirdMessageId = MessageId.random(),
                fourthMessageId = MessageId.random(),
            ),
            userService,
            groupProfilePictureUploader,
            fileService,
            groupCallManager,
            databaseService,
            outgoingCspMessageServices,
            handle,
        )

        return true
    }
}
