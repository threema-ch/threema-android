package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * Create forwardSecurityMode field in message model and forwardSecurityEnabled field in contact model
 */
public class DatabaseUpdateToVersion73 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion73(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, "message", "forwardSecurityMode")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN forwardSecurityMode TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, "contacts", "forwardSecurityEnabled")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN forwardSecurityEnabled TINYINT DEFAULT 0");
        }
    }

    @Override
    public String getDescription() {
        return "forwardSecurity";
    }

    @Override
    public int getVersion() {
        return 73;
    }
}
