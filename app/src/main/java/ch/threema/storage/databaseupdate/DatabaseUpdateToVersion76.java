package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * Create column for user-specific message states in group models.
 */
public class DatabaseUpdateToVersion76 implements DatabaseUpdate {
    public static final int VERSION = 76;

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion76(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, "m_group_message", "groupMessageStates")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE m_group_message ADD COLUMN groupMessageStates VARCHAR DEFAULT NULL");
        }
    }

    @Override
    public String getDescription() {
        return "groupMessageStates";
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
