package ch.threema.storage;

import android.database.Cursor;

import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.storage.models.group.GroupMemberModel;
import ch.threema.storage.models.group.GroupModelOld;

public class DatabaseUtil {

    private static final String PLACEHOLDER = "?";

    private DatabaseUtil() {
    }

    @Nullable
    public static Long getDateTimeContentValue(@Nullable Date date) {
        return date != null ? date.getTime() : null;
    }

    /**
     * @return A concatenated string of placeholder in this form: {@code "?,?,?,?..."}
     *
     * @throws RuntimeException for values of parameter {@code len} below 1
     */
    @NonNull
    public static String makePlaceholders(int len) throws RuntimeException {
        if (len < 1) {
            // It will lead to an invalid query anyway ..
            throw new RuntimeException("No placeholders");
        } else {
            StringBuilder stringBuilder = new StringBuilder(len * 2 - 1);
            stringBuilder.append(PLACEHOLDER);
            for (int i = 1; i < len; i++) {
                stringBuilder.append(",");
                stringBuilder.append(PLACEHOLDER);
            }
            return stringBuilder.toString();
        }
    }

    /**
     * Only for a select count(*) result, the first column must be the count value
     */
    public static long count(@Nullable Cursor cursor) {
        if (cursor == null) {
            return 0L;
        }
        try (cursor) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            } else {
                return 0L;
            }
        }
    }

    /**
     * Converts an array of {@code T} to a valid argument {@code String} array
     */
    @NonNull
    public static <T> String[] convertArguments(@NonNull List<T> objects) {
        final @NonNull String[] arguments = new String[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            arguments[i] = String.valueOf(objects.get(i));
        }
        return arguments;
    }

    /**
     * An SQL query that can be used to check whether an identity is part of a group. There is one
     * placeholder (?) that should be used for the identity that should be checked.
     */
    @NonNull
    public final static String IS_GROUP_MEMBER_QUERY = "SELECT EXISTS(" +
        "SELECT 1 FROM " + GroupModelOld.TABLE + " g INNER JOIN " + GroupMemberModel.TABLE + " m" +
        "  ON m." + GroupMemberModel.COLUMN_GROUP_ID + " = g." + GroupModelOld.COLUMN_ID + " " +
        "WHERE m." + GroupMemberModel.COLUMN_IDENTITY + " = ?" +
        ")";
}
