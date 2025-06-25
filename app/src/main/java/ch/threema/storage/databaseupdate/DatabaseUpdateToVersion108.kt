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

import android.content.Context
import androidx.preference.PreferenceManager
import ch.threema.app.stores.PreferenceStore
import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion108(
    private val sqLiteDatabase: SQLiteDatabase,
    private val context: Context,
) : DatabaseUpdate {
    override fun run() {
        val myIdentity = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PreferenceStore.PREFS_IDENTITY, null)
            ?: // In case there is no identity, there is also no need to remove the user's identity
            return

        sqLiteDatabase.rawExecSQL("DELETE FROM `contacts` WHERE `identity` = ?", myIdentity)
    }

    override fun getDescription() = "remove user's identity from contacts"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 108
    }
}
