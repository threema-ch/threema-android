package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion6 implements DatabaseUpdate {
    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion6(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        if (!fieldExists(sqLiteDatabase, "contacts", "threemaAndroidContactId")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN threemaAndroidContactId VARCHAR(255) DEFAULT NULL");
        }

        // There used to be logic here to populate the newly added column with data, but the field will be removed in the database version 94 anyways,
        // so we don't need to add any data into this field here anymore.
    }

    @Override
    public int getVersion() {
        return 6;
    }
}
