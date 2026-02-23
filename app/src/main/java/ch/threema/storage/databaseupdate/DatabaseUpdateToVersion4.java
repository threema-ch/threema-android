package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion4 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion4(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        //dirty stuff, but the only way to fix the update mess in beta phase

        //create postedAt field if not exists
        if (!fieldExists(sqLiteDatabase, "message", "postedAt")) {
            this.sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN postedAt DATETIME DEFAULT NULL");
            this.sqLiteDatabase.rawExecSQL("UPDATE message SET postedAt = createdAt");
        }

        //create isSaved field if not exists
        if (!fieldExists(sqLiteDatabase, "message", "isSaved")) {
            this.sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN isSaved INT DEFAULT 0");
            this.sqLiteDatabase.rawExecSQL("UPDATE message SET isSaved = 1");
        }
    }

    @Override
    public int getVersion() {
        return 4;
    }
}
