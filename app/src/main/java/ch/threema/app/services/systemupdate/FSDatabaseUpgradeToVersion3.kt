/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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

internal class FSDatabaseUpgradeToVersion3(
    private val sqLiteDatabase: SQLiteDatabase
): UpdateToVersion(), UpdateSystemService.SystemUpdate {

    companion object {
        const val VERSION = 3
    }

    override fun runASync() = true

    override fun runDirectly(): Boolean {
        // Create remote 4DH version column with default value 0x0100 (Version 1.0)
        if (!this.fieldExist(sqLiteDatabase, "session", "peerCurrentVersion_4dh")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE session ADD COLUMN peerCurrentVersion_4dh INTEGER DEFAULT 256")
        }
        return true
    }

    override fun getText() = "Version $VERSION (add yet another version column)"
}
