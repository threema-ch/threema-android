package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * add profile pic field to normal, group and distribution list message models
 */
public class DatabaseUpdateToVersion41 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion41(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, "contacts", "profilePicSent")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN profilePicSent BIGINT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 41;
    }
}
