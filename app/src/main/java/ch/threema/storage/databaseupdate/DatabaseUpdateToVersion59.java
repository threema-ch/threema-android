package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * Create all correlationId fields
 */
public class DatabaseUpdateToVersion59 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion59(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        //add new correlationId field to message model fields
        for (String table : new String[]{"message", "m_group_message", "distribution_list_message"}) {
            if (!fieldExists(this.sqLiteDatabase, table, "correlationId")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN correlationId VARCHAR NULL");
            }
        }

        // Create index
        this.sqLiteDatabase.rawExecSQL("CREATE INDEX `messageCorrelationIdIx` ON `message` ( `correlationId` )");
        this.sqLiteDatabase.rawExecSQL("CREATE INDEX `groupMessageCorrelationIdIdx` ON `m_group_message` ( `correlationId` )");
        this.sqLiteDatabase.rawExecSQL("CREATE INDEX `distributionListCorrelationIdIdx` ON `distribution_list_message` ( `correlationId` )");
    }

    @Override
    public String getDescription() {
        return "correlationId";
    }

    @Override
    public int getVersion() {
        return 59;
    }
}
