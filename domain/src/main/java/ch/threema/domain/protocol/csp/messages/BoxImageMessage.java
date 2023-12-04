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

import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.EndianUtils;

import androidx.annotation.Nullable;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A message that has an image (stored on the blob server) as its content.
 *
 * The contents are referenced by the {@code blobId}, the file {@code size} in bytes,
 * and the nonce to be used when decrypting the image blob.
 *
 *  @Deprecated Use FileMessage instead
 */
@Deprecated
public class BoxImageMessage extends AbstractMessage {

	private byte[] blobId;
	private int size;
	private byte[] nonce;

	public BoxImageMessage() {
		super();
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_IMAGE;
	}

	@Override
	public boolean flagSendPush() {
		return true;
	}

	@Override
	@Nullable
	public Version getMinimumRequiredForwardSecurityVersion() {
		return null;
	}

	@Override
	public boolean allowUserProfileDistribution() {
		return true;
	}

	@Override
	public boolean exemptFromBlocking() {
		return false;
	}

	@Override
	public byte[] getBody() {
		byte[] body = new byte[ProtocolDefines.BLOB_ID_LEN + 4 + NaCl.NONCEBYTES];

		System.arraycopy(blobId, 0, body, 0, ProtocolDefines.BLOB_ID_LEN);
		EndianUtils.writeSwappedInteger(body, ProtocolDefines.BLOB_ID_LEN, size);
		System.arraycopy(nonce, 0, body, ProtocolDefines.BLOB_ID_LEN + 4, nonce.length);

		return body;
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

	public byte[] getNonce() {
		return nonce;
	}

	public void setNonce(byte[] nonce) {
		this.nonce = nonce;
	}
}
