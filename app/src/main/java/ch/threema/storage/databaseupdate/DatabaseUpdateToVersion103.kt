package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion103(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        unStarStarredStatusMessages(messageTableName = "message")
        unStarStarredStatusMessages(messageTableName = "m_group_message")
        unStarStarredStatusMessages(messageTableName = "distribution_list_message")
    }

    /**
     *  This will un-star every status message that could be starred in previous app-versions.
     *
     *  The flag `DisplayTag.DISPLAY_TAG_STARRED` gets removed from cell value `displayTags` if set.
     *
     *  `1` here stands for the flag `DisplayTag.DISPLAY_TAG_STARRED`
     */
    private fun unStarStarredStatusMessages(messageTableName: String) {
        sqLiteDatabase.execSQL(
            """
                UPDATE $messageTableName
                SET displayTags = (displayTags & ~1)
                WHERE isStatusMessage = 1
                  AND (displayTags & 1) = 1;
            """,
        )
    }

    override fun getDescription() = "correct starred status message to not-starred"

    override val version = 103
}
