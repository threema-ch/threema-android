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

package ch.threema.storage.databaseupdate

import android.database.SQLException

/**
 * A [DatabaseUpdate] is used to migrate the database schema and/or data from a previous version.
 * All [DatabaseUpdate]s are processed sequentially on a worker thread in the order of their version number when the
 * database is opened.
 *
 * Important rules for [DatabaseUpdate]:
 * - must not depend on other services
 * - must not reference constants (e.g. table or field names) from other classes
 */
@JvmDefaultWithCompatibility
interface DatabaseUpdate {
    @Throws(SQLException::class)
    fun run()

    fun getVersion(): Int

    /**
     * A brief, human-readable description of what this update does. Only used for logging.
     */
    fun getDescription(): String? = null
}

val DatabaseUpdate.fullDescription: String
    get() = buildString {
        append("version ${getVersion()}")
        getDescription()?.let { description ->
            append(" ($description)")
        }
    }
