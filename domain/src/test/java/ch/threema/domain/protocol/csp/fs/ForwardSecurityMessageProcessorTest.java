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
import org.mockito.exceptions.base.MockitoException;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.crypto.NonceStore;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.fs.KDFRatchet;
import ch.threema.domain.helpers.DummyUsers;
import ch.threema.domain.helpers.ForwardSecurityMessageProcessorWrapper;
import ch.threema.domain.helpers.InMemoryDHSessionStore;
import ch.threema.domain.helpers.ServerAckTaskCodec;
import ch.threema.domain.helpers.UnusedTaskCodec;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.EmptyMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.GroupLocationMessage;
import ch.threema.domain.protocol.csp.messages.GroupNameMessage;
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage;
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage;
import ch.threema.domain.protocol.csp.messages.GroupTextMessage;
import ch.threema.domain.protocol.csp.messages.LocationMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.TextMessage;
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage;
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage;
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollVoteMessage;
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage;
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage;
import ch.threema.domain.protocol.csp.messages.file.FileMessage;
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityData;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataAccept;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataInit;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataTerminate;
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
import ch.threema.domain.taskmanager.ActiveTaskCodec;
import ch.threema.protobuf.csp.e2e.fs.Terminate;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.protobuf.csp.e2e.fs.VersionRange;

import static ch.threema.domain.protocol.connection.ConnectionTestUtilsKt.getFromOutboundMessage;
import static ch.threema.domain.taskmanager.OutgoingCspMessageUtilsKt.toCspMessage;

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

	private final ActiveTaskCodec testCodec = new UnusedTaskCodec();

	private final NonceFactory nonceFactory = new NonceFactory(new NonceStore() {
		@Override
		public boolean exists(@NonNull byte[] nonce) {
			return false;
		}

		@Override
		public boolean store(@NonNull byte[] nonce) {
			return true;
		}

		@Override
		@NonNull
		public List<byte[]> getAllHashedNonces() {
			return Collections.emptyList();
		}
	});

	private final ForwardSecurityStatusListener forwardSecurityStatusListener = new ForwardSecurityStatusListener() {
		@Override
		public void newSessionInitiated(@NonNull DHSession session, @NonNull Contact contact) {
			// Nothing to do
		}

		@Override
		public void responderSessionEstablished(@NonNull DHSession session, @NonNull Contact contact, boolean existingSessionPreempted) {
			// Nothing to do
		}

		@Override
		public void initiatorSessionEstablished(@NonNull DHSession session, @NonNull Contact contact) {
			// Nothing to do
		}

		@Override
		public void rejectReceived(@NonNull ForwardSecurityDataReject rejectData, @NonNull Contact contact, @Nullable DHSession session, boolean hasForwardSecuritySupport) {
			// Nothing to do
		}

		@Override
		public void sessionNotFound(@NonNull DHSessionId sessionId, @NonNull Contact contact) {
			// Nothing to do
		}

		@Override
		public void sessionForMessageNotFound(@NonNull DHSessionId sessionId, @Nullable MessageId messageId, @NonNull Contact contact) {
			// Nothing to do
		}

		@Override
		public void sessionTerminated(@Nullable DHSessionId sessionId, @NonNull Contact contact, boolean sessionUnknown, boolean hasForwardSecuritySupport) {
			// Nothing to do
		}

		@Override
		public void messagesSkipped(@NonNull DHSessionId sessionId, @NonNull Contact contact, int numSkipped) {
			// Nothing to do
		}

		@Override
		public void messageOutOfOrder(@NonNull DHSessionId sessionId, @NonNull Contact contact, @Nullable MessageId messageId) {
			// Nothing to do
		}

		@Override
		public void first4DhMessageReceived(@NonNull DHSession session, @NonNull Contact contact) {
			// Nothing to do
		}

		@Override
		public void versionsUpdated(@NonNull DHSession session, @NonNull DHSession.UpdatedVersionsSnapshot versionsSnapshot, @NonNull Contact contact) {
			// Nothing to do
		}

		@Override
		public void messageWithoutFSReceived(@NonNull Contact contact, @NonNull DHSession session, @NonNull AbstractMessage message) {
			// Nothing to do
		}

		@Override
		public void postIllegalSessionState(@NonNull DHSessionId sessionId, @NonNull Contact contact) {
			// Nothing to do
		}

		@Override
		public void allSessionsTerminated(@NonNull Contact contact, @NonNull Terminate.Cause cause) {
			// Nothing to do
		}

		@Override
		public boolean hasForwardSecuritySupport(@NonNull Contact contact) {
			// Note that we currently assume that all contacts support forward security in tests
			return true;
		}

		@Override
		public void updateFeatureMask(@NonNull Contact contact) {
			// Nothing to do
		}
	};

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
		Assert.assertEquals(2, aliceContext.handle.getOutboundMessages().size());

		// Alice should now have a 2DH session with Bob
		DHSession alicesInitiatorSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity(),
			testCodec
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
	public void testNegotiationAnd2DH() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Start the negotiation on Alice's side, up to the point where the Init and Message are
		// on the way to Bob, but have not been received by him yet
		startNegotiationAlice();

		// Let Bob process all the messages that he has received from Alice.
		// The decapsulated message should be the text message from Alice.
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);

		// Both should have the session now
		DHSession alicesInitiatorSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity(),
			testCodec
		);
		Assert.assertNotNull(alicesInitiatorSession);
		DHSession bobsResponderSession = bobContext.dhSessionStore.getDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			alicesInitiatorSession.getId(),
			testCodec
		);
		Assert.assertNotNull(bobsResponderSession);

		// At this point, Bob should have enqueued one FS message: Accept
		Assert.assertEquals(1, bobContext.handle.getOutboundMessages().size());

		// Let Alice process the Accept message that she has received from Bob
		List<AbstractMessage> alicesReceivedMessages = processReceivedMessages(bobContext.handle, aliceContext);

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
	public void test4DH() throws MissingPublicKeyException, BadMessageException, ThreemaException {
		testNegotiationAnd2DH();

		// Alice now sends Bob another message, this time in 4DH mode
		sendTextMessage(ALICE_MESSAGE_2, aliceContext, DummyUsers.BOB);

		// Let Bob process all the messages that he has received from Alice.
		// The decapsulated message should be the text message from Alice.
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_2, ForwardSecurityMode.FOURDH);

		// At this point, Bob should not have enqueued any further messages
		Assert.assertEquals(0, bobContext.handle.getOutboundMessages().size());

		// Bob should have discarded his 2DH peer ratchet now
		DHSession bobsResponderSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			testCodec
		);
		Assert.assertNotNull(bobsResponderSession);
		Assert.assertEquals(DHSession.State.RL44, bobsResponderSession.getState());
		Assert.assertNull(bobsResponderSession.getPeerRatchet2DH());

		// Bob now sends Alice a message in the new session
		sendTextMessage(BOB_MESSAGE_1, bobContext, DummyUsers.ALICE);

		// Let Alice process the messages that she has received from Bob.
		// The decapsulated message should be the text message from Bob.
		receiveAndAssertSingleMessage(bobContext.handle, aliceContext, BOB_MESSAGE_1, ForwardSecurityMode.FOURDH);
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
		AbstractMessage msg = processOneReceivedMessage(aliceContext.handle, bobContext, 1, false);
		Assert.assertEquals(ALICE_MESSAGE_4, ((TextMessage)msg).getText());
		Assert.assertEquals(ForwardSecurityMode.FOURDH, msg.getForwardSecurityMode());

		// At this point, Bob should not have enqueued any further messages
		Assert.assertEquals(0, bobContext.handle.getOutboundMessages().size());
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
			DummyUsers.BOB.getIdentity(),
			testCodec
		);
		Assert.assertNotNull(alicesSession);
		DHSessionId sessionId = alicesSession.getId();
		Assert.assertNotNull(bobContext.dhSessionStore.getDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			sessionId,
			testCodec
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
			DummyUsers.BOB.getIdentity(),
			testCodec
		);
		Assert.assertNotNull(alicesBestSession);
		DHSessionId alicesSessionId = alicesBestSession.getId();

		// Alice sends another message, which Bob can't decrypt and should trigger a Reject
		sendTextMessage(ALICE_MESSAGE_5, aliceContext, DummyUsers.BOB);

		// Let Bob process all the messages that he has received from Alice.
		List<AbstractMessage> bobsReceivedMessages = processReceivedMessages(aliceContext.handle, bobContext);

		// There should be no decrypted messages
		Assert.assertEquals(0, bobsReceivedMessages.size());

		// Bob should have enqueued one FS message (a reject) to Alice.
		Assert.assertEquals(1, bobContext.handle.getOutboundMessages().size());

		// Let Alice process the reject message that she has received from Bob.
		List<AbstractMessage> alicesReceivedMessages = processReceivedMessages(bobContext.handle, aliceContext);

		// There should be no decrypted messages
		Assert.assertEquals(0, alicesReceivedMessages.size());

		// Alice and Bob should have deleted their mutual DH sessions.
		Assert.assertNull(aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity(),
			testCodec
		));
		Assert.assertNull(bobContext.dhSessionStore.getDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			alicesSessionId,
			testCodec
		));
	}

	private void testDataLoss2() throws BadMessageException, MissingPublicKeyException, ThreemaException {
		// Data loss scenario 2: Bob loses his data and sends a message in a new session before
		// Alice gets a chance to send one. Alice should take the Init from Bob as a hint that he
		// has lost his session data, and she should discard the existing (4DH) session.

		setupDataLoss();

		// Bob sends Alice a message, and since he doesn't have a session anymore, he starts a new one
		sendTextMessage(BOB_MESSAGE_2, bobContext, DummyUsers.ALICE);
		DHSession bobsBestSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			testCodec
		);
		Assert.assertNotNull(bobsBestSession);
		Assert.assertEquals(DHSession.State.L20, bobsBestSession.getState());

		// Let Alice process all the messages that she has received from Bob.
		receiveAndAssertSingleMessage(bobContext.handle, aliceContext, BOB_MESSAGE_2, ForwardSecurityMode.TWODH);

		// Alice should have enqueued an Accept for the new session to Bob
		DHSession alicesBestSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity(),
			testCodec
		);
		Assert.assertNotNull(alicesBestSession);
		Assert.assertEquals(1, aliceContext.handle.getOutboundMessages().size());
		Assert.assertEquals(DHSession.State.R24, alicesBestSession.getState());

		// Alice now sends a message to Bob, which should be in 4DH mode
		sendTextMessage(ALICE_MESSAGE_6, aliceContext, DummyUsers.BOB);
		Assert.assertEquals(DHSession.State.R24, alicesBestSession.getState());

		// Let Bob process the messages that he has received from Alice.
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_6, ForwardSecurityMode.FOURDH);
		Assert.assertEquals(DHSession.State.RL44, bobsBestSession.getState());

		// Alice and Bob should now each have one matching 4DH session
		Assert.assertEquals(alicesBestSession.getId(), bobsBestSession.getId());
	}

	@Test
	public void testDowngrade() throws BadMessageException, MissingPublicKeyException, ThreemaException {
		test4DH();

		// Bob has received a 4DH message from Alice, and thus both parties should
		// not have a 2DH session anymore
		DHSession alicesSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity(),
			testCodec
		);
		Assert.assertNotNull(alicesSession);
		DHSessionId sessionId = alicesSession.getId();
		DHSession bobsSession = bobContext.dhSessionStore.getDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			sessionId,
			testCodec
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
	public void testMinorVersionUpgrade() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Alice supports version 1.0 and 1.2. Bob only supports version 1.0. Later he will upgrade
		// his version to 1.2.

		// Alice starts negotiation with supported version 1.2
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
		processOneReceivedMessage(aliceContext.handle, bobContext, 0, false);

		// Alice should process the accept message now (while supporting version 1.2)
		setSupportedVersionRange(
			VersionRange.newBuilder()
				.setMin(Version.V1_0.getNumber())
				.setMax(Version.V1_2.getNumber())
				.build()
		);
		processReceivedMessages(bobContext.handle, aliceContext);

		// Alice should now have initiated a session with negotiated version 1.0
		DHSession aliceSession = aliceContext.dhSessionStore.getBestDHSession(aliceContext.identityStore.getIdentity(), bobContext.identityStore.getIdentity(), testCodec);
		Assert.assertNotNull(aliceSession);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_0, Version.V1_0), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_2, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_0, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, aliceSession.getMinimumIncomingAppliedVersion());

		// Bob also has initiated a session with negotiated version 1.0
		DHSession bobSession = bobContext.dhSessionStore.getBestDHSession(bobContext.identityStore.getIdentity(), aliceContext.identityStore.getIdentity(), testCodec);
		Assert.assertNotNull(bobSession);
		Assert.assertEquals(DHSession.State.R24, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_0, Version.V1_0), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_2, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getMinimumIncomingAppliedVersion());

		// Now Bob processes the text message from Alice. Because this is still a 2DH message, Bob will not update the local/outgoing version to 1.2 yet.
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);
		Assert.assertEquals(DHSession.State.R24, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_0, Version.V1_0), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_2, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getMinimumIncomingAppliedVersion());

		// Alice sends another text message, this time with 4DH.
		sendTextMessage(ALICE_MESSAGE_1, aliceContext, DummyUsers.BOB);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_0, Version.V1_0), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_2, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_0, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, aliceSession.getMinimumIncomingAppliedVersion());

		// This time, Bob will update the local/outgoing version to 1.2.
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.FOURDH);
		Assert.assertEquals(DHSession.State.RL44, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_2, Version.V1_0), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_2, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_2, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getMinimumIncomingAppliedVersion());

		// Now Bob sends a message with offered and applied version 1.2.
		sendTextMessage(BOB_MESSAGE_1, bobContext, DummyUsers.ALICE);
		Assert.assertEquals(DHSession.State.RL44, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_2, Version.V1_0), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_2, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_2, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_0, bobSession.getMinimumIncomingAppliedVersion());

		// Alice processes Bob's message (where 1.2 is offered and applied). This updates both Alice's local/outgoing and remote/incoming version to 1.2 from Alice's perspective.
		// Process the empty message first (sent due to fresh update)
		processOneReceivedMessage(bobContext.handle, aliceContext, 0, false);
		// Then process the text message
		receiveAndAssertSingleMessage(bobContext.handle, aliceContext, BOB_MESSAGE_1, ForwardSecurityMode.FOURDH);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(Version.V1_2, Version.V1_2), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(Version.V1_2, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(Version.V1_2, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(Version.V1_2, aliceSession.getMinimumIncomingAppliedVersion());
	}

	@Test
	public void testMinorVersionUpgradeToUnknownVersion() throws ThreemaException, MissingPublicKeyException, BadMessageException, NoSuchFieldException, IllegalAccessException {
		// Alice and Bob support versions 1.x. Bob will later upgrade his version to 1.255.

		// Alice starts negotiation
		startNegotiationAlice();

		// Bob processes the init and the text message of alice
		processReceivedMessages(aliceContext.handle, bobContext);

		// Alice should process the accept message now
		processReceivedMessages(bobContext.handle, aliceContext);

		// Alice should now have initiated a session with the maximum supported version
		DHSession aliceSession = aliceContext.dhSessionStore.getBestDHSession(aliceContext.identityStore.getIdentity(), bobContext.identityStore.getIdentity(), testCodec);
		Assert.assertNotNull(aliceSession);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getMinimumIncomingAppliedVersion());

		// Bob also has initiated a session with the maximum supported version
		DHSession bobSession = bobContext.dhSessionStore.getBestDHSession(bobContext.identityStore.getIdentity(), aliceContext.identityStore.getIdentity(), testCodec);
		Assert.assertNotNull(bobSession);
		Assert.assertEquals(DHSession.State.R24, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MIN, bobSession.getMinimumIncomingAppliedVersion());

		// Alice now sends a message with offered version 0x01FF (1.255)
		List<AbstractMessage> messages = makeEncapTextMessage(ALICE_MESSAGE_2, aliceContext, DummyUsers.BOB);
		ForwardSecurityEnvelopeMessage message = getEncapsulatedMessageFromOutgoingMessageList(messages);
		ForwardSecurityDataMessage data = (ForwardSecurityDataMessage) message.getData();
		Field appliedVersionField = ForwardSecurityDataMessage.class.getDeclaredField("offeredVersion");
		appliedVersionField.setAccessible(true);
		appliedVersionField.setInt(data, 0x01FF);
		aliceContext.handle.writeAsync(toCspMessage(message, aliceContext.identityStore, aliceContext.contactStore, nonceFactory, nonceFactory.next(false)));

		// Now Bob processes the text message from Alice. This should not fail, even if the offered
		// version is not known.
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_2, ForwardSecurityMode.FOURDH);
		Assert.assertEquals(DHSession.State.RL44, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getMinimumIncomingAppliedVersion());

		// Assert that Alice did not receive session reject.
		Assert.assertEquals(0, bobContext.handle.getOutboundMessages().size());
	}

	@Test
	public void testMinorVersionDowngrade() throws ThreemaException, MissingPublicKeyException, BadMessageException, NoSuchFieldException, IllegalAccessException {
		// Alice and Bob support versions 1.x. Bob will later send a message with 1.0.

		// Alice starts negotiation
		startNegotiationAlice();

		// Bob processes the init and the text message of alice
		processReceivedMessages(aliceContext.handle, bobContext);

		// Alice should process the accept message now
		processReceivedMessages(bobContext.handle, aliceContext);

		// Alice should now have initiated a session with the maximum supported version
		DHSession aliceSession = aliceContext.dhSessionStore.getBestDHSession(aliceContext.identityStore.getIdentity(), bobContext.identityStore.getIdentity(), testCodec);
		Assert.assertNotNull(aliceSession);
		Assert.assertEquals(DHSession.State.RL44, aliceSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), aliceSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, aliceSession.getMinimumIncomingAppliedVersion());

		// Bob also has initiated a session with the maximum supported version
		DHSession bobSession = bobContext.dhSessionStore.getBestDHSession(bobContext.identityStore.getIdentity(), aliceContext.identityStore.getIdentity(), testCodec);
		Assert.assertNotNull(bobSession);
		Assert.assertEquals(DHSession.State.R24, bobSession.getState());
		Assert.assertEquals(DHSession.DHVersions.restored(DHSession.SUPPORTED_VERSION_MAX, DHSession.SUPPORTED_VERSION_MAX), bobSession.getCurrent4DHVersions());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingOfferedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MAX, bobSession.getOutgoingAppliedVersion());
		Assert.assertEquals(DHSession.SUPPORTED_VERSION_MIN, bobSession.getMinimumIncomingAppliedVersion());

		// Send message with applied version 0x0100 (1.0)
		List<AbstractMessage> messages = makeEncapTextMessage(ALICE_MESSAGE_2, aliceContext, DummyUsers.BOB);
		ForwardSecurityEnvelopeMessage message = getEncapsulatedMessageFromOutgoingMessageList(messages);
		ForwardSecurityDataMessage data = (ForwardSecurityDataMessage) message.getData();
		Field appliedVersionField = ForwardSecurityDataMessage.class.getDeclaredField("appliedVersion");
		appliedVersionField.setAccessible(true);
		appliedVersionField.setInt(data, 0x0100);
		aliceContext.handle.writeAsync(toCspMessage(message, aliceContext.identityStore, aliceContext.contactStore, nonceFactory, nonceFactory.next(false)));

		// Now Bob processes the text message from Alice. Note that the message should be rejected
		// and therefore return an empty list.
		Assert.assertNull(processOneReceivedMessage(aliceContext.handle, bobContext, 0, true));
		Assert.assertNull(bobContext.dhSessionStore.getBestDHSession(bobContext.identityStore.getIdentity(), aliceContext.identityStore.getIdentity(), testCodec));

		// Assert that Alice did receive a session reject
		Assert.assertEquals(1, bobContext.handle.getOutboundMessages().size());
		Assert.assertNull(processOneReceivedMessage(bobContext.handle, aliceContext, 0, true));
		Assert.assertNull(aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity(), testCodec
		));
	}

	@Test
	public void testDHSessionStates() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		startNegotiationAlice();

		// Assert that Alice has a session with state L20
		DHSession aliceInitialSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity(), testCodec
		);
		Assert.assertNotNull(aliceInitialSession);
		Assert.assertEquals(DHSession.State.L20, aliceInitialSession.getState());

		// Bob processes the init and should now have a session in state R24
		processOneReceivedMessage(aliceContext.handle, bobContext, 0, false);

		DHSession bobInitialSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity(), testCodec
		);
		Assert.assertNotNull(bobInitialSession);
		Assert.assertEquals(DHSession.State.R24, bobInitialSession.getState());

		// Bob processes the text message
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);

		// Alice should now process the accept from Bob and update the state to L44
		processOneReceivedMessage(bobContext.handle, aliceContext, 0, false);

		DHSession aliceFinalSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity(), testCodec
		);
		Assert.assertNotNull(aliceFinalSession);
		Assert.assertEquals(DHSession.State.RL44, aliceFinalSession.getState());

		// Alice sends now again a message to Bob (with 4DH)
		sendTextMessage(ALICE_MESSAGE_2, aliceContext, DummyUsers.BOB);

		// Bob processes the text message and should update the state to R44
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_2, ForwardSecurityMode.FOURDH);

		DHSession bobFinalSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity(), testCodec
		);
		Assert.assertNotNull(bobFinalSession);
		Assert.assertEquals(DHSession.State.RL44, bobFinalSession.getState());
	}

	@Test
	public void testRequiredVersionForMessageTypes() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Alice starts negotiation with supported version 1.2
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
		// to V1.2 (because we did not mock the announced version).
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);

		// Alice should process the accept message now (while supporting version 1.2)
		setSupportedVersionRange(
			VersionRange.newBuilder()
				.setMin(Version.V1_0.getNumber())
				.setMax(Version.V1_2.getNumber())
				.build()
		);
		processReceivedMessages(bobContext.handle, aliceContext);

		// At this point, Alice has a session with negotiated version 1.0, whereas Bob has
		// negotiated version 1.2. This does not change, as long as Alice does not process any
		// message of Bob (which now all would announce version 1.2).

		// Now we check that messages that are not supported in version 1.0 are not encapsulated
		assertMessageNotEncapsulated(new VoipCallOfferMessage(), aliceContext, DummyUsers.BOB);
		assertMessageNotEncapsulated(new VoipCallRingingMessage(), aliceContext, DummyUsers.BOB);
		assertMessageNotEncapsulated(new VoipCallAnswerMessage(), aliceContext, DummyUsers.BOB);
		assertMessageNotEncapsulated(new VoipCallHangupMessage(), aliceContext, DummyUsers.BOB);
		assertMessageNotEncapsulated(new VoipICECandidatesMessage(), aliceContext, DummyUsers.BOB);
		assertMessageNotEncapsulated(new DeliveryReceiptMessage(), aliceContext, DummyUsers.BOB);
		assertMessageNotEncapsulated(new TypingIndicatorMessage(), aliceContext, DummyUsers.BOB);
		assertMessageNotEncapsulated(new SetProfilePictureMessage(), aliceContext, DummyUsers.BOB);
		assertMessageNotEncapsulated(new DeleteProfilePictureMessage(), aliceContext, DummyUsers.BOB);
		assertMessageNotEncapsulated(new ContactRequestProfilePictureMessage(), aliceContext, DummyUsers.BOB);
	}

	@Test
	public void testInitialNegotiationVersion() {
		// Test that fs 1.0 messages are encapsulated when sent without an existing session
		List.of(
			new TextMessage(),
			new LocationMessage(),
			new FileMessage(),
			new PollSetupMessage(),
			new PollVoteMessage(),
			new EmptyMessage()
		).forEach( fs1_0_message -> {
			// We do not have an initiated session, therefore we expect version 1.0. All messages
			// that are supported in version 1.0 should be encapsulated.
			aliceContext = makeTestUserContext(DummyUsers.ALICE);
			bobContext = makeTestUserContext(DummyUsers.BOB);

			// Add mutual contacts
			aliceContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.BOB));
			bobContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.ALICE));

			// Check that messages supporting version 1.0 are encapsulated even if no session exists
			try {
				assertNewSessionMessageEncapsulated(fs1_0_message, aliceContext, DummyUsers.BOB);
			} catch (ThreemaException e) {
				Assert.fail(e.getMessage());
			}
		});

		// Test that a new session is created when a message that requires version 1.1 is sent
		// without an existing session
		List.of(
			new VoipCallOfferMessage(),
			new VoipCallRingingMessage(),
			new VoipCallAnswerMessage(),
			new VoipCallHangupMessage(),
			new VoipICECandidatesMessage(),
			new DeliveryReceiptMessage(),
			new TypingIndicatorMessage(),
			new SetProfilePictureMessage(),
			new DeleteProfilePictureMessage(),
			new ContactRequestProfilePictureMessage()
		).forEach( fs1_1_message -> {
			// We do not have an initiated session, therefore we expect version 1.0. All messages
			// that require version 1.1 or higher should be sent without fs encapsulation.
			aliceContext = makeTestUserContext(DummyUsers.ALICE);
			bobContext = makeTestUserContext(DummyUsers.BOB);

			// Add mutual contacts
			aliceContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.BOB));
			bobContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.ALICE));

			// Check that messages that require version 1.1 aren't encapsulated
			try {
				assertNewSessionMessageNotEncapsulated(fs1_1_message, aliceContext, DummyUsers.BOB);
			} catch (ThreemaException e) {
				Assert.fail(e.getMessage());
			}
		});

		// Test that a new session is created when a message that requires version 1.2 is sent
		// without an existing session
		List.of(
			new GroupSetupMessage(),
			new GroupNameMessage(),
			new GroupSetProfilePictureMessage(),
			new GroupDeleteProfilePictureMessage(),
			new GroupTextMessage(),
			new GroupLocationMessage(),
			new GroupFileMessage(),
			new GroupPollSetupMessage(),
			new GroupPollVoteMessage(),
			new GroupSyncRequestMessage()
		).forEach( fs1_2_message -> {
			// We do not have an initiated session, therefore we expect version 1.0. All messages
			// that require version 1.1 or higher should be sent without fs encapsulation.
			aliceContext = makeTestUserContext(DummyUsers.ALICE);
			bobContext = makeTestUserContext(DummyUsers.BOB);

			// Add mutual contacts
			aliceContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.BOB));
			bobContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.ALICE));

			// Check that messages that require version 1.2 aren't encapsulated
			try {
				assertNewSessionMessageNotEncapsulated(fs1_2_message, aliceContext, DummyUsers.BOB);
			} catch (ThreemaException e) {
				Assert.fail(e.getMessage());
			}
		});
	}

	@Test
	public void testEmptyMessageCreation() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// This creates a session
		startNegotiationAlice();

		// Process the init and initial text message from Alice
		processReceivedMessages(aliceContext.handle, bobContext);

		// Assert that Alice has created a session
		DHSession session = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity(), aliceContext.handle
		);
		Assert.assertNotNull(session);

		// Set the last outgoing message to 25 hours before now, so that an empty message must be
		// created. Note that this works without explicitly storing the session, as the sessions are
		// kept in memory in these tests.
		session.setLastOutgoingMessageTimestamp(new Date().getTime() - 25 * 60 * 60 * 1000);

		// Send a group text message to Bob. Note that the session exists but is not fresh anymore.
		// As the group message requires FS 1.2, it is sent without FS. Therefore, an empty message
		// will be required to ensure session freshness.
		GroupTextMessage textMessage = new GroupTextMessage();
		textMessage.setText(ALICE_MESSAGE_2);
		textMessage.setToIdentity(DummyUsers.BOB.getIdentity());
		// Set the (non-sense) group identity
		textMessage.setGroupCreator(DummyUsers.ALICE.getIdentity());
		textMessage.setApiGroupId(new GroupId(0));

		ForwardSecurityEncryptionResult encryptionResult = aliceContext.fsmp.makeMessage(
			DummyUsers.getContactForUser(DummyUsers.BOB),
			textMessage,
			aliceContext.handle
		);

		List<AbstractMessage> outgoingMessages = encryptionResult.getOutgoingMessages();
		Assert.assertEquals(2, outgoingMessages.size());

		// Assert that the first message is sent with FS
		Assert.assertTrue(outgoingMessages.get(0) instanceof ForwardSecurityEnvelopeMessage);

		// Assert that the second message contains the group message
		Assert.assertEquals(textMessage, outgoingMessages.get(1));

		for (AbstractMessage message : outgoingMessages) {
			aliceContext.handle.writeAsync(
				toCspMessage(message,
					aliceContext.identityStore,
					aliceContext.contactStore,
					nonceFactory,
					nonceFactory.next(false))
			);
		}

		// Assert that the first received message is an empty message
		Assert.assertTrue(processOneReceivedMessage(
			aliceContext.handle, bobContext, 0, false
		) instanceof EmptyMessage);

		// Note that checking the second message is not necessary, as we have done this just after
		// encapsulating the messages.
	}

	@Test
	public void testNoAdditionalMessageCreation() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// This creates a session
		startNegotiationAlice();

		// Process the init and initial text message from alice
		processReceivedMessages(aliceContext.handle, bobContext);

		// Send text message to Bob. Note that the session exists and is fresh. Therefore, no init
		// or empty message must be created.
		TextMessage textMessage = new TextMessage();
		textMessage.setText(ALICE_MESSAGE_2);
		textMessage.setToIdentity(DummyUsers.BOB.getIdentity());

		ForwardSecurityEncryptionResult encryptionResult = aliceContext.fsmp.makeMessage(
			DummyUsers.getContactForUser(DummyUsers.BOB),
			textMessage,
			aliceContext.handle
		);

		List<AbstractMessage> outgoingMessages = encryptionResult.getOutgoingMessages();
		Assert.assertEquals(1, outgoingMessages.size());

		aliceContext.handle.writeAsync(
			toCspMessage(outgoingMessages.get(0),
				aliceContext.identityStore,
				aliceContext.contactStore,
				nonceFactory,
				nonceFactory.next(false))
		);

		TextMessage receivedMessage = (TextMessage) processOneReceivedMessage(
			aliceContext.handle, bobContext, 0, false
		);

		Assert.assertEquals(textMessage.getText(), receivedMessage.getText());
	}

	@Test
	public void testFsRefreshStepsWithoutSession() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Initialize Alice and Bob contexts without any fs session
		aliceContext = makeTestUserContext(DummyUsers.ALICE);
		bobContext = makeTestUserContext(DummyUsers.BOB);

		// Add mutual contacts
		aliceContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.BOB));
		bobContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.ALICE));

		// Run fs refresh steps for Alice's contact Bob. Note that this should create an init
		// message (and a session) as there was no session before.
		aliceContext.fsmp.runFsRefreshSteps(DummyUsers.getContactForUser(DummyUsers.BOB), aliceContext.handle);

		// Assert that only one message has been created
		Assert.assertEquals(1, aliceContext.handle.getOutboundMessages().size());

		// Process the init. Note that the resulting abstract message should be null.
		AbstractMessage receivedMessage = processOneReceivedMessage(aliceContext.handle, bobContext, 0, false);
		Assert.assertNull(receivedMessage);

		// Assert that a session has been established
		DHSession aliceSession = aliceContext.dhSessionStore.getBestDHSession(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity(), testCodec);
		DHSession bobSession = bobContext.dhSessionStore.getBestDHSession(DummyUsers.BOB.getIdentity(), DummyUsers.ALICE.getIdentity(), testCodec);
		Assert.assertNotNull(aliceSession);
		Assert.assertNotNull(bobSession);
		Assert.assertArrayEquals(aliceSession.getId().get(), bobSession.getId().get());
	}

	@Test
	public void testFsRefreshStepsWithExistingSession() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Initialize a session between Alice and Bob
		startNegotiationAlice();

		// Let Bob process the messages (to clean up Alice's outgoing queue)
		processReceivedMessages(aliceContext.handle, bobContext);

		// Get the initial session's last outgoing fs message timestamp
		DHSession initialAliceSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity(), testCodec
		);
		Assert.assertNotNull(initialAliceSession);
		long lastOutgoingFsMessage = initialAliceSession.getLastOutgoingMessageTimestamp();

		// Run fs refresh steps for Alice's contact Bob. Note that this should create an empty
		// message as there was a session before.
		aliceContext.fsmp.runFsRefreshSteps(DummyUsers.getContactForUser(DummyUsers.BOB), aliceContext.handle);

		// Assert that only one message has been created
		Assert.assertEquals(1, aliceContext.handle.getOutboundMessages().size());

		// Process the empty message
		AbstractMessage receivedMessage = processOneReceivedMessage(aliceContext.handle, bobContext, 0, false);
		Assert.assertTrue(receivedMessage instanceof EmptyMessage);

		// Assert that a session has a newer outgoing message timestamp
		DHSession aliceSession = aliceContext.dhSessionStore.getBestDHSession(DummyUsers.ALICE.getIdentity(), DummyUsers.BOB.getIdentity(), testCodec);
		Assert.assertNotNull(aliceSession);
		Assert.assertTrue(aliceSession.getLastOutgoingMessageTimestamp() > lastOutgoingFsMessage);
	}

	@Test
	public void testTerminateUnknownSession() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Test clearance of all sessions of up to 5 existing sessions
		for (int i = 0; i < 5; i++) {
			assertInitAfterTerminate(Terminate.Cause.UNKNOWN_SESSION, i);
		}
	}

	@Test
	public void testTerminateReset() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Test clearance of all sessions of up to 5 existing sessions
		for (int i = 0; i < 5; i++) {
			assertInitAfterTerminate(Terminate.Cause.RESET, i);
		}
	}

	@Test
	public void testTerminateDisabledByLocal() throws MissingPublicKeyException, BadMessageException, ThreemaException {
		// Test clearance of all sessions of up to 5 existing sessions
		for (int i = 0; i < 5; i++) {
			assertNoInitAfterTerminate(Terminate.Cause.DISABLED_BY_LOCAL, i);
		}
	}

	@Test
	public void testTerminateDisabledByRemote() throws MissingPublicKeyException, BadMessageException, ThreemaException {
		// Test clearance of all sessions of up to 5 existing sessions
		for (int i = 0; i < 5; i++) {
			assertNoInitAfterTerminate(Terminate.Cause.DISABLED_BY_REMOTE, i);
		}
	}

	@Test
	public void testTerminationOfUnknownSessionVersions() throws ThreemaException, BadMessageException, MissingPublicKeyException {
		// Create session between Alice and Bob
		startNegotiationAlice();
		Contact alice = DummyUsers.getContactForUser(DummyUsers.ALICE);
		Contact bob = DummyUsers.getContactForUser(DummyUsers.BOB);
		aliceContext.contactStore.addContact(bob);
		bobContext.contactStore.addContact(alice);

		processReceivedMessages(aliceContext.handle, bobContext);
		Assert.assertNotNull(bobContext.dhSessionStore.getBestDHSession(bob.getIdentity(), alice.getIdentity(), testCodec));

		// Process the accept message from Bob, so that Alice's session ends up in RL44 state
		processReceivedMessages(bobContext.handle, aliceContext);

		// Get the session and update it with an invalid negotiated version
		DHSession session = aliceContext.dhSessionStore.getBestDHSession(alice.getIdentity(), bob.getIdentity(), testCodec);
		Assert.assertNotNull(session);
		DHSession invalidSession = new DHSession(
			session.getId(),
			session.getMyIdentity(),
			session.getPeerIdentity(),
			session.getMyEphemeralPrivateKey(),
			session.getMyEphemeralPublicKey(),
			null,
			session.getLastOutgoingMessageTimestamp(),
			session.getMyRatchet2DH(),
			session.getMyRatchet4DH(),
			session.getPeerRatchet2DH(),
			session.getPeerRatchet4DH()
		);

		// Store the invalid session
		aliceContext.dhSessionStore.storeDHSession(invalidSession);

		// Assert that initially no message has been sent
		Assert.assertTrue(aliceContext.handle.getOutboundMessages().isEmpty());

		// Terminate the sessions
		aliceContext.fsmp.terminateAllInvalidSessions(bob, aliceContext.handle);

		// Assert that there are two messages and that Alice has a new (different) session now
		Assert.assertEquals(2, aliceContext.handle.getOutboundMessages().size());
		DHSession newSessionAlice = aliceContext.dhSessionStore.getBestDHSession(alice.getIdentity(), bob.getIdentity(), testCodec);
		Assert.assertNotNull(newSessionAlice);
		Assert.assertNotEquals(invalidSession.getId().toString(), newSessionAlice.getId().toString());

		// Process the terminate message and assert that the session has been deleted
		processOneReceivedMessage(aliceContext.handle, bobContext, 0, true);
		Assert.assertNull(bobContext.dhSessionStore.getBestDHSession(bob.getIdentity(), alice.getIdentity(), testCodec));

		// Process the init message and assert that the new session has been created
		processOneReceivedMessage(aliceContext.handle, bobContext, 0 , false);
		DHSession newSessionBob = bobContext.dhSessionStore.getBestDHSession(bob.getIdentity(), alice.getIdentity(), testCodec);
		Assert.assertNotNull(newSessionBob);
		Assert.assertEquals(newSessionAlice.getId().toString(), newSessionBob.getId().toString());
	}

	@Test
	public void testNoTerminationOfKnownSessionVersions() throws ThreemaException, BadMessageException, MissingPublicKeyException {
		// Create session between Alice and Bob
		startNegotiationAlice();
		Contact alice = DummyUsers.getContactForUser(DummyUsers.ALICE);
		Contact bob = DummyUsers.getContactForUser(DummyUsers.BOB);
		aliceContext.contactStore.addContact(bob);
		bobContext.contactStore.addContact(alice);

		// Assert that Alice has created a session
		DHSession initialSession = aliceContext.dhSessionStore.getBestDHSession(alice.getIdentity(), bob.getIdentity(), testCodec);
		Assert.assertNotNull(initialSession);

		// Let Bob process it and assert that the session exists now also for Bob
		processReceivedMessages(aliceContext.handle, bobContext);
		DHSession initialBobSession = bobContext.dhSessionStore.getBestDHSession(bob.getIdentity(), alice.getIdentity(), testCodec);
		Assert.assertNotNull(initialBobSession);
		Assert.assertEquals(initialSession.getId().toString(), initialBobSession.getId().toString());

		// Assert that initially no message has been sent
		Assert.assertTrue(aliceContext.handle.getOutboundMessages().isEmpty());

		// Terminate the invalid sessions (no invalid session available!)
		aliceContext.fsmp.terminateAllInvalidSessions(bob, aliceContext.handle);

		// Assert that there are no messages sent and that Alice still has the same session
		Assert.assertTrue(aliceContext.handle.getOutboundMessages().isEmpty());
		DHSession newAliceSession = aliceContext.dhSessionStore.getBestDHSession(alice.getIdentity(), bob.getIdentity(), testCodec);
		Assert.assertNotNull(newAliceSession);
		Assert.assertEquals(initialSession.getId().toString(), newAliceSession.getId().toString());

		// Alice now processes the accept message from Bob
		processOneReceivedMessage(bobContext.handle, aliceContext, 0, false);
		// Assert that the session still exists for Alice and it now includes 4DH versions
		newAliceSession = aliceContext.dhSessionStore.getBestDHSession(alice.getIdentity(), bob.getIdentity(), testCodec);
		Assert.assertNotNull(newAliceSession);
		Assert.assertNotNull(newAliceSession.getCurrent4DHVersions());

		// Terminate again all invalid sessions (still no invalid session expected)
		aliceContext.fsmp.terminateAllInvalidSessions(bob, aliceContext.handle);
		// Assert no message has been sent because of the termination
		Assert.assertTrue(aliceContext.handle.getOutboundMessages().isEmpty());
		// Assert that the session still exists and matches the initial session
		newAliceSession = aliceContext.dhSessionStore.getBestDHSession(alice.getIdentity(), bob.getIdentity(), testCodec);
		Assert.assertNotNull(newAliceSession);
		Assert.assertEquals(initialSession.getId().toString(), newAliceSession.getId().toString());
	}

	private void assertInitAfterTerminate(Terminate.Cause cause, int numExistingSessions) throws ThreemaException, MissingPublicKeyException, BadMessageException {
		if (numExistingSessions > 0) {
			// Initiate a session between Alice and Bob
			startNegotiationAlice();
			processReceivedMessages(aliceContext.handle, bobContext);
		} else {
			// Do not initiate any session if the number o existing sessions should be zero
			aliceContext = makeTestUserContext(DummyUsers.ALICE);
			bobContext = makeTestUserContext(DummyUsers.BOB);

			// Add mutual contacts
			aliceContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.BOB));
			bobContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.ALICE));
		}

		Contact bob = Objects.requireNonNull(
			aliceContext.contactStore.getContactForIdentity(bobContext.identityStore.getIdentity())
		);

		// Now create some unused sessions to test that all sessions are being terminated
		for (int i = 0; i < numExistingSessions - 1; i++) {
			aliceContext.dhSessionStore.storeDHSession(new DHSession(bob, aliceContext.identityStore));
		}

		String aliceIdentity = aliceContext.identityStore.getIdentity();
		String bobIdentity = bobContext.identityStore.getIdentity();

		// Alice now sends a terminate with the given cause
		aliceContext.fsmp.clearAndTerminateAllSessions(bob, cause, aliceContext.handle);

		if (numExistingSessions > 0) {
			// Assert that there is still a session, as Bob did not yet process all the terminates
			Assert.assertNotNull(bobContext.dhSessionStore.getBestDHSession(bobIdentity, aliceIdentity, testCodec));
		}

		// Process all the terminate messages
		for (int i = 0; i < numExistingSessions; i++) {
			processOneReceivedMessage(aliceContext.handle, bobContext, 0, true);
		}

		// Assert that there is no session anymore, as the terminate messages have been processed or
		// there was no session initially
		Assert.assertNull(bobContext.dhSessionStore.getBestDHSession(bobIdentity, aliceIdentity, testCodec));

		if (numExistingSessions > 0) {
			// Assert that there is still a message (init) left
			Assert.assertFalse(aliceContext.handle.getOutboundMessages().isEmpty());

			// Process the last message (should be an init)
			processOneReceivedMessage(aliceContext.handle, bobContext, 0, false);

			// Assert that there is a session again
			Assert.assertNotNull(bobContext.dhSessionStore.getBestDHSession(bobIdentity, aliceIdentity, testCodec));
		}

		// Assert that no outgoing message of Alice is left
		Assert.assertTrue(aliceContext.handle.getOutboundMessages().isEmpty());
	}

	private void assertNoInitAfterTerminate(Terminate.Cause cause, int numExistingSessions) throws ThreemaException, MissingPublicKeyException, BadMessageException {
		if (numExistingSessions > 0) {
			// Initiate a session between Alice and Bob
			startNegotiationAlice();
			processReceivedMessages(aliceContext.handle, bobContext);
		} else {
			// Do not initiate any session if the number o existing sessions should be zero
			aliceContext = makeTestUserContext(DummyUsers.ALICE);
			bobContext = makeTestUserContext(DummyUsers.BOB);

			// Add mutual contacts
			aliceContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.BOB));
			bobContext.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.ALICE));
		}

		String aliceIdentity = aliceContext.identityStore.getIdentity();
		String bobIdentity = bobContext.identityStore.getIdentity();

		Contact bob = Objects.requireNonNull(
			aliceContext.contactStore.getContactForIdentity(bobContext.identityStore.getIdentity())
		);

		// Now create some unused sessions to test that all sessions are being terminated
		for (int i = 0; i < numExistingSessions - 1; i++) {
			aliceContext.dhSessionStore.storeDHSession(new DHSession(bob, aliceContext.identityStore));
		}

		// Alice now sends a terminate with the given cause
		aliceContext.fsmp.clearAndTerminateAllSessions(bob, cause, aliceContext.handle);

		if (numExistingSessions > 0) {
			// Assert that there is still a session, as Bob did not yet process the terminate messages
			Assert.assertNotNull(bobContext.dhSessionStore.getBestDHSession(bobIdentity, aliceIdentity, testCodec));
		}

		// Process all the terminate messages
		for (int i = 0; i < numExistingSessions; i++) {
			processOneReceivedMessage(aliceContext.handle, bobContext, 0, true);
		}

		// Assert that there is no session anymore, as a terminate has been sent
		Assert.assertNull(bobContext.dhSessionStore.getBestDHSession(bobIdentity, aliceIdentity, testCodec));

		// Assert that there is no message left (this means no init has been sent by Alice)
		Assert.assertTrue(aliceContext.handle.getOutboundMessages().isEmpty());
	}

	private void setupRaceCondition() throws ThreemaException {
		// Start the negotiation on Alice's side, up to the point where the Init and Message are
		// on the way to Bob, but have not been received by him yet
		startNegotiationAlice();

		// Simulate a race condition: Before Bob has received the initial messages from Alice, he
		// starts his own negotiation and sends Alice a message
		sendTextMessage(BOB_MESSAGE_2, bobContext, DummyUsers.ALICE);

		// At this point, Bob has enqueued two FS messages: Init and Message.
		Assert.assertEquals(2, bobContext.handle.getOutboundMessages().size());

		// Bob should now have a (separate) 2DH session with Alice
		DHSession bobsInitiatorSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			testCodec
		);
		DHSession alicesInitiatorSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity(),
			testCodec
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
		receiveAndAssertSingleMessage(bobContext.handle, aliceContext, BOB_MESSAGE_2, ForwardSecurityMode.TWODH);

		// Now Bob finally gets the initial messages from Alice, after he has already started his own session.
		// The decapsulated message should be the 2DH text message from Alice.
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_1, ForwardSecurityMode.TWODH);

		// Bob now sends another message, this time in 4DH mode using the session with the lower ID
		sendTextMessage(BOB_MESSAGE_3, bobContext, DummyUsers.ALICE);

		// Alice receives this message, it should be in 4DH mode
		receiveAndAssertSingleMessage(bobContext.handle, aliceContext, BOB_MESSAGE_3, ForwardSecurityMode.FOURDH);

		// Alice also sends a message to Bob
		sendTextMessage(ALICE_MESSAGE_6, aliceContext, DummyUsers.BOB);

		// Bob receives this message, it should be in 4DH mode
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_6, ForwardSecurityMode.FOURDH);

		// Both sides should now agree on the best session
		assertSameBestSession();
	}

	private void testRaceCondition2() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		// Set up a race condition: both sides have a 2DH session, but their mutual messages have not arrived yet
		setupRaceCondition();

		// Let Alice process the messages that she has received from Bob.
		// The decapsulated message should be the 2DH text message from Bob.
		receiveAndAssertSingleMessage(bobContext.handle, aliceContext, BOB_MESSAGE_2, ForwardSecurityMode.TWODH);

		// Alice now sends a message to Bob in 4DH mode
		sendTextMessage(ALICE_MESSAGE_6, aliceContext, DummyUsers.BOB);

		// Now Bob finally gets the initial messages from Alice, after he has already started his own session.
		// The first decapsulated message should be the 2DH text message from Alice, and the second one should be in 4DH mode.
		List<AbstractMessage> receivedMessages = processReceivedMessages(aliceContext.handle, bobContext);
		Assert.assertEquals(2, receivedMessages.size());
		Assert.assertEquals(ALICE_MESSAGE_1, ((TextMessage)receivedMessages.get(0)).getText());
		Assert.assertEquals(ForwardSecurityMode.TWODH, receivedMessages.get(0).getForwardSecurityMode());
		Assert.assertEquals(ALICE_MESSAGE_6, ((TextMessage)receivedMessages.get(1)).getText());
		Assert.assertEquals(ForwardSecurityMode.FOURDH, receivedMessages.get(1).getForwardSecurityMode());

		// Bob now sends another message, this time in 4DH mode using the session with the lower ID
		sendTextMessage(BOB_MESSAGE_3, bobContext, DummyUsers.ALICE);

		// Alice receives this message, it should be in 4DH mode
		receiveAndAssertSingleMessage(bobContext.handle, aliceContext, BOB_MESSAGE_3, ForwardSecurityMode.FOURDH);

		// Alice now sends another message, this time in 4DH mode using the session with the lower ID
		sendTextMessage(ALICE_MESSAGE_7, aliceContext, DummyUsers.BOB);

		// Bob receives this message, it should be in 4DH mode
		receiveAndAssertSingleMessage(aliceContext.handle, bobContext, ALICE_MESSAGE_7, ForwardSecurityMode.FOURDH);

		// Both sides should now agree on the best session
		assertSameBestSession();
	}

	private List<AbstractMessage> processReceivedMessages(ServerAckTaskCodec sourceHandle, UserContext recipientContext) throws BadMessageException, ThreemaException, MissingPublicKeyException {
		List<AbstractMessage> decapsulatedMessages = new LinkedList<>();
		while (sourceHandle.getOutboundMessages().size() > 0) {
			AbstractMessage decapMsg = processOneReceivedMessage(sourceHandle, recipientContext, 0, false);
			if (decapMsg != null) {
				decapsulatedMessages.add(decapMsg);
			}
		}
		return decapsulatedMessages;
	}

	private AbstractMessage processOneReceivedMessage(
		ServerAckTaskCodec sourceHandle,
		UserContext recipientContext,
		long numSkippedMessages,
		boolean shouldSessionBeDeleted
	) throws BadMessageException, ThreemaException, MissingPublicKeyException {
		MessageBox messageBox = getFromOutboundMessage(sourceHandle.getOutboundMessages().remove(0));
		Assert.assertNotNull(messageBox);

		MessageCoder messageCoder = new MessageCoder(recipientContext.contactStore, recipientContext.identityStore);
		ForwardSecurityEnvelopeMessage msg = (ForwardSecurityEnvelopeMessage) messageCoder.decode(messageBox);

		long counterBeforeProcessing = getRatchetCounterInSession(recipientContext, msg);

		ForwardSecurityDecryptionResult result = processMessage(
			recipientContext.fsmp,
			Objects.requireNonNull(recipientContext.contactStore.getContactForIdentity(msg.getFromIdentity())),
			msg,
			recipientContext.handle
		);

		long counterAfterProcessing = getRatchetCounterInSession(recipientContext, msg);

		if (result.getPeerRatchetIdentifier() != null) {
			recipientContext.fsmp.commitPeerRatchet(result.getPeerRatchetIdentifier(), recipientContext.handle);
		}

		long counterAfterCommittingRatchet = getRatchetCounterInSession(recipientContext, msg);

		if (!shouldSessionBeDeleted) {
			Assert.assertEquals("Ratchet counter should be exactly increased by the number of skipped messages:", counterBeforeProcessing + numSkippedMessages, counterAfterProcessing);
		}

		if (result.getPeerRatchetIdentifier() != null) {
			if (shouldSessionBeDeleted) {
				Assert.assertEquals("Session should be deleted:", -1, counterAfterCommittingRatchet);
			} else {
				Assert.assertEquals("Ratchet counter should be increased when turning the ratchet:", counterAfterProcessing + 1, counterAfterCommittingRatchet);
			}
		}

		return result.getMessage();
	}

	private long getRatchetCounterInSession(@NonNull UserContext ctx, @NonNull ForwardSecurityEnvelopeMessage msg) throws DHSessionStoreException {
		if (!(msg.getData() instanceof ForwardSecurityDataMessage)) {
			return 0;
		}

		DHSession session = ctx.dhSessionStore.getDHSession(ctx.identityStore.getIdentity(), msg.getFromIdentity(), msg.getData().getSessionId(), testCodec);
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

	private AbstractMessage sendTextMessage(String text, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException {
		List<AbstractMessage> messages = makeEncapTextMessage(text, senderContext, recipient);
		for (AbstractMessage message : messages) {
			senderContext.handle.writeAsync(toCspMessage(message, senderContext.identityStore, senderContext.contactStore, nonceFactory, nonceFactory.next(false)));
		}
		return getEncapsulatedMessageFromOutgoingMessageList(messages);
	}

	private void receiveAndAssertSingleMessage(ServerAckTaskCodec sourceHandle, UserContext recipientContext, String expectedMessage, ForwardSecurityMode expectedMode) throws MissingPublicKeyException, BadMessageException, ThreemaException {
		List<AbstractMessage> receivedMessages = processReceivedMessages(sourceHandle, recipientContext);
		Assert.assertEquals(1, receivedMessages.size());
		Assert.assertEquals(expectedMessage, ((TextMessage)receivedMessages.get(0)).getText());
		Assert.assertEquals(expectedMode, receivedMessages.get(0).getForwardSecurityMode());
	}

	private void assertSameBestSession() throws DHSessionStoreException {
		DHSession bobsInitiatorSession = bobContext.dhSessionStore.getBestDHSession(
			DummyUsers.BOB.getIdentity(),
			DummyUsers.ALICE.getIdentity(),
			testCodec
		);
		Assert.assertNotNull(bobsInitiatorSession);

		DHSession alicesInitiatorSession = aliceContext.dhSessionStore.getBestDHSession(
			DummyUsers.ALICE.getIdentity(),
			DummyUsers.BOB.getIdentity(),
			testCodec
		);
		Assert.assertNotNull(alicesInitiatorSession);

		Assert.assertEquals(alicesInitiatorSession.getId(), bobsInitiatorSession.getId());
	}

	private List<AbstractMessage> makeEncapTextMessage(String text, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException {
		TextMessage textMessage = new TextMessage();
		textMessage.setText(text);
		textMessage.setToIdentity(recipient.getIdentity());
		ForwardSecurityEncryptionResult result = senderContext.fsmp.makeMessage(DummyUsers.getContactForUser(recipient), textMessage, senderContext.handle);
		senderContext.fsmp.commitSessionState(result);
		List<AbstractMessage> outgoingMessages = result.getOutgoingMessages();
		for (AbstractMessage message : outgoingMessages) {
			message.setToIdentity(recipient.getIdentity());
		}
		return outgoingMessages;
	}

	private void assertNewSessionMessageEncapsulated(AbstractMessage message, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException {
		// We mock 'getBody' to support encapsulating 'invalid' messages. This simplifies message
		// creation in the tests.
		AbstractMessage messageMock;
		try {
			messageMock = Mockito.spy(message);
			Mockito.doReturn(new byte[0]).when(messageMock).getBody();
		} catch (MockitoException exception) {
			// If mocking did fail, we try without mocking. Note that sending the message without
			// mocking usually only works for the empty message.
			messageMock = message;
		}
		ForwardSecurityEncryptionResult result = senderContext.fsmp.makeMessage(
			DummyUsers.getContactForUser(recipient),
			messageMock,
			senderContext.handle
		);

		List<AbstractMessage> messages = result.getOutgoingMessages();

		Assert.assertEquals(2, messages.size());
		Assert.assertTrue(((ForwardSecurityEnvelopeMessage) messages.get(0)).getData() instanceof ForwardSecurityDataInit);
		Assert.assertTrue(((ForwardSecurityEnvelopeMessage) messages.get(1)).getData() instanceof ForwardSecurityDataMessage);
	}

	private void assertNewSessionMessageNotEncapsulated(AbstractMessage message, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException {
		ForwardSecurityEncryptionResult result = senderContext.fsmp.makeMessage(DummyUsers.getContactForUser(recipient), message, senderContext.handle);
		senderContext.fsmp.commitSessionState(result);
		List<AbstractMessage> messages = result.getOutgoingMessages();
		// As the message type is not supported for the available forward security session, it is
		// sent without being encapsulated. Therefore the message equals the original message.
		Assert.assertEquals(2, messages.size());
		Assert.assertTrue(((ForwardSecurityEnvelopeMessage) messages.get(0)).getData() instanceof ForwardSecurityDataInit);
		Assert.assertEquals(message, messages.get(1));
	}

	private void assertMessageNotEncapsulated(AbstractMessage message, UserContext senderContext, DummyUsers.User recipient) throws ThreemaException {
		ForwardSecurityEncryptionResult result = senderContext.fsmp.makeMessage(DummyUsers.getContactForUser(recipient), message, senderContext.handle);
		senderContext.fsmp.commitSessionState(result);
		List<AbstractMessage> messages = result.getOutgoingMessages();
		// As the message type is not supported for the available forward security session, it is
		// sent without being encapsulated. Therefore the message equals the original message.
		// If the size does not match, then check that this method is used for existing and fresh
		// sessions only.
		Assert.assertEquals(1, messages.size());
		Assert.assertEquals(message, messages.get(0));
	}

	private ForwardSecurityEnvelopeMessage getEncapsulatedMessageFromOutgoingMessageList(List<AbstractMessage> outgoingMessages) {
		// Note that the last message of the list is the original message, as an fs init message may
		// be prepended.
		return (ForwardSecurityEnvelopeMessage) outgoingMessages.get(outgoingMessages.size() - 1);
	}

	private UserContext makeTestUserContext(DummyUsers.User user) {
		UserContext context = new UserContext();

		context.dhSessionStore = new InMemoryDHSessionStore();
		context.contactStore = new DummyContactStore();
		context.identityStore = DummyUsers.getIdentityStoreForUser(user);
		context.handle = new ServerAckTaskCodec();

		context.fsmp = new ForwardSecurityMessageProcessorWrapper(
			new ForwardSecurityMessageProcessor(
				context.dhSessionStore,
				context.contactStore,
				context.identityStore,
				nonceFactory,
				forwardSecurityStatusListener
			)
		);

		return context;
	}

	@NonNull
	private ForwardSecurityDecryptionResult processMessage(
		ForwardSecurityMessageProcessorWrapper fsmp,
		Contact contact,
		ForwardSecurityEnvelopeMessage msg,
		ActiveTaskCodec handle
	) throws BadMessageException, ThreemaException {
		ForwardSecurityData data = msg.getData();
		if (data instanceof ForwardSecurityDataInit) {
			fsmp.processInit(contact, (ForwardSecurityDataInit) data, handle);
		} else if (data instanceof ForwardSecurityDataAccept) {
			fsmp.processAccept(contact, (ForwardSecurityDataAccept) data, handle);
		} else if (data instanceof ForwardSecurityDataReject) {
			fsmp.processReject(contact, (ForwardSecurityDataReject) data, handle);
		} else if (data instanceof ForwardSecurityDataTerminate) {
			fsmp.processTerminate(contact, (ForwardSecurityDataTerminate) data);
		} else {
			return fsmp.processMessage(contact, msg, handle);
		}

		return ForwardSecurityDecryptionResult.getNONE();
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
		ServerAckTaskCodec handle;
		ForwardSecurityMessageProcessorWrapper fsmp;
	}
}
