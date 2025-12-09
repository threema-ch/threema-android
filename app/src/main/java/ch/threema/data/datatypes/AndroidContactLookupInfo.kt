/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.data.datatypes

/**
 * This contains the required information to look up a contact in the android contacts.
 */
data class AndroidContactLookupInfo(
    /**
     * The lookup key used to look up a contact.
     */
    val lookupKey: String,

    /**
     * The contact id is only used to optimize the performance when querying the contact.
     */
    val contactId: Long?,
) {
    /**
     * The obfuscated string representation of the android contact lookup info. This can be used for logging purposes.
     */
    val obfuscatedString: String
        get() = "${lookupKey.hashCode()}/$contactId"
}
