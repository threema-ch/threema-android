package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.systemupdates.updates.SystemUpdateToVersion110
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.awaitReflectAck
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedGroupSyncUpdate
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.Group
import ch.threema.protobuf.d2d.sync.group
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * This task is needed for [SystemUpdateToVersion110], to ensure that the state changes made by the system update
 * are reflected if needed. It should not be used for any other purposes.
 */
class SyncFormerlyOrphanedGroupsTask() : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val nonceFactory: NonceFactory by inject()

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            return
        }

        val groups = groupModelRepository.getAll()
            .filter { groupModel -> !groupModel.isCreator() && groupModel.isKicked() }
        if (groups.isEmpty()) {
            return
        }

        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()
        handle.createTransaction(
            keys = multiDeviceProperties.keys,
            scope = TransactionScope.Scope.GROUP_SYNC,
            ttl = TRANSACTION_TTL_MAX,
        )
            .execute {
                groups.map { groupModel ->
                    val encryptedEnvelopeResult = getEncryptedGroupSyncUpdate(
                        group = group {
                            groupIdentity = groupModel.groupIdentity.toProtobuf()
                            userState = Group.UserState.KICKED
                        },
                        memberStateChanges = emptyMap(),
                        multiDeviceProperties = multiDeviceProperties,
                    )

                    handle.reflect(encryptedEnvelopeResult) to encryptedEnvelopeResult.nonce
                }
                    .forEach { (reflectId, nonce) ->
                        handle.awaitReflectAck(reflectId)
                        nonceFactory.store(
                            scope = NonceScope.D2D,
                            nonce = nonce,
                        )
                    }
            }
    }

    override val type = "SyncFormerlyOrphanedGroupsTask"

    override fun serialize(): SerializableTaskData = SyncFormerlyOrphanedGroupsTaskData

    @Serializable
    data object SyncFormerlyOrphanedGroupsTaskData : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> = SyncFormerlyOrphanedGroupsTask()
    }
}
