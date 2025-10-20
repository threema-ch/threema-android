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

import ch.threema.app.managers.ServiceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy.Policy
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.UserProfileKt.profilePictureShareWith
import ch.threema.protobuf.d2d.sync.userProfile
import ch.threema.protobuf.identities
import kotlinx.serialization.Serializable

/**
 *
 * Sync the profile picture sharing policy of type [Policy.ALLOW_LIST] with its allowed identities into the device group.
 *
 * @param allowedIdentities Will be sent to the device group in a transaction and saved additionally after the transaction was committed.
 * Any empty list can be passed here as specified by the protocol.
 */
class ReflectUserProfileShareWithAllowListSyncTask(
    private val allowedIdentities: Set<Identity>,
    serviceManager: ServiceManager,
) : ReflectUserProfileShareWithPolicySyncTaskBase(
    newPolicy = Policy.ALLOW_LIST,
    serviceManager = serviceManager,
) {
    override val type = "ReflectUserProfileShareWithAllowListSyncTask"

    private val profilePicRecipientsService by lazy { serviceManager.profilePicRecipientsService }

    override fun createUpdatedUserProfile(): MdD2DSync.UserProfile = userProfile {
        this.profilePictureShareWith = profilePictureShareWith {
            this.allowList = identities {
                this.identities.addAll(allowedIdentities)
            }
        }
    }

    override fun persistLocally(preferenceService: PreferenceService) {
        super.persistLocally(preferenceService)
        profilePicRecipientsService.replaceAll(allowedIdentities.toTypedArray<String>())
    }

    override fun serialize(): SerializableTaskData =
        ReflectUserProfileShareWithAllowListSyncTaskData(allowedIdentities.toList())

    @Serializable
    data class ReflectUserProfileShareWithAllowListSyncTaskData(
        val allowedIdentities: List<Identity>,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
            return ReflectUserProfileShareWithAllowListSyncTask(
                allowedIdentities = allowedIdentities.toSet(),
                serviceManager = serviceManager,
            )
        }
    }
}
