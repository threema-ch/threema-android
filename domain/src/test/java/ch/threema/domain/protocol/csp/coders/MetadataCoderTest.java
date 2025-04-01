/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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
import ch.threema.base.crypto.NonceScope;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.testhelpers.TestHelpers;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.domain.helpers.DummyUsers;
import ch.threema.protobuf.csp.e2e.MessageMetadata;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

public class MetadataCoderTest {

    private static final String TEST_NICKNAME = "John Doe";

    private static final byte[] TEST_NONCE = Utils.hexStringToByteArray("f0a6de071e2fee0ec5e58637f707c73cd5ba1889db2b89b9");
    private static final byte[] TEST_BOX = Utils.hexStringToByteArray("859031ebffa23b44a55fa7e5e8f05db602eef238ba866a25afbe");

    private static final IdentityStoreInterface identityStoreA = DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE);
    private static final IdentityStoreInterface identityStoreB = DummyUsers.getIdentityStoreForUser(DummyUsers.BOB);

    @Test
    public void testEncodeDecode() throws ThreemaException, InvalidProtocolBufferException {

        byte[] nonce = TestHelpers.getNoopNonceFactory().nextNonce(NonceScope.CSP);
        MessageId messageId = new MessageId();

        Date createdAt = new Date();
        MessageMetadata metadata = MessageMetadata.newBuilder()
            .setNickname(TEST_NICKNAME)
            .setCreatedAt(createdAt.getTime())
            .setMessageId(messageId.getMessageIdLong())
            .build();
        MetadataBox box = new MetadataCoder(identityStoreA).encode(metadata, nonce, identityStoreB.getPublicKey());

        MessageMetadata metadataDecoded = new MetadataCoder(identityStoreB).decode(nonce, box, identityStoreA.getPublicKey());

        Assert.assertEquals(TEST_NICKNAME, metadataDecoded.getNickname());
        Assert.assertEquals(messageId, new MessageId(metadataDecoded.getMessageId()));
        Assert.assertEquals(createdAt.getTime(), metadataDecoded.getCreatedAt());
    }

    @Test
    public void testEncodedBox() throws ThreemaException {

        MessageMetadata metadata = MessageMetadata.newBuilder()
            .setNickname(TEST_NICKNAME)
            .build();
        MetadataBox box = new MetadataCoder(identityStoreA).encode(metadata, TEST_NONCE, identityStoreB.getPublicKey());

        Assert.assertArrayEquals(TEST_BOX, box.getBox());
    }
}
