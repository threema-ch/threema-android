/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import com.neilalexander.jnacl.NaCl;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.crypto.NonceScope;
import ch.threema.domain.helpers.InMemoryContactStore;
import ch.threema.domain.helpers.InMemoryIdentityStore;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupTextMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.protocol.csp.messages.TextMessage;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.domain.testhelpers.TestHelpers;
import ch.threema.domain.protocol.csp.messages.voip.*;

import org.junit.Assert;
import org.junit.Test;

import static ch.threema.domain.testhelpers.TestHelpers.setMessageDefaultSenderAndReceiver;

public class MessageCoderTest {

    @NonNull
    private final MessageCoder encoder;
    @NonNull
    private final MessageCoder decoder;

    public MessageCoderTest() {
        byte[] myPublicKey = new byte[NaCl.PUBLICKEYBYTES];
        byte[] myPrivateKey = new byte[NaCl.SECRETKEYBYTES];
        byte[] peerPublicKey = new byte[NaCl.PUBLICKEYBYTES];
        byte[] peerPrivateKey = new byte[NaCl.SECRETKEYBYTES];

        NaCl.genkeypair(myPublicKey, myPrivateKey);
        NaCl.genkeypair(peerPublicKey, peerPrivateKey);

        IdentityStoreInterface myIdentityStore = new InMemoryIdentityStore(
            "01234567",
            null,
            myPrivateKey,
            "Me"
        );

        IdentityStoreInterface peerIdentityStore = new InMemoryIdentityStore(
            "0ABCDEFG",
            null,
            peerPrivateKey,
            "Peer"
        );

        ContactStore myContactStore = new InMemoryContactStore();
        myContactStore.addContact(new Contact("0ABCDEFG", peerPublicKey, VerificationLevel.UNVERIFIED));

        ContactStore peerContactStore = new InMemoryContactStore();
        peerContactStore.addContact(new Contact("01234567", myPublicKey, VerificationLevel.UNVERIFIED));

        encoder = new MessageCoder(
            myContactStore,
            myIdentityStore
        );

        decoder = new MessageCoder(
            peerContactStore,
            peerIdentityStore
        );
    }

    private MessageBox box(AbstractMessage msg) throws ThreemaException {
        MessageCoder messageCoder = new MessageCoder(
            TestHelpers.getNoopContactStore(),
            TestHelpers.getNoopIdentityStore()
        );
        NonceFactory nonceFactory = TestHelpers.getNoopNonceFactory();
        byte[] nonce = nonceFactory.nextNonce(NonceScope.CSP);
        return messageCoder.encode(msg, nonce);
    }

    @Test
    public void testVoipFlagsOffer() throws ThreemaException {
        final VoipCallOfferMessage msg = new VoipCallOfferMessage();
        setMessageDefaultSenderAndReceiver(msg);
        final VoipCallOfferData offerData = new VoipCallOfferData();
        final VoipCallOfferData.OfferData data = new VoipCallOfferData.OfferData()
            .setSdp("testsdp")
            .setSdpType("offer");
        offerData.setOfferData(data);
        msg.setData(offerData);
        final MessageBox boxed = this.box(msg);
        // Flags: Voip + Push
        Assert.assertEquals(0x20 | 0x01, boxed.getFlags());
    }

    @Test
    public void testVoipFlagsAnswer() throws ThreemaException {
        final VoipCallAnswerMessage msg = new VoipCallAnswerMessage();
        final VoipCallAnswerData answerData = new VoipCallAnswerData()
            .setAction(VoipCallAnswerData.Action.REJECT)
            .setAnswerData(null)
            .setRejectReason(VoipCallAnswerData.RejectReason.BUSY);
        msg.setData(answerData);
        setMessageDefaultSenderAndReceiver(msg);
        final MessageBox boxed = this.box(msg);
        // Flags: Voip + Push
        Assert.assertEquals(0x20 | 0x01, boxed.getFlags());
    }

    @Test
    public void testVoipFlagsCandidates() throws ThreemaException {
        final VoipICECandidatesMessage msg = new VoipICECandidatesMessage();
        final VoipICECandidatesData candidatesData = new VoipICECandidatesData()
            .setCandidates(new VoipICECandidatesData.Candidate[]{
                new VoipICECandidatesData.Candidate("testcandidate1", "testmid1", 42, "testufrag1"),
                new VoipICECandidatesData.Candidate("testcandidate2", "testmid2", 23, "testufrag2"),
            });
        msg.setData(candidatesData);
        setMessageDefaultSenderAndReceiver(msg);
        final MessageBox boxed = this.box(msg);
        // Flags: Voip + Push
        Assert.assertEquals(0x20 | 0x01, boxed.getFlags());
    }

    @Test
    public void testVoipFlagsHangup() throws ThreemaException {
        final VoipCallHangupMessage msg = new VoipCallHangupMessage();
        msg.setData(new VoipCallHangupData());
        setMessageDefaultSenderAndReceiver(msg);
        final MessageBox boxed = this.box(msg);
        // Flags: Push only
        Assert.assertEquals(0x01, boxed.getFlags());
    }

    @Test
    public void testDeserializeTextMessage() throws MissingPublicKeyException, BadMessageException, ThreemaException {
        TextMessage textMessage = new TextMessage();

        setAndAssertText(textMessage, "Hello");
        assertEqualMessage(textMessage, encodeAndDecode(textMessage));

        setAndAssertText(textMessage, ".");
        assertEqualMessage(textMessage, encodeAndDecode(textMessage));

        setAndAssertText(textMessage, new String(Character.toChars(0x1F4A1)));
        assertEqualMessage(textMessage, encodeAndDecode(textMessage));

        setAndAssertText(textMessage, "");
        Assert.assertThrows(BadMessageException.class, () -> encodeAndDecode(textMessage));

        setAndAssertText(textMessage, "a");
        byte[] body = encode(textMessage).getBox();
        Assert.assertThrows(BadMessageException.class, () ->
            // Invalid offset
            TextMessage.fromByteArray(body, body.length, body.length)
        );

        Assert.assertThrows(BadMessageException.class, () ->
            // Invalid length
            TextMessage.fromByteArray(body, 1, body.length)
        );
    }

    private void setAndAssertText(@NonNull TextMessage message, @NonNull String text) {
        message.setText(text);
        Assert.assertEquals(text, message.getText());
    }

    @Test
    public void testDeserializeGroupTextMessage() throws MissingPublicKeyException, BadMessageException, ThreemaException {
        GroupTextMessage textMessage = new GroupTextMessage();
        textMessage.setGroupCreator("01234567");
        textMessage.setApiGroupId(new GroupId());

        setAndAssertText(textMessage, "Hello");
        assertEqualMessage(textMessage, encodeAndDecode(textMessage));

        setAndAssertText(textMessage, ".");
        assertEqualMessage(textMessage, encodeAndDecode(textMessage));

        setAndAssertText(textMessage, new String(Character.toChars(0x1F4A1)));
        assertEqualMessage(textMessage, encodeAndDecode(textMessage));

        setAndAssertText(textMessage, "");
        Assert.assertThrows(BadMessageException.class, () -> encodeAndDecode(textMessage));

        setAndAssertText(textMessage, "a");
        byte[] body = encode(textMessage).getBox();
        Assert.assertThrows(BadMessageException.class, () ->
            // Invalid offset
            GroupTextMessage.fromByteArray(body, body.length, body.length)
        );

        Assert.assertThrows(BadMessageException.class, () ->
            // Invalid length
            GroupTextMessage.fromByteArray(body, 1, body.length)
        );
    }

    private void setAndAssertText(@NonNull GroupTextMessage message, @NonNull String text) {
        message.setText(text);
        Assert.assertEquals(text, message.getText());
    }

    @Test
    public void testDeliveryReceiptMessage() throws MissingPublicKeyException, BadMessageException, ThreemaException {
        DeliveryReceiptMessage deliveryReceiptMessage = new DeliveryReceiptMessage();

        setAndAssertReceiptType(deliveryReceiptMessage, ProtocolDefines.MSGTYPE_DELIVERY_RECEIPT);
        setAndAssertMessageIds(deliveryReceiptMessage, new MessageId[]{new MessageId()});
        assertEqualMessage(deliveryReceiptMessage, encodeAndDecode(deliveryReceiptMessage));

        setAndAssertMessageIds(deliveryReceiptMessage, new MessageId[]{});
        Assert.assertThrows(BadMessageException.class, () -> encodeAndDecode(deliveryReceiptMessage));

        setAndAssertMessageIds(deliveryReceiptMessage, new MessageId[]{
            new MessageId(), new MessageId(), new MessageId(), new MessageId()
        });
        assertEqualMessage(deliveryReceiptMessage, encodeAndDecode(deliveryReceiptMessage));

        MessageBox messageBox = encode(deliveryReceiptMessage);
        byte[] invalidLengthBody = new byte[messageBox.getBox().length + 1];
        Assert.assertThrows(BadMessageException.class, () -> DeliveryReceiptMessage.fromByteArray(invalidLengthBody, 1, invalidLengthBody.length - 1));

        byte[] body = messageBox.getBox();
        Assert.assertThrows(BadMessageException.class, () ->
            // Invalid offset
            TextMessage.fromByteArray(body, body.length, body.length)
        );

        Assert.assertThrows(BadMessageException.class, () ->
            // Invalid length
            TextMessage.fromByteArray(body, 1, body.length)
        );
    }

    private void setAndAssertReceiptType(@NonNull DeliveryReceiptMessage message, int type) {
        message.setReceiptType(type);
        Assert.assertEquals(type, message.getType());
    }

    private void setAndAssertMessageIds(
        @NonNull DeliveryReceiptMessage message,
        @NonNull MessageId[] messageIds
    ) {
        message.setReceiptMessageIds(messageIds);
        Assert.assertArrayEquals(messageIds, message.getReceiptMessageIds());
    }

    private void assertEqualMessage(@NonNull AbstractMessage expected, @NonNull AbstractMessage actual) throws ThreemaException {
        Assert.assertArrayEquals(expected.getBody(), actual.getBody());
    }

    private MessageBox encode(@NonNull AbstractMessage message) throws ThreemaException {
        message.setToIdentity("0ABCDEFG");
        message.setFromIdentity("01234567");
        return encoder.encode(message, new byte[NaCl.NONCEBYTES]);
    }

    @NonNull
    private AbstractMessage encodeAndDecode(@NonNull AbstractMessage message) throws ThreemaException, MissingPublicKeyException, BadMessageException {
        return decoder.decode(encode(message));
    }

}
