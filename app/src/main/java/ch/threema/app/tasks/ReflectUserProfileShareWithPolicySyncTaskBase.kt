/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

import androidx.annotation.CallSuper
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy
import ch.threema.app.services.PreferenceService
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedUserProfileSyncUpdate
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.sync.MdD2DSync

abstract class ReflectUserProfileShareWithPolicySyncTaskBase(
    protected val newPolicy: ProfilePictureSharePolicy.Policy,
    serviceManager: ServiceManager
) : ActiveTask<Unit>, PersistableTask {

    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }
    private val preferenceService by lazy { serviceManager.preferenceService }

    abstract fun createUpdatedUserProfile(): MdD2DSync.UserProfile

    override suspend fun invoke(handle: ActiveTaskCodec) {
        check(multiDeviceManager.isMultiDeviceActive) {
            "Multi device is not active and a user profile picture policy change must not be reflected"
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
            multiDeviceProperties = mdProperties
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory
        )
    }

    @CallSuper
    open fun persistLocally(preferenceService: PreferenceService) {
        preferenceService.profilePicRelease = newPolicy.ordinal
    }
}
