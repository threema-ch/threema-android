/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.domain.protocol.csp.fs;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import ch.threema.base.ThreemaException;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.helpers.DummyUsers;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.BoxTextMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.DHSessionStoreException;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.domain.stores.DummyContactStore;
import ch.threema.domain.stores.InMemoryDHSessionStore;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.domain.testhelpers.TestHelpers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ForwardSecurityMessageProcessorTest {

	private static final int NUM_RANDOM_RUNS = 20;

	private static final String ALICE_MESSAGE_1 = "Hello Bob!";
	private static final String ALICE_MESSAGE_2 = "Now we're in 4DH mode!";
	private static final String ALICE_MESSAGE_3 = "This message will never arrive.";
	private static final String ALICE_MESSAGE_4 = "But this one will.";
	private static final String ALICE_MESSAGE_5 = "Why did you lose your data, Bob?";
	private static final String ALICE_MESSAGE_6 = "Just making sure I can still reach you in 4DH mode.";
	private static final String ALICE_MESSAGE_7 = "Looks good.";
	private static final String BOB_MESSAGE_1 = "Hello Alice, glad to talk to you in 4DH mode!";
	private static final String BOB_MESSAGE_2 = "Hello Alice, I haven't heard from you yet!";
	private static final String BOB_MESSAGE_3 = "Let's see whose session we will use...";

	private UserContext aliceContext;
	private UserContext bobContext;

	private void startNegotiationAlice() throws ThreemaException {
		aliceContext = makeTestUserContext(DummyUsers.ALICE);
		bobContext = makeTestUserContext(DummyUsers.BOB);

		// Add mutual contacts
		aliceContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.BOB));
		bobContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.ALICE));

		// Alice now sends Bob a text message with forward security. No DH session exists,
		// so a new one has to be negotiated.
		sendTextMessage(ALICE_MESSAGE_1, aliceContext, DummyUsers.BOB);

		// At this point, Alice has enqueued two FS messages: Init and Message.
		Assert.assertEquals(2, aliceContext.messageQueue.getQueueSize());

		// Alice should now have a 2DH session with Bob
		DHSession alicesInitiatorSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(alicesInitiatorSession);

		// The 2DH "my" ratchet counter should be 2 on Alice's side (as she has already incremented it
		// for the next message). There should be no peer 2DH ratchet, as it is never needed for the initiator.
		Assert.assertNotNull(alicesInitiatorSession.getMyRatchet2DH());
		Assert.assertNull(alicesInitiatorSession.getPeerRatchet2DH());
		Assert.assertEquals(2, alicesInitiatorSession.getMyRatchet2DH().getCounter());
	}

	@Test
	public void testNegotiationAnd2DH() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Start the negotiation on Alice's side, up to the point where the Init and Message are
		// on the way to Bob, but have not been received by him yet
		startNegotiationAlice();

		// Let Bob process all the messages that he has received from Alice.
		// The decapsulated message should be the text message from Alice.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);

		// At this point, Bob should have enqueued one FS message: Accept
		Assert.assertEquals(1, bobContext.messageQueue.getQueueSize());

		// Let Alice process the Accept message that she has received from Bob
		List<AbstractMessage> alicesReceivedMessages = processReceivedMessages(bobContext.messageQueue, aliceContext);

		// Bob has not sent any actual message to Alice
		Assert.assertEquals(0, alicesReceivedMessages.size());

		// At this point, Alice and Bob should have one mutual 4DH session. Alice has already
		// discarded her 2DH ratchets.
		DHSession alicesInitiatorSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(alicesInitiatorSession);
		Assert.assertNull(alicesInitiatorSession.getMyRatchet2DH());
		Assert.assertNull(alicesInitiatorSession.getPeerRatchet2DH());
		Assert.assertNotNull(alicesInitiatorSession.getMyRatchet4DH());
		Assert.assertNotNull(alicesInitiatorSession.getPeerRatchet4DH());

		// Bob has not received a 4DH message yet, so he still has a 2DH peer ratchet
		DHSession bobsResponderSession = bobContext.dhSessionStore.getDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			alicesInitiatorSession.getId()
		);
		Assert.assertNotNull(bobsResponderSession);
		Assert.assertNull(bobsResponderSession.getMyRatchet2DH());
		Assert.assertNotNull(bobsResponderSession.getPeerRatchet2DH());
		Assert.assertNotNull(bobsResponderSession.getMyRatchet4DH());
		Assert.assertNotNull(bobsResponderSession.getPeerRatchet4DH());

		// The 2DH peer ratchet counter should be 2 on Bob's side to match the single 2DH message that
		// he has received (counter is incremented automatically after reception), and the 4DH ratchet
		// counters should be 1 on both sides as no 4DH messages have been exchanged yet.
		Assert.assertEquals(2, bobsResponderSession.getPeerRatchet2DH().getCounter());
		Assert.assertEquals(1, alicesInitiatorSession.getMyRatchet4DH().getCounter());
		Assert.assertEquals(1, alicesInitiatorSession.getPeerRatchet4DH().getCounter());
		Assert.assertEquals(1, bobsResponderSession.getMyRatchet4DH().getCounter());
		Assert.assertEquals(1, bobsResponderSession.getPeerRatchet4DH().getCounter());
	}

	@Test
	public void test4DH() throws MissingPublicKeyException, BadMessageException, ThreemaException {
		testNegotiationAnd2DH();

		// Check that we're in 4DH mode from the previous exchange
		Assert.assertNotNull(aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		));

		// Alice now sends Bob another message, this time in 4DH mode
		sendTextMessage(ALICE_MESSAGE_2, aliceContext, DummyUsers.BOB);

		// Let Bob process all the messages that he has received from Alice.
		// The decapsulated message should be the text message from Alice.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_2, ForwardSecurityMode.FOURDH);

		// At this point, Bob should not have enqueued any further messages
		Assert.assertEquals(0, bobContext.messageQueue.getQueueSize());

		// Bob should have discarded his 2DH peer ratchet now
		DHSession bobsResponderSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity()
		);
		Assert.assertNotNull(bobsResponderSession);
		Assert.assertNull(bobsResponderSession.getPeerRatchet2DH());

		// Bob now sends Alice a message in the new session
		sendTextMessage(BOB_MESSAGE_1, bobContext, DummyUsers.ALICE);

		// Let Alice process the messages that she has received from Bob.
		// The decapsulated message should be the text message from Bob.
		receiveAndAssertSingleMessage(bobContext.messageQueue, aliceContext, BOB_MESSAGE_1, ForwardSecurityMode.FOURDH);
	}

	@Test
	public void testMissingMessage() throws BadMessageException, MissingPublicKeyException, ThreemaException {
		test4DH();

		// Alice now sends Bob another message, but it never arrives
		makeEncapTextMessage(ALICE_MESSAGE_3, aliceContext, DummyUsers.BOB);

		// Drop this message.

		// Alice now sends Bob another message, which arrives and should be decodable
		sendTextMessage(ALICE_MESSAGE_4, aliceContext, DummyUsers.BOB);

		// Let Bob process all the messages that he has received from Alice.
		// The decapsulated message should be the text message from Alice.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_4, ForwardSecurityMode.FOURDH);

		// At this point, Bob should not have enqueued any further messages
		Assert.assertEquals(0, bobContext.messageQueue.getQueueSize());
	}

	@Test
	public void testDataLoss() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Repeat the tests several times, as random session IDs are involved
		for (int i = 0; i < NUM_RANDOM_RUNS; i++) {
			testDataLoss1();
			testDataLoss2();
		}
	}

	private void setupDataLoss() throws MissingPublicKeyException, BadMessageException, ThreemaException {
		test4DH();

		// Check that Bob has a responder DH session that matches Alice's initiator session.
		DHSession alicesSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(alicesSession);
		DHSessionId sessionId = alicesSession.getId();
		Assert.assertNotNull(bobContext.dhSessionStore.getDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			sessionId
		));

		// Now Bob loses his session data
		bobContext.dhSessionStore.deleteDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			sessionId
		);
	}

	private void testDataLoss1() throws BadMessageException, MissingPublicKeyException, ThreemaException {
		// Data loss scenario 1: Bob loses his data, but does not send any messages until Alice
		// sends her first message after the data loss. This message gets rejected by Bob, and eventually
		// both agree on a new 4DH session.

		setupDataLoss();

		DHSession alicesBestSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(alicesBestSession);
		DHSessionId alicesSessionId = alicesBestSession.getId();

		// Alice sends another message, which Bob can't decrypt and should trigger a Reject
		AbstractMessage encapMessage = sendTextMessage(ALICE_MESSAGE_5, aliceContext, DummyUsers.BOB);

		// Let Bob process all the messages that he has received from Alice.
		List<AbstractMessage> bobsReceivedMessages = processReceivedMessages(aliceContext.messageQueue, bobContext);

		// There should be no decrypted messages
		Assert.assertEquals(0, bobsReceivedMessages.size());

		// Bob should have enqueued one FS message (a reject) to Alice.
		Assert.assertEquals(1, bobContext.messageQueue.getQueueSize());

		// Let Alice process the reject message that she has received from Bob.
		List<AbstractMessage> alicesReceivedMessages = processReceivedMessages(bobContext.messageQueue, aliceContext);

		// There should be no decrypted messages
		Assert.assertEquals(0, alicesReceivedMessages.size());

		// Alice and Bob should have deleted their mutual DH sessions.
		Assert.assertNull(aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		));
		Assert.assertNull(bobContext.dhSessionStore.getDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			alicesSessionId
		));

		// Check that the failure listener has been informed on Alice's side.
		verify(aliceContext.failureListener).notifyRejectReceived(DummyUsers.getContactForUser(DummyUsers.BOB), encapMessage.getMessageId());
	}

	private void testDataLoss2() throws BadMessageException, MissingPublicKeyException, ThreemaException {
		// Data loss scenario 2: Bob loses his data and sends a message in a new session before
		// Alice gets a chance to send one. Alice should take the Init from Bob as a hint that he
		// has lost his session data, and she should discard the existing (4DH) session.

		setupDataLoss();

		// Bob sends Alice a message, and since he doesn't have a session anymore, he starts a new one
		sendTextMessage(BOB_MESSAGE_2, bobContext, DummyUsers.ALICE);

		// Let Alice process all the messages that she has received from Bob.
		receiveAndAssertSingleMessage(bobContext.messageQueue, aliceContext, BOB_MESSAGE_2, ForwardSecurityMode.TWODH);

		// Alice should have enqueued an Accept for the new session to Bob
		Assert.assertEquals(1, aliceContext.messageQueue.getQueueSize());

		// Alice now sends a message to Bob, which should be in 4DH mode
		sendTextMessage(ALICE_MESSAGE_6, aliceContext, DummyUsers.BOB);

		// Let Bob process the messages that he has received from Alice.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_6, ForwardSecurityMode.FOURDH);

		// Alice and Bob should now each have one matching 4DH session
		DHSession alicesBestSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(alicesBestSession);
		DHSession bobsBestSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity()
		);
		Assert.assertNotNull(bobsBestSession);
		Assert.assertEquals(alicesBestSession.getId(), bobsBestSession.getId());
	}

	@Test
	public void testDowngrade() throws BadMessageException, MissingPublicKeyException, ThreemaException {
		test4DH();

		// Bob has received a 4DH message from Alice, and thus both parties should
		// not have a 2DH session anymore
		DHSession alicesSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(alicesSession);
		DHSessionId sessionId = alicesSession.getId();
		DHSession bobsSession = bobContext.dhSessionStore.getDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			sessionId
		);
		Assert.assertNotNull(bobsSession);
		Assert.assertNull(alicesSession.getMyRatchet2DH());
		Assert.assertNull(alicesSession.getPeerRatchet2DH());
		Assert.assertNull(bobsSession.getMyRatchet2DH());
		Assert.assertNull(bobsSession.getPeerRatchet2DH());
	}

	private void setupRaceCondition() throws ThreemaException {
		// Start the negotiation on Alice's side, up to the point where the Init and Message are
		// on the way to Bob, but have not been received by him yet
		startNegotiationAlice();

		// Simulate a race condition: Before Bob has received the initial messages from Alice, he
		// starts his own negotiation and sends Alice a message
		sendTextMessage(BOB_MESSAGE_2, bobContext, DummyUsers.ALICE);

		// At this point, Bob has enqueued two FS messages: Init and Message.
		Assert.assertEquals(2, bobContext.messageQueue.getQueueSize());

		// Bob should now have a (separate) 2DH session with Alice
		DHSession bobsInitiatorSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity()
		);
		DHSession alicesInitiatorSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(bobsInitiatorSession);
		Assert.assertNotNull(alicesInitiatorSession);
		Assert.assertNotEquals(alicesInitiatorSession.getId(), bobsInitiatorSession.getId());
	}

	@Test
	public void testRaceConditions() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Repeat the tests several times, as random session IDs are involved
		for (int i = 0; i < NUM_RANDOM_RUNS; i++) {
			testRaceCondition1();
			testRaceCondition2();
		}
	}

	private void testRaceCondition1() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Set up a race condition: both sides have a 2DH session, but their mutual messages have not arrived yet
		setupRaceCondition();

		// Let Alice process the messages that she has received from Bob.
		// The decapsulated message should be the 2DH text message from Bob.
		receiveAndAssertSingleMessage(bobContext.messageQueue, aliceContext, BOB_MESSAGE_2, ForwardSecurityMode.TWODH);

		// Now Bob finally gets the initial messages from Alice, after he has already started his own session.
		// The decapsulated message should be the 2DH text message from Alice.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);

		// Bob now sends another message, this time in 4DH mode using the session with the lower ID
		sendTextMessage(BOB_MESSAGE_3, bobContext, DummyUsers.ALICE);

		// Alice receives this message, it should be in 4DH mode
		receiveAndAssertSingleMessage(bobContext.messageQueue, aliceContext, BOB_MESSAGE_3, ForwardSecurityMode.FOURDH);

		// Alice also sends a message to Bob
		sendTextMessage(ALICE_MESSAGE_6, aliceContext, DummyUsers.BOB);

		// Bob receives this message, it should be in 4DH mode
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_6, ForwardSecurityMode.FOURDH);

		// Both sides should now agree on the best session
		assertSameBestSession();
	}

	private void testRaceCondition2() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Set up a race condition: both sides have a 2DH session, but their mutual messages have not arrived yet
		setupRaceCondition();

		// Let Alice process the messages that she has received from Bob.
		// The decapsulated message should be the 2DH text message from Bob.
		receiveAndAssertSingleMessage(bobContext.messageQueue, aliceContext, BOB_MESSAGE_2, ForwardSecurityMode.TWODH);

		// Alice now sends a message to Bob in 4DH mode
		sendTextMessage(ALICE_MESSAGE_6, aliceContext, DummyUsers.BOB);

		// Now Bob finally gets the initial messages from Alice, after he has already started his own session.
		// The first decapsulated message should be the 2DH text message from Alice, and the second one should be in 4DH mode.
		List<AbstractMessage> receivedMessages = processReceivedMessages(aliceContext.messageQueue, bobContext);
		Assert.assertEquals(2, receivedMessages.size());
		Assert.assertEquals(ALICE_MESSAGE_1, ((BoxTextMessage)receivedMessages.get(0)).getText());
		Assert.assertEquals(ForwardSecurityMode.TWODH, receivedMessages.get(0).getForwardSecurityMode());
		Assert.assertEquals(ALICE_MESSAGE_6, ((BoxTextMessage)receivedMessages.get(1)).getText());
		Assert.assertEquals(ForwardSecurityMode.FOURDH, receivedMessages.get(1).getForwardSecurityMode());

		// Bob now sends another message, this time in 4DH mode using the session with the lower ID
		sendTextMessage(BOB_MESSAGE_3, bobContext, DummyUsers.ALICE);

		// Alice receives this message, it should be in 4DH mode
		receiveAndAssertSingleMessage(bobContext.messageQueue, aliceContext, BOB_MESSAGE_3, ForwardSecurityMode.FOURDH);

		// Alice now sends another message, this time in 4DH mode using the session with the lower ID
		sendTextMessage(ALICE_MESSAGE_7, aliceContext, DummyUsers.BOB);

		// Bob receives this message, it should be in 4DH mode
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_7, ForwardSecurityMode.FOURDH);

		// Both sides should now agree on the best session
		assertSameBestSession();
	}

	private List<AbstractMessage> processReceivedMessages(MessageQueue sourceQueue, UserContext recipientContext) throws BadMessageException, ThreemaException, MissingPublicKeyException {
		List<AbstractMessage> decapsulatedMessages = new LinkedList<>();
		for (MessageBox box : sourceQueue.getQueue()) {
			MessageCoder messageCoder = new MessageCoder(recipientContext.contactStore, recipientContext.identityStore);
			AbstractMessage msg = messageCoder.decode(box, false);

			AbstractMessage decapMsg = recipientContext.fsmp.processEnvelopeMessage(recipientContext.contactStore.getContactForIdentity(msg.getFromIdentity()),
				(ForwardSecurityEnvelopeMessage) msg);

			if (decapMsg != null) {
				decapsulatedMessages.add(decapMsg);
			}
		}
		sourceQueue.flushQueue();
		return decapsulatedMessages;
	}

	private AbstractMessage sendTextMessage(String message, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException {
		AbstractMessage encapMessage = makeEncapTextMessage(message, senderContext, recipient);
		senderContext.messageQueue.enqueue(encapMessage);
		return encapMessage;
	}

	private void receiveAndAssertSingleMessage(MessageQueue sourceQueue, UserContext recipientContext, String expectedMessage, ForwardSecurityMode expectedMode) throws MissingPublicKeyException, BadMessageException, ThreemaException {
		List<AbstractMessage> receivedMessages = processReceivedMessages(sourceQueue, recipientContext);
		Assert.assertEquals(1, receivedMessages.size());
		Assert.assertEquals(expectedMessage, ((BoxTextMessage)receivedMessages.get(0)).getText());
		Assert.assertEquals(expectedMode, receivedMessages.get(0).getForwardSecurityMode());
	}

	private void assertSameBestSession() throws DHSessionStoreException {
		DHSession bobsInitiatorSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity()
		);
		Assert.assertNotNull(bobsInitiatorSession);

		DHSession alicesInitiatorSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(alicesInitiatorSession);

		Assert.assertEquals(alicesInitiatorSession.getId(), bobsInitiatorSession.getId());
	}

	private AbstractMessage makeEncapTextMessage(String text, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException {
		BoxTextMessage textMessage = new BoxTextMessage();
		textMessage.setText(text);
		textMessage.setToIdentity(recipient.getIdentity());
		return senderContext.fsmp.makeMessage(DummyUsers.getContactForUser(recipient), textMessage);
	}

	private UserContext makeTestUserContext(DummyUsers.User user) {
		UserContext context = new UserContext();

		context.dhSessionStore = new InMemoryDHSessionStore();
		context.contactStore = new DummyContactStore();
		context.identityStore = DummyUsers.getIdentityStoreForUser(user);
		context.messageQueue = TestHelpers.getNoopMessageQueue(context.contactStore, context.identityStore);
		context.failureListener = mock(ForwardSecurityFailureListener.class);
		context.fsmp = new ForwardSecurityMessageProcessor(context.dhSessionStore,
			context.contactStore,
			context.identityStore,
			context.messageQueue,
			context.failureListener);

		return context;
	}

	private static class UserContext {
		DHSessionStoreInterface dhSessionStore;
		ContactStore contactStore;
		IdentityStoreInterface identityStore;
		MessageQueue messageQueue;
		ForwardSecurityFailureListener failureListener;
		ForwardSecurityMessageProcessor fsmp;
	}
}
