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

package ch.threema.app.processors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.Objects;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.group.GroupJoinResponseService;
import ch.threema.app.services.group.IncomingGroupJoinRequestService;
import ch.threema.app.testutils.CaptureLogcatOnTestFailureRule;
import ch.threema.app.testutils.TestHelpers;
import ch.threema.app.testutils.ThreemaAssert;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.utils.Utils;
import ch.threema.domain.helpers.InMemoryContactStore;
import ch.threema.domain.helpers.InMemoryIdentityStore;
import ch.threema.domain.helpers.InMemoryNonceStore;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.connection.MessageProcessorInterface.ProcessIncomingResult;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MessageProcessorTest {
	// Test rules
	@Rule public TestName name = new TestName();
	@Rule public CaptureLogcatOnTestFailureRule captureLogcatOnTestFailureRule = new CaptureLogcatOnTestFailureRule();

	private final static Contact TEST_CONTACT_1 = new Contact("09BNNVR2", Utils.hexStringToByteArray("e4613bbe5408d342fdabc3edf4509d1a3aecd7cb0598773987eef8400e74c81a"));
	private final static Contact TEST_CONTACT_2 = new Contact("0BSXZ4P8", Utils.hexStringToByteArray("dee1cd341de88f783a768941eac702951c8bbb21e836da4a43ab8f3776fc0a65"));

	private NonceFactory nonceFactory;

	// Stores
	private IdentityStoreInterface identityStore;
	private IdentityStoreInterface identityStore2;
	private ContactStore contactStore;

	// Message processor
	private MessageProcessor messageProcessor;

	@Before
	public void setUp() throws Exception {
		// Load services
		// Services
		final ServiceManager serviceManager = Objects.requireNonNull(ThreemaApplication.getServiceManager());
		MessageService messageService = serviceManager.getMessageService();
		ContactService contactService = serviceManager.getContactService();
		PreferenceService preferenceService = serviceManager.getPreferenceService();
		GroupService groupService = serviceManager.getGroupService();
		GroupJoinResponseService groupJoinResponseService = serviceManager.getGroupJoinResponseService();
		IncomingGroupJoinRequestService incomingGroupJoinRequestService = serviceManager.getIncomingGroupJoinRequestService();
		IdListService blackListService = serviceManager.getBlackListService();
		BallotService ballotService = serviceManager.getBallotService();
		FileService fileService = serviceManager.getFileService();
		NotificationService notificationService = serviceManager.getNotificationService();
		VoipStateService voipStateService = serviceManager.getVoipStateService();
		this.nonceFactory = new NonceFactory(new InMemoryNonceStore());
		ForwardSecurityMessageProcessor forwardSecurityMessageProcessor = serviceManager.getForwardSecurityMessageProcessor();
		GroupCallManager groupCallManager = serviceManager.getGroupCallManager();
		ServerAddressProvider serverAddressProvider = serviceManager.getServerAddressProviderService().getServerAddressProvider();

		// Create in-memory stores
		this.contactStore = new InMemoryContactStore();

		// Create two identities
		this.identityStore = new InMemoryIdentityStore(
			"07N3PDDA",
			"86",
			Utils.hexStringToByteArray("e2c457e6f90b9e4dd1f9feedb078382d1dbd1e64c616b2e8ac8ae28b8cede36e"),
			"07N3PDDA");
		this.identityStore2 = new InMemoryIdentityStore(
			"07ZKBCYU",
			"3a",
			Utils.hexStringToByteArray("4a7983f3e4dc7d5d1a591a94dfc03b16b94a4ca5a15e4e68c3bdba4dd030dd3e"),
			"07ZKBCYU"
		);

		// Store contacts (including ourselves, so we can encrypt messages to ourselves)
		this.contactStore.addContact(TEST_CONTACT_1);
		this.contactStore.addContact(TEST_CONTACT_2);
		this.contactStore.addContact(new Contact(this.identityStore.getIdentity(), this.identityStore.getPublicKey()));
		this.contactStore.addContact(new Contact(this.identityStore2.getIdentity(), this.identityStore2.getPublicKey()));

		// Create message processor
		this.messageProcessor = new MessageProcessor(
			serviceManager,
			messageService,
			contactService,
			this.identityStore,
			this.contactStore,
			preferenceService,
			groupService,
			groupJoinResponseService,
			incomingGroupJoinRequestService,
			blackListService,
			ballotService,
			fileService,
			notificationService,
			voipStateService,
			forwardSecurityMessageProcessor,
			groupCallManager,
			serverAddressProvider
		);
	}

	/**
	 * Return logcat logs for this test (without clearing the log).
	 */
	private String getLogs() {
		final String testName = this.name.getMethodName() + "(" + MessageProcessorTest.class.getName() + ")";
		return TestHelpers.getTestLogs(testName);
	}

	/**
	 * When a message is processed that is not directed at us, processing fails.
	 */
	@Test
	@Ignore("because getLogs does not work consistently.") // TODO(ANDR-1484)
	public void messageForOtherIdentity() throws ThreemaException {
		final MessageBox boxmsg = new MessageBox();
		boxmsg.setFromIdentity(TEST_CONTACT_1.getIdentity());
		boxmsg.setToIdentity(TEST_CONTACT_2.getIdentity());
		boxmsg.setBox(new byte[] { 0, 1, 2, 3 });
		boxmsg.setNonce(this.nonceFactory.next());
		final ProcessIncomingResult result = this.messageProcessor.processIncomingMessage(boxmsg);
		Assert.assertFalse(result.wasProcessed());
		final String logs = this.getLogs();
		ThreemaAssert.assertContains(logs, "BadMessageException: Message is not for own identity, cannot decode");
	}

	/**
	 * When processing an invalid box, no exception should be thrown.
	 */
	@Test
	@Ignore("because getLogs does not work consistently.") // TODO(ANDR-1484)
	public void processInvalidBox() throws ThreemaException {
		final MessageBox boxmsg = new MessageBox();
		boxmsg.setFromIdentity(TEST_CONTACT_1.getIdentity());
		boxmsg.setToIdentity(this.identityStore.getIdentity());
		boxmsg.setBox(new byte[] { 0, 1, 2, 3 });
		boxmsg.setNonce(this.nonceFactory.next());
		final ProcessIncomingResult result = this.messageProcessor.processIncomingMessage(boxmsg);
		Assert.assertFalse(result.wasProcessed());
		final String logs = this.getLogs();
		ThreemaAssert.assertContains(logs, "ch.threema.domain.protocol.csp.messages.BadMessageException: Decryption of message from");
	}

	/**
	 * Process a delivery receipt for an unknown message.
	 *
	 * Because the confirmed message is not known, a log is created,
	 * but nothing in the database is changed.
	 */
	@Test
	@Ignore("because getLogs does not work consistently.") // TODO(ANDR-1484)
	public void processDeliveryReceiptForUnknownMessage() throws ThreemaException {
		// Message IDs
		final MessageId deliveryMessageId = new MessageId();
		final MessageId deliveredMessageId = new MessageId();

		// Create message box
		final DeliveryReceiptMessage msg = new DeliveryReceiptMessage();
		msg.setMessageId(deliveryMessageId);
		msg.setFromIdentity(this.identityStore2.getIdentity());
		msg.setToIdentity(this.identityStore.getIdentity());
		msg.setReceiptType(ProtocolDefines.DELIVERYRECEIPT_MSGRECEIVED);
		msg.setReceiptMessageIds(new MessageId[] { deliveredMessageId });

		MessageCoder messageCoder = new MessageCoder(this.contactStore, this.identityStore2);
		final MessageBox boxmsg = messageCoder.encode(msg, this.nonceFactory);

		// Process message
		final ProcessIncomingResult result = this.messageProcessor.processIncomingMessage(boxmsg);
		Assert.assertTrue(result.wasProcessed());

		// Assert log messages
		final String logs = this.getLogs();
		ThreemaAssert.assertContains(
			logs,
			"MessageProcessor: Incoming message " + deliveryMessageId
				+ " from " + this.identityStore2.getIdentity()
				+ " to " + this.identityStore.getIdentity()
				+ " (type " + Utils.byteToHex((byte) ProtocolDefines.MSGTYPE_DELIVERY_RECEIPT, false, true) + ")"
		);
		ThreemaAssert.assertContains(
			logs,
			"MessageServiceImpl: Updated message state (DELIVERED) for unknown message with id " + deliveredMessageId.toString()
		);
	}
}
