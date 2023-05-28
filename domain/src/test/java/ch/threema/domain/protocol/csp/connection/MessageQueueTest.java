/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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

package ch.threema.domain.protocol.csp.connection;

import ch.threema.base.ThreemaException;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.testhelpers.TestHelpers;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.domain.helpers.DummyUsers;
import ch.threema.domain.helpers.InMemoryContactStore;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.QueueMessageId;
import ch.threema.domain.protocol.csp.messages.BoxTextMessage;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageQueueTest {
	private ContactStore contactStore;
	private IdentityStoreInterface identityStore;
	private ThreemaConnection connection;
	private MessageQueue mq;

	private String identity1;
	private String identity2;

	@Before
	public void makeQueue() {
		this.contactStore = new InMemoryContactStore();
		this.identityStore = DummyUsers.getIdentityStoreForUser(DummyUsers.CAROL);
		this.connection = mock(ThreemaConnection.class);
		when(this.connection.getNonceFactory()).thenReturn(TestHelpers.getNoopNonceFactory());
		this.mq = new MessageQueue(this.contactStore, this.identityStore, this.connection);

		// Insert identities
		this.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.ALICE));
		this.contactStore.addContact(DummyUsers.getContactForUser(DummyUsers.BOB));

		this.identity1 = DummyUsers.ALICE.getIdentity();
		this.identity2 = DummyUsers.BOB.getIdentity();
	}

	@Test
	public void testProcessAckUnknownMessage() {
		// Does not throw
		this.mq.processAck(new QueueMessageId(new MessageId(), "0AAABBBB"));
	}

	@Test
	public void testProcessAck() throws ThreemaException {
		Assert.assertEquals(0, this.mq.getQueueSize());

		// Create message
		final MessageId msgId = new MessageId();
		final BoxTextMessage msg = new BoxTextMessage();
		msg.setMessageId(msgId);
		msg.setToIdentity(this.identity1);
		msg.setText("Hello!!!");

		// Enqueue message
		this.mq.enqueue(msg);
		Assert.assertEquals(1, this.mq.getQueueSize());

		// Process ack, wrong identity
		this.mq.processAck(new QueueMessageId(msgId, this.identity2));
		Assert.assertEquals(1, this.mq.getQueueSize());

		// Process ack, wrong message ID
		this.mq.processAck(new QueueMessageId(new MessageId(), this.identity1));
		Assert.assertEquals(1, this.mq.getQueueSize());

		// Process ack, success
		this.mq.processAck(new QueueMessageId(msgId, this.identity1));
		Assert.assertEquals(0, this.mq.getQueueSize());
	}

	/**
	 * Test enqueue, dequeue and isQueued methods.
	 */
	@Test
	public void testEnqueueDequeue() throws ThreemaException {
		// Create message
		final MessageId msgId = new MessageId();
		final BoxTextMessage msg = new BoxTextMessage();
		msg.setMessageId(msgId);
		msg.setToIdentity(this.identity1);
		msg.setText("Hello!!!");

		// Not enqueued
		final QueueMessageId queueMessageId = new QueueMessageId(msgId, this.identity1);
		Assert.assertFalse(this.mq.isQueued(queueMessageId));

		// Enqueue
		this.mq.enqueue(msg);
		Assert.assertTrue(this.mq.isQueued(queueMessageId));
		Assert.assertFalse(this.mq.isQueued(new QueueMessageId(new MessageId(), this.identity1)));
		Assert.assertFalse(this.mq.isQueued(new QueueMessageId(msgId, this.identity2)));

		// Dequeue
		this.mq.dequeue(queueMessageId);
		Assert.assertFalse(this.mq.isQueued(queueMessageId));
	}

	/**
	 * Test the dequeueAll method.
	 */
	@Test
	public void testDequeueAll() throws ThreemaException {
		// Two message IDs
		final MessageId msgId1 = new MessageId();
		final MessageId msgId2 = new MessageId();

		// Three messages
		final BoxTextMessage msg1 = new BoxTextMessage();
		msg1.setMessageId(msgId1);
		msg1.setToIdentity(this.identity1);
		msg1.setText("Hello!!!");
		final BoxTextMessage msg2 = new BoxTextMessage();
		msg2.setMessageId(msgId2);
		msg2.setToIdentity(this.identity1);
		msg2.setText("Another message");
		final BoxTextMessage msg3 = new BoxTextMessage();
		msg3.setMessageId(msgId1); // Re-use!
		msg3.setToIdentity(this.identity2);
		msg3.setText("Hello!!!");

		// Not enqueued
		Assert.assertEquals(0, this.mq.getQueueSize());

		// Enqueue
		this.mq.enqueue(msg1);
		this.mq.enqueue(msg2);
		this.mq.enqueue(msg3);
		Assert.assertEquals(3, this.mq.getQueueSize());

		// Dequeue all
		this.mq.dequeueAll(msgId1);
		Assert.assertEquals(1, this.mq.getQueueSize());
		Assert.assertTrue(this.mq.isQueued(Objects.requireNonNull(msg2.getQueueMessageId())));
	}
}
