package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

public class DatabaseUpdateToVersion34 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion34(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        sqLiteDatabase.rawExecSQL(
            "CREATE INDEX IF NOT EXISTS `distribution_list_member_dis_idx`" +
                " ON `distribution_list_member`(`distributionListId`)");
    }

    @Override
    public int getVersion() {
        return 34;
    }
}
