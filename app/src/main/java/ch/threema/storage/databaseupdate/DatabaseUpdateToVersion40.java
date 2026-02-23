package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;


public class DatabaseUpdateToVersion40 implements DatabaseUpdate {
    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion40(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(sqLiteDatabase, "wc_session", "push_token")) {
            sqLiteDatabase.execSQL("ALTER TABLE wc_session ADD COLUMN push_token VARCHAR(255) DEFAULT NULL");
        }
    }

    @Override
    public int getVersion() {
        return 40;
    }
}
