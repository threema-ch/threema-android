/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import androidx.annotation.Nullable;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class SecureDeleteUtil {
    private static final Logger logger = getThreemaLogger("SecureDeleteUtil");

    public static void secureDelete(@Nullable File file) throws IOException {
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (int i = 0; i < children.length; i++) {
                        SecureDeleteUtil.secureDelete(children[i]);
                    }
                }
                //remove directory
                FileUtil.deleteFileOrWarn(file, "secureDelete", logger);
                return;
            }

            long length = file.length();
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek(0);
                byte[] zero = new byte[16384];
                long pos = 0;
                while (pos < length) {
                    int nwrite = (int) Math.min((long) zero.length, length - pos);
                    raf.write(zero, 0, nwrite);
                    pos += nwrite;
                }
            }
            FileUtil.deleteFileOrWarn(file, "secureDelete", logger);
        }
    }
}
