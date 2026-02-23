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
