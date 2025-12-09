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

package ch.threema.app.startup

import android.content.Context
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseService
import ch.threema.storage.SQLDHSessionStore
import org.koin.mp.KoinPlatform

/**
 * Removes every file that might have been encrypted with an old, no longer existing master key.
 * This mainly happens when the setup wizard is aborted and then restarted, as the master key is only persisted at the end of the wizard and
 * therefore all data stored prior to that can no longer be decrypted if the key is never persisted.
 */
fun deleteOrphanedUserData(context: Context) {
    deleteDatabaseFiles(context)
    deleteAllPreferences()
}

private fun deleteDatabaseFiles(context: Context) {
    val defaultDatabaseFile = DatabaseService.getDatabaseFile(context)
    if (defaultDatabaseFile.exists()) {
        val databaseBackup = DatabaseService.getDatabaseBackupFile(context)
        if (!defaultDatabaseFile.renameTo(databaseBackup)) {
            defaultDatabaseFile.delete()
        }
    }

    val nonceDatabaseFile = DatabaseNonceStore.getDatabaseFile(context)
    if (nonceDatabaseFile.exists()) {
        nonceDatabaseFile.delete()
    }

    val sqldhSessionDatabaseFile = context.getDatabasePath(SQLDHSessionStore.DATABASE_NAME)
    if (sqldhSessionDatabaseFile.exists()) {
        sqldhSessionDatabaseFile.delete()
    }
}

private fun deleteAllPreferences() {
    KoinPlatform.getKoin().get<PreferenceStore>().clear()
    KoinPlatform.getKoin().get<EncryptedPreferenceStore>().clear()
}
