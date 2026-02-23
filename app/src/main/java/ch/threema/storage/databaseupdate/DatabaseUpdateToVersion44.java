package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;


public class DatabaseUpdateToVersion44 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion44(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, "contacts", "type")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts"
                + " ADD COLUMN type INT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 44;
    }
}
