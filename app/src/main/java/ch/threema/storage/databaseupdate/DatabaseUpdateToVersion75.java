package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;


public class DatabaseUpdateToVersion75 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion75(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        String table = "m_group";
        String groupDescColumn = "groupDesc";
        String groupDescTimestampColumn = "changedGroupDescTimestamp";

        if (!fieldExists(this.sqLiteDatabase, table, groupDescColumn)) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN " + groupDescColumn + " VARCHAR DEFAULT NULL");
        }

        if (!fieldExists(this.sqLiteDatabase, table, groupDescTimestampColumn)) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN " + groupDescTimestampColumn + " VARCHAR DEFAULT NULL");
        }
    }

    @Override
    public int getVersion() {
        return 75;
    }
}
