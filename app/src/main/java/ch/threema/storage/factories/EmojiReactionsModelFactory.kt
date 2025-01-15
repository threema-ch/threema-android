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

package ch.threema.storage.factories

import ch.threema.data.storage.DbEmojiReaction.Companion.COLUMN_EMOJI_SEQUENCE
import ch.threema.data.storage.DbEmojiReaction.Companion.COLUMN_MESSAGE_ID
import ch.threema.data.storage.DbEmojiReaction.Companion.COLUMN_REACTED_AT
import ch.threema.data.storage.DbEmojiReaction.Companion.COLUMN_SENDER_IDENTITY
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel

abstract class EmojiReactionModelFactory(dbService: DatabaseServiceNew, tableName: String) : ModelFactory(dbService, tableName) {
    override fun getStatements(): Array<String> = arrayOf(
        "CREATE TABLE IF NOT EXISTS `$tableName` (" +
            "`$COLUMN_MESSAGE_ID` INTEGER NOT NULL, " +
            "`$COLUMN_SENDER_IDENTITY` VARCHAR NOT NULL, " +
            "`$COLUMN_EMOJI_SEQUENCE` VARCHAR NOT NULL, " +
            "`$COLUMN_REACTED_AT` DATETIME NOT NULL, " +
            getConstraints() +
            ");",
        getIndices()
    )

    protected abstract fun getIndices(): String
    protected abstract fun getConstraints(): String
}

class ContactEmojiReactionModelFactory(dbService: DatabaseServiceNew) : EmojiReactionModelFactory(dbService, TABLE) {
    companion object {
        const val TABLE = "contact_reactions"
    }

    override fun getConstraints(): String {
        return "UNIQUE(`$COLUMN_MESSAGE_ID`,`$COLUMN_SENDER_IDENTITY`,`$COLUMN_EMOJI_SEQUENCE`) ON CONFLICT REPLACE, " +
            "CONSTRAINT `fk_contact_message_id` FOREIGN KEY(`$COLUMN_MESSAGE_ID`) " +
            "REFERENCES `${MessageModel.TABLE}` (`${MessageModel.COLUMN_ID}`) ON UPDATE CASCADE ON DELETE CASCADE "
    }

    override fun getIndices(): String {
        return "CREATE INDEX `contact_reactions_idx` ON `$TABLE` (`$COLUMN_MESSAGE_ID`);"
    }
}

class GroupEmojiReactionModelFactory(dbService: DatabaseServiceNew) : EmojiReactionModelFactory(dbService, TABLE) {
    companion object {
        const val TABLE = "group_reactions"
    }

    override fun getConstraints(): String {
        return "UNIQUE(`$COLUMN_MESSAGE_ID`,`$COLUMN_SENDER_IDENTITY`,`$COLUMN_EMOJI_SEQUENCE`) ON CONFLICT REPLACE, " +
            "CONSTRAINT `fk_group_message_id` FOREIGN KEY(`$COLUMN_MESSAGE_ID`) " +
            "REFERENCES `${GroupMessageModel.TABLE}` (`${GroupMessageModel.COLUMN_ID}`) ON UPDATE CASCADE ON DELETE CASCADE "
    }

    override fun getIndices(): String {
        return "CREATE INDEX `group_reactions_idx` ON `$TABLE` (`$COLUMN_MESSAGE_ID`);"
    }
}
