package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * Add a messageFlags field
 */
public class DatabaseUpdateToVersion62 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion62(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        // add new messageFlags field to message model table
        for (String table : new String[]{"message", "m_group_message", "distribution_list_message"}) {
            if (!fieldExists(this.sqLiteDatabase, table, "messageFlags")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN messageFlags INT DEFAULT 0");
            }
        }
    }

    @Override
    public String getDescription() {
        return "messageFlags";
    }

    @Override
    public int getVersion() {
        return 62;
    }
}
