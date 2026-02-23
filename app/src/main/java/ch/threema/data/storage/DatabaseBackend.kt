package ch.threema.data.storage

import android.database.sqlite.SQLiteException
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.types.Identity

/**
 * This interface fully abstracts the database access.
 */
interface DatabaseBackend {
    /**
     *  Returns all contacts.
     */
    fun getAllContacts(): List<DbContact>

    /**
     * Insert a new contact.
     *
     * @throws SQLiteException if insertion fails due to a conflict
     * @throws IllegalArgumentException if the length of the identity or public key is invalid
     */
    fun createContact(contact: DbContact)

    /**
     * Return the contact with the specified [identity].
     */
    fun getContactByIdentity(identity: Identity): DbContact?

    /**
     * Update the specified contact (using the identity as lookup key).
     *
     * Note: Some fields will not be overwritten:
     *
     * - The identity
     * - The public key
     * - The createdAt timestamp
     */
    fun updateContact(contact: DbContact)

    /**
     * Delete the contact with the specified identity.
     *
     * Return whether the contact was deleted (true) or wasn't found (false).
     */
    fun deleteContactByIdentity(identity: Identity): Boolean

    /**
     * Check whether the contact is currently part of a group. Note that only groups are considered
     * where 'deleted' is set to 0.
     */
    fun isContactInGroup(identity: Identity): Boolean

    /**
     * Insert a new group.
     *
     * @throws SQLiteException if insertion fails due to a conflict
     */
    fun createGroup(group: DbGroup)

    /**
     * Remove all associated data in the database of the given group identity.
     */
    fun removeGroup(localDbId: Long)

    /**
     * Get all groups.
     */
    fun getAllGroups(): Collection<DbGroup>

    /**
     * Return the group with the specified [localDbId].
     */
    fun getGroupByLocalGroupDbId(localDbId: Long): DbGroup?

    /**
     * Return the group with the specified [groupIdentity].
     */
    fun getGroupByGroupIdentity(groupIdentity: GroupIdentity): DbGroup?

    /**
     * Return the row id of the group with the specified [groupIdentity].
     */
    fun getGroupDatabaseId(groupIdentity: GroupIdentity): Long?

    /**
     * Update the specified group (using the creator identity and group id as lookup key).
     *
     * Note: Some fields will not be overwritten:
     *
     * - The creator identity
     * - The group id
     * - The createdAt timestamp
     */
    fun updateGroup(group: DbGroup)
}
