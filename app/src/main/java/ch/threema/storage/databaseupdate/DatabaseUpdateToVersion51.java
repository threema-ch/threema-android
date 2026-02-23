package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * add "created" column to webclient sessions table
 */
public class DatabaseUpdateToVersion51 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion51(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(sqLiteDatabase, "wc_session", "created")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE wc_session ADD COLUMN created BIGINT NULL");
        }
    }

    @Override
    public int getVersion() {
        return 51;
    }
}
