package ch.threema.app.tasks

import androidx.annotation.CallSuper
import ch.threema.app.managers.ServiceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedUserProfileSyncUpdate
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.sync.MdD2DSync

private val logger = getThreemaLogger("ReflectUserProfileShareWithPolicySyncTaskBase")

abstract class ReflectUserProfileShareWithPolicySyncTaskBase(
    protected val newPolicy: ProfilePictureSharePolicy.Policy,
    serviceManager: ServiceManager,
) : ActiveTask<Unit>, PersistableTask {
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }
    private val preferenceService by lazy { serviceManager.preferenceService }

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
        preferenceService.profilePicRelease = newPolicy.ordinal
    }
}
