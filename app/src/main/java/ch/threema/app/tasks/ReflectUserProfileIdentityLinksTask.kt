package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.UserService
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedUserProfileSyncUpdate
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.UserProfile
import ch.threema.protobuf.d2d.sync.UserProfileKt.IdentityLinksKt.identityLink
import ch.threema.protobuf.d2d.sync.UserProfileKt.identityLinks
import ch.threema.protobuf.d2d.sync.userProfile
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectUserProfileIdentityLinksTask")

/**
 * Note that this task always reflects the current state when it is running. Therefore, this task should be scheduled whenever the identity links
 * are changed by the user on this device.
 */
class ReflectUserProfileIdentityLinksTask() : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val userService: UserService by inject()
    private val nonceFactory: NonceFactory by inject()
    private val multiDeviceManager: MultiDeviceManager by inject()

    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    override val type: String = "ReflectUserProfileIdentityLinksTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cannot reflect identity links because multi device is not active")
            return
        }

        handle.createTransaction(
            keys = mdProperties.keys,
            scope = TransactionScope.Scope.USER_PROFILE_SYNC,
            ttl = TRANSACTION_TTL_MAX,
        ).execute {
            encryptAndReflectUserProfileIdentityLinks(handle)
        }
    }

    private suspend fun encryptAndReflectUserProfileIdentityLinks(handle: ActiveTaskCodec) {
        val encryptedEnvelopeResult = getEncryptedUserProfileSyncUpdate(
            userProfile = getUserProfile(),
            multiDeviceProperties = mdProperties,
        )

        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    private fun getUserProfile(): UserProfile {
        return userProfile {
            identityLinks = getUserProfileSyncIdentityLinks(userService)
        }
    }

    override fun serialize(): SerializableTaskData = ReflectUserProfileIdentityLinksTaskData

    @Serializable
    data object ReflectUserProfileIdentityLinksTaskData : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            ReflectUserProfileIdentityLinksTask()
    }

    companion object {
        /**
         * Get the identity links that are part of the user profile sync.
         */
        fun getUserProfileSyncIdentityLinks(userService: UserService): UserProfile.IdentityLinks = identityLinks {
            if (userService.mobileLinkingState == UserService.LinkingState_LINKED) {
                val linkedPhoneNumber = userService.linkedMobileE164
                if (linkedPhoneNumber != null) {
                    links += identityLink { phoneNumber = linkedPhoneNumber }
                } else {
                    logger.error("Invalid state: mobile linking state is linked but no phone number is available")
                }
            }
            if (userService.emailLinkingState == UserService.LinkingState_LINKED) {
                val linkedEmail = userService.linkedEmail
                if (linkedEmail != null) {
                    links += identityLink { email = linkedEmail }
                } else {
                    logger.error("Invalid state: email linking state is linked but no email address is available")
                }
            }
        }
    }
}
