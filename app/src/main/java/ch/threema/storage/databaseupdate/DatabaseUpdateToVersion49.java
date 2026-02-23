package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * add contact visibility field to contact models
 */
public class DatabaseUpdateToVersion49 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion49(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        final String tableName = "contacts";
        final String columnName = "isHidden";
        if (!fieldExists(this.sqLiteDatabase, tableName, columnName)) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " TINYINT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 49;
    }
}
