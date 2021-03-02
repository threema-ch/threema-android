/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

import androidx.annotation.NonNull;
import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.WorkerThread;
import ch.threema.base.ThreemaException;

@WorkerThread
public class ThreemaConnection implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(ThreemaConnection.class);

	private static final int IDENTITY_LEN = 8;
	private static final int COOKIE_LEN = 16;
	private static final int SERVER_HELLO_BOXLEN = NaCl.PUBLICKEYBYTES + COOKIE_LEN + NaCl.BOXOVERHEAD;
	private static final int VOUCH_LEN = NaCl.PUBLICKEYBYTES;
	private static final int VERSION_LEN = 32;
	private static final int LOGIN_LEN = IDENTITY_LEN + VERSION_LEN + COOKIE_LEN + NaCl.NONCEBYTES + VOUCH_LEN + NaCl.BOXOVERHEAD;
	private static final int LOGIN_ACK_RESERVED_LEN = 16;
	private static final int LOGIN_ACK_LEN = LOGIN_ACK_RESERVED_LEN + NaCl.BOXOVERHEAD;

	/* Delegate objects */
	private final IdentityStoreInterface identityStore;
	private final NonceFactory nonceFactory;
	private MessageProcessorInterface messageProcessor;

	/* Permanent data */
	private final String serverNamePrefix;
	private final String serverNameSuffix;
	private final int serverPort;
	private final int serverPortAlt;
	private final boolean useServerGroup;
	private final byte[] serverPubKey;
	private final byte[] serverPubKeyAlt;

	/* Temporary data for each individual TCP connection */
	private volatile Socket socket;
	private byte[] clientTempKeyPub;
	private byte[] clientTempKeySec;
	private SenderThread senderThread;
	private int lastSentEchoSeq;
	private int lastRcvdEchoSeq;

	/* Connection state dependent objects */
	private volatile ConnectionState state;
	private volatile Thread curThread;
	private volatile boolean running;
	private final @NonNull AtomicInteger connectionNumber = new AtomicInteger();
	private int reconnectAttempts;
	private int curSocketAddressIndex;
	private ArrayList<InetSocketAddress> serverSocketAddresses;

	/* Helper objects */
	private final SecureRandom random;
	private final Timer timer;
	private Date clientTempKeyGenTime;
	private int pushTokenType;
	private String pushToken;
	private final Set<String> lastAlertMessages;
	private Version version;
	private int anotherConnectionCount;

	/* Listeners */
	private final Set<MessageAckListener> ackListeners;
	private final Set<ConnectionStateListener> connectionStateListeners;
	private final Set<QueueSendCompleteListener> queueSendCompleteListeners;


	/**
	 * Create a new ThreemaConnection.
	 *
	 * @param identityStore the identity store to use for login
	 * @param serverNamePrefix prefix for server name (prepended to server group)
	 * @param serverNameSuffix suffix for server name (appended to server group)
	 * @param serverPort default server port
	 * @param serverPortAlt alternate server port (used if the default port does not work)
	 * @param ipv6 whether to use IPv4+IPv6 for connection, or only IPv4
	 * @param serverPubKey server public key
	 * @param serverPubKeyAlt alternate server public key
	 * @param useServerGroup whether to use the server group
	 */
	public ThreemaConnection(IdentityStoreInterface identityStore,
							 NonceFactory nonceFactory,
							 String serverNamePrefix,
							 String serverNameIPv6Prefix,
							 String serverNameSuffix,
							 int serverPort,
							 int serverPortAlt,
							 boolean ipv6,
							 byte[] serverPubKey,
							 byte[] serverPubKeyAlt,
							 boolean useServerGroup) {
		this.identityStore = identityStore;
		this.nonceFactory = nonceFactory;
		this.serverNamePrefix = (ipv6 ? serverNameIPv6Prefix : "") + serverNamePrefix;
		this.serverNameSuffix = serverNameSuffix;
		this.serverPort = serverPort;
		this.serverPortAlt = serverPortAlt;
		this.serverPubKey = serverPubKey;
		this.serverPubKeyAlt = serverPubKeyAlt;
		this.useServerGroup = useServerGroup;
		this.curSocketAddressIndex = 0;
		this.serverSocketAddresses = new ArrayList<>();
		this.connectionNumber.set(0);

		random = new SecureRandom();
		timer = new Timer(/*"ThreemaConnectionTimer", */true);

		ackListeners = new HashSet<>();
		connectionStateListeners = new HashSet<>();
		queueSendCompleteListeners = new CopyOnWriteArraySet<>();

		lastAlertMessages = new HashSet<>();

		state = ConnectionState.DISCONNECTED;

		version = new Version();
	}

	public MessageProcessorInterface getMessageProcessor() {
		return messageProcessor;
	}

	public void setMessageProcessor(MessageProcessorInterface messageProcessor) {
		this.messageProcessor = messageProcessor;
	}

	private void getInetAdresses() throws UnknownHostException, ExecutionException, InterruptedException {
		ArrayList<InetSocketAddress> addresses = new ArrayList<>();

		String serverHost = serverNamePrefix + (useServerGroup ? identityStore.getServerGroup() : ".") + serverNameSuffix;

		if (ProxyAwareSocketFactory.shouldUseProxy(serverHost, serverPort)) {
			// Create unresolved addresses for proxy
			addresses.add(InetSocketAddress.createUnresolved(serverHost, serverPort));
			addresses.add(InetSocketAddress.createUnresolved(serverHost, serverPortAlt));
		} else {
			InetAddress[] inetAddresses = AsyncResolver.getAllByName(serverHost);
			if (inetAddresses.length == 0) {
				throw new UnknownHostException();
			}

			Arrays.sort(inetAddresses, new Comparator<InetAddress>() {
				@Override
				public int compare(InetAddress o1, InetAddress o2) {
					if (o1 instanceof Inet6Address) {
						if (o2 instanceof Inet6Address) {
							return o1.getHostAddress().compareTo(o2.getHostAddress());
						} else {
							return -1;
						}
					}
					if (o2 instanceof Inet4Address) {
						return o1.getHostAddress().compareTo(o2.getHostAddress());
					}
					return 1;
				}
			});

			for (InetAddress inetAddress : inetAddresses) {
				addresses.add(new InetSocketAddress(inetAddress, serverPort));
				addresses.add(new InetSocketAddress(inetAddress, serverPortAlt));
			}
		}

		if (addresses.size() != serverSocketAddresses.size()) {
			serverSocketAddresses = addresses;
			curSocketAddressIndex = 0;
			return;
		}

		for (int i = 0; i < addresses.size(); i++) {
			// Check if any of the resolved addresses have changed. We also need to check whether
			// we have switched between unresolved and resolved addresses.
			if ((addresses.get(i).getAddress() == null && serverSocketAddresses.get(i).getAddress() != null) ||
				(addresses.get(i).getAddress() != null && serverSocketAddresses.get(i).getAddress() == null) ||
				(addresses.get(i).getAddress() != null && serverSocketAddresses.get(i).getAddress() != null && !addresses.get(i).getAddress().getHostAddress().equals(serverSocketAddresses.get(i).getAddress().getHostAddress()))) {
				serverSocketAddresses = addresses;
				curSocketAddressIndex = 0;
				return;
			}
		}
	}

	/**
	 * Start the ThreemaConnection thread.
	 */
	public synchronized void start() {
		if (curThread != null)
			return;

		running = true;

		curThread = new Thread(this, "ThreemaConnection");
		curThread.start();
	}

	/**
	 * Stop the connection and wait for the connection thread to terminate.
	 *
	 * Because this calls {@link Thread#join} (and thus blocks), it should only be called
	 * from a worker thread, not from the main thread.
	 */
	@WorkerThread
	public synchronized void stop() throws InterruptedException {
		Thread myCurThread = curThread;

		if (myCurThread == null) {
			return;
		}

		running = false;

		/* must close socket, as interrupt() will not interrupt socket read */
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (Exception e) {
			logger.warn("Ignored exception", e);
		}

		myCurThread.interrupt();
		// TODO(ANDR-1216): THIS CALL IS CURRENTLY THE MOST PROMINENT CAUSE FOR ANRs :-(
		myCurThread.join();
	}

	public boolean isRunning() {
		return (curThread != null);
	}

	public ConnectionState getConnectionState() {
		return state;
	}

	public boolean sendBoxedMessage(BoxedMessage boxedMessage) {
		logger.info("sendBoxedMessage " + boxedMessage.getMessageId());

		return sendPayload(boxedMessage.makePayload());
	}

	/**
	 * Set the push token to be used by the server when pushing messages to this client. This method
	 * can be called no matter if the client is currently connected to the server.
	 *
	 * @param pushTokenType the push token type (usually ProtocolDefines.PUSHTOKEN_TYPE_GCM)
	 * @param pushToken the new push token (or "registration ID" in case of GCM)
	 */
	public void setPushToken(int pushTokenType, String pushToken) throws ThreemaException {
		/* new token - store and send it */
		this.pushTokenType = pushTokenType;
		this.pushToken = pushToken;

		if (getConnectionState() != ConnectionState.LOGGEDIN || !sendPushToken()) {
			throw new ThreemaException("Unable to send / clear push token. Make sure you're online.");
		}
	}

	public boolean sendPayload(Payload payload) {
		/* delegate to sender thread, if connected */
		SenderThread mySenderThread = senderThread;
		if (mySenderThread != null) {
			mySenderThread.sendPayload(payload);
			return true;
		} else {
			logger.info("SenderThread not available");
			return false;
		}
	}

	public Version getVersion() {
		return version;
	}

	/**
	 * Set the version object to be used for communicating the client version to the server.
	 * Defaults to a plain Version object that only includes generic Java information.
	 *
	 * @param version
	 */
	public void setVersion(Version version) {
		this.version = version;
	}

	@Override
	public void run() {
		/* generate a new temporary key pair for the server connection, if necessary */
		if (clientTempKeyPub == null || clientTempKeySec == null || ((new Date().getTime() - clientTempKeyGenTime.getTime()) / 1000) > ProtocolDefines.CLIENT_TEMPKEY_MAXAGE) {
			clientTempKeyPub = new byte[NaCl.PUBLICKEYBYTES];
			clientTempKeySec = new byte[NaCl.SECRETKEYBYTES];
			NaCl.genkeypair(clientTempKeyPub, clientTempKeySec);
			clientTempKeyGenTime = new Date();
		}

		anotherConnectionCount = 0;

		while (running) {
			TimerTask echoSendTask = null;

			try {
				getInetAdresses();

				connectionNumber.getAndIncrement();
				setConnectionState(ConnectionState.CONNECTING);

				InetSocketAddress address = serverSocketAddresses.get(curSocketAddressIndex);
				logger.info("Connecting to {}...", address);

				socket = ProxyAwareSocketFactory.makeSocket(address);

				socket.connect(address, address.getAddress() instanceof Inet6Address ?
						ProtocolDefines.CONNECT_TIMEOUT_IPV6 * 1000 :
						ProtocolDefines.CONNECT_TIMEOUT * 1000);

				setConnectionState(ConnectionState.CONNECTED);

				DataInputStream dis = new DataInputStream(socket.getInputStream());
				OutputStream bos = new BufferedOutputStream(socket.getOutputStream());

				/* set socket timeout for connection phase */
				socket.setSoTimeout(ProtocolDefines.READ_TIMEOUT * 1000);

				/* send client hello */
				byte[] clientCookie = new byte[COOKIE_LEN];
				random.nextBytes(clientCookie);
				if (logger.isDebugEnabled()) {
					logger.debug("Client cookie = {}", NaCl.asHex(clientCookie));
				}

				bos.write(clientTempKeyPub);
				bos.write(clientCookie);
				bos.flush();

				/* read server hello */
				byte[] serverCookie = new byte[COOKIE_LEN];
				dis.readFully(serverCookie);
				if (logger.isDebugEnabled()) {
					logger.debug("Server cookie = {}", NaCl.asHex(serverCookie));
				}

				NonceCounter serverNonce = new NonceCounter(serverCookie);
				NonceCounter clientNonce = new NonceCounter(clientCookie);

				byte[] serverHelloBox = new byte[SERVER_HELLO_BOXLEN];
				dis.readFully(serverHelloBox);

				/* decrypt server hello */
				byte[] nonce = serverNonce.nextNonce();
				if (logger.isDebugEnabled()) {
					logger.debug("Server nonce = {}", NaCl.asHex(nonce));
				}

				/* precalculate shared keys */
				byte[] serverPubKeyCur = serverPubKey;
				NaCl kclientTempServerPerm = new NaCl(clientTempKeySec, serverPubKeyCur);

				byte[] serverHello = kclientTempServerPerm.decrypt(serverHelloBox, nonce);

				if (serverHello == null) {
					/* Try again with alternate key */
					serverPubKeyCur = serverPubKeyAlt;
					kclientTempServerPerm = new NaCl(clientTempKeySec, serverPubKeyCur);
					serverHello = kclientTempServerPerm.decrypt(serverHelloBox, nonce);
					if (serverHello == null) {
						throw new ThreemaException("Decryption of server hello box failed");
					}
				}

				/* extract server tempkey and client cookie from server hello */
				byte[] serverTempKeyPub = new byte[NaCl.PUBLICKEYBYTES];
				byte[] clientCookieFromServer = new byte[COOKIE_LEN];
				System.arraycopy(serverHello, 0, serverTempKeyPub, 0, NaCl.PUBLICKEYBYTES);
				System.arraycopy(serverHello, NaCl.PUBLICKEYBYTES, clientCookieFromServer, 0, COOKIE_LEN);

				/* verify client copy */
				if (!Arrays.equals(clientCookie, clientCookieFromServer)) {
					throw new ThreemaException("Client cookie mismatch");
				}

				logger.info("Server hello successful");

				/* prepare vouch sub packet */
				byte[] vouchNonce = new byte[NaCl.NONCEBYTES];
				random.nextBytes(vouchNonce);
				byte[] vouchBox = identityStore.encryptData(clientTempKeyPub, vouchNonce, serverPubKeyCur);
				if (vouchBox == null) {
					throw new ThreemaException("Vouch box encryption failed");
				}

				/* now prepare login packet */
				byte[] version = this.makeVersion();
				byte[] login = new byte[LOGIN_LEN];
				int login_i = 0;
				System.arraycopy(identityStore.getIdentity().getBytes(), 0, login, 0, IDENTITY_LEN);
				login_i += IDENTITY_LEN;
				System.arraycopy(version, 0, login, login_i, VERSION_LEN);
				login_i += VERSION_LEN;
				System.arraycopy(serverCookie, 0, login, login_i, COOKIE_LEN);
				login_i += COOKIE_LEN;
				System.arraycopy(vouchNonce, 0, login, login_i, NaCl.NONCEBYTES);
				login_i += NaCl.NONCEBYTES;
				System.arraycopy(vouchBox, 0, login, login_i, VOUCH_LEN + NaCl.BOXOVERHEAD);

				/* encrypt login packet */
				NaCl kclientTempServerTemp = new NaCl(clientTempKeySec, serverTempKeyPub);
				byte[] loginBox = kclientTempServerTemp.encrypt(login, clientNonce.nextNonce());

				/* send it! */
				bos.write(loginBox);
				bos.flush();
				logger.debug("Sent login packet");

				/* read login ack */
				byte[] loginackBox = new byte[LOGIN_ACK_LEN];
				dis.readFully(loginackBox);

				/* decrypt login ack */
				byte[] loginack = kclientTempServerTemp.decrypt(loginackBox, serverNonce.nextNonce());
				if (loginack == null) {
					throw new ThreemaException("Decryption of login ack box failed");
				}

				logger.info("Login ack received");

				/* clear socket timeout */
				socket.setSoTimeout(0);
				reconnectAttempts = 0;

				/* fire up sender thread to send packets while we are receiving */
				senderThread = new SenderThread(bos, kclientTempServerTemp, clientNonce);
				senderThread.start();

				/* schedule timer task for sending echo packets */
				echoSendTask = new TimerTask() {
					@Override
					public void run() {
						sendEchoRequest();
					}
				};
				timer.schedule(echoSendTask, ProtocolDefines.KEEPALIVE_INTERVAL * 1000, ProtocolDefines.KEEPALIVE_INTERVAL * 1000);

				/* tell our listeners */
				setConnectionState(ConnectionState.LOGGEDIN);

				/* receive packets until the connection dies */
				while (running) {
					int length = EndianUtils.swapShort(dis.readShort());
					byte[] data = new byte[length];

					dis.readFully(data);
					logger.debug("Received payload ({} bytes)", length);

					if (length < 4) {
						logger.error("Short payload received (" + length + " bytes)");
						break;
					}

					/* decrypt payload */
					byte[] decrypted = kclientTempServerTemp.decrypt(data, serverNonce.nextNonce());
					if (decrypted == null) {
						logger.error("Payload decryption failed");
						break;
					}

					int payloadType = decrypted[0] & 0xFF;
					byte[] payloadData = new byte[decrypted.length - 4];
					System.arraycopy(decrypted, 4, payloadData, 0, decrypted.length - 4);
					Payload payload = new Payload(payloadType, payloadData);
					processPayload(payload);
				}

			} catch (Exception e) {
				if (running) {
					logger.warn("Connection exception", e);

					if (state != ConnectionState.LOGGEDIN) {
						/* switch to alternate port */
						curSocketAddressIndex++;
						if (curSocketAddressIndex >= serverSocketAddresses.size()) {
							curSocketAddressIndex = 0;
						}
					}
				}
			}

			setConnectionState(ConnectionState.DISCONNECTED);

			if (senderThread != null) {
				senderThread.shutdown();
				senderThread = null;
			}

			if (echoSendTask != null)
				echoSendTask.cancel();

			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					logger.warn("Ignored IOException", e);
				}
				socket = null;
			}

			if (!running) {
				break;  /* no point in sleeping if we're not going to reconnect */
			}

			/* Sleep before reconnecting (bounded exponential backoff) */
			double reconnectDelay = Math.pow(ProtocolDefines.RECONNECT_BASE_INTERVAL, Math.min(reconnectAttempts - 1, 10));
			if (reconnectDelay > ProtocolDefines.RECONNECT_MAX_INTERVAL)
				reconnectDelay = ProtocolDefines.RECONNECT_MAX_INTERVAL;
			reconnectAttempts++;

			try {
				/* Don't reconnect too quickly */
				logger.debug("Waiting {} seconds before reconnecting", reconnectDelay);
				Thread.sleep((long) (reconnectDelay * 1000));
			} catch (InterruptedException ignored) {
				// We were interrupted. Break the main loop.
				logger.debug("Interrupted");
				break;
			}
		}

		curThread = null;
		logger.info("Ended");
	}

	public void addMessageAckListener(MessageAckListener listener) {
		ackListeners.add(listener);
	}

	public void removeMessageAckListener(MessageAckListener listener) {
		ackListeners.remove(listener);
	}

	/**
	 * Notify active listeners that a new message ack was received from the server.
	 */
	private void notifyMessageAckListeners(@NonNull MessageAck messageAck) {
		for (MessageAckListener listener : ackListeners) {
			try {
				listener.processAck(messageAck);
			} catch (Exception e) {
				logger.warn("Exception while invoking message ACK listener", e);
			}
		}
	}

	public void addConnectionStateListener(ConnectionStateListener listener) {
		synchronized (connectionStateListeners) {
			connectionStateListeners.add(listener);
		}
	}

	public void removeConnectionStateListener(ConnectionStateListener listener) {
		synchronized (connectionStateListeners) {
			connectionStateListeners.remove(listener);
		}
	}

	private void setConnectionState(ConnectionState state) {
		this.state = state;

		if (curSocketAddressIndex < serverSocketAddresses.size()) {
			synchronized (connectionStateListeners) {
				for (ConnectionStateListener listener : connectionStateListeners) {
					try {
						listener.updateConnectionState(state, serverSocketAddresses.get(curSocketAddressIndex));
					} catch (Exception e) {
						logger.warn("Exception while invoking connection state listener", e);
					}
				}
			}
		}
	}

	public void addQueueSendCompleteListener(QueueSendCompleteListener listener) {
		queueSendCompleteListeners.add(listener);
	}

	public void removeQueueSendCompleteListener(QueueSendCompleteListener listener) {
		queueSendCompleteListeners.remove(listener);
	}

	private void notifyQueueSendComplete() {
		for (QueueSendCompleteListener listener : queueSendCompleteListeners) {
			try {
				listener.queueSendComplete();
			} catch (Exception e) {
				logger.warn("Exception while invoking queue send complete listener", e);
			}
		}
	}

	private void processPayload(Payload payload) throws PayloadProcessingException {
		byte[] data = payload.getData();

		logger.debug("Payload type {}", payload.getType());

		switch (payload.getType()) {
			case ProtocolDefines.PLTYPE_ECHO_REPLY:
				if (data.length == 4) {
					lastRcvdEchoSeq = Utils.byteArrayToInt(data);
				} else {
					throw new PayloadProcessingException("Bad length (" + data.length + ") for echo reply payload");
				}

				logger.info("Received echo reply (seq {})", lastRcvdEchoSeq);
				break;

			case ProtocolDefines.PLTYPE_ERROR:
				if (data.length < 1) {
					throw new PayloadProcessingException("Bad length (" + data.length + ") for error payload");
				}

				int reconnectAllowed = data[0] & 0xFF;
				String errorMessage = new String(data, 1, data.length - 1, StandardCharsets.UTF_8);

				logger.error("Received error message from server: {}", errorMessage);

				/* workaround for stray "Another connection" messages due to weird timing
				   when switching between networks: ignore first few occurrences */
				if (errorMessage.contains("Another connection") && anotherConnectionCount < 5) {
					anotherConnectionCount++;
					break;
				}

				if (messageProcessor != null) {
					messageProcessor.processServerError(errorMessage, reconnectAllowed != 0);
				}

				if (reconnectAllowed == 0) {
					running = false;
				}

				break;

			case ProtocolDefines.PLTYPE_ALERT:
				final String alertMessage = new String(data, StandardCharsets.UTF_8);
				logger.info("Received alert message from server: {}", alertMessage);

				if (!lastAlertMessages.contains(alertMessage)) {
					if (messageProcessor != null) {
						messageProcessor.processServerAlert(alertMessage);
						lastAlertMessages.add(alertMessage);
					}
				}
				break;

			case ProtocolDefines.PLTYPE_OUTGOING_MESSAGE_ACK:
				processOutgoingMessageAckPayload(payload);
				break;

			case ProtocolDefines.PLTYPE_INCOMING_MESSAGE:
				processIncomingMessagePayload(payload);
				break;

			case ProtocolDefines.PLTYPE_QUEUE_SEND_COMPLETE:
				notifyQueueSendComplete();
				break;
		}
	}

	/**
	 * Process a message ack payload received from the server.
	 */
	private void processOutgoingMessageAckPayload(@NonNull Payload payload) throws PayloadProcessingException {
		final byte[] data = payload.getData();

		// Validate message length
		if (data.length != ProtocolDefines.IDENTITY_LEN + ProtocolDefines.MESSAGE_ID_LEN) {
			throw new PayloadProcessingException("Bad length (" + data.length + ") for message ack payload");
		}

		// Recipient identity
		byte[] recipientId = new byte[ProtocolDefines.IDENTITY_LEN];
		System.arraycopy(data, 0, recipientId, 0, ProtocolDefines.IDENTITY_LEN);

		// Message ID
		final MessageId messageId = new MessageId(data, ProtocolDefines.IDENTITY_LEN /* offset */);

		// Create MessageAck instance
		final MessageAck ack = new MessageAck(recipientId, messageId);
		logger.debug("Received message ack for message {} to {}", ack.getMessageId(), ack.getRecipientId());

		// Notify listeners
		notifyMessageAckListeners(ack);
	}

	private void processIncomingMessagePayload(Payload payload) throws PayloadProcessingException {
		byte[] data = payload.getData();

		if (data.length < ProtocolDefines.OVERHEAD_MSG_HDR) {
			throw new PayloadProcessingException("Bad length (" + data.length + ") for message payload");
		}

		try {
			BoxedMessage boxmsg = BoxedMessage.parsePayload(payload);

			logger.info("Incoming message from {} (ID {})", boxmsg.getFromIdentity(), boxmsg.getMessageId());

			if (messageProcessor != null) {
				boolean ackMessage;
				if (!this.nonceFactory.exists(boxmsg.getNonce())) {

					MessageProcessorInterface.ProcessIncomingResult result = messageProcessor.processIncomingMessage(boxmsg);

					// Save nonce if the incoming message was successfully processed
					// and if the message is *not* a typing indicator
					if (result != null
							&& result.processed
							&& result.abstractMessage != null
							&& result.abstractMessage.getType() != ProtocolDefines.MSGTYPE_TYPING_INDICATOR) {
						this.nonceFactory.store(boxmsg.getNonce());
					}

					ackMessage = result != null && result.processed;
				} else {
					// auto ack a already nonce'd message
					ackMessage = true;
				}

				if (ackMessage && (boxmsg.getFlags() & ProtocolDefines.MESSAGE_FLAG_NOACK) == 0) {
					sendAck(boxmsg.getMessageId(), boxmsg.getFromIdentity());
				}
			}

		} catch (Exception e) {
			/* don't break connection if we cannot parse the message (may not be the server's fault) */
			logger.warn("Box message parse failed", e);
		}
	}

	private void sendAck(MessageId messageId, String identity) {
		logger.debug("Sending ack for message ID {} from {}", messageId, identity);

		byte[] plData = new byte[ProtocolDefines.IDENTITY_LEN + ProtocolDefines.MESSAGE_ID_LEN];

		byte[] identityB = identity.getBytes();
		System.arraycopy(identityB, 0, plData, 0, ProtocolDefines.IDENTITY_LEN);
		System.arraycopy(messageId.getMessageId(), 0, plData, ProtocolDefines.IDENTITY_LEN, ProtocolDefines.MESSAGE_ID_LEN);

		Payload ackPayload = new Payload(ProtocolDefines.PLTYPE_INCOMING_MESSAGE_ACK, plData);
		sendPayload(ackPayload);
	}

	private void sendEchoRequest() {
		lastSentEchoSeq++;
		logger.debug("Sending echo request (seq {})", lastSentEchoSeq);

		byte[] echoData = Utils.intToByteArray(lastSentEchoSeq);
		Payload echoPayload = new Payload(ProtocolDefines.PLTYPE_ECHO_REQUEST, echoData);
		sendPayload(echoPayload);

		/* schedule timer task to check that we have received the echo reply */
		final int curConnectionNumber = connectionNumber.get();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (connectionNumber.get() == curConnectionNumber && lastRcvdEchoSeq < lastSentEchoSeq) {
					logger.info("No reply to echo payload; reconnecting");

					try {
						Socket s = socket;  /* avoid race condition */
						if (s != null) {
							s.close();
						}
					} catch (Exception ignored) { }
				}
			}
		}, ProtocolDefines.READ_TIMEOUT * 1000);
	}

	private boolean sendPushToken() {
		if (this.pushToken != null) {
			logger.debug("Sending push token type {}: {}", pushTokenType, pushToken);

			byte[] pushTokenBytes = pushToken.getBytes(StandardCharsets.US_ASCII);
			byte[] pushTokenData = new byte[pushTokenBytes.length + 1];

			/* prepend token type */
			pushTokenData[0] = (byte) pushTokenType;
			System.arraycopy(pushTokenBytes, 0, pushTokenData, 1, pushTokenBytes.length);

			/* send regular push token */
			final Payload pushPayload = new Payload(ProtocolDefines.PLTYPE_PUSH_NOTIFICATION_TOKEN, pushTokenData);
			if (sendPayload(pushPayload)) {
				/* Send voip push token */
				/* This is identical to the regular push token. */
				final Payload voipPushPayload = new Payload(ProtocolDefines.PLTYPE_VOIP_PUSH_NOTIFICATION_TOKEN, pushTokenData);
				if (sendPayload(voipPushPayload)) {
					/* clear push filter (we don't need pushes to be filtered as we can handle "block unknown"/blacklist ourselves) */
					Payload pushfilterPayload = new Payload(ProtocolDefines.PLTYPE_PUSH_ALLOWED_IDENTITIES, new byte[1]);
					if (sendPayload(pushfilterPayload)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private byte[] makeVersion() {
		byte[] versionTrunc = new byte[VERSION_LEN];
		byte[] versionBytes = version.getFullVersion().getBytes();
		System.arraycopy(versionBytes, 0, versionTrunc, 0, Math.min(VERSION_LEN, versionBytes.length));
		return versionTrunc;
	}

	public NonceFactory getNonceFactory() {
		return this.nonceFactory;
	}
}
