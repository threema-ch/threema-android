/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

import ch.threema.base.ThreemaException;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Wrapper class for group IDs (consisting of 8 bytes, chosen by the group creator and not guaranteed
 * to be unique across multiple group creators).
 */
public class GroupId {

	private final byte[] groupId;

	public GroupId() {
		groupId = new byte[ProtocolDefines.GROUP_ID_LEN];
		SecureRandom rnd = new SecureRandom();
		rnd.nextBytes(groupId);
	}

	public GroupId(byte[] groupId) throws ThreemaException {
		if (groupId.length != ProtocolDefines.GROUP_ID_LEN)
			throw new ThreemaException("TM016");    /* Invalid group ID length */

		this.groupId = groupId;
	}

	public GroupId(byte[] data, int offset) {
		groupId = new byte[ProtocolDefines.GROUP_ID_LEN];
		System.arraycopy(data, offset, groupId, 0, ProtocolDefines.GROUP_ID_LEN);
	}

	public byte[] getGroupId() {
		return groupId;
	}

	@Override
	public String toString() {
		return Utils.byteArrayToHexString(groupId);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != getClass())
			return false;

		return Arrays.equals(groupId, ((GroupId)obj).groupId);
	}

	@Override
	public int hashCode() {
		/* group IDs are usually random, so just taking the first four bytes is fine */
		return groupId[0] << 24 | (groupId[1] & 0xFF) << 16 | (groupId[2] & 0xFF) << 8 | (groupId[3] & 0xFF);
	}
}
