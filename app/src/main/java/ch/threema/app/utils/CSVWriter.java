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

import java.io.Writer;

public class CSVWriter extends au.com.bytecode.opencsv.CSVWriter {
	private String[] header = null;

	public CSVWriter(Writer writer) {
		super(writer);
	}

	public CSVWriter(Writer writer, String[] header) {
		super(writer);
		this.header = header;
		//write directly
		this.writeNext(this.header);
	}

	public CSVWriter(Writer writer, char c) {
		super(writer, c);
	}

	public CSVWriter(Writer writer, char c, char c2) {
		super(writer, c, c2);
	}

	public CSVWriter(Writer writer, char c, char c2, char c3) {
		super(writer, c, c2, c3);
	}

	public CSVWriter(Writer writer, char c, char c2, String s) {
		super(writer, c, c2, s);
	}

	public CSVWriter(Writer writer, char c, char c2, char c3, String s) {
		super(writer, c, c2, c3, s);
	}

	public CSVRow createRow() {
		return new CSVRow(this, this.header);
	}
}
