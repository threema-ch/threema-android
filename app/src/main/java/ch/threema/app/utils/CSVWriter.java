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
        // write directly
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
