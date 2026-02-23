package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

/**
 * add index for unread message count
 */
public class DatabaseUpdateToVersion50 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion50(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        sqLiteDatabase.rawExecSQL("CREATE INDEX IF NOT EXISTS `message_count_idx` ON `message"
            + "`(`identity"
            + "`, `outbox"
            + "`, `isSaved"
            + "`, `isRead"
            + "`, `isStatusMessage"
            + "`)");

        sqLiteDatabase.rawExecSQL("CREATE INDEX IF NOT EXISTS `message_queue_idx` ON `message"
            + "`(`type"
            + "`, `isQueued"
            + "`, `outbox"
            + "`)");
    }

    @Override
    public int getVersion() {
        return 50;
    }
}
