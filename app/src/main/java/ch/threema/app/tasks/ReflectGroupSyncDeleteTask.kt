package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.data.models.GroupModel
import ch.threema.domain.models.UserState
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.getEncryptedGroupSyncDelete

enum class ReflectGroupSyncDeletePrecondition {
    USER_IS_NO_MEMBER,
    USER_IS_MEMBER,
}

/**
 * Reflects a group delete message.
 */
class ReflectGroupSyncDeleteTask(
    private val groupModel: GroupModel,
    private val precondition: ReflectGroupSyncDeletePrecondition,
    private val nonceFactory: NonceFactory,
    multiDeviceManager: MultiDeviceManager,
) : ReflectGroupSyncTask<Unit, Unit>(), ActiveTask<ReflectionResult<Unit>> {
    override val type = "ReflectGroupSyncDeleteTask"

    override val runPrecondition: () -> Boolean = {
        when (precondition) {
            ReflectGroupSyncDeletePrecondition.USER_IS_NO_MEMBER ->
                groupModel.data?.userState != UserState.MEMBER

            ReflectGroupSyncDeletePrecondition.USER_IS_MEMBER ->
                groupModel.data?.userState == UserState.MEMBER
        }
    }

    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        val encryptedEnvelopeResult = getEncryptedGroupSyncDelete(
            groupModel.getProtoGroupIdentity(),
            multiDeviceManager.propertiesProvider.get(),
        )

        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult,
            true,
            nonceFactory,
        )
    }

    override val runAfterSuccessfulTransaction: suspend (transactionResult: Unit) -> Unit = { }

    override suspend fun invoke(handle: ActiveTaskCodec): ReflectionResult<Unit> {
        if (!multiDeviceManager.isMultiDeviceActive) {
            return ReflectionResult.MultiDeviceNotActive()
        }

        return runTransaction(handle)
    }
}
