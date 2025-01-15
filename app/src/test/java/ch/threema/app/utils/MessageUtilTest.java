/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

package ch.threema.app.utils;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

import static ch.threema.testhelpers.TestHelpersKt.nonSecureRandomArray;
import static ch.threema.testhelpers.TestHelpersKt.randomIdentity;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class MessageUtilTest  {
	private MessageModel contactMessageModelInbox;
	private MessageModel contactMessageModelInboxUserAcknowledged;
	private MessageModel contactMessageModelInboxUserDeclined;
	private MessageModel contactMessageModelOutbox;
	private MessageModel contactMessageModelOutboxUserAcknowledged;
	private MessageModel contactMessageModelOutboxUserDeclined;

	private MessageModel businessContactMessageModelInbox;
	private MessageModel businessContactMessageModelInboxUserAcknowledged;
	private MessageModel businessContactMessageModelInboxUserDeclined;
	private MessageModel businessContactMessageModelOutbox;
	private MessageModel businessContactMessageModelOutboxUserAcknowledged;
	private MessageModel businessContactMessageModelOutboxUserDeclined;

	private GroupMessageModel groupMessageModelInbox;
	private GroupMessageModel groupMessageModelOutbox;

	private final ServiceManager serviceManagerMock = mock(ServiceManager.class);

	private DistributionListMessageModel distributionListMessageModelOutbox;

	@Before
	public void setUp() throws Exception {
		final String contactThreemaId = ThreemaApplication.ECHO_USER_IDENTITY;
		final String businessContactThreemaId = "*THREEMA";

		//mock object
		this.contactMessageModelInbox = new MessageModel();
		this.contactMessageModelInbox.setIdentity(contactThreemaId);
		this.contactMessageModelInbox.setSaved(true);
		this.contactMessageModelInbox.setOutbox(false);
		this.contactMessageModelInbox.setType(MessageType.TEXT);

		this.contactMessageModelInboxUserAcknowledged = new MessageModel();
		this.contactMessageModelInboxUserAcknowledged.setIdentity(contactThreemaId);
		this.contactMessageModelInboxUserAcknowledged.setSaved(true);
		this.contactMessageModelInboxUserAcknowledged.setOutbox(false);
		this.contactMessageModelInboxUserAcknowledged.setType(MessageType.TEXT);
		this.contactMessageModelInboxUserAcknowledged.setState(MessageState.USERACK);

		this.contactMessageModelInboxUserDeclined = new MessageModel();
		this.contactMessageModelInboxUserDeclined.setIdentity(contactThreemaId);
		this.contactMessageModelInboxUserDeclined.setSaved(true);
		this.contactMessageModelInboxUserDeclined.setOutbox(false);
		this.contactMessageModelInboxUserDeclined.setType(MessageType.TEXT);
		this.contactMessageModelInboxUserDeclined.setState(MessageState.USERDEC);

		this.contactMessageModelOutbox = new MessageModel();
		this.contactMessageModelOutbox.setIdentity(contactThreemaId);
		this.contactMessageModelOutbox.setSaved(true);
		this.contactMessageModelOutbox.setOutbox(true);
		this.contactMessageModelOutbox.setType(MessageType.TEXT);

		this.contactMessageModelOutboxUserAcknowledged = new MessageModel();
		this.contactMessageModelOutboxUserAcknowledged.setIdentity(contactThreemaId);
		this.contactMessageModelOutboxUserAcknowledged.setSaved(true);
		this.contactMessageModelOutboxUserAcknowledged.setOutbox(true);
		this.contactMessageModelOutboxUserAcknowledged.setType(MessageType.TEXT);
		this.contactMessageModelOutboxUserAcknowledged.setState(MessageState.USERACK);

		this.contactMessageModelOutboxUserDeclined = new MessageModel();
		this.contactMessageModelOutboxUserDeclined.setIdentity(contactThreemaId);
		this.contactMessageModelOutboxUserDeclined.setSaved(true);
		this.contactMessageModelOutboxUserDeclined.setOutbox(true);
		this.contactMessageModelOutboxUserDeclined.setType(MessageType.TEXT);
		this.contactMessageModelOutboxUserDeclined.setState(MessageState.USERDEC);

		this.businessContactMessageModelInbox = new MessageModel();
		this.businessContactMessageModelInbox.setIdentity(businessContactThreemaId);
		this.businessContactMessageModelInbox.setSaved(true);
		this.businessContactMessageModelInbox.setOutbox(false);
		this.businessContactMessageModelInbox.setType(MessageType.TEXT);

		this.businessContactMessageModelInboxUserAcknowledged = new MessageModel();
		this.businessContactMessageModelInboxUserAcknowledged.setIdentity(businessContactThreemaId);
		this.businessContactMessageModelInboxUserAcknowledged.setSaved(true);
		this.businessContactMessageModelInboxUserAcknowledged.setOutbox(false);
		this.businessContactMessageModelInboxUserAcknowledged.setType(MessageType.TEXT);
		this.businessContactMessageModelInboxUserAcknowledged.setState(MessageState.USERACK);

		this.businessContactMessageModelInboxUserDeclined = new MessageModel();
		this.businessContactMessageModelInboxUserDeclined.setIdentity(businessContactThreemaId);
		this.businessContactMessageModelInboxUserDeclined.setSaved(true);
		this.businessContactMessageModelInboxUserDeclined.setOutbox(false);
		this.businessContactMessageModelInboxUserDeclined.setType(MessageType.TEXT);
		this.businessContactMessageModelInboxUserDeclined.setState(MessageState.USERDEC);

		this.businessContactMessageModelOutbox = new MessageModel();
		this.businessContactMessageModelOutbox.setIdentity(businessContactThreemaId);
		this.businessContactMessageModelOutbox.setSaved(true);
		this.businessContactMessageModelOutbox.setOutbox(true);
		this.businessContactMessageModelOutbox.setType(MessageType.TEXT);

		this.businessContactMessageModelOutboxUserAcknowledged = new MessageModel();
		this.businessContactMessageModelOutboxUserAcknowledged.setIdentity(businessContactThreemaId);
		this.businessContactMessageModelOutboxUserAcknowledged.setSaved(true);
		this.businessContactMessageModelOutboxUserAcknowledged.setOutbox(true);
		this.businessContactMessageModelOutboxUserAcknowledged.setType(MessageType.TEXT);
		this.businessContactMessageModelOutboxUserAcknowledged.setState(MessageState.USERACK);

		this.businessContactMessageModelOutboxUserDeclined = new MessageModel();
		this.businessContactMessageModelOutboxUserDeclined.setIdentity(businessContactThreemaId);
		this.businessContactMessageModelOutboxUserDeclined.setSaved(true);
		this.businessContactMessageModelOutboxUserDeclined.setOutbox(true);
		this.businessContactMessageModelOutboxUserDeclined.setType(MessageType.TEXT);
		this.businessContactMessageModelOutboxUserDeclined.setState(MessageState.USERDEC);

		this.groupMessageModelInbox = new GroupMessageModel();
		this.groupMessageModelInbox.setSaved(true);
		this.groupMessageModelInbox.setOutbox(false);
		this.groupMessageModelInbox.setType(MessageType.TEXT);

		this.groupMessageModelOutbox= new GroupMessageModel();
		this.groupMessageModelOutbox.setSaved(true);
		this.groupMessageModelOutbox.setOutbox(true);
		this.groupMessageModelOutbox.setType(MessageType.TEXT);

		this.distributionListMessageModelOutbox= new DistributionListMessageModel();
		this.distributionListMessageModelOutbox.setSaved(true);
		this.distributionListMessageModelOutbox.setOutbox(true);
		this.distributionListMessageModelOutbox.setType(MessageType.TEXT);
	}


	@Test
	public void hasDataFile() {
		assertFalse(MessageUtil.hasDataFile(this.createMessageModel(MessageType.TEXT)));
		assertTrue(MessageUtil.hasDataFile(this.createMessageModel(MessageType.IMAGE)));
		assertTrue(MessageUtil.hasDataFile(this.createMessageModel(MessageType.VIDEO)));
		assertTrue(MessageUtil.hasDataFile(this.createMessageModel(MessageType.VOICEMESSAGE)));
		assertFalse(MessageUtil.hasDataFile(this.createMessageModel(MessageType.LOCATION)));
		assertFalse(MessageUtil.hasDataFile(this.createMessageModel(MessageType.CONTACT)));
		assertFalse(MessageUtil.hasDataFile(this.createMessageModel(MessageType.STATUS)));
		assertFalse(MessageUtil.hasDataFile(this.createMessageModel(MessageType.BALLOT)));
		assertTrue(MessageUtil.hasDataFile(this.createMessageModel(MessageType.FILE)));
	}

	@Test
	public void hasThumbnailFile() {
		assertFalse(MessageUtil.canHaveThumbnailFile(this.createMessageModel(MessageType.TEXT)));
		assertTrue(MessageUtil.canHaveThumbnailFile(this.createMessageModel(MessageType.IMAGE)));
		assertTrue(MessageUtil.canHaveThumbnailFile(this.createMessageModel(MessageType.VIDEO)));
		assertFalse(MessageUtil.canHaveThumbnailFile(this.createMessageModel(MessageType.VOICEMESSAGE)));
		assertFalse(MessageUtil.canHaveThumbnailFile(this.createMessageModel(MessageType.LOCATION)));
		assertFalse(MessageUtil.canHaveThumbnailFile(this.createMessageModel(MessageType.CONTACT)));
		assertFalse(MessageUtil.canHaveThumbnailFile(this.createMessageModel(MessageType.STATUS)));
		assertFalse(MessageUtil.canHaveThumbnailFile(this.createMessageModel(MessageType.BALLOT)));
		assertTrue(MessageUtil.canHaveThumbnailFile(this.createMessageModel(MessageType.FILE)));
	}

	@Test
	public void getFileTypes() {
		assertFalse(MessageUtil.getFileTypes().contains(MessageType.TEXT));
		assertFalse(MessageUtil.getFileTypes().contains(MessageType.BALLOT));
		assertFalse(MessageUtil.getFileTypes().contains(MessageType.CONTACT));
		assertFalse(MessageUtil.getFileTypes().contains(MessageType.LOCATION));
		assertFalse(MessageUtil.getFileTypes().contains(MessageType.STATUS));

		assertTrue(MessageUtil.getFileTypes().contains(MessageType.IMAGE));
		assertTrue(MessageUtil.getFileTypes().contains(MessageType.VOICEMESSAGE));
		assertTrue(MessageUtil.getFileTypes().contains(MessageType.VIDEO));
		assertTrue(MessageUtil.getFileTypes().contains(MessageType.FILE));

	}

	@Test
	public void canSendDeliveryReceipt() {
		assertTrue(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));
		assertTrue(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelInboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));
		assertFalse(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelOutbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));
		assertFalse(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelOutboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));

		assertTrue(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));
		assertTrue(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelInboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));
		assertFalse(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelOutbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));
		assertFalse(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelOutboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));

		assertFalse(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));

		if (ConfigUtils.isGroupAckEnabled()) {
			assertTrue(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK));
			assertTrue(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC));
		} else {
			assertFalse(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK));
			assertFalse(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC));
		}

		assertFalse(MessageUtil.canSendDeliveryReceipt(this.distributionListMessageModelOutbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD));

	}

	/**
	 * User acknowledge can be sent following message setups:
	 *  - User, Inbox, Not acknowledged
	 *  - Business, Inbox acknowledged
	 */
	@Test
	public void canSendUserAcknowledge() {
		//contact message (inbox) can be acknowledge by the user
		assertTrue(MessageUtil.canSendUserAcknowledge(this.contactMessageModelInbox));

		//contact message (outbox) can be acknowledge by the user
		assertFalse(MessageUtil.canSendUserAcknowledge(this.contactMessageModelOutbox));

		//contact message (inbox) with state UserAck can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserAcknowledge(this.contactMessageModelInboxUserAcknowledged));

		//contact message (outbox) with state UserAck can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserAcknowledge(this.contactMessageModelOutboxUserAcknowledged));

		//business contact message (inbox) can be acknowledge by the user
		assertTrue(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelInbox));

		//business contact message (outbox) can be acknowledge by the user
		assertFalse(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelOutbox));

		//business contact message (inbox) with state UserAck can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelInboxUserAcknowledged));

		//business contact message (outbox) with state UserAck can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelOutboxUserAcknowledged));

		//group message (inbox) can be acknowledged by the user
		assertTrue(MessageUtil.canSendUserAcknowledge(this.groupMessageModelInbox));

		//group message (outbox) can be acknowledged by the user
		assertTrue(MessageUtil.canSendUserAcknowledge(this.groupMessageModelOutbox));

		//group message (outbox) can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserAcknowledge(this.distributionListMessageModelOutbox));

	}

	/**
	 * User decline can be sent following message setups:
	 *  - User, Inbox, Not acknowledged
	 *  - Business, Inbox acknowledged
	 */
	@Test
	public void canSendUserDecline() {
		//contact message (inbox) can be decline by the user
		assertTrue(MessageUtil.canSendUserDecline(this.contactMessageModelInbox));

		//contact message (outbox) can be decline by the user
		assertFalse(MessageUtil.canSendUserDecline(this.contactMessageModelOutbox));

		//contact message (inbox) with state UserDec can not be decline by the user
		assertFalse(MessageUtil.canSendUserDecline(this.contactMessageModelInboxUserDeclined));

		//contact message (outbox) with state UserAck can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserDecline(this.contactMessageModelOutboxUserDeclined));

		//business contact message (inbox) can be acknowledge by the user
		assertTrue(MessageUtil.canSendUserDecline(this.businessContactMessageModelInbox));

		//business contact message (outbox) can be acknowledge by the user
		assertFalse(MessageUtil.canSendUserDecline(this.businessContactMessageModelOutbox));

		//business contact message (inbox) with state UserAck can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserDecline(this.businessContactMessageModelInboxUserDeclined));

		//business contact message (outbox) with state UserAck can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserDecline(this.businessContactMessageModelOutboxUserDeclined));

		//group message (inbox) can be declined by the user
		assertTrue(MessageUtil.canSendUserDecline(this.groupMessageModelInbox));

		//group message (outbox) can be declined by the user
		assertTrue(MessageUtil.canSendUserDecline(this.groupMessageModelOutbox));

		//group message (outbox) can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserDecline(this.distributionListMessageModelOutbox));

	}

	/**
	 * status icons show for following message setups:
	 * - User, Outbox
	 * - User, Inbox, User acknowledged
	 * - Business, Inbox, User acknowledged
	 * - Business|Group|Distribution List, outbox, send failed|pending|sending
	 */
	@Test
	public void showStatusIcon() {

		assertFalse(MessageUtil.showStatusIcon(this.contactMessageModelInbox));
		assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox));
		assertFalse(MessageUtil.showStatusIcon(this.contactMessageModelInboxUserAcknowledged));
		assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutboxUserAcknowledged));

		assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelInbox));
		assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox));
		assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelInboxUserAcknowledged));
		assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelOutboxUserAcknowledged));

		assertFalse(MessageUtil.showStatusIcon(this.groupMessageModelInbox));
		assertFalse(MessageUtil.showStatusIcon(this.groupMessageModelOutbox));

		assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox));

		//all types in state sending
		this.contactMessageModelOutbox.setState(MessageState.SENDING);
		this.businessContactMessageModelOutbox.setState(MessageState.SENDING);
		this.groupMessageModelOutbox.setState(MessageState.SENDING);
		this.distributionListMessageModelOutbox.setState(MessageState.SENDING);

		assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox));
		assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox));
		assertTrue(MessageUtil.showStatusIcon(this.groupMessageModelOutbox));
		assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox));

		//all types in state failed
		this.contactMessageModelOutbox.setState(MessageState.SENDFAILED);
		this.businessContactMessageModelOutbox.setState(MessageState.SENDFAILED);
		this.groupMessageModelOutbox.setState(MessageState.SENDFAILED);
		this.distributionListMessageModelOutbox.setState(MessageState.SENDFAILED);

		assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox));
		assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox));
		assertTrue(MessageUtil.showStatusIcon(this.groupMessageModelOutbox));
		assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox));

		//all types in state pending
		this.contactMessageModelOutbox.setState(MessageState.PENDING);
		this.businessContactMessageModelOutbox.setState(MessageState.PENDING);
		this.groupMessageModelOutbox.setState(MessageState.PENDING);
		this.distributionListMessageModelOutbox.setState(MessageState.PENDING);

		assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox));
		assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox));
		assertTrue(MessageUtil.showStatusIcon(this.groupMessageModelOutbox));
		assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox));
	}

	@Test
	public void isUnread() {
		assertTrue(MessageUtil.isUnread(this.createMessageModel(messageModel -> {
			messageModel.setOutbox(false);
			messageModel.setIsStatusMessage(false);
		})));
		assertFalse(MessageUtil.isUnread(this.createMessageModel(messageModel -> {
			messageModel.setOutbox(true);
			messageModel.setIsStatusMessage(false);
		})));

		assertFalse(MessageUtil.isUnread(this.createMessageModel(messageModel -> {
			messageModel.setOutbox(false);
			messageModel.setIsStatusMessage(true);
		})));

		assertFalse(MessageUtil.isUnread(null));

	}

	@Test
	public void canMarkAsRead() {
		assertTrue(MessageUtil.canMarkAsRead(this.createMessageModel(messageModel -> {
			messageModel.setRead(false);
			messageModel.setOutbox(false);
		})));
		assertFalse(MessageUtil.canMarkAsRead(this.createMessageModel(messageModel -> {
			messageModel.setRead(true);
			messageModel.setOutbox(false);
		})));
		assertFalse(MessageUtil.canMarkAsRead(this.createMessageModel(messageModel -> {
			messageModel.setRead(false);
			messageModel.setOutbox(true);
		})));

		assertFalse(MessageUtil.canMarkAsRead(this.createMessageModel(messageModel -> {
			messageModel.setRead(true);
			messageModel.setOutbox(true);
		})));
	}

	@Test
	public void autoGenerateThumbnail() {
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(messageModel -> messageModel.setType(MessageType.TEXT))));
		assertTrue(MessageUtil.autoGenerateThumbnail(this.createMessageModel(messageModel -> messageModel.setType(MessageType.IMAGE))));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(messageModel -> messageModel.setType(MessageType.VIDEO))));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(messageModel -> messageModel.setType(MessageType.VOICEMESSAGE))));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(messageModel -> messageModel.setType(MessageType.CONTACT))));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(messageModel -> messageModel.setType(MessageType.STATUS))));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(messageModel -> messageModel.setType(MessageType.BALLOT))));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(messageModel -> messageModel.setType(MessageType.FILE))));
	}

	@Test
	public void getAllReceivers_message_without_affected_receivers() {
		ContactMessageReceiver contactMessageReceiver = createContactMessageReceiver();

		// Message receiver without affected receivers
		List<MessageReceiver> allReceivers = MessageUtil.getAllReceivers(contactMessageReceiver);
		assertEquals(1, allReceivers.size());
		assertEquals(contactMessageReceiver, allReceivers.get(0));
	}

	@Test
	public void getAllReceivers_message_with_affected_receivers() {
		String identity1 = "ABCDEFG1";
		String identity2 = "ABCDEFG2";
		String identity3 = "ABCDEFG3";
		MessageReceiver distributionListReceiver = createDistributionListMessageReceiver(Arrays.asList(
			Pair.create(identity1, nonSecureRandomArray(32)),
			Pair.create(identity2, nonSecureRandomArray(32)),
			Pair.create(identity3, nonSecureRandomArray(32))
		));

		List<MessageReceiver> allReceivers = MessageUtil.getAllReceivers(distributionListReceiver);
		assertEquals(4, allReceivers.size());
		assertEquals(distributionListReceiver, allReceivers.get(0));
		assertEquals(identity1, allReceivers.get(1).getIdentities()[0]);
		assertEquals(identity2, allReceivers.get(2).getIdentities()[0]);
		assertEquals(identity3, allReceivers.get(3).getIdentities()[0]);
	}

	@Test
	public void addDistributionListReceivers_empty_message_receivers_list() {
		assertEquals(0, MessageUtil.addDistributionListReceivers(new MessageReceiver[0]).length);
	}

	@Test
	public void addDistributionListReceivers_must_contain_passed_receivers() {
		MessageReceiver contactMessageReceiver = createContactMessageReceiver();
		MessageReceiver distributionListReceiver = createDistributionListMessageReceiver(Collections.emptyList());

		MessageReceiver[] resolvedReceivers = MessageUtil.addDistributionListReceivers(new MessageReceiver[] {
			contactMessageReceiver, distributionListReceiver
		});
		assertEquals(2, resolvedReceivers.length);
		assertEquals(contactMessageReceiver, resolvedReceivers[0]);
		assertEquals(distributionListReceiver, resolvedReceivers[1]);
	}

	@Test
	public void addDistributionListReceivers_must_preserve_order_of_receivers() {
		MessageReceiver contactMessageReceiver1 = createContactMessageReceiver("ABCDEFG1", nonSecureRandomArray(32));
		MessageReceiver contactMessageReceiver2 = createContactMessageReceiver("ABCDEFG2", nonSecureRandomArray(32));
		MessageReceiver contactMessageReceiver3 = createContactMessageReceiver("ABCDEFG3", nonSecureRandomArray(32));
		String identity4 = "ABCDEFG4";
		String identity5 = "ABCDEFG5";
		MessageReceiver emptyDistributionListMessageReceiver = createDistributionListMessageReceiver(Collections.emptyList());
		MessageReceiver distributionListMessageReceiver = createDistributionListMessageReceiver(Arrays.asList(
			Pair.create(identity4, nonSecureRandomArray(32)),
			Pair.create(identity5, nonSecureRandomArray(32))
		));

		MessageReceiver[] receivers = new MessageReceiver[] {
			contactMessageReceiver1,
			emptyDistributionListMessageReceiver,
			contactMessageReceiver2,
			distributionListMessageReceiver,
			contactMessageReceiver3
		};

		MessageReceiver[] resolvedReceivers = MessageUtil.addDistributionListReceivers(receivers);

		assertEquals(7, resolvedReceivers.length);
		assertEquals(contactMessageReceiver1, resolvedReceivers[0]);
		assertEquals(emptyDistributionListMessageReceiver, resolvedReceivers[1]);
		assertEquals(contactMessageReceiver2, resolvedReceivers[2]);
		assertEquals(distributionListMessageReceiver, resolvedReceivers[3]);
		assertEquals(identity4, resolvedReceivers[4].getIdentities()[0]);
		assertEquals(identity5, resolvedReceivers[5].getIdentities()[0]);
		assertEquals(contactMessageReceiver3, resolvedReceivers[6]);
	}

	@Test
	public void addDistributionListReceivers_must_preserve_order_of_receivers_and_remove_duplicates() {
		final byte[] publicKey1 = nonSecureRandomArray(32);
		final byte[] publicKey2 = nonSecureRandomArray(32);
		final byte[] publicKey3 = nonSecureRandomArray(32);
		final byte[] publicKey4 = nonSecureRandomArray(32);
		final byte[] publicKey5 = nonSecureRandomArray(32);

		ContactMessageReceiver contactMessageReceiver1 = createContactMessageReceiver("ABCDEFG1", publicKey1);
		ContactMessageReceiver contactMessageReceiver2 = createContactMessageReceiver("ABCDEFG2", publicKey2);
		ContactMessageReceiver contactMessageReceiver3 = createContactMessageReceiver("ABCDEFG3", publicKey3);

		ContactMessageReceiver duplicate1 = createContactMessageReceiver("ABCDEFG1", publicKey1);

		String identity2 = "ABCDEFG2";
		String identity3 = "ABCDEFG3";
		String identity4 = "ABCDEFG4";
		String identity5 = "ABCDEFG5";

		MessageReceiver emptyDistributionListMessageReceiver = createDistributionListMessageReceiver(Collections.emptyList());
		MessageReceiver distributionListMessageReceiver = createDistributionListMessageReceiver(Arrays.asList(
			Pair.create(identity4, publicKey4),
			Pair.create(identity5, publicKey5),
			Pair.create(identity2, publicKey2),
			Pair.create(identity3, publicKey3)
		));

		MessageReceiver[] receivers = new MessageReceiver[] {
			contactMessageReceiver1,
			duplicate1,
			emptyDistributionListMessageReceiver,
			contactMessageReceiver2,
			distributionListMessageReceiver,
			contactMessageReceiver3,
			distributionListMessageReceiver
		};

		MessageReceiver[] resolvedReceivers = MessageUtil.addDistributionListReceivers(receivers);

		assertEquals(7, resolvedReceivers.length);
		assertEquals(contactMessageReceiver1, resolvedReceivers[0]);
		assertEquals(emptyDistributionListMessageReceiver, resolvedReceivers[1]);
		assertEquals(contactMessageReceiver2, resolvedReceivers[2]);
		assertEquals(distributionListMessageReceiver, resolvedReceivers[3]);
		assertEquals(identity4, resolvedReceivers[4].getIdentities()[0]);
		assertEquals(identity5, resolvedReceivers[5].getIdentities()[0]);
		assertEquals(identity3, resolvedReceivers[6].getIdentities()[0]);
	}

	private ContactMessageReceiver createContactMessageReceiver() {
		return createContactMessageReceiver(randomIdentity(), nonSecureRandomArray(32));
	}

	private ContactMessageReceiver createContactMessageReceiver(@NonNull String identity, @NonNull byte[] publicKey) {
		ContactModel contactModel = new ContactModel(identity, publicKey);
		return new ContactMessageReceiver(contactModel, null, serviceManagerMock, null, null, null);
	}

	private @NonNull MessageReceiver createDistributionListMessageReceiver(@NonNull List<Pair<String, byte[]>> identitiesWithPublicKey) {
		DistributionListService distributionListService = mock(DistributionListService.class);
		List<ContactModel> contacts = StreamSupport.stream(identitiesWithPublicKey)
			.map(pair -> new ContactModel(pair.first, pair.second))
			.collect(Collectors.toList());
		when(distributionListService.getMembers(any())).thenReturn(contacts);

		ContactService contactService = mock(ContactService.class);
		when(contactService.createReceiver((ContactModel) any())).thenAnswer(invocation -> {
			ContactModel contactModel = invocation.getArgument(0, ContactModel.class);
			return new ContactMessageReceiver(contactModel, null, serviceManagerMock, null, null, null);
		});

		return new DistributionListMessageReceiver(null, contactService, null, distributionListService);
	}

	private interface ConfigureMessageModel {
		void configure(MessageModel messageModel);
	}


	private AbstractMessageModel createMessageModel(final MessageType t) {
		return this.createMessageModel(messageModel -> messageModel.setType(t));
	}

	private MessageModel createMessageModel(ConfigureMessageModel ConfigureMessageModel) {
		MessageModel m = new MessageModel();
		ConfigureMessageModel.configure(m);
		return m;
	}
}
