/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.domain.protocol.connection.csp;

import org.apache.commons.io.EndianUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ProtocolExtension {
    public static final int CLIENT_INFO_TYPE = 0x00;
    public static final int CSP_DEVICE_ID_TYPE = 0x01;
    public static final int MESSAGE_PAYLOAD_VERSION_TYPE = 0x02;
    public static final int DEVICE_COOKIE_TYPE = 0x03;
    public static final String VERSION_MAGIC_STRING = "threema-clever-extension-field";
    public static final int MESSAGE_PAYLOAD_VERSION = 0x01;

    private final int type;
    private final byte[] data;

    ProtocolExtension(int type, byte[] data) {
        this.type = type;
        this.data = data;
    }

    public int getType() {
        return type;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        bos.write(type);
        EndianUtils.writeSwappedShort(bos, (short) data.length);
        bos.write(data);
        return bos.toByteArray();
    }
}
