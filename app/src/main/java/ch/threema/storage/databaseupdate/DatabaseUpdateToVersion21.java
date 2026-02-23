package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

public class DatabaseUpdateToVersion21 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion21(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        for (String statement : getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
    }

    private String[] getStatements() {
        return new String[]{
            "CREATE TABLE `m_group_request_sync_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `apiGroupId` VARCHAR , `creatorIdentity` VARCHAR , `lastRequest` VARCHAR )",
            "CREATE UNIQUE INDEX `apiGroupIdAndCreatorGroupRequestSyncLogModel` ON `m_group_request_sync_log` ( `apiGroupId`, `creatorIdentity` );"
        };
    }

    @Override
    public int getVersion() {
        return 21;
    }
}
