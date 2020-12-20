/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

import java.io.IOException;
import java.io.Reader;

public class CSVReader extends au.com.bytecode.opencsv.CSVReader {
	private String[] headerRow;

	public CSVReader(Reader reader) {
		super(reader);
	}

	public CSVReader(Reader reader, boolean firstRowIsHeader) throws IOException{
		super(reader);
		this.headerRow = this.readNext();
	}

	public CSVReader(Reader reader, char c) {
		super(reader, c);
	}

	public CSVReader(Reader reader, char c, char c2) {
		super(reader, c, c2);
	}

	public CSVReader(Reader reader, char c, char c2, boolean b) {
		super(reader, c, c2, b);
	}

	public CSVReader(Reader reader, char c, char c2, char c3) {
		super(reader, c, c2, c3);
	}

	public CSVReader(Reader reader, char c, char c2, int i) {
		super(reader, c, c2, i);
	}

	public CSVReader(Reader reader, char c, char c2, char c3, int i) {
		super(reader, c, c2, c3, i);
	}

	public CSVReader(Reader reader, char c, char c2, char c3, int i, boolean b) {
		super(reader, c, c2, c3, i, b);
	}

	public CSVReader(Reader reader, char c, char c2, char c3, int i, boolean b, boolean b2) {
		super(reader, c, c2, c3, i, b, b2);
	}

	public CSVRow readNextRow() throws IOException {
		String[] dataRow = super.readNext();
		if(dataRow != null) {
			return new CSVRow(this.headerRow, dataRow);
		}
		return null;
	}

}
