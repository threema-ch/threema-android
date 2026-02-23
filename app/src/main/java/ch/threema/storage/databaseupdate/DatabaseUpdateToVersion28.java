package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * creates the state column
 */
public class DatabaseUpdateToVersion28 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion28(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(this.sqLiteDatabase, "contacts", "state")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN state VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'");
        }
    }

    @Override
    public int getVersion() {
        return 28;
    }
}
