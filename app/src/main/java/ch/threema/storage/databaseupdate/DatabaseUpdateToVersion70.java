package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion70 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion70(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(sqLiteDatabase, "ballot", "displayType")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE ballot ADD COLUMN displayType VARCHAR");
        }
    }

    @Override
    public int getVersion() {
        return 70;
    }
}
