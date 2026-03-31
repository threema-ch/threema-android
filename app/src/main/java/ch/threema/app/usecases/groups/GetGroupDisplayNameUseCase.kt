package ch.threema.app.usecases.groups

import android.content.Context
import ch.threema.android.ResolvableString
import ch.threema.android.ResolvedString
import ch.threema.android.ResourceIdString
import ch.threema.android.toResolvedString
import ch.threema.app.R
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.models.ContactModel
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.ContactModelRepository

class GetGroupDisplayNameUseCase(val contactModelRepository: ContactModelRepository) {

    /**
     *  Get the groups display name. It can either be:
     *  - A defined name ([GroupDisplayName.Defined]) by the creator
     *  - A name build from all concatenated *sorted* member display names ([GroupDisplayName.AllMembers]). If the user is still a member, its display
     *  name (`Me`) will always be at the first position.
     *  - The api-group-id hex string as a fallback in case of any error
     *
     *  This implementation uses the same logic as [ch.threema.app.utils.NameUtil.getGroupDisplayName], but does so without using deprecated models.
     */
    fun call(groupModel: GroupModel, contactNameFormat: ContactNameFormat): GroupDisplayName {
        val groupModelData = groupModel.data
            ?: return GroupDisplayName.Unknown(groupIdHexString = groupModel.groupIdentity.groupIdHexString)
        if (groupModelData.name != null && groupModelData.name.isNotBlank()) {
            return GroupDisplayName.Defined(groupModelData.name)
        }
        val memberDisplayNames: MutableList<ResolvableString> = mutableListOf()
        // The own users name should appear at the beginning (if still a member)
        if (groupModel.isMember()) {
            memberDisplayNames.add(ResourceIdString(R.string.me_myself_and_i))
        }
        // Add all other group members names
        memberDisplayNames.addAll(
            getOtherMembersDisplayNames(groupModel, contactNameFormat),
        )
        if (memberDisplayNames.isEmpty()) {
            return GroupDisplayName.Unknown(groupIdHexString = groupModel.groupIdentity.groupIdHexString)
        }
        return GroupDisplayName.AllMembers(memberDisplayNames = memberDisplayNames)
    }

    private fun getOtherMembersDisplayNames(
        groupModel: GroupModel,
        contactNameFormat: ContactNameFormat,
    ): List<ResolvedString> =
        contactModelRepository
            .getByIdentities(groupModel.getRecipients())
            .mapNotNull(ContactModel::data)
            .map { contactModelData ->
                contactModelData.getDisplayName(
                    contactNameFormat = contactNameFormat,
                    nicknameHasPrefix = true,
                ).toResolvedString()
            }
            .sortedBy(ResolvedString::string)
}

sealed interface GroupDisplayName {

    fun resolve(context: Context): String

    /**
     *  The group name could not be determined
     */
    data class Unknown(val groupIdHexString: String) : GroupDisplayName {

        override fun resolve(context: Context): String = groupIdHexString
    }

    /**
     *  The name of the group was explicitly defined by the group creator
     *
     *  @param name must *not* be blank
     */
    data class Defined(val name: String) : GroupDisplayName {

        override fun resolve(context: Context): String = name
    }

    /**
     *  The group currently has no explicitly set name.
     *  In this case the groups display name is a concatenated list of all members display names
     *
     *  @param memberDisplayNames must *not* be empty
     */
    data class AllMembers(val memberDisplayNames: List<ResolvableString>) : GroupDisplayName {

        override fun resolve(context: Context): String =
            memberDisplayNames.joinToString { resolvableString ->
                resolvableString.get(context)
            }
    }
}
