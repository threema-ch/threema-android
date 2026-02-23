package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion27 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion27(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(this.sqLiteDatabase, "contacts", "featureLevel")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN featureLevel TINYINT(1) DEFAULT 1");
        }

        //update feature level
        sqLiteDatabase.rawExecSQL("UPDATE contacts SET featureLevel = 1 WHERE isGroupCapable = 1 and featureLevel < 1");
    }

    @Override
    public int getVersion() {
        return 27;
    }
}
