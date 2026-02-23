package ch.threema.storage.models

import ch.threema.domain.types.Identity
import java.util.Date

/**
 * This model is used to track at what time a group sync request has been sent to what group. This
 * is required to prevent that group sync requests are sent too often per group.
 */
data class OutgoingGroupSyncRequestLogModel(
    val id: Int,
    val apiGroupId: String,
    val creatorIdentity: Identity,
    val lastRequest: Date?,
) {

    override fun toString(): String {
        return "m_group_request_sync_log.id = " + this.id
    }

    companion object {
        const val TABLE: String = "m_group_request_sync_log"
        const val COLUMN_ID: String = "id"
        const val COLUMN_API_GROUP_ID: String = "apiGroupId"
        const val COLUMN_CREATOR_IDENTITY: String = "creatorIdentity"
        const val COLUMN_LAST_REQUEST: String = "lastRequest"
    }
}
