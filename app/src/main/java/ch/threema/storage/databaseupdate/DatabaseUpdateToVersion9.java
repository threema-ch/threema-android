package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion9 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion9(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(sqLiteDatabase, "message", "isStatusMessage")) {
            //update the message model with the uid and move every file to the new filename rule
            sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN isStatusMessage TINYINT(1) DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 9;
    }
}
