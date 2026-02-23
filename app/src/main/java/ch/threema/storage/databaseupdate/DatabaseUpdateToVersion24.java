package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.util.Date;
import java.util.TimeZone;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion24 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;
    private String sqlTimezone = "";

    public DatabaseUpdateToVersion24(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;

        TimeZone currentTimeZone = TimeZone.getDefault();
        if (currentTimeZone != null) {
            Date now = new Date();
            int timezone = currentTimeZone.getOffset(now.getTime());
            this.sqlTimezone = (timezone < 0 ? "+" : "-") + String.valueOf(Math.abs(timezone));
        }
    }

    @Override
    public void run() {
        this.renameMessageTable("message");
        this.renameMessageTable("m_group_message");
        this.renameMessageTable("distribution_list_message");
    }

    private void renameMessageTable(String msgTable) {
        String[] fields = new String[]{
            "createdAt",
            "modifiedAt",
            "postedAt"
        };

        //fix NULL modified at field (backup restore bug)
        if (fieldExists(sqLiteDatabase, msgTable, "modifiedAt")) {
            String query = "UPDATE " + msgTable + " SET modifiedAt=createdAt WHERE modifiedAt = '' OR modifiedAt IS NULL;";
            this.sqLiteDatabase.execSQL(query);
        }
        for (final String field : fields) {
            String query = "";
            final String fieldName = field + "Utc";

            if (!fieldExists(sqLiteDatabase, msgTable, fieldName)) {
                sqLiteDatabase.execSQL("ALTER TABLE " + msgTable + " ADD COLUMN " + fieldName + " LONG DEFAULT 0");
            }

            //ask again
            boolean hasFieldOld = fieldExists(sqLiteDatabase, msgTable, field);
            boolean hasFieldNew = fieldExists(sqLiteDatabase, msgTable, fieldName);

            //only update table if old and new field exists (hack to fix exception in release 1.61
            if (hasFieldOld && hasFieldNew) {
                query += (!query.isEmpty() ? ", " : "") +
                    fieldName + "=((strftime('%s', DATETIME(" + field + "))*1000)" + this.sqlTimezone + ")";

                if (query != null && !query.isEmpty()) {
                    query = "UPDATE " + msgTable + " SET " + query + " WHERE " + field + " != '';";
                    this.sqLiteDatabase.execSQL(query);
                }
            }
        }
    }

    @Override
    public int getVersion() {
        return 24;
    }
}
