/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

import static java.util.zip.GZIPInputStream.GZIP_MAGIC;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Copy of AOSP's GZIPOutputStream exposing a parameter for Deflator's compression level
 */
public class GzipOutputStream extends DeflaterOutputStream {
    protected CRC32 crc = new CRC32();
    private final static int TRAILER_SIZE = 8;

    public GzipOutputStream(OutputStream out, int level, int size, boolean syncFlush) throws IOException {
        super(out, new Deflater(level, true), size, syncFlush);
        writeHeader();
        crc.reset();
    }

    @Override
    public synchronized void write(byte[] buf, int off, int len) throws IOException {
        super.write(buf, off, len);
        crc.update(buf, off, len);
    }

    @Override
    public void finish() throws IOException {
        if (!def.finished()) {
            def.finish();
            while (!def.finished()) {
                int len = def.deflate(buf, 0, buf.length);
                if (def.finished() && len <= buf.length - TRAILER_SIZE) {
                    writeTrailer(buf, len);
                    len = len + TRAILER_SIZE;
                    out.write(buf, 0, len);
                    return;
                }
                if (len > 0)
                    out.write(buf, 0, len);
            }
            byte[] trailer = new byte[TRAILER_SIZE];
            writeTrailer(trailer, 0);
            out.write(trailer);
        }
    }

    private void writeHeader() throws IOException {
        out.write(new byte[]{
            (byte) GZIP_MAGIC,
            (byte) (GZIP_MAGIC >> 8),
            Deflater.DEFLATED,
            0, 0, 0, 0, 0, 0, 0
        });
    }

    private void writeTrailer(byte[] buf, int offset) {
        writeInt((int) crc.getValue(), buf, offset);
        writeInt(def.getTotalIn(), buf, offset + 4);
    }

    private void writeInt(int i, byte[] buf, int offset) {
        writeShort(i & 0xffff, buf, offset);
        writeShort((i >> 16) & 0xffff, buf, offset + 2);
    }

    private void writeShort(int s, byte[] buf, int offset) {
        buf[offset] = (byte) (s & 0xff);
        buf[offset + 1] = (byte) ((s >> 8) & 0xff);
    }
}
