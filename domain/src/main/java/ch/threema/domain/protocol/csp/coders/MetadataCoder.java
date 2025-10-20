/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.libthreema.CryptoException;
import ch.threema.libthreema.LibthreemaKt;
import ch.threema.protobuf.csp.e2e.MessageMetadata;

import com.google.protobuf.InvalidProtocolBufferException;

import ch.threema.base.crypto.NaCl;

import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;

public class MetadataCoder {

    private static final Logger logger = LoggingUtil.getThreemaLogger("MetadataCoder");

    private final IdentityStore identityStore;

    public MetadataCoder(IdentityStore identityStore) {
        this.identityStore = identityStore;
    }

    public MetadataBox encode(@NonNull MessageMetadata metadata, byte[] nonce, byte[] publicKey) throws ThreemaException {
        final byte[] box;
        try {
            box = NaCl.symmetricEncryptData(metadata.toByteArray(), deriveMetadataKey(publicKey), nonce);
        } catch (CryptoException cryptoException) {
            throw new ThreemaException("Failed to encrypt data", cryptoException);
        }
        return new MetadataBox(box);
    }

    public MessageMetadata decode(byte[] nonce, @NonNull MetadataBox metadataBox, byte[] publicKey) throws InvalidProtocolBufferException, ThreemaException {
        final @NonNull byte[] pb;
        try {
            pb = NaCl.symmetricDecryptData(metadataBox.getBox(), deriveMetadataKey(publicKey), nonce);
        } catch (CryptoException cryptoException) {
            throw new ThreemaException("Metadata decryption failed", cryptoException);
        }
        return MessageMetadata.parseFrom(pb);
    }

    @NonNull
    private byte[] deriveMetadataKey(byte[] publicKey) throws ThreemaException {
        byte[] sharedSecret = identityStore.calcSharedSecret(publicKey);
        try {
            return LibthreemaKt.blake2bMac256(
                sharedSecret,
                "3ma-csp".getBytes(StandardCharsets.UTF_8),
                "mm".getBytes(StandardCharsets.UTF_8),
                new byte[0]
            );
        } catch (CryptoException cryptoException) {
            logger.error("Failed to compute blake2b hash", cryptoException);
            throw new ThreemaException("Failed to compute blake2b hash", cryptoException);
        }
    }
}
