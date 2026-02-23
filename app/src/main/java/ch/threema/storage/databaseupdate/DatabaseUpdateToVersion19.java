package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion19 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion19(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(sqLiteDatabase, "m_group", "deleted")) {
            sqLiteDatabase.execSQL("ALTER TABLE m_group ADD COLUMN deleted TINYINT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 19;
    }
}
