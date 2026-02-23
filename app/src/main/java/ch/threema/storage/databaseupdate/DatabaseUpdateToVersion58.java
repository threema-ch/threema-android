package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

/**
 * update constraint for GroupMessagePendingMessageIdModel
 */
public class DatabaseUpdateToVersion58 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion58(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        sqLiteDatabase.rawExecSQL("DROP TABLE IF EXISTS `m_group_message_pending_message_id`");
        sqLiteDatabase.rawExecSQL("DROP TABLE IF EXISTS `m_group_message_pending_msg_id`");
        sqLiteDatabase.rawExecSQL(
            "CREATE TABLE `m_group_message_pending_msg_id`"
                + "("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupMessageId` INTEGER,"
                + "`apiMessageId` VARCHAR"
                + ")");
    }

    @Override
    public String getDescription() {
        return "change GroupMessagePendingMessageIdModel primary key";
    }

    @Override
    public int getVersion() {
        return 58;
    }
}
