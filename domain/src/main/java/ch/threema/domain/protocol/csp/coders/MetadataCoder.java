package ch.threema.domain.protocol.csp.coders;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.libthreema.CryptoException;
import ch.threema.libthreema.LibthreemaKt;
import ch.threema.protobuf.csp.e2e.MessageMetadata;

import com.google.protobuf.InvalidProtocolBufferException;

import ch.threema.base.crypto.NaCl;

import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;

public class MetadataCoder {

    private static final Logger logger = getThreemaLogger("MetadataCoder");

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
