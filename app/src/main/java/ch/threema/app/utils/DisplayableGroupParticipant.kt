package ch.threema.app.utils

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.utils.DisplayableGroupParticipant.Creator
import ch.threema.app.utils.DisplayableGroupParticipant.Member
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.ContactModelRepository

/**
 * A displayable group participant is a displayable contact or user that is either the creator or a member.
 */
sealed interface DisplayableGroupParticipant {
    val displayableContactOrUser: DisplayableContactOrUser

    data class Creator(override val displayableContactOrUser: DisplayableContactOrUser) : DisplayableGroupParticipant
    data class Member(override val displayableContactOrUser: DisplayableContactOrUser) : DisplayableGroupParticipant
}

/**
 * The group participants include the creator of the group and the list of members.
 */
data class DisplayableGroupParticipants(
    val creator: Creator,
    val membersWithoutCreator: List<Member>,
) {
    companion object {
        @JvmStatic
        fun getDisplayableGroupParticipantsOfGroup(
            groupModel: GroupModel,
            contactModelRepository: ContactModelRepository,
            userService: UserService,
            preferenceService: PreferenceService,
        ): DisplayableGroupParticipants? {
            val groupModelData = groupModel.data ?: return null

            val creator = getDisplayableCreator(
                groupModel = groupModel,
                contactModelRepository = contactModelRepository,
                userService = userService,
                preferenceService = preferenceService,
            )

            val members = buildList {
                // Add the user except it is the creator (and therefore should not be included in the members list)
                if (!groupModel.isCreator() && groupModel.isMember()) {
                    add(
                        DisplayableContactOrUser.User.createByIdentity(userService = userService),
                    )
                }

                val membersWithoutCreatorAndUser = groupModelData.otherMembers.map { identity ->
                    DisplayableContactOrUser.Contact.createByIdentity(
                        identity = identity,
                        contactModelRepository = contactModelRepository,
                        preferenceService = preferenceService,
                    )
                }

                // Add the other members
                addAll(membersWithoutCreatorAndUser)
            }.map(::Member)

            return DisplayableGroupParticipants(
                creator = creator,
                membersWithoutCreator = members,
            )
        }

        private fun getDisplayableCreator(
            groupModel: GroupModel,
            contactModelRepository: ContactModelRepository,
            userService: UserService,
            preferenceService: PreferenceService,
        ): Creator {
            val contactOrUser = if (groupModel.isCreator()) {
                DisplayableContactOrUser.User.createByIdentity(userService)
            } else {
                DisplayableContactOrUser.Contact.createByIdentity(
                    identity = groupModel.groupIdentity.creatorIdentity,
                    contactModelRepository = contactModelRepository,
                    preferenceService = preferenceService,
                )
            }
            return Creator(
                displayableContactOrUser = contactOrUser,
            )
        }
    }
}
