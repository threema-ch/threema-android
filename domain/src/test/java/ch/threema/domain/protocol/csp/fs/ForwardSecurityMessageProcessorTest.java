/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.fs.KDFRatchet;
import ch.threema.domain.helpers.DummyUsers;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.BoxLocationMessage;
import ch.threema.domain.protocol.csp.messages.BoxTextMessage;
import ch.threema.domain.protocol.csp.messages.ContactDeleteProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.ContactSetProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupCreateMessage;
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotCreateMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVoteMessage;
import ch.threema.domain.protocol.csp.messages.file.FileMessage;
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.DHSessionStoreException;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.domain.stores.DummyContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.domain.stores.InMemoryDHSessionStore;
import ch.threema.domain.testhelpers.TestHelpers;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.protobuf.csp.e2e.fs.VersionRange;

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

	private void startNegotiationAlice() throws ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
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
		Assert.assertEquals(DHSession.State.L20, alicesInitiatorSession.getState());
		Assert.assertNotNull(alicesInitiatorSession.getMyRatchet2DH());
		Assert.assertNull(alicesInitiatorSession.getPeerRatchet2DH());
		Assert.assertEquals(2, alicesInitiatorSession.getMyRatchet2DH().getCounter());
	}

	@Test
	public void testNegotiationAnd2DH() throws ThreemaException, MissingPublicKeyException, BadMessageException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		// Start the negotiation on Alice's side, up to the point where the Init and Message are
		// on the way to Bob, but have not been received by him yet
		startNegotiationAlice();

		// Let Bob process all the messages that he has received from Alice.
		// The decapsulated message should be the text message from Alice.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);

		// Both should have the session now
		DHSession alicesInitiatorSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(alicesInitiatorSession);
		DHSession bobsResponderSession = bobContext.dhSessionStore.getDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			alicesInitiatorSession.getId()
		);
		Assert.assertNotNull(bobsResponderSession);

		// At this point, Bob should have enqueued one FS message: Accept
		Assert.assertEquals(1, bobContext.messageQueue.getQueueSize());

		// Let Alice process the Accept message that she has received from Bob
		List<AbstractMessage> alicesReceivedMessages = processReceivedMessages(bobContext.messageQueue, aliceContext);

		// Bob has not sent any actual message to Alice
		Assert.assertEquals(0, alicesReceivedMessages.size());

		// Alice has already discarded her 2DH ratchets.
		Assert.assertEquals(DHSession.State.RL44, alicesInitiatorSession.getState());
		Assert.assertNull(alicesInitiatorSession.getMyRatchet2DH());
		Assert.assertNull(alicesInitiatorSession.getPeerRatchet2DH());
		Assert.assertNotNull(alicesInitiatorSession.getMyRatchet4DH());
		Assert.assertNotNull(alicesInitiatorSession.getPeerRatchet4DH());

		// Bob has not received a 4DH message yet, so he still has a 2DH peer ratchet
		Assert.assertEquals(DHSession.State.R24, bobsResponderSession.getState());
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
	public void test4DH() throws MissingPublicKeyException, BadMessageException, ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		testNegotiationAnd2DH();

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
		Assert.assertEquals(DHSession.State.RL44, bobsResponderSession.getState());
		Assert.assertNull(bobsResponderSession.getPeerRatchet2DH());

		// Bob now sends Alice a message in the new session
		sendTextMessage(BOB_MESSAGE_1, bobContext, DummyUsers.ALICE);

		// Let Alice process the messages that she has received from Bob.
		// The decapsulated message should be the text message from Bob.
		receiveAndAssertSingleMessage(bobContext.messageQueue, aliceContext, BOB_MESSAGE_1, ForwardSecurityMode.FOURDH);
	}

	@Test
	public void testMissingMessage() throws BadMessageException, MissingPublicKeyException, ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		test4DH();

		// Alice now sends Bob another message, but it never arrives
		makeEncapTextMessage(ALICE_MESSAGE_3, aliceContext, DummyUsers.BOB);

		// Drop this message.

		// Alice now sends Bob another message, which arrives and should be decodable
		sendTextMessage(ALICE_MESSAGE_4, aliceContext, DummyUsers.BOB);

		// Let Bob process all the messages that he has received from Alice.
		// The decapsulated message should be the text message from Alice.
		AbstractMessage msg = processOneReceivedMessage(aliceContext.messageQueue, bobContext, 1, false);
		Assert.assertEquals(ALICE_MESSAGE_4, ((BoxTextMessage)msg).getText());
		Assert.assertEquals(ForwardSecurityMode.FOURDH, msg.getForwardSecurityMode());

		// At this point, Bob should not have enqueued any further messages
		Assert.assertEquals(0, bobContext.messageQueue.getQueueSize());
	}

	@Test
	public void testDataLoss() throws ThreemaException, MissingPublicKeyException, BadMessageException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		// Repeat the tests several times, as random session IDs are involved
		for (int i = 0; i < NUM_RANDOM_RUNS; i++) {
			testDataLoss1();
			testDataLoss2();
		}
	}

	private void setupDataLoss() throws MissingPublicKeyException, BadMessageException, ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
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

	private void testDataLoss1() throws BadMessageException, MissingPublicKeyException, ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
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

	private void testDataLoss2() throws BadMessageException, MissingPublicKeyException, ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		// Data loss scenario 2: Bob loses his data and sends a message in a new session before
		// Alice gets a chance to send one. Alice should take the Init from Bob as a hint that he
		// has lost his session data, and she should discard the existing (4DH) session.

		setupDataLoss();

		// Bob sends Alice a message, and since he doesn't have a session anymore, he starts a new one
		sendTextMessage(BOB_MESSAGE_2, bobContext, DummyUsers.ALICE);
		DHSession bobsBestSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity()
		);
		Assert.assertNotNull(bobsBestSession);
		Assert.assertEquals(DHSession.State.L20, bobsBestSession.getState());

		// Let Alice process all the messages that she has received from Bob.
		receiveAndAssertSingleMessage(bobContext.messageQueue, aliceContext, BOB_MESSAGE_2, ForwardSecurityMode.TWODH);

		// Alice should have enqueued an Accept for the new session to Bob
		DHSession alicesBestSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(alicesBestSession);
		Assert.assertEquals(1, aliceContext.messageQueue.getQueueSize());
		Assert.assertEquals(DHSession.State.R24, alicesBestSession.getState());

		// Alice now sends a message to Bob, which should be in 4DH mode
		sendTextMessage(ALICE_MESSAGE_6, aliceContext, DummyUsers.BOB);
		Assert.assertEquals(DHSession.State.R24, alicesBestSession.getState());

		// Let Bob process the messages that he has received from Alice.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_6, ForwardSecurityMode.FOURDH);
		Assert.assertEquals(DHSession.State.RL44, bobsBestSession.getState());

		// Alice and Bob should now each have one matching 4DH session
		Assert.assertEquals(alicesBestSession.getId(), bobsBestSession.getId());
	}

	@Test
	public void testDowngrade() throws BadMessageException, MissingPublicKeyException, ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
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
		Assert.assertEquals(DHSession.State.RL44, alicesSession.getState());
		Assert.assertNull(alicesSession.getMyRatchet2DH());
		Assert.assertNull(alicesSession.getPeerRatchet2DH());
		Assert.assertEquals(DHSession.State.RL44, bobsSession.getState());
		Assert.assertNull(bobsSession.getMyRatchet2DH());
		Assert.assertNull(bobsSession.getPeerRatchet2DH());
	}

	@Test
	public void testMinorVersionUpgrade() throws ThreemaException, MissingPublicKeyException, BadMessageException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		// Alice supports version 1.0 and 1.1. Bob only supports version 1.0. Later he will upgrade
		// his version to 1.1.

		// Alice starts negotiation with supported version 1.1
		startNegotiationAlice();

		// Bob handles the init while only supporting version 1.0
		setSupportedVersionRange(
			VersionRange.newBuilder()
				.setMin(Version.V1_0.getNumber())
				.setMax(Version.V1_0.getNumber())
				.build()
		);
		Assert.assertEquals(Version.V1_0.getNumber(), DHSession.SUPPORTED_VERSION_RANGE.getMax());
		// Note that Bob only processes one message, i.e. the init message. He does not yet process
		// the text message
		processOneReceivedMessage(aliceContext.messageQueue, bobContext, 0, false);

		// Alice should process the accept message now (while supporting version 1.1)
		setSupportedVersionRange(
			VersionRange.newBuilder()
				.setMin(Version.V1_0.getNumber())
				.setMax(Version.V1_1.getNumber())
				.build()
		);
		processReceivedMessages(bobContext.messageQueue, aliceContext);

		// Alice should now have initiated a session with negotiated version 1.0
		DHSession aliceSession = aliceContext.dhSessionStore.getBestDHSession(aliceContext.identityStore.getIdentity(), bobContext.identityStore.getIdentity());
		Assert.assertNotNull(aliceSession);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_0, Version.V1_0), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_1, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_0, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, aliceSession.getMinimumIncomingAppliedVersion());

		// Bob also has initiated a session with negotiated version 1.0
		DHSession bobSession = bobContext.dhSessionStore.getBestDHSession(bobContext.identityStore.getIdentity(), aliceContext.identityStore.getIdentity());
		Assert.assertNotNull(bobSession);
		Assert.assertEquals(DHSession.State.R24, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_0, Version.V1_0), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_1, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getMinimumIncomingAppliedVersion());

		// Now Bob processes the text message from Alice. Because this is still a 2DH message, Bob will not update the local/outgoing version to 1.1 yet.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);
		Assert.assertEquals(DHSession.State.R24, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_0, Version.V1_0), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_1, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getMinimumIncomingAppliedVersion());

		// Alice sends another text message, this time with 4DH.
		sendTextMessage(ALICE_MESSAGE_1, aliceContext, DummyUsers.BOB);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_0, Version.V1_0), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_1, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_0, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, aliceSession.getMinimumIncomingAppliedVersion());

		// This time, Bob will update the local/outgoing version to 1.1.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.FOURDH);
		Assert.assertEquals(DHSession.State.RL44, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_1, Version.V1_0), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_1, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_1, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getMinimumIncomingAppliedVersion());

		// Now Bob sends a message with offered and applied version 1.1.
		sendTextMessage(BOB_MESSAGE_1, bobContext, DummyUsers.ALICE);
		Assert.assertEquals(DHSession.State.RL44, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_1, Version.V1_0), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_1, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_1, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getMinimumIncomingAppliedVersion());

		// Alice processes Bob's message (where 1.1 is offered and applied). This updates both Alice's local/outgoing and remote/incoming version to 1.1 from Alice's perspective.
		receiveAndAssertSingleMessage(bobContext.messageQueue, aliceContext, BOB_MESSAGE_1, ForwardSecurityMode.FOURDH);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_1, Version.V1_1), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_1, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_1, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_1, aliceSession.getMinimumIncomingAppliedVersion());
	}

	@Test
	public void testMinorVersionUpgradeToUnknownVersion() throws ThreemaException, MissingPublicKeyException, BadMessageException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession, NoSuchFieldException, IllegalAccessException {
		// Alice and Bob support versions 1.x. Bob will later upgrade his version to 1.255.

		// Alice starts negotiation
		startNegotiationAlice();

		// Bob processes the init and the text message of alice
		processReceivedMessages(aliceContext.messageQueue, bobContext);

		// Alice should process the accept message now
		processReceivedMessages(bobContext.messageQueue, aliceContext);

		// Alice should now have initiated a session with the maximum supported version
		DHSession aliceSession = aliceContext.dhSessionStore.getBestDHSession(aliceContext.identityStore.getIdentity(), bobContext.identityStore.getIdentity());
		Assert.assertNotNull(aliceSession);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getMinimumIncomingAppliedVersion());

		// Bob also has initiated a session with the maximum supported version
		DHSession bobSession = bobContext.dhSessionStore.getBestDHSession(bobContext.identityStore.getIdentity(), aliceContext.identityStore.getIdentity());
		Assert.assertNotNull(bobSession);
		Assert.assertEquals(DHSession.State.R24, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MIN, bobSession.getMinimumIncomingAppliedVersion());

		// Alice now sends a message with offered version 0x01FF (1.255)
		ForwardSecurityEnvelopeMessage message = makeEncapTextMessage(ALICE_MESSAGE_2, aliceContext, DummyUsers.BOB);
		ForwardSecurityDataMessage data = (ForwardSecurityDataMessage) message.getData();
		Field appliedVersionField = ForwardSecurityDataMessage.class.getDeclaredField("offeredVersion");
		appliedVersionField.setAccessible(true);
		appliedVersionField.setInt(data, 0x01FF);
		aliceContext.messageQueue.enqueue(message);

		// Now Bob processes the text message from Alice. This should not fail, even if the offered
		// version is not known.
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_2, ForwardSecurityMode.FOURDH);
		Assert.assertEquals(DHSession.State.RL44, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getMinimumIncomingAppliedVersion());

		// Assert that Alice did not receive session reject.
		Assert.assertEquals(0, bobContext.messageQueue.getQueueSize());
	}

	@Test
	public void testMinorVersionDowngrade() throws ThreemaException, MissingPublicKeyException, BadMessageException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession, NoSuchFieldException, IllegalAccessException {
		// Alice and Bob support versions 1.x. Bob will later send a message with 1.0.

		// Alice starts negotiation
		startNegotiationAlice();

		// Bob processes the init and the text message of alice
		processReceivedMessages(aliceContext.messageQueue, bobContext);

		// Alice should process the accept message now
		processReceivedMessages(bobContext.messageQueue, aliceContext);

		// Alice should now have initiated a session with the maximum supported version
		DHSession aliceSession = aliceContext.dhSessionStore.getBestDHSession(aliceContext.identityStore.getIdentity(), bobContext.identityStore.getIdentity());
		Assert.assertNotNull(aliceSession);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getMinimumIncomingAppliedVersion());

		// Bob also has initiated a session with the maximum supported version
		DHSession bobSession = bobContext.dhSessionStore.getBestDHSession(bobContext.identityStore.getIdentity(), aliceContext.identityStore.getIdentity());
		Assert.assertNotNull(bobSession);
		Assert.assertEquals(DHSession.State.R24, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MIN, bobSession.getMinimumIncomingAppliedVersion());

		// Send message with applied version 0x0100 (1.0)
		ForwardSecurityEnvelopeMessage message = makeEncapTextMessage(ALICE_MESSAGE_2, aliceContext, DummyUsers.BOB);
		ForwardSecurityDataMessage data = (ForwardSecurityDataMessage) message.getData();
		Field appliedVersionField = ForwardSecurityDataMessage.class.getDeclaredField("appliedVersion");
		appliedVersionField.setAccessible(true);
		appliedVersionField.setInt(data, 0x0100);
		aliceContext.messageQueue.enqueue(message);

		// Now Bob processes the text message from Alice. Note that the message should be rejected
		// and therefore return an empty list.
		Assert.assertNull(processOneReceivedMessage(aliceContext.messageQueue, bobContext, 0, true));
		Assert.assertNull(bobContext.dhSessionStore.getBestDHSession(bobContext.identityStore.getIdentity(), aliceContext.identityStore.getIdentity()));

		// Assert that Alice did receive a session reject
		Assert.assertEquals(1, bobContext.messageQueue.getQueueSize());
		Assert.assertNull(processOneReceivedMessage(bobContext.messageQueue, aliceContext, 0, true));
		Assert.assertNull(aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity()
		));
	}

	@Test
	public void testDHSessionStates() throws ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession, ThreemaException, MissingPublicKeyException, BadMessageException {
		startNegotiationAlice();

		// Assert that Alice has a session with state L20
		DHSession aliceInitialSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(aliceInitialSession);
		Assert.assertEquals(DHSession.State.L20, aliceInitialSession.getState());

		// Bob processes the init and should now have a session in state R24
		processOneReceivedMessage(aliceContext.messageQueue, bobContext, 0, false);

		DHSession bobInitialSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity()
		);
		Assert.assertNotNull(bobInitialSession);
		Assert.assertEquals(DHSession.State.R24, bobInitialSession.getState());

		// Bob processes the text message
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);

		// Alice should now process the accept from Bob and update the state to L44
		processOneReceivedMessage(bobContext.messageQueue, aliceContext, 0, false);

		DHSession aliceFinalSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity()
		);
		Assert.assertNotNull(aliceFinalSession);
		Assert.assertEquals(DHSession.State.RL44, aliceFinalSession.getState());

		// Alice sends now again a message to Bob (with 4DH)
		sendTextMessage(ALICE_MESSAGE_2, aliceContext, DummyUsers.BOB);

		// Bob processes the text message and should update the state to R44
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_2, ForwardSecurityMode.FOURDH);

		DHSession bobFinalSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity()
		);
		Assert.assertNotNull(bobFinalSession);
		Assert.assertEquals(DHSession.State.RL44, bobFinalSession.getState());
	}

	@Test
	public void testRequiredVersionForMessageTypes() throws ThreemaException, MissingPublicKeyException, BadMessageException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		// Alice starts negotiation with supported version 1.1
		startNegotiationAlice();

		// Bob handles the init while only supporting version 1.0
		setSupportedVersionRange(
			VersionRange.newBuilder()
				.setMin(Version.V1_0.getNumber())
				.setMax(Version.V1_0.getNumber())
				.build()
		);
		Assert.assertEquals(Version.V1_0.getNumber(), DHSession.SUPPORTED_VERSION_RANGE.getMax());
		// Bob processes the messages now. First he processes the init message, and sends back an
		// accept with support for only v1.0. Then he processes Alice's text message and upgrades
		// to V1.1 (because we did not mock the announced version).
		receiveAndAssertSingleMessage(aliceContext.messageQueue, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);

		// Alice should process the accept message now (while supporting version 1.1)
		setSupportedVersionRange(
			VersionRange.newBuilder()
				.setMin(Version.V1_0.getNumber())
				.setMax(Version.V1_1.getNumber())
				.build()
		);
		processReceivedMessages(bobContext.messageQueue, aliceContext);

		// At this point, Alice has a session with negotiated version 1.0, whereas Bob has
		// negotiated version 1.1. This does not change, as long as Alice does not process any
		// message of Bob (which now all would announce version 1.1).

		// Now we check that messages that are not supported in version 1.0 are rejected by the forward security message processor
		assertMessageTypeNotSupportedForForwardSecurity(new VoipCallOfferMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new VoipCallRingingMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new VoipCallAnswerMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new VoipCallHangupMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new VoipICECandidatesMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new DeliveryReceiptMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new TypingIndicatorMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new ContactSetProfilePictureMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new ContactDeleteProfilePictureMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new ContactRequestProfilePictureMessage(), aliceContext, DummyUsers.BOB);
	}

	@Test
	public void testInitialNegotiationVersion() throws ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		// We do not have an initiated session, therefore we expect version 1.0. Therefore all
		// messages that require version 1.1 or higher should be denied by the forward security
		// message processor.
		aliceContext = makeTestUserContext(DummyUsers.ALICE);
		bobContext = makeTestUserContext(DummyUsers.BOB);

		// Add mutual contacts
		aliceContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.BOB));
		bobContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.ALICE));

		// Check that messages that require version 1.1 are rejected
		assertMessageTypeNotSupportedForForwardSecurity(new VoipCallOfferMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new VoipCallRingingMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new VoipCallAnswerMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new VoipCallHangupMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new VoipICECandidatesMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new DeliveryReceiptMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new TypingIndicatorMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new ContactSetProfilePictureMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new ContactDeleteProfilePictureMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new ContactRequestProfilePictureMessage(), aliceContext, DummyUsers.BOB);

		// Check that messages that are currently not supported to send with forward security are rejected
		assertMessageTypeNotSupportedForForwardSecurity(new GroupCreateMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new GroupFileMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeNotSupportedForForwardSecurity(new GroupRequestSyncMessage(), aliceContext, DummyUsers.BOB);

		// Check that messages that are supported starting with version 1.0 are not rejected initially
		assertMessageTypeSupportedForForwardSecurity(new BoxTextMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeSupportedForForwardSecurity(new BoxLocationMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeSupportedForForwardSecurity(new FileMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeSupportedForForwardSecurity(new BallotCreateMessage(), aliceContext, DummyUsers.BOB);
		assertMessageTypeSupportedForForwardSecurity(new BallotVoteMessage(), aliceContext, DummyUsers.BOB);
	}

	private void setupRaceCondition() throws ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
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
	public void testRaceConditions() throws ThreemaException, MissingPublicKeyException, BadMessageException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		// Repeat the tests several times, as random session IDs are involved
		for (int i = 0; i < NUM_RANDOM_RUNS; i++) {
			testRaceCondition1();
			testRaceCondition2();
		}
	}

	private void testRaceCondition1() throws ThreemaException, MissingPublicKeyException, BadMessageException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
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

	private void testRaceCondition2() throws ThreemaException, MissingPublicKeyException, BadMessageException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
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
		while (sourceQueue.getQueueSize() > 0) {
			AbstractMessage decapMsg = processOneReceivedMessage(sourceQueue, recipientContext, 0, false);
			if (decapMsg != null) {
				decapsulatedMessages.add(decapMsg);
			}
		}
		return decapsulatedMessages;
	}

	private AbstractMessage processOneReceivedMessage(
		MessageQueue sourceQueue,
		UserContext recipientContext,
		long numSkippedMessages,
		boolean shouldSessionBeDeleted
	) throws BadMessageException, ThreemaException, MissingPublicKeyException {
		MessageBox messageBox = sourceQueue.getQueue().remove(0);
		Assert.assertNotNull(messageBox);

		MessageCoder messageCoder = new MessageCoder(recipientContext.contactStore, recipientContext.identityStore);
		ForwardSecurityEnvelopeMessage msg = (ForwardSecurityEnvelopeMessage) messageCoder.decode(messageBox);

		long counterBeforeProcessing = getRatchetCounterInSession(recipientContext, msg);

		ForwardSecurityMessageProcessor.ForwardSecurityDecryptionResult result = recipientContext.fsmp.processEnvelopeMessage(
			Objects.requireNonNull(recipientContext.contactStore.getContactForIdentity(msg.getFromIdentity())),
			msg
		);

		long counterAfterProcessing = getRatchetCounterInSession(recipientContext, msg);

		if (result.peerRatchetIdentifier != null) {
			recipientContext.fsmp.commitPeerRatchet(result.peerRatchetIdentifier);
		}

		long counterAfterCommittingRatchet = getRatchetCounterInSession(recipientContext, msg);

		if (!shouldSessionBeDeleted) {
			Assert.assertEquals("Ratchet counter should be exactly increased by the number of skipped messages:", counterBeforeProcessing + numSkippedMessages, counterAfterProcessing);
		}

		if (result.peerRatchetIdentifier != null) {
			if (shouldSessionBeDeleted) {
				Assert.assertEquals("Session should be deleted:", -1, counterAfterCommittingRatchet);
			} else {
				Assert.assertEquals("Ratchet counter should be increased when turning the ratchet:", counterAfterProcessing + 1, counterAfterCommittingRatchet);
			}
		}

		return result.message;
	}

	private long getRatchetCounterInSession(@NonNull UserContext ctx, @NonNull ForwardSecurityEnvelopeMessage msg) throws DHSessionStoreException {
		if (!(msg.getData() instanceof ForwardSecurityDataMessage)) {
			return 0;
		}

		DHSession session = ctx.dhSessionStore.getDHSession(ctx.identityStore.getIdentity(), msg.getFromIdentity(), msg.getData().getSessionId());
		if (session == null) {
			return -1;
		}
		KDFRatchet ratchet = null;
		switch (((ForwardSecurityDataMessage)msg.getData()).getType()) {
			case TWODH:
				ratchet = session.getPeerRatchet2DH();
				break;
			case FOURDH:
				ratchet = session.getPeerRatchet4DH();
				break;
		}
		if (ratchet == null) {
			return -1;
		} else {
			return ratchet.getCounter();
		}
	}

	private AbstractMessage sendTextMessage(String message, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
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

	private ForwardSecurityEnvelopeMessage makeEncapTextMessage(String text, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		BoxTextMessage textMessage = new BoxTextMessage();
		textMessage.setText(text);
		textMessage.setToIdentity(recipient.getIdentity());
		return senderContext.fsmp.makeMessage(DummyUsers.getContactForUser(recipient), textMessage);
	}

	private void assertMessageTypeSupportedForForwardSecurity(AbstractMessage message, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException, ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession {
		// We mock 'getBody' to support incomplete
		AbstractMessage messageMock = Mockito.spy(message);
		Mockito.doReturn(new byte[0]).when(messageMock).getBody();
		Assert.assertNotNull(senderContext.fsmp.makeMessage(
			DummyUsers.getContactForUser(recipient),
			messageMock
		));
	}

	private void assertMessageTypeNotSupportedForForwardSecurity(AbstractMessage message, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException {
		boolean messageCreated = true;
		try {
			// We expect that this throws an exception as the message type is not supported in the
			// given session.
			senderContext.fsmp.makeMessage(DummyUsers.getContactForUser(recipient), message);
		} catch (ForwardSecurityMessageProcessor.MessageTypeNotSupportedInSession e) {
			messageCreated = false;
		}
		Assert.assertFalse(messageCreated);
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

	/**
	 * Replaces the static {@link DHSession#SUPPORTED_VERSION_RANGE} with the given range. Note that
	 * this has only an impact on the initial handshake. A client with a restricted supported
	 * version range still announces the latest minor version.
	 * Also, this method sets the version range globally, so that both Alice and Bob are affected.
	 *
	 * @param versionRange the new supported version range
	 */
	private void setSupportedVersionRange(VersionRange versionRange) {
		Whitebox.setInternalState(DHSession.class, "SUPPORTED_VERSION_RANGE", versionRange);
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
