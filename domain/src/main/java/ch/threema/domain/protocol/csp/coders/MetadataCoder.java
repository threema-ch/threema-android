/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

package ch.threema.domain.protocol.csp.coders;

import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.ThreemaKDF;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.protobuf.csp.e2e.MessageMetadata;
import com.google.protobuf.InvalidProtocolBufferException;
import com.neilalexander.jnacl.NaCl;

public class MetadataCoder {
	private final IdentityStoreInterface identityStore;

	public MetadataCoder(IdentityStoreInterface identityStore) {
		this.identityStore = identityStore;
	}

	public MetadataBox encode(MessageMetadata metadata, byte[] nonce, byte[] publicKey) throws ThreemaException {
		byte[] box = NaCl.symmetricEncryptData(metadata.toByteArray(), deriveMetadataKey(publicKey), nonce);
		return new MetadataBox(box);
	}

	public MessageMetadata decode(byte[] nonce, MetadataBox metadataBox, byte[] publicKey) throws InvalidProtocolBufferException, ThreemaException {
		byte[] pb = NaCl.symmetricDecryptData(metadataBox.getBox(), deriveMetadataKey(publicKey), nonce);
		if (pb == null) {
			throw new ThreemaException("Metadata decryption failed");
		}

		return MessageMetadata.parseFrom(pb);
	}

	private byte[] deriveMetadataKey(byte[] publicKey) {
		byte[] sharedSecret = identityStore.calcSharedSecret(publicKey);
		ThreemaKDF kdf = new ThreemaKDF("3ma-csp");
		return kdf.deriveKey("mm", sharedSecret);
	}
}
