/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.data.storage

import android.database.sqlite.SQLiteException

/**
 * This interface fully abstracts the database access.
 */
interface DatabaseBackend {

    /**
     * Insert a new contact.
     *
     * @throws SQLiteException if insertion fails due to a conflict
     */
    fun createContact(contact: DbContact)

    /**
     * Return the contact with the specified [identity].
     */
    fun getContactByIdentity(identity: String): DbContact?

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
    fun deleteContactByIdentity(identity: String): Boolean
}
