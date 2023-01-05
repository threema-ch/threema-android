/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages;

import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;

import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

/**
 * A group message sent by the group creator that causes a new photo to be set for the group.
 */
public class GroupSetPhotoMessage extends AbstractGroupMessage {

	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupSetPhotoMessage");

	private byte[] blobId;
	private int size;
	private byte[] encryptionKey;

	public GroupSetPhotoMessage() {
		super();
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_GROUP_SET_PHOTO;
	}

	@Override
	public byte[] getBody() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write(getApiGroupId().getGroupId());
			bos.write(blobId);
			EndianUtils.writeSwappedInteger(bos, size);
			bos.write(encryptionKey);
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
	}

	public byte[] getBlobId() {
		return blobId;
	}

	public void setBlobId(byte[] blobId) {
		this.blobId = blobId;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public byte[] getEncryptionKey() {
		return encryptionKey;
	}

	public void setEncryptionKey(byte[] encryptionKey) {
		this.encryptionKey = encryptionKey;
	}
}
