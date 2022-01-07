/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.storage;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java8.util.function.Function;

/**
 * Handling NULL Values and Support Date (as Long) fields
 */
public class CursorHelper implements AutoCloseable {
	private final net.sqlcipher.Cursor cursor;
	private final ColumnIndexCache columnIndexCache;
	// SimpleDateFormat is not thread-safe, so give one to each thread
	public static final ThreadLocal<SimpleDateFormat> dateAsStringFormat = new ThreadLocal<SimpleDateFormat>(){
		@Override
		protected SimpleDateFormat initialValue()
		{
			return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
		}
	};

	public interface Callback {
		/**
		 * @return return false to stop the iteration
		 */
		boolean next(CursorHelper cursorFactory);
	}

	public interface CallbackInstance<T> {
		T next(CursorHelper cursorHelper);
	}


	public CursorHelper(net.sqlcipher.Cursor cursor, ColumnIndexCache columnIndexCache) {
		this.cursor = cursor;
		this.columnIndexCache = columnIndexCache;
	}

	public CursorHelper first(Callback callback) {
		if(callback != null && this.cursor != null) {
			if(this.cursor.moveToFirst()) {
				callback.next(this);
			}
		}
		return this;
	}

	public CursorHelper current(Callback callback) {
		if(callback != null && this.cursor != null) {
			callback.next(this);
		}
		return this;
	}


	public <T> T current(CallbackInstance<T> callback) {
		if(callback != null && this.cursor != null) {
			return callback.next(this);
		}
		return null;
	}

	public CursorHelper each(Callback callback)
	{
		if(callback != null && this.cursor != null) {
			while(cursor.moveToNext()) {
				if(!callback.next(this)) {
					break;
				}
			}
		}
		return this;
	}

	public @Nullable Integer getInt(String columnName) {
		Integer index = this.i(columnName);
		if(index != null) {
			return this.cursor.getInt(index);
		}

		return null;
	}

	public @Nullable Long getLong(String columnName) {
		Integer index = this.i(columnName);
		if(index != null) {
			if (this.cursor.isNull(index)) {
				return null;
			}
			return this.cursor.getLong(index);
		}

		return null;
	}

	public @Nullable String getString(String columnName) {
		Integer index = this.i(columnName);
		if(index != null) {
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
		Long v = this.getLong(columnName);
		if(v != null) {
			return new Date(v);
		}

		return null;
	}
	public @Nullable Date getDateByString(String columnName) {
		String s = this.getString(columnName);
		if(s != null) {

			try {
				return dateAsStringFormat.get().parse(s);
			} catch (ParseException e) {
				return null;
			}
		}

		return null;
	}


	public byte[] getBlob(String columnName) {
		Integer index = this.i(columnName);
		if(index != null) {
			if (!this.cursor.isNull(index)) {
				return this.cursor.getBlob(index);
			}
		}

		return new byte[]{};
	}

	public void close() {
		if(this.cursor != null) {
			this.cursor.close();
		}
	}

	private Integer i(String columnName) {
		if(this.cursor != null) {
			int i = this.columnIndexCache.getColumnIndex(this.cursor, columnName);
			if(i >= 0 && !this.cursor.isNull(i)) {
				return i;
			}
		}
		return null;
	}

	/**
	 * Reset the cursor position before the first record and return an {@link ModelIterator}.
	 *
	 * @param converter Function to convert from a cursor entry to the respective model {@code <T>}
	 * @param <T> Type of the Model
	 * @return Iterator for this cursor
	 */
	public <T> ModelIterator<T> modelIterator(@NonNull Function<CursorHelper,T> converter) {
		this.cursor.moveToPosition(-1); // Reset start position
		return new ModelIterator<T>() {
			@Override
			protected @NonNull T getModelFromCursor() {
				return converter.apply(CursorHelper.this);
			}
		};
	}

	/**
	 * Returns a {@link Iterable} instance for this cursor.
	 * Note that multiple iterators use the same underlying cursor.
	 *
	 * @param converter Function to convert from a cursor entry to the respective model {@code <T>}
	 * @param <T> Type of the Model
	 * @return Iterable for this cursor
	 */
	public <T> Iterable<T> modelIterable(@NonNull Function<CursorHelper,T> converter) {
		return () -> this.modelIterator(converter);
	}

	/**
	 * Iterator over Models of type {@code <T>} generated from this cursor.
	 * Note that the iterator moves the cursor in the result set.
	 *
	 * @param <T> Type of the Model
	 */
	abstract public class ModelIterator<T> implements Iterator<T> {

		@Override
		public boolean hasNext() {
			return !(CursorHelper.this.cursor.isLast() || CursorHelper.this.cursor.isAfterLast());
		}

		@Override
		public @NonNull T next() {
			final boolean movedSuccessfully = CursorHelper.this.cursor.moveToNext();
			if (!movedSuccessfully) {
				throw new NoSuchElementException();
			}

			return this.getModelFromCursor();
		}

		/**
		 * Create a Model Instance of type {@code <T>} from the current cursor
		 * @return Model created from current cursor position.
		 */
		protected abstract @NonNull T getModelFromCursor();
	}
}
