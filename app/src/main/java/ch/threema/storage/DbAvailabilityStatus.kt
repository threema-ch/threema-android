package ch.threema.storage

import ch.threema.domain.types.IdentityString

/**
 * @property identity    The contacts identity (can never be the user's identity)
 * @property category    The status category value
 * @property description The optional (meaning empty) description for categories like `UNAVAILABLE` or `BUSY`
 *
 * @see ch.threema.data.datatypes.AvailabilityStatus.fromDatabaseModel
 */
data class DbAvailabilityStatus(
    val identity: IdentityString,
    val category: Int,
    val description: String,
) {
    companion object {
        const val TABLE = "contact_availability_status"
        const val COLUMN_IDENTITY = "identity"
        const val COLUMN_CATEGORY = "category"
        const val COLUMN_DESCRIPTION = "description"
    }
}
