package ch.threema.storage;

import android.database.Cursor;

import java.util.Date;

import androidx.annotation.Nullable;

/**
 * Handling NULL Values and Support Date (as Long) fields
 */
public class CursorHelper implements AutoCloseable {
    private final Cursor cursor;
    private final ColumnIndexCache columnIndexCache;

    public interface Callback {
        /**
         * @return return false to stop the iteration
         */
        boolean next(CursorHelper cursorFactory);
    }

    public interface CallbackInstance<T> {
        T next(CursorHelper cursorHelper);
    }


    public CursorHelper(Cursor cursor, ColumnIndexCache columnIndexCache) {
        this.cursor = cursor;
        this.columnIndexCache = columnIndexCache;
    }

    public CursorHelper first(Callback callback) {
        if (callback != null && this.cursor != null) {
            if (this.cursor.moveToFirst()) {
                callback.next(this);
            }
        }
        return this;
    }

    public CursorHelper current(Callback callback) {
        if (callback != null && this.cursor != null) {
            callback.next(this);
        }
        return this;
    }


    public <T> T current(CallbackInstance<T> callback) {
        if (callback != null && this.cursor != null) {
            return callback.next(this);
        }
        return null;
    }

    public CursorHelper each(Callback callback) {
        if (callback != null && this.cursor != null) {
            while (cursor.moveToNext()) {
                if (!callback.next(this)) {
                    break;
                }
            }
        }
        return this;
    }

    public @Nullable Integer getInt(String columnName) {
        Integer index = this.getColumnIndex(columnName);
        if (index != null) {
            return this.cursor.getInt(index);
        }

        return null;
    }

    public @Nullable Long getLong(String columnName) {
        Integer index = this.getColumnIndex(columnName);
        if (index != null) {
            if (this.cursor.isNull(index)) {
                return null;
            }
            return this.cursor.getLong(index);
        }

        return null;
    }

    public @Nullable String getString(String columnName) {
        Integer index = this.getColumnIndex(columnName);
        if (index != null) {
            if (this.cursor.isNull(index)) {
                return null;
            }
            return this.cursor.getString(index);
        }

        return null;
    }

    public boolean getBoolean(String columnName) {
        Integer v = this.getInt(columnName);
        return v != null && v == 1;
    }

    public @Nullable Date getDate(String columnName) {
        Long timestampMillis = this.getLong(columnName);
        if (timestampMillis != null) {
            return new Date(timestampMillis);
        }

        return null;
    }

    public byte[] getBlob(String columnName) {
        Integer index = this.getColumnIndex(columnName);
        if (index != null) {
            if (!this.cursor.isNull(index)) {
                return this.cursor.getBlob(index);
            }
        }

        return new byte[]{};
    }

    @Override
    public void close() {
        if (this.cursor != null) {
            this.cursor.close();
        }
    }

    private Integer getColumnIndex(String columnName) {
        if (this.cursor != null) {
            int index = this.columnIndexCache.getColumnIndex(this.cursor, columnName);
            if (index >= 0 && !this.cursor.isNull(index)) {
                return index;
            }
        }
        return null;
    }
}
