package ch.threema.storage.databaseupdate

import ch.threema.storage.runDelete
import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion116(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.runDelete(
            table = "group_member",
            whereClause = "id IN (SELECT GM.id FROM group_member GM INNER JOIN m_group G ON GM.groupId = G.id AND GM.identity = G.creatorIdentity)",
        )
    }

    override val version = 116

    override fun getDescription() = "Remove group creator from member table"
}
