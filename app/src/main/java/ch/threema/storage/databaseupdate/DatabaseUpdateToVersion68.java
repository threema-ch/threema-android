package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * Create readAt and deliveredAt fields in message model
 */
public class DatabaseUpdateToVersion68 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion68(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        for (String table : new String[]{"message", "m_group_message", "distribution_list_message"}) {
            if (!fieldExists(this.sqLiteDatabase, table, "deliveredAtUtc")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN deliveredAtUtc DATETIME DEFAULT NULL");
            }
            if (!fieldExists(this.sqLiteDatabase, table, "readAtUtc")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN readAtUtc DATETIME DEFAULT NULL");
            }
        }
    }

    @Override
    public String getDescription() {
        return "correlationId";
    }

    @Override
    public int getVersion() {
        return 68;
    }
}
