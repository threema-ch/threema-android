package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * Create forwardSecurityMode field in group and distribution list message model.
 */
public class DatabaseUpdateToVersion74 implements DatabaseUpdate {
    public static final int VERSION = 74;

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion74(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, "m_group_message", "forwardSecurityMode")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE m_group_message ADD COLUMN " +
                "forwardSecurityMode TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, "distribution_list_message", "forwardSecurityMode")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE distribution_list_message ADD COLUMN " +
                "forwardSecurityMode TINYINT DEFAULT 0");
        }
    }

    @Override
    public String getDescription() {
        return "forwardSecurity 2";
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
