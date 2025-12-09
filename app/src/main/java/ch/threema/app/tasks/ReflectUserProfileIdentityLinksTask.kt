/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
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
import ch.threema.protobuf.d2d.MdD2D.TransactionScope
import ch.threema.protobuf.d2d.sync.MdD2DSync.UserProfile
import ch.threema.protobuf.d2d.sync.MdD2DSync.UserProfile.IdentityLinks
import ch.threema.protobuf.d2d.sync.UserProfileKt.IdentityLinksKt.identityLink
import ch.threema.protobuf.d2d.sync.UserProfileKt.identityLinks
import ch.threema.protobuf.d2d.sync.userProfile
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("ReflectUserProfileIdentityLinksTask")

/**
 * Note that this task always reflects the current state when it is running. Therefore, this task should be scheduled whenever the identity links
 * are changed by the user on this device.
 */
class ReflectUserProfileIdentityLinksTask(
    private val userService: UserService,
    private val nonceFactory: NonceFactory,
    private val multiDeviceManager: MultiDeviceManager,
) : ActiveTask<Unit>, PersistableTask {
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
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            ReflectUserProfileIdentityLinksTask(
                userService = serviceManager.userService,
                nonceFactory = serviceManager.nonceFactory,
                multiDeviceManager = serviceManager.multiDeviceManager,
            )
    }

    companion object {
        /**
         * Get the identity links that are part of the user profile sync.
         */
        fun getUserProfileSyncIdentityLinks(userService: UserService): IdentityLinks = identityLinks {
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
