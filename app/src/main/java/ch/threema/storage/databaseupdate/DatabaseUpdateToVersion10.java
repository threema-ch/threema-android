package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion10 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion10(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(sqLiteDatabase, "contacts", "isGroupCapable")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN isGroupCapable TINYINT(1) DEFAULT 0");
        }
    }

    @Override
    public String getDescription() {
        return "set group capable";
    }

    @Override
    public int getVersion() {
        return 10;
    }
}
