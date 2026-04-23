package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedUserProfileSyncUpdate
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.userProfile
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectUserProfileNicknameSyncTask")

class ReflectUserProfileNicknameSyncTask(
    private val newNickname: String,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val identityStore: IdentityStore by inject()
    private val nonceFactory: NonceFactory by inject()
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    override val type: String = "ReflectUserProfileNicknameSyncTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cannot reflect nickname because multi device is not active")
            return
        }

        handle.createTransaction(
            keys = mdProperties.keys,
            scope = TransactionScope.Scope.USER_PROFILE_SYNC,
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
        override fun createTask(): Task<*, TaskCodec> =
            ReflectUserProfileNicknameSyncTask(
                newNickname = newNickname,
            )
    }
}
