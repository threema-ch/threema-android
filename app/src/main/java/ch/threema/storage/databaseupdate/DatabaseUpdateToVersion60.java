package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * add caption field to normal, group and distribution list message models
 */
public class DatabaseUpdateToVersion60 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion60(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        //add new quote field to message model fields
        for (String table : new String[]{
            "message",
            "m_group_message",
            "distribution_list_message"
        }) {
            if (!fieldExists(sqLiteDatabase, table, "quotedMessageId")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN quotedMessageId VARCHAR NULL");
            }
        }
    }

    @Override
    public String getDescription() {
        return "add quoted message id";
    }

    @Override
    public int getVersion() {
        return 60;
    }
}
