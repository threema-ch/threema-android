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

import ch.threema.app.services.UpdateSystemService
import ch.threema.base.utils.LoggingUtil
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion106")

internal class SystemUpdateToVersion106(
    private val sqLiteDatabase: SQLiteDatabase,
) : UpdateSystemService.SystemUpdate {
    companion object {
        const val VERSION = 106
    }

    override fun runAsync() = true

    override fun runDirectly(): Boolean {
        migrateMessageIndices()
        migrateContactReactions()
        migrateGroupReactions()
        return true
    }

    private fun migrateMessageIndices() {
        // Recreate message uid indices without UNIQUE requirement
        // Note that the indices `message_uid_idx` and `m_group_message_uid_idx` were created in
        // an earlier version of `SystemUpdateToVersion105` as UNIQUE indices which caused that
        // migration to fail on some devices and were thus removed in `SystemUpdateToVersion105`

        sqLiteDatabase.execSQL(
            "DROP INDEX IF EXISTS `messageUidIdx`"
        )
        sqLiteDatabase.execSQL(
            "DROP INDEX IF EXISTS `message_uid_idx`"
        )
        sqLiteDatabase.execSQL(
            "CREATE INDEX IF NOT EXISTS `contact_message_uid_idx` ON `message` ( `uid` )"
        )

        sqLiteDatabase.execSQL(
            "DROP INDEX IF EXISTS `groupMessageUidIdx`"
        )
        sqLiteDatabase.execSQL(
            "DROP INDEX IF EXISTS `m_group_message_uid_idx`"
        )
        sqLiteDatabase.execSQL(
            "CREATE INDEX IF NOT EXISTS `group_message_uid_idx` ON `m_group_message` ( `uid` )"
        )
    }


    private fun migrateContactReactions() {
        migrateReactionTables(
            targetReactionTable = "contact_reactions",
            legacyReactionTable = "contact_emoji_reactions",
            messageTable = "message",
            foreignKeyName = "fk_contact_message_id",
            reactionIndexName = "contact_reactions_idx",
            legacyReactionIndexName = "contact_reactions_message_idx"
        )
    }

    private fun migrateGroupReactions() {
        migrateReactionTables(
            targetReactionTable = "group_reactions",
            legacyReactionTable = "group_emoji_reactions",
            messageTable = "m_group_message",
            foreignKeyName = "fk_group_message_id",
            reactionIndexName = "group_reactions_idx",
            legacyReactionIndexName = "group_reactions_message_idx"
        )
    }

    private fun migrateReactionTables(
        targetReactionTable: String,
        legacyReactionTable: String,
        messageTable: String,
        foreignKeyName: String,
        reactionIndexName: String,
        legacyReactionIndexName: String
    ) {
        sqLiteDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS `$targetReactionTable` (" +
                "`messageId` INTEGER NOT NULL, " +
                "`senderIdentity` VARCHAR NOT NULL, " +
                "`emojiSequence` VARCHAR NOT NULL, " +
                "`reactedAt` DATETIME NOT NULL, " +
                "UNIQUE(`messageId`,`senderIdentity`,`emojiSequence`) ON CONFLICT REPLACE, " +
                "CONSTRAINT `$foreignKeyName` FOREIGN KEY (`messageId`) " +
                "REFERENCES `$messageTable` (`id`) ON UPDATE CASCADE ON DELETE CASCADE " +
                ")"
        )
        sqLiteDatabase.execSQL("CREATE INDEX `$reactionIndexName` ON `$targetReactionTable` (`messageId`);")

        if (tableExists(sqLiteDatabase, legacyReactionTable)) {
            migrateReactionData(targetReactionTable, legacyReactionTable, messageTable)
        } else {
            logger.info(
                "Skip migrating existing reactions. Legacy reaction table {} does not exist",
                legacyReactionTable
            )
        }

        // Cleanup potential leftovers from migration to version 105
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS `$legacyReactionTable`")
        sqLiteDatabase.execSQL("DROP INDEX IF EXISTS `$legacyReactionIndexName`")
    }

    private fun migrateReactionData(
        targetReactionTable: String,
        legacyReactionTable: String,
        messageTable: String
    ) {
        logger.info(
            "Migrate existing reactions from {} to {}",
            legacyReactionTable,
            targetReactionTable
        )
        sqLiteDatabase.execSQL(
            "INSERT INTO $targetReactionTable " +
                "SELECT m.id as messageId, r.senderIdentity, r.emojiSequence, r.reactedAt " +
                "FROM $messageTable m JOIN $legacyReactionTable r ON m.uid = r.messageUid"
        )
    }

    override fun getText() = "version $VERSION (create/migrate reaction tables/indices)"
}
