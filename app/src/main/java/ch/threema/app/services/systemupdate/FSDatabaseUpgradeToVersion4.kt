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
import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class FSDatabaseUpgradeToVersion4(
    private val sqLiteDatabase: SQLiteDatabase,
) : UpdateSystemService.SystemUpdate {
    companion object {
        const val VERSION = 4
    }

    override fun runAsync() = true

    override fun runDirectly(): Boolean {
        if (!fieldExists(sqLiteDatabase, "session", "lastOutgoingMessageTimestamp")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE session ADD COLUMN lastOutgoingMessageTimestamp INTEGER DEFAULT 0")
        }
        return true
    }

    override fun getText() = "Version $VERSION (add timestamp of last sent fs message)"
}
