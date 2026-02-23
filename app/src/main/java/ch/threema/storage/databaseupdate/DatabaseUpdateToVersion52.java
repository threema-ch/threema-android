package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * add contact restore state field to contact models
 */
public class DatabaseUpdateToVersion52 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion52(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(sqLiteDatabase, "contacts", "isRestored")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN isRestored TINYINT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 52;
    }
}
