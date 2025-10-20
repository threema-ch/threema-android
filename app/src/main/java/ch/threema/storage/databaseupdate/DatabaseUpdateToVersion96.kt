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

package ch.threema.storage.databaseupdate

import ch.threema.base.utils.Utils
import ch.threema.domain.types.Identity
import ch.threema.storage.buildContentValues
import java.security.MessageDigest
import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion96(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        removeIsActiveColumn()
        removeGroupKindColumn()
        addColorIndexColumn()
    }

    private fun removeIsActiveColumn() {
        if (sqLiteDatabase.fieldExists("group_member", "isActive")) {
            // Remove 'isActive' column in group member table
            sqLiteDatabase.execSQL("ALTER TABLE `group_member` DROP COLUMN `isActive`")
        }
    }

    private fun removeGroupKindColumn() {
        if (sqLiteDatabase.fieldExists("m_group", "groupKind")) {
            // Remove 'groupKind' column in group member table
            sqLiteDatabase.execSQL("ALTER TABLE `m_group` DROP COLUMN `groupKind`")
        }
    }

    private fun addColorIndexColumn() {
        // Add color index column
        if (!sqLiteDatabase.fieldExists("m_group", "colorIndex")) {
            sqLiteDatabase.execSQL("ALTER TABLE `m_group` ADD COLUMN `colorIndex` INTEGER DEFAULT 0 NOT NULL")
        }

        // Get the required fields and calculate the color index for each entry
        val cursor = sqLiteDatabase.query("SELECT `apiGroupId`, `creatorIdentity` FROM `m_group`")
        val colorIndices = mutableListOf<Triple<String, String, Int>>()
        while (cursor.moveToNext()) {
            val creatorIdentity = cursor.getString(cursor.getColumnIndexOrThrow("creatorIdentity"))
            val groupIdString = cursor.getString(cursor.getColumnIndexOrThrow("apiGroupId"))
            val colorIndex = computeColorIndex(creatorIdentity, groupIdString)
            colorIndices.add(Triple(creatorIdentity, groupIdString, colorIndex))
        }

        // Write the calculated color indices to the database
        colorIndices.forEach {
            val (creatorIdentity, groupIdString, colorIndex) = it
            val contentValues = buildContentValues {
                put("colorIndex", colorIndex)
            }
            sqLiteDatabase.update(
                "m_group",
                contentValues,
                "`creatorIdentity` = ? AND `apiGroupId` = ?",
                arrayOf(creatorIdentity, groupIdString),
            )
        }
    }

    override fun getDescription() = "remove isActive from group member table"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 96

        private const val ID_COLORS_SIZE = 16

        private fun computeColorIndex(creatorIdentity: Identity, groupIdString: String): Int {
            val groupIdByteArray = Utils.hexStringToByteArray(groupIdString)
            val groupCreatorIdentity = creatorIdentity.toByteArray()

            return try {
                (groupCreatorIdentity + groupIdByteArray).computeIdColor()
            } catch (_: Exception) {
                0
            }
        }

        private fun ByteArray.computeIdColor(): Int {
            val firstByte = MessageDigest.getInstance("SHA-256").digest(this).first()
            return firstByte.getIdColorIndex()
        }

        private fun Byte.getIdColorIndex(): Int {
            return (((toInt()) and 0xff) / ID_COLORS_SIZE)
        }
    }
}
