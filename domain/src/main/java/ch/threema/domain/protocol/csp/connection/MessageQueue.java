/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import androidx.annotation.NonNull;
import org.slf4j.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.QueueMessageId;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;

/**
 * Message queue, used to send messages.
 *
 * This class is thread safe.
 */
public class MessageQueue implements MessageAckListener, ConnectionStateListener {

	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageQueue");

	private final ContactStore contactStore;
	private final IdentityStoreInterface identityStore;
	private final ThreemaConnection con;
	private ConnectionState connectionState;

	private final LinkedList<MessageBox> queue;

	private MessageEnqueueListener messageEnqueueListener = null;

	public MessageQueue(ContactStore contactStore, IdentityStoreInterface identityStore, ThreemaConnection con) {
		this.contactStore = contactStore;
		this.identityStore = identityStore;
		this.con = con;

		queue = new LinkedList<>();

		/* add ourselves as an ACK listener to the connection */
		con.addMessageAckListener(this);
		con.addConnectionStateListener(this);
	}

	/**
	 * A listener that can be set to listen to message enqueue events.
	 */
	public interface MessageEnqueueListener {
		void onMessageEnqueued(@NonNull AbstractMessage message);
	}

	public void setMessageEnqueueListener(@Nullable MessageEnqueueListener messageEnqueueListener) {
		this.messageEnqueueListener = messageEnqueueListener;
	}

	/**
	 * Send a message if there is a connection. If there is no connection, the message is added to
	 * the queue and sent later - except the no server queuing flag is set.
	 *
	 * @param message the message that should be sent
	 * @return the message box of the enqueued message
	 * @throws ThreemaException if creating the message box failed
	 */
	@NonNull
	public synchronized MessageBox enqueue(@NonNull AbstractMessage message) throws ThreemaException {
		logger.debug("Enqueue message");

		/* add missing attributes, if necessary */
		if (message.getFromIdentity() == null)
			message.setFromIdentity(identityStore.getIdentity());

		/* make box */
		MessageCoder messageCoder = new MessageCoder(contactStore, identityStore);
		MessageBox boxmsg = messageCoder.encode(message, this.con.getNonceFactory());

		// for the sake of efficiency: simply deduct overhead size
		final int overhead = ProtocolDefines.OVERHEAD_MSG_HDR
			+ ProtocolDefines.OVERHEAD_NACL_BOX
			+ ProtocolDefines.OVERHEAD_PKT_HDR;
		if (boxmsg.getBox() != null && boxmsg.getBox().length > (ProtocolDefines.MAX_PKT_LEN - overhead)) {
			throw new MessageTooLongException();
		}

		if (connectionState == ConnectionState.LOGGEDIN) {
			logger.info("Currently connected - sending message {} now", message.getMessageId());

			con.sendBoxedMessage(boxmsg);

			/* Only add to queue if we want an ACK for this message */
			if (!message.flagNoServerAck()) {
				queue.add(boxmsg);
			}
		} else {
			if (message.flagNoServerQueuing()) {
				logger.debug("Discarding non-queued message because not connected");
			} else {
				queue.add(boxmsg);
			}
		}

		if (messageEnqueueListener != null) {
			messageEnqueueListener.onMessageEnqueued(message);
		}

		return boxmsg;
	}

	public synchronized boolean isQueued(@NonNull QueueMessageId queueMessageId) {
		for (MessageBox boxmsg : queue) {
			if (boxmsg.getMessageId().equals(queueMessageId.getMessageId())
				&& boxmsg.getToIdentity().equals(queueMessageId.getRecipientId()))
				return true;
		}
		return false;
	}

	/**
	 * Remove a message from the message queue if it's in the queue.
	 * @return true if a message has been dequeued, false otherwise
	 */
	public synchronized boolean dequeue(@NonNull QueueMessageId queueMessageId) {
		Iterator<MessageBox> it = queue.iterator();
		while (it.hasNext()) {
			final MessageBox next = it.next();
			// Compare both message ID and recipient ID
			if (next.getMessageId().equals(queueMessageId.getMessageId())
				&& next.getToIdentity().equals(queueMessageId.getRecipientId())) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	/**
	 * Remove all messages from the message queue with a matching message ID.
	 *
	 * Note: There may be multiple messages with the same message ID in the queue,
	 * but with different recipients.
	 *
	 * @return the number of dequeued messages.
	 */
	public synchronized int dequeueAll(@NonNull MessageId messageId) {
		final Iterator<MessageBox> it = queue.iterator();
		int dequeuedCount = 0;
		while (it.hasNext()) {
			final MessageBox next = it.next();
			// Compare only the message ID
			if (next.getMessageId().equals(messageId)) {
				it.remove();
				dequeuedCount += 1;
			}
		}
		return dequeuedCount;
	}

	/**
	 * Process incoming server ack, remove corresponding message from queue.
	 */
	@Override
	public synchronized void processAck(@NonNull QueueMessageId queueMessageId) {
		logger.debug("Processing server ack for message ID {} from {}", queueMessageId.getMessageId(), queueMessageId.getRecipientId());

		// Find this message in the queue and remove it
		final Iterator<MessageBox> it = queue.iterator();
		while (it.hasNext()) {
			final MessageBox next = it.next();
			// Compare both message ID and recipient ID
			if (next.getMessageId().equals(queueMessageId.getMessageId())
					&& next.getToIdentity().equals(queueMessageId.getRecipientId())) {
				it.remove();
				return;
			}
		}

		logger.warn("Message ID {} from {} not found in queue", queueMessageId.getMessageId(), queueMessageId.getRecipientId());
	}

	public synchronized int getQueueSize() {
		return queue.size();
	}

	public synchronized List<MessageBox> getQueue() {
		return queue;
	}

	public synchronized void flushQueue() {
		queue.clear();
	}

	private synchronized void processQueue() {
		logger.info("Processing queue");

		/* Send all messages in our queue */
		for (MessageBox boxmsg : queue) {
			con.sendBoxedMessage(boxmsg);
		}
	}

	@Override
	public synchronized void updateConnectionState(ConnectionState connectionState, InetSocketAddress socketAddress) {
		this.connectionState = connectionState;
		if (connectionState == ConnectionState.LOGGEDIN) {
			processQueue();
		}
	}

	public synchronized void serializeToStream(OutputStream os) throws IOException {
		ObjectOutputStream oos = new ObjectOutputStream(os);

		for (MessageBox msg : queue) {
			oos.writeObject(msg);
		}

		oos.close();
	}

	public synchronized void unserializeFromStream(InputStream is) throws IOException, ClassNotFoundException {
		ObjectInputStream ois = new ObjectInputStream(is);
		String myId = identityStore.getIdentity();

		if (myId == null)
			return;

		while (true) {
			try {
				MessageBox msg = (MessageBox)ois.readObject();

				/* make sure this message matches our own current ID (the user may have switched IDs in the meantime) */
				if (!myId.equals(msg.getFromIdentity()))
					continue;

				queue.add(msg);

			} catch (EOFException e) {
				break;
			}
		}

		ois.close();

		processQueue();
	}
}
