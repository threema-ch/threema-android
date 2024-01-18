/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.app.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupMessagingService;
import ch.threema.app.services.GroupMessagingServiceImpl;
import ch.threema.app.services.UserService;
import ch.threema.domain.models.Contact;
import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.messages.GroupTextMessage;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.domain.helpers.DummyUsers;
import ch.threema.domain.helpers.InMemoryContactStore;
import ch.threema.domain.helpers.InMemoryNonceStore;
import ch.threema.storage.models.ContactModel;

import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.doReturn;

@RunWith(PowerMockRunner.class)
public class GroupMessagingServiceTest {
	// Mocks
	@Mock
	private	UserService userServiceMock;
	@Mock
	private	ContactService contactServiceMock;
	@Mock
	private MessageQueue messageQueue;

	// Stores
	final InMemoryContactStore contactStore = new InMemoryContactStore();
	final NonceFactory nonceFactory = new NonceFactory(new InMemoryNonceStore());

	// Service under test
	private GroupMessagingService service;

	// Our own identity
	private final String ownIdentity = "ABCDEFGH";

	@Before
	public void setUp() {
		// Create service
		this.service = new GroupMessagingServiceImpl(this.userServiceMock, this.contactServiceMock, this.messageQueue);

		// Ensure that user service mock returns our own identity
		doReturn(ownIdentity).when(this.userServiceMock).getIdentity();
	}

	@Test
	public void testSendMessage() throws ThreemaException {
		// Our own ID
		final IdentityStoreInterface myIdentityStore = DummyUsers.getIdentityStoreForUser(DummyUsers.ALICE);

		// Recipient contacts
		final Contact contact1 = DummyUsers.getContactForUser(DummyUsers.BOB);
		final Contact contact2 = DummyUsers.getContactForUser(DummyUsers.CAROL);
		final Contact contact3 = DummyUsers.getContactForUser(DummyUsers.DAVE);
		this.contactStore.addContact(contact1);
		this.contactStore.addContact(contact2);
		this.contactStore.addContact(contact3);

		// Create models for contacts, mark the third model as INVALID
		final ContactModel contactModel1 = new ContactModel(contact1);
		final ContactModel contactModel2 = new ContactModel(contact2);
		final ContactModel contactModel3 = new ContactModel(contact3);
		contactModel3.setState(ContactModel.State.INVALID);

		// Ensure that mocked contact service knows these contacts
		doReturn(contactModel1).when(this.contactServiceMock).getByIdentity(contact1.getIdentity());
		doReturn(contactModel2).when(this.contactServiceMock).getByIdentity(contact2.getIdentity());
		doReturn(contactModel3).when(this.contactServiceMock).getByIdentity(contact3.getIdentity());

		// Get copy of enqueued messages
		final List<AbstractMessage> enqueuedMessages = new ArrayList<>();
		doAnswer(invocation -> {
			final AbstractMessage msg = invocation.getArgument(0);
			enqueuedMessages.add(msg);
			MessageCoder messageCoder = new MessageCoder(this.contactStore, myIdentityStore);
			return messageCoder.encode(msg, this.nonceFactory);
		}).when(this.messageQueue).enqueue(any());

		// Send message
		final String[] recipients = {
			// First contact
			contact1.getIdentity(),
			// Second contact
			contact2.getIdentity(),
			// Duplicate identity
			contact1.getIdentity(),
			// Invalid identity
			contact3.getIdentity(),
			// Null entry
			null,
			// Own ID
			ownIdentity
		};
		this.service.sendMessage(
			new GroupId(),
			ownIdentity,
			recipients,
			messageId -> {
				final GroupTextMessage msg = new GroupTextMessage();
				msg.setMessageId(messageId);
				msg.setText("Hello");
				return msg;
			},
			null
		);

		// Two messages should be enqueued
		Assert.assertEquals(2, enqueuedMessages.size());

		// Recipients: The two contacts
		final Set<String> recipientIds = enqueuedMessages
			.stream()
			.map(AbstractMessage::getToIdentity)
			.collect(Collectors.toSet());
		Assert.assertTrue(recipientIds.contains(contact1.getIdentity()));
		Assert.assertTrue(recipientIds.contains(contact2.getIdentity()));

		// All messages share the same message ID
		final Set<MessageId> messageIds = enqueuedMessages
			.stream()
			.map(AbstractMessage::getMessageId)
			.collect(Collectors.toSet());
		Assert.assertEquals(1, messageIds.size());

		// All messages share the same sender (our own identity)
		final Set<String> fromIdentities = enqueuedMessages
			.stream()
			.map(AbstractMessage::getFromIdentity)
			.collect(Collectors.toSet());
		Assert.assertEquals(1, fromIdentities.size());
		Assert.assertEquals(this.ownIdentity, fromIdentities.iterator().next());
	}
}
