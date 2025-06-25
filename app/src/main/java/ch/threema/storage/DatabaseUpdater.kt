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

package ch.threema.storage

import android.content.Context
import ch.threema.storage.databaseupdate.*
import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdater(
    private val context: Context,
    private val database: SQLiteDatabase,
    @Deprecated("Only available for old updates. New updates must NOT use the database service and instead use raw queries")
    private val databaseService: DatabaseService,
) {
    fun getUpdates(oldVersion: Int): List<DatabaseUpdate> = buildList {
        if (oldVersion < 4) {
            add(DatabaseUpdateToVersion4(database))
        }
        if (oldVersion < 6) {
            add(DatabaseUpdateToVersion6(context, database))
        }
        if (oldVersion < 7) {
            add(DatabaseUpdateToVersion7(context, database))
        }
        if (oldVersion < 8) {
            add(DatabaseUpdateToVersion8(databaseService, database))
        }
        if (oldVersion < 9) {
            add(DatabaseUpdateToVersion9(database))
        }
        if (oldVersion < 10) {
            add(DatabaseUpdateToVersion10(database))
        }
        if (oldVersion < 11) {
            add(DatabaseUpdateToVersion11(database))
        }
        if (oldVersion < 12) {
            add(DatabaseUpdateToVersion12(database))
        }
        if (oldVersion < 13) {
            add(DatabaseUpdateToVersion13(database))
        }
        if (oldVersion < 15) {
            add(DatabaseUpdateToVersion15(databaseService, database))
        }
        if (oldVersion < 16) {
            add(DatabaseUpdateToVersion16(database))
        }
        if (oldVersion < 17) {
            add(DatabaseUpdateToVersion17(database))
        }
        if (oldVersion < 19) {
            add(DatabaseUpdateToVersion19(database))
        }
        if (oldVersion < 20) {
            add(DatabaseUpdateToVersion20(database))
        }
        if (oldVersion < 21) {
            add(DatabaseUpdateToVersion21(databaseService, database))
        }
        if (oldVersion < 24) {
            add(DatabaseUpdateToVersion24(database))
        }
        if (oldVersion < 25) {
            add(DatabaseUpdateToVersion25(databaseService, database))
        }
        if (oldVersion < 27) {
            add(DatabaseUpdateToVersion27(database))
        }
        if (oldVersion < 28) {
            add(DatabaseUpdateToVersion28(database))
        }
        if (oldVersion < 32) {
            add(DatabaseUpdateToVersion32(database))
        }
        if (oldVersion < 33) {
            add(DatabaseUpdateToVersion33(databaseService, database))
        }
        if (oldVersion < 34) {
            add(DatabaseUpdateToVersion34(database))
        }
        if (oldVersion < 35) {
            add(DatabaseUpdateToVersion35(database))
        }
        if (oldVersion < 36) {
            add(DatabaseUpdateToVersion36(database))
        }
        if (oldVersion < 37) {
            add(DatabaseUpdateToVersion37(databaseService, database))
        }
        if (oldVersion < 38) {
            add(DatabaseUpdateToVersion38(databaseService, database))
        }
        if (oldVersion < 40) {
            add(DatabaseUpdateToVersion40(database))
        }
        if (oldVersion < 41) {
            add(DatabaseUpdateToVersion41(database))
        }
        if (oldVersion < 44) {
            add(DatabaseUpdateToVersion44(database))
        }
        if (oldVersion < 45) {
            add(DatabaseUpdateToVersion45(databaseService, database))
        }
        if (oldVersion < 47) {
            add(DatabaseUpdateToVersion47(database))
        }
        if (oldVersion < 49) {
            add(DatabaseUpdateToVersion49(database))
        }
        if (oldVersion < 50) {
            add(DatabaseUpdateToVersion50(database))
        }
        if (oldVersion < 51) {
            add(DatabaseUpdateToVersion51(database))
        }
        if (oldVersion < 52) {
            add(DatabaseUpdateToVersion52(database))
        }
        if (oldVersion < 56) {
            add(DatabaseUpdateToVersion56(database))
        }
        if (oldVersion < 58) {
            add(DatabaseUpdateToVersion58(database))
        }
        if (oldVersion < 59) {
            add(DatabaseUpdateToVersion59(database))
        }
        if (oldVersion < 60) {
            add(DatabaseUpdateToVersion60(database))
        }
        if (oldVersion < 61) {
            add(DatabaseUpdateToVersion61(database))
        }
        if (oldVersion < 62) {
            add(DatabaseUpdateToVersion62(database))
        }
        if (oldVersion < DatabaseUpdateToVersion65.VERSION) {
            add(DatabaseUpdateToVersion65(databaseService, database))
        }
        if (oldVersion < DatabaseUpdateToVersion67.VERSION) {
            add(DatabaseUpdateToVersion67(database))
        }
        if (oldVersion < DatabaseUpdateToVersion68.VERSION) {
            add(DatabaseUpdateToVersion68(database))
        }
        if (oldVersion < DatabaseUpdateToVersion69.VERSION) {
            add(DatabaseUpdateToVersion69(databaseService, database))
        }
        if (oldVersion < DatabaseUpdateToVersion70.VERSION) {
            add(DatabaseUpdateToVersion70(database))
        }
        if (oldVersion < DatabaseUpdateToVersion71.VERSION) {
            add(DatabaseUpdateToVersion71(database))
        }
        if (oldVersion < DatabaseUpdateToVersion72.VERSION) {
            add(DatabaseUpdateToVersion72(database))
        }
        if (oldVersion < DatabaseUpdateToVersion73.VERSION) {
            add(DatabaseUpdateToVersion73(database))
        }
        if (oldVersion < DatabaseUpdateToVersion74.VERSION) {
            add(DatabaseUpdateToVersion74(database))
        }
        if (oldVersion < DatabaseUpdateToVersion75.VERSION) {
            add(DatabaseUpdateToVersion75(database))
        }
        if (oldVersion < DatabaseUpdateToVersion76.VERSION) {
            add(DatabaseUpdateToVersion76(database))
        }
        if (oldVersion < DatabaseUpdateToVersion77.VERSION) {
            add(DatabaseUpdateToVersion77(database))
        }
        if (oldVersion < DatabaseUpdateToVersion78.VERSION) {
            add(DatabaseUpdateToVersion78(database))
        }
        if (oldVersion < DatabaseUpdateToVersion79.VERSION) {
            add(DatabaseUpdateToVersion79(database))
        }
        if (oldVersion < DatabaseUpdateToVersion80.VERSION) {
            add(DatabaseUpdateToVersion80(database))
        }
        if (oldVersion < DatabaseUpdateToVersion81.VERSION) {
            add(DatabaseUpdateToVersion81(database))
        }
        if (oldVersion < DatabaseUpdateToVersion82.VERSION) {
            add(DatabaseUpdateToVersion82(database))
        }
        if (oldVersion < DatabaseUpdateToVersion83.VERSION) {
            add(DatabaseUpdateToVersion83(database))
        }
        if (oldVersion < DatabaseUpdateToVersion84.VERSION) {
            add(DatabaseUpdateToVersion84(database))
        }
        if (oldVersion < DatabaseUpdateToVersion85.VERSION) {
            add(DatabaseUpdateToVersion85(database))
        }
        if (oldVersion < DatabaseUpdateToVersion86.VERSION) {
            add(DatabaseUpdateToVersion86(database))
        }
        if (oldVersion < DatabaseUpdateToVersion87.VERSION) {
            add(DatabaseUpdateToVersion87(database))
        }
        if (oldVersion < DatabaseUpdateToVersion88.VERSION) {
            add(DatabaseUpdateToVersion88(database))
        }
        if (oldVersion < DatabaseUpdateToVersion89.VERSION) {
            add(DatabaseUpdateToVersion89(database))
        }
        if (oldVersion < DatabaseUpdateToVersion90.VERSION) {
            add(DatabaseUpdateToVersion90(database))
        }
        if (oldVersion < DatabaseUpdateToVersion92.VERSION) {
            add(DatabaseUpdateToVersion92(database))
        }
        if (oldVersion < DatabaseUpdateToVersion93.VERSION) {
            add(DatabaseUpdateToVersion93(database))
        }
        if (oldVersion < DatabaseUpdateToVersion94.VERSION) {
            add(DatabaseUpdateToVersion94(database))
        }
        if (oldVersion < DatabaseUpdateToVersion95.VERSION) {
            add(DatabaseUpdateToVersion95(database))
        }
        if (oldVersion < DatabaseUpdateToVersion96.VERSION) {
            add(DatabaseUpdateToVersion96(database))
        }
        if (oldVersion < DatabaseUpdateToVersion97.VERSION) {
            add(DatabaseUpdateToVersion97(database))
        }
        if (oldVersion < DatabaseUpdateToVersion98.VERSION) {
            add(DatabaseUpdateToVersion98(database))
        }
        if (oldVersion < DatabaseUpdateToVersion99.VERSION) {
            add(DatabaseUpdateToVersion99(database))
        }
        if (oldVersion < DatabaseUpdateToVersion100.VERSION) {
            add(DatabaseUpdateToVersion100(database))
        }
        if (oldVersion < DatabaseUpdateToVersion101.VERSION) {
            add(DatabaseUpdateToVersion101(database))
        }
        if (oldVersion < DatabaseUpdateToVersion102.VERSION) {
            add(DatabaseUpdateToVersion102(database))
        }
        if (oldVersion < DatabaseUpdateToVersion103.VERSION) {
            add(DatabaseUpdateToVersion103(database))
        }
        if (oldVersion < DatabaseUpdateToVersion104.VERSION) {
            add(DatabaseUpdateToVersion104(database, context))
        }
        if (oldVersion < DatabaseUpdateToVersion105.VERSION) {
            add(DatabaseUpdateToVersion105())
        }
        if (oldVersion < DatabaseUpdateToVersion106.VERSION) {
            add(DatabaseUpdateToVersion106(database))
        }
        if (oldVersion < DatabaseUpdateToVersion107.VERSION) {
            add(DatabaseUpdateToVersion107(database, context))
        }
        if (oldVersion < DatabaseUpdateToVersion108.VERSION) {
            add(DatabaseUpdateToVersion108(database, context))
        }
        if (oldVersion < DatabaseUpdateToVersion109.VERSION) {
            add(DatabaseUpdateToVersion109(database))
        }
        if (oldVersion < DatabaseUpdateToVersion110.VERSION) {
            add(DatabaseUpdateToVersion110(database))
        }
    }

    companion object {
        const val VERSION = DatabaseUpdateToVersion110.VERSION
    }
}
