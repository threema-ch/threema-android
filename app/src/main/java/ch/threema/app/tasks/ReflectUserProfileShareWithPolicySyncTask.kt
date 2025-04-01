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
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy.Policy
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.UserProfileKt.profilePictureShareWith
import ch.threema.protobuf.d2d.sync.userProfile
import ch.threema.protobuf.unit
import kotlinx.serialization.Serializable

/**
 *
 * Sync the profile picture sharing policy setting into the device group.
 *
 * @param newPolicy Will be sent to the device group in a transaction and saved additionally after the transaction was committed.
 * The value can be one of [Policy.EVERYONE] or [Policy.NOBODY]. In case this task is created with policy [Policy.ALLOW_LIST] an exception
 * will be thrown by the constructor. One should use the more specific task [ReflectUserProfileShareWithAllowListSyncTask] in this case.
 *
 * @throws IllegalStateException if the policy is [Policy.ALLOW_LIST]
 */
class ReflectUserProfileShareWithPolicySyncTask(
    newPolicy: Policy,
    serviceManager: ServiceManager
) : ReflectUserProfileShareWithPolicySyncTaskBase(
    newPolicy = newPolicy,
    serviceManager = serviceManager
) {

    override val type = "ReflectUserProfileShareWithPolicySyncTask"

    init {
        check(newPolicy != Policy.ALLOW_LIST) {
            "This task does not support policy of type ALLOW_LIST. Use the more specific task in that case."
        }
    }

    override fun createUpdatedUserProfile(): MdD2DSync.UserProfile = userProfile {
        this.profilePictureShareWith = profilePictureShareWith {
            when (newPolicy) {
                Policy.NOBODY -> this.nobody = unit {}
                Policy.EVERYONE -> this.everyone = unit {}
                Policy.ALLOW_LIST -> throw IllegalStateException(
                    "This task does not support policy of type ALLOW_LIST. Use the more specific task in that case."
                )
            }
        }
    }

    override fun serialize(): SerializableTaskData =
        ReflectUserProfileShareWithPolicySyncTaskData(newPolicy)

    @Serializable
    data class ReflectUserProfileShareWithPolicySyncTaskData(
        val newPolicy: Policy
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> {
            return ReflectUserProfileShareWithPolicySyncTask(
                newPolicy = newPolicy,
                serviceManager = serviceManager
            )
        }
    }
}
