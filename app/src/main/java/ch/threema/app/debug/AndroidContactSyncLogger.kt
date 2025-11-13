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

package ch.threema.app.debug

import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("AndroidContactSyncLogger")

/**
 * This logger is used to count the number of identical contacts when importing them from the android contacts.
 */
class AndroidContactSyncLogger {
    private val nameMap = mutableMapOf<Pair<String, String>, MutableList<LookupKeyAndContactId>>()

    /**
     * Add the first and last name that was received for a certain lookup key to the logger.
     */
    fun addSyncedNames(lookupKeyAndContactId: LookupKeyAndContactId, firstName: String?, lastName: String?) {
        nameMap.getOrPut(firstName.orEmpty() to lastName.orEmpty()) { mutableListOf() }.add(lookupKeyAndContactId)
    }

    /**
     * Check whether there were multiple contacts with the same name added.
     */
    fun logDuplicates() {
        val duplicateNames = nameMap.values.filter { it.size > 1 }
        duplicateNames.forEach { lookupKeysAndContactId ->
            // The number of different combinations of lookup key and contact id
            val lookupKeyAndContactIdCombinations = lookupKeysAndContactId.toSet().size
            // The number of different lookup keys
            val lookupKeys = lookupKeysAndContactId.map(LookupKeyAndContactId::lookupKey).toSet().size
            // The number of different contact ids
            val contactIds = lookupKeysAndContactId.map(LookupKeyAndContactId::contactId).toSet().size
            logger.warn(
                "Got the same name for {} contacts (with {} lookup key and contact id combinations; {} lookup keys; {} contact ids)",
                lookupKeysAndContactId.size,
                lookupKeyAndContactIdCombinations,
                lookupKeys,
                contactIds,
            )
        }

        if (duplicateNames.isEmpty()) {
            logger.info("All synchronized android contacts' names are unique")
        }
    }
}

data class LookupKeyAndContactId(
    val lookupKey: String,
    val contactId: Long,
)
