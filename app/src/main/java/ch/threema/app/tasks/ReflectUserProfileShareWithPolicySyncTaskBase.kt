package ch.threema.app.tasks

import androidx.annotation.CallSuper
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedUserProfileSyncUpdate
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.sync.MdD2DSync
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectUserProfileShareWithPolicySyncTaskBase")

abstract class ReflectUserProfileShareWithPolicySyncTaskBase(
    protected val newPolicy: ProfilePictureSharePolicy.Policy,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val nonceFactory: NonceFactory by inject()
    private val preferenceService: PreferenceService by inject()

    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    abstract fun createUpdatedUserProfile(): MdD2DSync.UserProfile

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cannot reflect share-with-policy of type {} because multi device is not active", type)
            return
        }

        handle.createTransaction(
            keys = mdProperties.keys,
            scope = MdD2D.TransactionScope.Scope.USER_PROFILE_SYNC,
            ttl = TRANSACTION_TTL_MAX,
        ).execute {
            encryptAndReflectUserProfileUpdate(handle)
        }
        persistLocally(preferenceService)
    }

    private suspend fun encryptAndReflectUserProfileUpdate(handle: ActiveTaskCodec) {
        val encryptedEnvelopeResult = getEncryptedUserProfileSyncUpdate(
            userProfile = createUpdatedUserProfile(),
            multiDeviceProperties = mdProperties,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    @CallSuper
    open fun persistLocally(preferenceService: PreferenceService) {
        preferenceService.setProfilePicRelease(newPolicy.ordinal)
    }
}
