package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion47 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion47(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(sqLiteDatabase, "contacts", "dateCreated")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN dateCreated BIGINT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 47;
    }
}
