package ch.threema.app.tasks

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.getEncryptedUserProfileSyncUpdate
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.userProfile
import kotlinx.serialization.Serializable
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectUserAvailabilityStatusTask")

class ReflectUserAvailabilityStatusTask :
    ReflectSyncTask<Result<Unit>, Result<Unit>>(TransactionScope.Scope.USER_PROFILE_SYNC),
    ActiveTask<Result<Unit>>,
    PersistableTask {

    private val preferenceService: PreferenceService by inject()
    private val nonceFactory: NonceFactory by inject()

    private val availabilityStatus: AvailabilityStatus? by lazy {
        preferenceService.getAvailabilityStatus()
    }

    override val type: String = "ReflectUserAvailabilityStatusTask"

    override val runPrecondition: () -> Boolean = precondition@{
        if (!ConfigUtils.supportsAvailabilityStatus()) {
            logger.error("Cannot reflect availability status because this feature is not supported by this build")
            return@precondition false
        }
        if (availabilityStatus == null) {
            logger.error("Cannot reflect availability status because it is null")
            return@precondition false
        }
        true
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Result<Unit> = transaction@{ handle ->

        val currentAvailabilityStatus = availabilityStatus
            ?: return@transaction Result.failure(IllegalStateException("Missing current availability status"))

        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = getEncryptedUserProfileSyncUpdate(
                userProfile = userProfile {
                    workAvailabilityStatus = currentAvailabilityStatus.toProtocolModel()
                },
                multiDeviceProperties = mdProperties,
            ),
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )

        return@transaction Result.success(Unit)
    }

    override val runAfterSuccessfulTransaction: suspend (transactionResult: Result<Unit>) -> Result<Unit> = { it }

    override suspend fun invoke(handle: ActiveTaskCodec): Result<Unit> = reflectSync(handle)

    override fun serialize(): SerializableTaskData = ReflectUserAvailabilityStatusTaskData

    @Serializable
    data object ReflectUserAvailabilityStatusTaskData : SerializableTaskData {

        override fun createTask(): Task<*, TaskCodec> = ReflectUserAvailabilityStatusTask()
    }
}
