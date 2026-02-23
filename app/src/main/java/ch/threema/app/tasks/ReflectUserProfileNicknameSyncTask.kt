package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedUserProfileSyncUpdate
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.sync.userProfile
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("ReflectUserProfileNicknameSyncTask")

class ReflectUserProfileNicknameSyncTask(
    private val newNickname: String,
    serviceManager: ServiceManager,
) : ActiveTask<Unit>, PersistableTask {
    private val identityStore by lazy { serviceManager.identityStore }
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    override val type: String = "ReflectUserProfileNicknameSyncTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cannot reflect nickname because multi device is not active")
            return
        }

        handle.createTransaction(
            keys = mdProperties.keys,
            scope = MdD2D.TransactionScope.Scope.USER_PROFILE_SYNC,
            ttl = TRANSACTION_TTL_MAX,
        ).execute {
            encryptAndReflectUserProfileUpdate(handle)
        }
        identityStore.setPublicNickname(newNickname)
    }

    private suspend fun encryptAndReflectUserProfileUpdate(handle: ActiveTaskCodec) {
        val encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult =
            getEncryptedUserProfileSyncUpdate(
                userProfile = userProfile {
                    nickname = newNickname
                },
                mdProperties,
            )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    override fun serialize(): SerializableTaskData =
        ReflectUserProfileNicknameSyncTaskData(
            newNickname = newNickname,
        )

    @Serializable
    data class ReflectUserProfileNicknameSyncTaskData(
        private val newNickname: String,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            ReflectUserProfileNicknameSyncTask(
                newNickname = newNickname,
                serviceManager = serviceManager,
            )
    }
}
