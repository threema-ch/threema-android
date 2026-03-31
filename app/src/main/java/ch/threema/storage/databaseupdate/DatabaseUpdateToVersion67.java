package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion67 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion67(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, "contacts", "readReceipts")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN readReceipts TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, "contacts", "typingIndicators")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN typingIndicators TINYINT DEFAULT 0");
        }
    }

    @Override
    public String getDescription() {
        return "add readReceipts and typingIndicators";
    }

    @Override
    public int getVersion() {
        return 67;
    }
}
