package ch.threema.app.utils

import ch.threema.data.models.GroupModel
import ch.threema.domain.types.ConversationUID
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.storage.models.DistributionListModel

object ConversationUtil {

    private const val CONTACT_UID_PREFIX = "i-"
    private const val GROUP_UID_PREFIX = "g-"
    private const val DISTRIBUTION_LIST_UID_PREFIX = "d-"

    @JvmStatic
    fun getIdentityConversationUid(identity: Identity): ConversationUID {
        return "$CONTACT_UID_PREFIX$identity"
    }

    @JvmStatic
    fun getGroupConversationUid(groupId: Long): ConversationUID {
        return "$GROUP_UID_PREFIX$groupId"
    }

    @JvmStatic
    fun getDistributionListConversationUid(distributionListId: Long): ConversationUID {
        return "$DISTRIBUTION_LIST_UID_PREFIX$distributionListId"
    }

    /**
     * Get the contact's identity from the conversation uid. If the conversation uid does not belong to a contact, then this method returns null.
     */
    @JvmStatic
    fun getContactIdentityFromUid(conversationUid: ConversationUID): Identity? {
        val identity = conversationUid.removePrefix(CONTACT_UID_PREFIX)
        if (identity == conversationUid) {
            // In this case the conversation uid does not belong to a contact
            return null
        }
        return identity
    }

    /**
     * Get the group's local database id from the conversation uid. If the conversation uid does not to belong to a group, then this method returns
     * null.
     */
    @JvmStatic
    fun getGroupDatabaseIdFromUid(conversationUid: ConversationUID): GroupDatabaseId? {
        val groupId = conversationUid.removePrefix(GROUP_UID_PREFIX)
        if (groupId == conversationUid) {
            // In this case the conversation uid does not belong to a group
            return null
        }
        return groupId.toLongOrNull()
    }

    fun GroupModel.getConversationUid(): ConversationUID {
        return getGroupConversationUid(getDatabaseId())
    }

    fun DistributionListModel.getConversationUid(): ConversationUID {
        return getDistributionListConversationUid(id)
    }
}
