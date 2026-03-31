package ch.threema.app.startup

import android.content.Context
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseOpenHelper
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
    val defaultDatabaseFile = DatabaseOpenHelper.getDatabaseFile(context)
    if (defaultDatabaseFile.exists()) {
        val databaseBackup = DatabaseOpenHelper.getDatabaseBackupFile(context)
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
