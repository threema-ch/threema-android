package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

/**
 * For ID colors we store the first byte of the SHA-256 hash of the contact identity.
 */
public class DatabaseUpdateToVersion72 implements DatabaseUpdate {
    public static final int VERSION = 72;

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion72(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        final String table = "contacts";
        final String columnColor = "color";
        final String columnColorIndex = "idColorIndex";

        // Rename color column to id color index column
        sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " RENAME " + columnColor + " TO " + columnColorIndex);
        // Temporarily set value to -1 to prevent null pointer exception when loading the contacts
        sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET " + columnColorIndex + " = -1");
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
