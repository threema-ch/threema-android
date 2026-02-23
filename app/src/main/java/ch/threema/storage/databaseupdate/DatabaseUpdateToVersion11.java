package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion11 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion11(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(sqLiteDatabase, "contacts", "publicNickName")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN publicNickName VARCHAR(255) NULL");
        }
    }

    @Override
    public int getVersion() {
        return 11;
    }
}
