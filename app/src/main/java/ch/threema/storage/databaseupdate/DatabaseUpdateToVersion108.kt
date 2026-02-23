package ch.threema.storage.databaseupdate

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion108(
    private val sqLiteDatabase: SQLiteDatabase,
    private val context: Context,
) : DatabaseUpdate {
    override fun run() {
        val myIdentity = getMyIdentity(context)
            ?: // In case there is no identity, there is also no need to remove the user's identity
            return

        sqLiteDatabase.rawExecSQL("DELETE FROM `contacts` WHERE `identity` = ?", myIdentity)
    }

    override fun getDescription() = "remove user's identity from contacts"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 108
    }
}
