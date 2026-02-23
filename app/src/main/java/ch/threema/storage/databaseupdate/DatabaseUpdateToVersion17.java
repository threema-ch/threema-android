package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

public class DatabaseUpdateToVersion17 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion17(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        //unique uid indexes
        run("CREATE INDEX IF NOT EXISTS `messageUidIdx` ON `message`(`uid`)");
        run("CREATE INDEX IF NOT EXISTS `groupMessageUidIdx` ON `m_group_message`(`uid`)");
        run("CREATE INDEX IF NOT EXISTS `distributionListMessageUidIdx` ON `distribution_list_message`(`uid`)");

        //index on apiMessageId
        run("CREATE INDEX IF NOT EXISTS `messageApiMessageIdIdx` ON `message`(`apiMessageId`)");
        run("CREATE INDEX IF NOT EXISTS `groupMessageApiMessageIdIdx` ON `m_group_message`(`apiMessageId`)");
        run("CREATE INDEX IF NOT EXISTS `distributionListMessageIdIdx` ON `distribution_list_message`(`apiMessageId`)");

        run("CREATE INDEX IF NOT EXISTS `distributionListDistributionListIdIdx` ON `distribution_list_message`(`distributionListId`)");
    }

    private void run(String query) {
        sqLiteDatabase.rawExecSQL(query);
    }

    @Override
    public int getVersion() {
        return 17;
    }
}
