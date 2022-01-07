/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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

import ch.threema.app.ThreemaApplication;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class MessageUtilTest  {
	private final String contactThreemaId = ThreemaApplication.ECHO_USER_IDENTITY;
	private final String businessContactThreemaId = "*THREEMA";

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


	private DistributionListMessageModel distributionListMessageModelOutbox;

	@Before
	public void setUp() throws Exception {
		//mock object
		this.contactMessageModelInbox = new MessageModel();
		this.contactMessageModelInbox.setIdentity(this.contactThreemaId);
		this.contactMessageModelInbox.setSaved(true);
		this.contactMessageModelInbox.setOutbox(false);
		this.contactMessageModelInbox.setType(MessageType.TEXT);

		this.contactMessageModelInboxUserAcknowledged = new MessageModel();
		this.contactMessageModelInboxUserAcknowledged.setIdentity(this.contactThreemaId);
		this.contactMessageModelInboxUserAcknowledged.setSaved(true);
		this.contactMessageModelInboxUserAcknowledged.setOutbox(false);
		this.contactMessageModelInboxUserAcknowledged.setType(MessageType.TEXT);
		this.contactMessageModelInboxUserAcknowledged.setState(MessageState.USERACK);

		this.contactMessageModelInboxUserDeclined = new MessageModel();
		this.contactMessageModelInboxUserDeclined.setIdentity(this.contactThreemaId);
		this.contactMessageModelInboxUserDeclined.setSaved(true);
		this.contactMessageModelInboxUserDeclined.setOutbox(false);
		this.contactMessageModelInboxUserDeclined.setType(MessageType.TEXT);
		this.contactMessageModelInboxUserDeclined.setState(MessageState.USERDEC);

		this.contactMessageModelOutbox = new MessageModel();
		this.contactMessageModelOutbox.setIdentity(this.contactThreemaId);
		this.contactMessageModelOutbox.setSaved(true);
		this.contactMessageModelOutbox.setOutbox(true);
		this.contactMessageModelOutbox.setType(MessageType.TEXT);

		this.contactMessageModelOutboxUserAcknowledged = new MessageModel();
		this.contactMessageModelOutboxUserAcknowledged.setIdentity(this.contactThreemaId);
		this.contactMessageModelOutboxUserAcknowledged.setSaved(true);
		this.contactMessageModelOutboxUserAcknowledged.setOutbox(true);
		this.contactMessageModelOutboxUserAcknowledged.setType(MessageType.TEXT);
		this.contactMessageModelOutboxUserAcknowledged.setState(MessageState.USERACK);

		this.contactMessageModelOutboxUserDeclined = new MessageModel();
		this.contactMessageModelOutboxUserDeclined.setIdentity(this.contactThreemaId);
		this.contactMessageModelOutboxUserDeclined.setSaved(true);
		this.contactMessageModelOutboxUserDeclined.setOutbox(true);
		this.contactMessageModelOutboxUserDeclined.setType(MessageType.TEXT);
		this.contactMessageModelOutboxUserDeclined.setState(MessageState.USERDEC);

		this.businessContactMessageModelInbox = new MessageModel();
		this.businessContactMessageModelInbox.setIdentity(this.businessContactThreemaId);
		this.businessContactMessageModelInbox.setSaved(true);
		this.businessContactMessageModelInbox.setOutbox(false);
		this.businessContactMessageModelInbox.setType(MessageType.TEXT);

		this.businessContactMessageModelInboxUserAcknowledged = new MessageModel();
		this.businessContactMessageModelInboxUserAcknowledged.setIdentity(this.businessContactThreemaId);
		this.businessContactMessageModelInboxUserAcknowledged.setSaved(true);
		this.businessContactMessageModelInboxUserAcknowledged.setOutbox(false);
		this.businessContactMessageModelInboxUserAcknowledged.setType(MessageType.TEXT);
		this.businessContactMessageModelInboxUserAcknowledged.setState(MessageState.USERACK);

		this.businessContactMessageModelInboxUserDeclined = new MessageModel();
		this.businessContactMessageModelInboxUserDeclined.setIdentity(this.businessContactThreemaId);
		this.businessContactMessageModelInboxUserDeclined.setSaved(true);
		this.businessContactMessageModelInboxUserDeclined.setOutbox(false);
		this.businessContactMessageModelInboxUserDeclined.setType(MessageType.TEXT);
		this.businessContactMessageModelInboxUserDeclined.setState(MessageState.USERDEC);

		this.businessContactMessageModelOutbox = new MessageModel();
		this.businessContactMessageModelOutbox.setIdentity(this.businessContactThreemaId);
		this.businessContactMessageModelOutbox.setSaved(true);
		this.businessContactMessageModelOutbox.setOutbox(true);
		this.businessContactMessageModelOutbox.setType(MessageType.TEXT);

		this.businessContactMessageModelOutboxUserAcknowledged = new MessageModel();
		this.businessContactMessageModelOutboxUserAcknowledged.setIdentity(this.businessContactThreemaId);
		this.businessContactMessageModelOutboxUserAcknowledged.setSaved(true);
		this.businessContactMessageModelOutboxUserAcknowledged.setOutbox(true);
		this.businessContactMessageModelOutboxUserAcknowledged.setType(MessageType.TEXT);
		this.businessContactMessageModelOutboxUserAcknowledged.setState(MessageState.USERACK);

		this.businessContactMessageModelOutboxUserDeclined = new MessageModel();
		this.businessContactMessageModelOutboxUserDeclined.setIdentity(this.businessContactThreemaId);
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
	public void hasDataFile() throws Exception {
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
	public void hasThumbnailFile() throws Exception {
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
	public void getFileTypes() throws Exception {
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
	public void canSendDeliveryReceipt() throws Exception {
		assertTrue(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelInbox));
		assertTrue(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelInboxUserAcknowledged));
		assertFalse(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelOutbox));
		assertFalse(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelOutboxUserAcknowledged));

		assertTrue(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelInbox));
		assertTrue(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelInboxUserAcknowledged));
		assertFalse(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelOutbox));
		assertFalse(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelOutboxUserAcknowledged));


		assertFalse(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox));
		assertFalse(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox));

		assertFalse(MessageUtil.canSendDeliveryReceipt(this.distributionListMessageModelOutbox));

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

		//group message (inbox) can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserAcknowledge(this.groupMessageModelInbox));

		//group message (outbox) can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserAcknowledge(this.groupMessageModelOutbox));

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

		//group message (inbox) can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserDecline(this.groupMessageModelInbox));

		//group message (outbox) can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserDecline(this.groupMessageModelOutbox));

		//group message (outbox) can not be acknowledge by the user
		assertFalse(MessageUtil.canSendUserDecline(this.distributionListMessageModelOutbox));

	}

	/**
	 * status icons show for following message setups:
	 * - User, Outbox
	 * - User, Inbox, User acknowledged
	 * - Business, Inbox, User acknowledged
	 * - Business|Group|Distribution List, outbox, send failed|pending|sending
	 *
	 * @throws Exception
	 */
	@Test
	public void showStatusIcon() throws Exception {

		assertFalse(MessageUtil.showStatusIcon(this.contactMessageModelInbox));
		assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox));
		assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelInboxUserAcknowledged));
		assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutboxUserAcknowledged));

		assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelInbox));
		assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox));
		assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelInboxUserAcknowledged));
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
	public void isUnread() throws Exception {
		assertTrue(MessageUtil.isUnread(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setOutbox(false);
				messageModel.setIsStatusMessage(false);
			}
		})));
		assertFalse(MessageUtil.isUnread(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setOutbox(true);
				messageModel.setIsStatusMessage(false);
			}
		})));

		assertFalse(MessageUtil.isUnread(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setOutbox(false);
				messageModel.setIsStatusMessage(true);
			}
		})));

		assertFalse(MessageUtil.isUnread(null));

	}

	@Test
	public void canMarkAsRead() throws Exception {
		assertTrue(MessageUtil.canMarkAsRead(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setRead(false);
				messageModel.setOutbox(false);
			}
		})));
		assertFalse(MessageUtil.canMarkAsRead(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setRead(true);
				messageModel.setOutbox(false);
			}
		})));
		assertFalse(MessageUtil.canMarkAsRead(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setRead(false);
				messageModel.setOutbox(true);
			}
		})));

		assertFalse(MessageUtil.canMarkAsRead(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setRead(true);
				messageModel.setOutbox(true);
			}
		})));
	}

	@Test
	public void autoGenerateThumbnail() throws Exception {
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setType(MessageType.TEXT);
			}
		})));
		assertTrue(MessageUtil.autoGenerateThumbnail(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setType(MessageType.IMAGE);
			}
		})));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setType(MessageType.VIDEO);
			}
		})));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setType(MessageType.VOICEMESSAGE);
			}
		})));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setType(MessageType.CONTACT);
			}
		})));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setType(MessageType.STATUS);
			}
		})));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setType(MessageType.BALLOT);
			}
		})));
		assertFalse(MessageUtil.autoGenerateThumbnail(this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setType(MessageType.FILE);
			}
		})));
	}

	private interface ConfigureMessageModel {
		void configure(MessageModel messageModel);
	}


	private AbstractMessageModel createMessageModel(final MessageType t) {
		return this.createMessageModel(new ConfigureMessageModel() {
			@Override
			public void configure(MessageModel messageModel) {
				messageModel.setType(t);
			}
		});
	}

	private MessageModel createMessageModel(ConfigureMessageModel ConfigureMessageModel) {
		MessageModel m = new MessageModel();
		ConfigureMessageModel.configure(m);
		return m;
	}
}
