package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion71 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion71(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        // add isHidden column if not present already
        String table = "distribution_list";
        String column = "isHidden";

        if (!fieldExists(this.sqLiteDatabase, table, column)) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " TINYINT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 71;
    }
}
