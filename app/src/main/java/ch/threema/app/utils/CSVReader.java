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

import java.io.IOException;
import java.io.Reader;

public class CSVReader extends au.com.bytecode.opencsv.CSVReader {
    private String[] headerRow;

    public CSVReader(Reader reader, boolean firstRowIsHeader) throws IOException {
        super(reader);
        if (firstRowIsHeader) {
            this.headerRow = this.readNext();
        }
    }

    public CSVRow readNextRow() throws IOException {
        String[] dataRow = super.readNext();
        if (dataRow != null) {
            return new CSVRow(this.headerRow, dataRow);
        }
        return null;
    }

}
