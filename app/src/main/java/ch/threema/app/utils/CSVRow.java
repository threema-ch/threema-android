/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

package ch.threema.app.utils;

import java.util.Date;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;

public class CSVRow {
	private CSVWriter writer;
	final private String[] header;
	final private String[] data;

	public CSVRow(CSVWriter writer, String[] header) {
		this.writer = writer;
		this.header = header;
		this.data = new String[this.header.length];
	}

	public CSVRow(String[] header, String[] data) {
		this.header = header;
		this.data = data;
	}

	public String getString(int pos) throws ThreemaException {
		if(this.data == null || this.data.length < pos) {
			throw new ThreemaException("invalid csv position");
		}

		return this.data[pos];
	}

	public @NonNull Integer getInteger(int pos) throws ThreemaException {
		return Integer.valueOf(this.getString(pos));
	}

	public @NonNull Long getLong(int pos) throws ThreemaException {
		return Long.valueOf(this.getString(pos));
	}

	public boolean getBoolean(int pos) throws ThreemaException {
		return TestUtil.compare("1", this.getString(pos));
	}

	public Date getDate(int pos) throws ThreemaException {
		String cell = this.getString(pos);
		if(cell != null && cell.length() > 0) {
			return new Date(Long.parseLong(cell));
		}

		return null;
	}

	public CSVRow write(String fieldName, Object v) throws ThreemaException{
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position");
		}

		return this.write(pos, v);
	}

	public CSVRow write(int pos, Object v) throws ThreemaException {
		if(this.data.length < pos) {
			throw new ThreemaException("invalid position to write [" + pos + "]");
		}

		this.data[pos] = this.escape(v);
		return this;
	}
	public CSVRow write(String fieldName, String v) throws ThreemaException{
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position");
		}

		return this.write(pos, v);
	}

	public CSVRow write(int pos, String v) throws ThreemaException {
		if(this.data.length < pos) {
			throw new ThreemaException("invalid position to write [" + pos + "]");
		}

		this.data[pos] = this.escape(v);
		return this;
	}

	public CSVRow write(String fieldName, boolean v) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position");
		}

		return this.write(pos, v);
	}

	public CSVRow write(int pos, int v) throws ThreemaException {
		if(this.data.length < pos) {
			throw new ThreemaException("invalid position to write [" + pos + "]");
		}

		this.data[pos] = this.escape(v);
		return this;
	}

	public CSVRow write(String fieldName, int v) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position");
		}

		return this.write(pos, v);
	}

	public CSVRow write(int pos, boolean v) throws ThreemaException {
		if(this.data.length < pos) {
			throw new ThreemaException("invalid position to write [" + pos + "]");
		}

		this.data[pos] = this.escape(v);
		return this;
	}

	public CSVRow write(String fieldName, Date v) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position");
		}

		return this.write(pos, v);
	}

	public CSVRow write(int pos, Date v) throws ThreemaException {
		if(this.data.length < pos) {
			throw new ThreemaException("invalid position to write [" + pos + "]");
		}

		this.data[pos] = this.escape(v);
		return this;
	}

	public CSVRow write(String fieldName, Object[] v) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position");
		}

		return this.write(pos, v);
	}

	public CSVRow write(int pos, Object[] v) throws ThreemaException {
		if(this.data.length < pos) {
			throw new ThreemaException("invalid position to write [" + pos + "]");
		}

		this.data[pos] = this.escape(v);
		return this;
	}

	public String[] getStrings(int pos) throws ThreemaException {
		String r = this.getString(pos);
		return TestUtil.isEmptyOrNull(r) ? new String[]{} : r.split(";");
	}

	public String getString(String fieldName) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position [" + fieldName + "]");
		}
		return this.getString(pos);
	}

	public @NonNull Integer getInteger(String fieldName) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position [" + fieldName + "]");
		}
		return this.getInteger(pos);
	}

	public @NonNull Long getLong(String fieldName) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position [" + fieldName + "]");
		}
		return this.getLong(pos);
	}

	public boolean getBoolean(String fieldName) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position [" + fieldName + "]");
		}
		return this.getBoolean(pos);
	}

	public Date getDate(String fieldName) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position [" + fieldName + "]");
		}
		return this.getDate(pos);

	}

	public String[] getStrings(String fieldName) throws ThreemaException {
		int pos = this.getValuePosition(fieldName);
		if(pos < 0) {
			throw new ThreemaException("invalid csv header position [" + fieldName + "]");
		}
		return this.getStrings(pos);

	}

	/**
	 * Return the CSV column index for the specified column name.
	 *
	 * @return the index, or -1 if the column was not found.
	 */
	public int getValuePosition(String fieldName) {
		if(this.header != null && fieldName != null) {
			for(int n = 0; n < this.header.length; n++) {
				if(fieldName.equals(this.header[n])) {
					return n;
				}
			}
		}

		return -1;
	}

	public void write() {
		if(this.writer != null) {
			this.writer.writeNext(this.data);
		}
	}


	/**
	 * return a csv well formed string
	 */
	private String escape(Date date) {
		if(date == null) {
			return "";
		}
		return String.valueOf(date.getTime());
	}

	/**
	 * return a csv well formed string
	 */
	private String escape(boolean bool) {
		return bool ? "1" : "0";
	}
	/**
	 * return a csv well formed string
	 */
	private String escape(String ns) {
		if (ns == null) {
			return "";
		}
		return ns.replace("\\", "\\\\");
	}

	private String escape(Object[] os) {
		String result = "";
		if(os != null) {
			for (Object o : os) {
				if (result.length() > 0) {
					result += ';';
				}

				result += this.escape(o);
			}
		}

		return result;
	}

	/**
	 * return a csv well formed string
	 */
	private String escape(Object ns) {
		if (ns == null) {
			return "";
		}

		return this.escape(ns.toString());
	}
}
