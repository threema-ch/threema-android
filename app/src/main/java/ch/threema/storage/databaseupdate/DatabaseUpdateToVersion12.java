package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion12 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion12(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(sqLiteDatabase, "contacts", "isSynchronized")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN isSynchronized TINYINT(1) DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 12;
    }
}
