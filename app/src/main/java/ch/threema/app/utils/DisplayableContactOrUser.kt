package ch.threema.app.utils

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.types.IdentityString

sealed interface DisplayableContactOrUser {
    val identity: IdentityString
    val displayName: String
    val identityState: IdentityState
    val showBadge: Boolean

    class Contact(
        override val identity: IdentityString,
        override val displayName: String,
        override val identityState: IdentityState,
        override val showBadge: Boolean,
    ) : DisplayableContactOrUser {
        companion object {
            @JvmStatic
            fun createByIdentity(
                identity: IdentityString,
                contactModelRepository: ContactModelRepository,
                preferenceService: PreferenceService,
            ): Contact {
                val contactModelData = contactModelRepository.getByIdentity(identity)?.data ?: return Contact(
                    identity = identity,
                    displayName = identity,
                    identityState = IdentityState.INVALID,
                    showBadge = false,
                )
                return Contact(
                    identity = identity,
                    displayName = contactModelData.getDisplayName(
                        contactNameFormat = preferenceService.getContactNameFormat(),
                        nicknameHasPrefix = true,
                    ),
                    identityState = contactModelData.activityState,
                    showBadge = if (ConfigUtils.isWorkBuild()) {
                        contactModelData.identityType == IdentityType.NORMAL && !ContactUtil.isEchoEchoOrGatewayContact(identity)
                    } else {
                        contactModelData.identityType == IdentityType.WORK
                    },
                )
            }
        }
    }

    class User(
        override val identity: IdentityString,
        override val displayName: String,
    ) : DisplayableContactOrUser {
        // The user is always active
        override val identityState = IdentityState.ACTIVE

        // There is never a need to show a badge for the user
        override val showBadge = false

        companion object {
            @JvmStatic
            fun createByIdentity(userService: UserService) = User(
                identity = userService.identity ?: "",
                displayName = userService.displayName,
            )
        }
    }
}
