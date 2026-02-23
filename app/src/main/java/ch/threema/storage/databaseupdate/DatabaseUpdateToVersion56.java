package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * add contact restore state field to contact models
 */
public class DatabaseUpdateToVersion56 implements DatabaseUpdate {
    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion56(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, "contacts", "isArchived")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN isArchived TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, "m_group", "isArchived")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE m_group ADD COLUMN isArchived TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, "distribution_list", "isArchived")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE distribution_list ADD COLUMN isArchived TINYINT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 56;
    }
}
