package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * add caption field to normal, group and distribution list message models
 */
public class DatabaseUpdateToVersion35 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion35(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        //add new caption field to message model fields
        for (String table : new String[]{
            "message",
            "m_group_message",
            "distribution_list_message"
        }) {
            if (!fieldExists(this.sqLiteDatabase, table, "caption")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table
                    + " ADD COLUMN caption VARCHAR NULL");
            }
        }
    }

    @Override
    public String getDescription() {
        return "add caption";
    }

    @Override
    public int getVersion() {
        return 35;
    }
}
