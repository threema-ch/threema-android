package ch.threema.storage.databaseupdate

import android.content.Context
import ch.threema.domain.types.Identity
import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion104(
    private val sqLiteDatabase: SQLiteDatabase,
    private val context: Context,
) : DatabaseUpdate {
    override fun run() {
        addUserStateColumn()

        val myIdentity = getMyIdentity(context)
            ?: // In case there is no identity, there is also no data in the database and we can
            // skip the following steps.
            return

        initializeUserStateColumn(myIdentity)

        removeUserFromGroupMembers(myIdentity)
    }

    private fun addUserStateColumn() {
        val table = "m_group"
        val field = "userState"

        // Add field
        if (!sqLiteDatabase.fieldExists(table, field)) {
            sqLiteDatabase.execSQL("ALTER TABLE `$table` ADD COLUMN `$field` INTEGER DEFAULT 0 NOT NULL")
        }
    }

    private fun initializeUserStateColumn(myIdentity: Identity) {
        // The default value is 0 (member) and we set all groups where the user is no member anymore
        // to 2 (left). We cannot (yet) distinguish 1 (kicked) from 2 (left) at this point.
        sqLiteDatabase.execSQL(
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

    private fun removeUserFromGroupMembers(myIdentity: Identity) {
        // Ensure that the user is not part of any groups' member list. From this point on, we do
        // never store the user in the member list even if the user is a member of the group. This
        // is because we now have the user state which becomes the new single source of truth
        // regarding the user's group membership.
        sqLiteDatabase.execSQL(
            """
            DELETE FROM group_member
            WHERE identity = ?
        """,
            arrayOf(myIdentity),
        )
    }

    override fun getDescription() = "add group user state"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 104
    }
}
