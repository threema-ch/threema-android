/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.domain.models;

import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Wrapper class for group IDs (consisting of 8 bytes, chosen by the group creator and not guaranteed
 * to be unique across multiple group creators).
 */
public class GroupId implements Serializable {

    private final byte[] value;

    public GroupId() {
        this.value = new byte[ProtocolDefines.GROUP_ID_LEN];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(this.value);
    }

    public GroupId(byte[] groupId) throws ThreemaException {
        if (groupId.length != ProtocolDefines.GROUP_ID_LEN)
            throw new ThreemaException("TM016");    /* Invalid group ID length */

        this.value = groupId;
    }

    public GroupId(byte[] data, int offset) {
        this.value = new byte[ProtocolDefines.GROUP_ID_LEN];
        System.arraycopy(data, offset, this.value, 0, ProtocolDefines.GROUP_ID_LEN);
    }

    public GroupId(long groupId) {
        this.value = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(groupId).array();
    }

    public GroupId(String groupId) {
        this.value = Utils.hexStringToByteArray(groupId);
    }

    public byte[] getGroupId() {
        return this.value;
    }

    @Override
    public String toString() {
        return Utils.byteArrayToHexString(this.value);
    }

    public long toLong() {
        return ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (obj.getClass() != this.getClass())
            return false;

        return Arrays.equals(this.value, ((GroupId) obj).value);
    }

    @Override
    public int hashCode() {
        /* group IDs are usually random, so just taking the first four bytes is fine */
        return this.value[0] << 24 | (this.value[1] & 0xFF) << 16 | (this.value[2] & 0xFF) << 8 | (this.value[3] & 0xFF);
    }
}
