package ch.threema.storage.factories

import ch.threema.data.storage.DbEmojiReaction.Companion.COLUMN_EMOJI_SEQUENCE
import ch.threema.data.storage.DbEmojiReaction.Companion.COLUMN_MESSAGE_ID
import ch.threema.data.storage.DbEmojiReaction.Companion.COLUMN_REACTED_AT
import ch.threema.data.storage.DbEmojiReaction.Companion.COLUMN_SENDER_IDENTITY
import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.group.GroupMessageModel

class GroupEmojiReactionModelFactory(databaseProvider: DatabaseProvider) :
    ModelFactory(databaseProvider, TABLE) {

    object Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            "CREATE TABLE IF NOT EXISTS `$TABLE` (" +
                "`$COLUMN_MESSAGE_ID` INTEGER NOT NULL, " +
                "`$COLUMN_SENDER_IDENTITY` VARCHAR NOT NULL, " +
                "`$COLUMN_EMOJI_SEQUENCE` VARCHAR NOT NULL, " +
                "`$COLUMN_REACTED_AT` DATETIME NOT NULL, " +
                getConstraints() +
                ");",
            getIndices(),
        )

        private fun getConstraints(): String =
            "UNIQUE(`$COLUMN_MESSAGE_ID`,`$COLUMN_SENDER_IDENTITY`,`$COLUMN_EMOJI_SEQUENCE`) ON CONFLICT REPLACE, " +
                "CONSTRAINT `fk_group_message_id` FOREIGN KEY(`$COLUMN_MESSAGE_ID`) " +
                "REFERENCES `${GroupMessageModel.TABLE}` (`${AbstractMessageModel.COLUMN_ID}`) ON UPDATE CASCADE ON DELETE CASCADE "

        private fun getIndices(): String =
            "CREATE INDEX `group_reactions_idx` ON `$TABLE` (`$COLUMN_MESSAGE_ID`);"
    }

    companion object {
        const val TABLE = "group_reactions"
    }
}
