package ch.threema.app.tasks

import ch.threema.app.services.ContactService.ProfilePictureSharePolicy.Policy
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.sync.UserProfile
import ch.threema.protobuf.d2d.sync.UserProfileKt.profilePictureShareWith
import ch.threema.protobuf.d2d.sync.userProfile
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
) : ReflectUserProfileShareWithPolicySyncTaskBase(
    newPolicy = newPolicy,
) {
    override val type = "ReflectUserProfileShareWithPolicySyncTask"

    init {
        check(newPolicy != Policy.ALLOW_LIST) {
            "This task does not support policy of type ALLOW_LIST. Use the more specific task in that case."
        }
    }

    override fun createUpdatedUserProfile(): UserProfile = userProfile {
        this.profilePictureShareWith = profilePictureShareWith {
            when (newPolicy) {
                Policy.NOBODY -> this.nobody = unit {}
                Policy.EVERYONE -> this.everyone = unit {}
                Policy.ALLOW_LIST -> throw IllegalStateException(
                    "This task does not support policy of type ALLOW_LIST. Use the more specific task in that case.",
                )
            }
        }
    }

    override fun serialize(): SerializableTaskData =
        ReflectUserProfileShareWithPolicySyncTaskData(newPolicy)

    @Serializable
    data class ReflectUserProfileShareWithPolicySyncTaskData(
        val newPolicy: Policy,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> {
            return ReflectUserProfileShareWithPolicySyncTask(
                newPolicy = newPolicy,
            )
        }
    }
}
