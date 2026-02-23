package ch.threema.domain.protocol.csp.coders;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;

import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceScope;
import ch.threema.base.utils.Utils;
import ch.threema.domain.helpers.DummyUsers;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.domain.testhelpers.TestHelpers;
import ch.threema.protobuf.csp.e2e.MessageMetadata;

public class MetadataCoderTest {

    private static final String TEST_NICKNAME = "John Doe";

    private static final byte[] TEST_NONCE = Utils.hexStringToByteArray("f0a6de071e2fee0ec5e58637f707c73cd5ba1889db2b89b9");
    private static final byte[] TEST_BOX = Utils.hexStringToByteArray("859031ebffa23b44a55fa7e5e8f05db602eef238ba866a25afbe");

    private static final IdentityStore identityStoreA = DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE);
    private static final IdentityStore identityStoreB = DummyUsers.getIdentityStoreForUser(DummyUsers.BOB);

    @Test
    public void testEncodeDecode() throws ThreemaException, InvalidProtocolBufferException {
        byte[] nonce = TestHelpers.getNoopNonceFactory().nextNonce(NonceScope.CSP);
        MessageId messageId = MessageId.random();

        Date createdAt = new Date();
        MessageMetadata metadata = MessageMetadata.newBuilder()
            .setNickname(TEST_NICKNAME)
            .setCreatedAt(createdAt.getTime())
            .setMessageId(messageId.getMessageIdLong())
            .build();
        MetadataBox box = new MetadataCoder(identityStoreA).encode(metadata, nonce, identityStoreB.getPublicKey());

        MessageMetadata metadataDecoded = new MetadataCoder(identityStoreB).decode(nonce, box, identityStoreA.getPublicKey());

        Assertions.assertEquals(TEST_NICKNAME, metadataDecoded.getNickname());
        Assertions.assertEquals(messageId, new MessageId(metadataDecoded.getMessageId()));
        Assertions.assertEquals(createdAt.getTime(), metadataDecoded.getCreatedAt());
    }

    @Test
    public void testEncodedBox() throws ThreemaException {

        MessageMetadata metadata = MessageMetadata.newBuilder()
            .setNickname(TEST_NICKNAME)
            .build();
        MetadataBox box = new MetadataCoder(identityStoreA).encode(metadata, TEST_NONCE, identityStoreB.getPublicKey());

        Assertions.assertArrayEquals(TEST_BOX, box.getBox());
    }
}
