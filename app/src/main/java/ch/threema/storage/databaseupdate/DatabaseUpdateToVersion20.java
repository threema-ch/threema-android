package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion20 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion20(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(sqLiteDatabase, "m_group", "synchronizedAt")) {
            sqLiteDatabase.execSQL("ALTER TABLE m_group ADD COLUMN synchronizedAt LONG DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 20;
    }
}
