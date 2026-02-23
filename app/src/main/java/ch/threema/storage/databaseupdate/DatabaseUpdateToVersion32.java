package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion32 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion32(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(this.sqLiteDatabase, "contacts", "avatarExpires")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN avatarExpires LONG DEFAULT NULL");
        }
    }

    @Override
    public int getVersion() {
        return 32;
    }
}
