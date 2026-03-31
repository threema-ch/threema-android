package ch.threema.app.tasks

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy.Policy
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.UserProfileKt.profilePictureShareWith
import ch.threema.protobuf.d2d.sync.userProfile
import ch.threema.protobuf.identities
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 *
 * Sync the profile picture sharing policy of type [Policy.ALLOW_LIST] with its allowed identities into the device group.
 *
 * @param allowedIdentities Will be sent to the device group in a transaction and saved additionally after the transaction was committed.
 * Any empty list can be passed here as specified by the protocol.
 */
class ReflectUserProfileShareWithAllowListSyncTask(
    private val allowedIdentities: Set<IdentityString>,
) : ReflectUserProfileShareWithPolicySyncTaskBase(
    newPolicy = Policy.ALLOW_LIST,
),
    KoinComponent {
    private val profilePicRecipientsService: ProfilePictureRecipientsService by inject()

    override val type = "ReflectUserProfileShareWithAllowListSyncTask"

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
        val allowedIdentities: List<IdentityString>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> {
            return ReflectUserProfileShareWithAllowListSyncTask(
                allowedIdentities = allowedIdentities.toSet(),
            )
        }
    }
}
