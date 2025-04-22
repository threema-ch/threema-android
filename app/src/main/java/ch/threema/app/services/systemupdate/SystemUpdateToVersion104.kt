/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.services.systemupdate

import android.content.Context
import androidx.preference.PreferenceManager
import ch.threema.app.services.UpdateSystemService
import ch.threema.app.stores.PreferenceStore
import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class SystemUpdateToVersion104(
    private val db: SQLiteDatabase,
    private val context: Context,
) : UpdateSystemService.SystemUpdate {
    companion object {
        const val VERSION = 104
    }

    override fun runAsync() = true

    override fun runDirectly(): Boolean {
        addUserStateColumn()

        val myIdentity = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PreferenceStore.PREFS_IDENTITY, null)
            ?: // In case there is no identity, there is also no data in the database and we can
            // skip the following steps.
            return true

        initializeUserStateColumn(myIdentity)

        removeUserFromGroupMembers(myIdentity)

        return true
    }

    private fun addUserStateColumn() {
        val table = "m_group"
        val field = "userState"

        // Add field
        if (!fieldExists(db, table, field)) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `$field` INTEGER DEFAULT 0 NOT NULL")
        }
    }

    private fun initializeUserStateColumn(myIdentity: String) {
        // The default value is 0 (member) and we set all groups where the user is no member anymore
        // to 2 (left). We cannot (yet) distinguish 1 (kicked) from 2 (left) at this point.
        db.execSQL(
            """
                UPDATE m_group
                SET userState = 2
                WHERE m_group.id NOT IN (
                    SELECT groupId
                    FROM group_member
                    WHERE identity = ?
                );
        """,
            arrayOf(myIdentity),
        )
    }

    private fun removeUserFromGroupMembers(myIdentity: String) {
        // Ensure that the user is not part of any groups' member list. From this point on, we do
        // never store the user in the member list even if the user is a member of the group. This
        // is because we now have the user state which becomes the new single source of truth
        // regarding the user's group membership.
        db.execSQL(
            """
            DELETE FROM group_member
            WHERE identity = ?
        """,
            arrayOf(myIdentity),
        )
    }

    override fun getText() = "version $VERSION (add group user state)"
}
