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

import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceCounter;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.crypto.ThreemaKDF;
import ch.threema.base.utils.AsyncResolver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.QueueMessageId;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.Version;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.stores.IdentityStoreInterface;
import ove.crypto.digest.Blake2b;

@WorkerThread
public class ThreemaConnection implements Runnable {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaConnection");

	private static final int IDENTITY_LEN = 8;
	private static final int COOKIE_LEN = 16;
	private static final int SERVER_HELLO_BOXLEN = NaCl.PUBLICKEYBYTES + COOKIE_LEN + NaCl.BOXOVERHEAD;
	private static final int RESERVED1_LEN = 24;
	private static final int VOUCH_LEN = 32;
	private static final int RESERVED2_LEN = 16;
	private static final int VERSION_LEN = 32;
	private static final int LOGIN_LEN = IDENTITY_LEN + VERSION_LEN + COOKIE_LEN + RESERVED1_LEN + VOUCH_LEN + RESERVED2_LEN;
	private static final int LOGIN_ACK_RESERVED_LEN = 16;
	private static final int LOGIN_ACK_LEN = LOGIN_ACK_RESERVED_LEN + NaCl.BOXOVERHEAD;

	/* Delegate objects */
	private final IdentityStoreInterface identityStore;
	private final NonceFactory nonceFactory;
	private MessageProcessorInterface messageProcessor;
	private DeviceCookieManager deviceCookieManager;

	/* Server address object */
	private ServerAddressProvider serverAddressProvider;
	private boolean ipv6;

	/* Temporary data for each individual TCP connection */
	private volatile Socket socket;
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
	 * @param serverAddressProvider object that provides server address information
	 * @param ipv6 whether to use IPv4+IPv6 for connection, or only IPv4
	 */
	public ThreemaConnection(IdentityStoreInterface identityStore,
							 NonceFactory nonceFactory,
	                         ServerAddressProvider serverAddressProvider,
	                         boolean ipv6) {
		this.identityStore = identityStore;
		this.nonceFactory = nonceFactory;
		this.serverAddressProvider = serverAddressProvider;
		this.ipv6 = ipv6;
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

	public void setServerAddressProvider(ServerAddressProvider serverAddressProvider) {
		this.serverAddressProvider = serverAddressProvider;
	}

	public void setDeviceCookieManager(DeviceCookieManager deviceCookieManager) {
		this.deviceCookieManager = deviceCookieManager;
	}

	private void getInetAdresses() throws UnknownHostException, ExecutionException, InterruptedException, ThreemaException {
		ArrayList<InetSocketAddress> addresses = new ArrayList<>();

		String serverHost = "";
		String serverNamePrefix = serverAddressProvider.getChatServerNamePrefix(ipv6);
		if (serverNamePrefix.length() > 0) {
			serverHost = serverNamePrefix + (serverAddressProvider.getChatServerUseServerGroups() ? identityStore.getServerGroup() : ".");
		}
		serverHost += serverAddressProvider.getChatServerNameSuffix(ipv6);

		if (ProxyAwareSocketFactory.shouldUseProxy(serverHost, serverAddressProvider.getChatServerPorts()[0])) {
			// Create unresolved addresses for proxy
			for (int port : serverAddressProvider.getChatServerPorts()) {
				addresses.add(InetSocketAddress.createUnresolved(serverHost, port));
			}
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
				for (int port : serverAddressProvider.getChatServerPorts()) {
					addresses.add(new InetSocketAddress(inetAddress, port));
				}
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

	public boolean sendBoxedMessage(MessageBox messageBox) {
		logger.info("sendBoxedMessage " + messageBox.getMessageId());

		return sendPayload(messageBox.makePayload());
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
		anotherConnectionCount = 0;

		while (running) {
			TimerTask echoSendTask = null;

			/* generate a new temporary key pair for the server connection */
			byte[] clientTempKeyPub = new byte[NaCl.PUBLICKEYBYTES];
			byte[] clientTempKeySec = new byte[NaCl.SECRETKEYBYTES];
			NaCl.genkeypair(clientTempKeyPub, clientTempKeySec);

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
				byte[] serverPubKeyCur = serverAddressProvider.getChatServerPublicKey();
				NaCl kclientTempServerPerm = new NaCl(clientTempKeySec, serverPubKeyCur);

				byte[] serverHello = kclientTempServerPerm.decrypt(serverHelloBox, nonce);

				if (serverHello == null) {
					/* Try again with alternate key */
					serverPubKeyCur = serverAddressProvider.getChatServerPublicKeyAlt();
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

				/* prepare NaCl for login and extension encryption */
				NaCl kclientTempServerTemp = new NaCl(clientTempKeySec, serverTempKeyPub);
				byte[] loginNonce = clientNonce.nextNonce();
				byte[] extensionsNonce = clientNonce.nextNonce();

				/* prepare extensions box */
				byte[] extensionsBox = kclientTempServerTemp.encrypt(makeExtensions(), extensionsNonce);

				/* prepare vouch sub packet */
				byte[] vouch = makeVouch(serverPubKeyCur, serverTempKeyPub, serverCookie, clientTempKeyPub);

				/* now prepare login packet */
				byte[] cleverExtensionVersion = this.makeCleverExtensionVersion(extensionsBox.length);
				byte[] login = new byte[LOGIN_LEN];
				int login_i = 0;
				System.arraycopy(identityStore.getIdentity().getBytes(), 0, login, 0, IDENTITY_LEN);
				login_i += IDENTITY_LEN;
				System.arraycopy(cleverExtensionVersion, 0, login, login_i, VERSION_LEN);
				login_i += VERSION_LEN;
				System.arraycopy(serverCookie, 0, login, login_i, COOKIE_LEN);
				login_i += COOKIE_LEN;
				login_i += RESERVED1_LEN; // all zero
				System.arraycopy(vouch, 0, login, login_i, VOUCH_LEN);

				/* encrypt login packet */
				byte[] loginBox = kclientTempServerTemp.encrypt(login, loginNonce);

				/* send it! */
				bos.write(loginBox);
				bos.write(extensionsBox);
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
	private void notifyMessageAckListeners(@NonNull QueueMessageId queueMessageId) {
		for (MessageAckListener listener : ackListeners) {
			try {
				listener.processAck(queueMessageId);
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

			case ProtocolDefines.PLTYPE_DEVICE_COOKIE_CHANGE_INDICATION:
				processDeviceCookieChangeIndicationPayload();
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
		final byte[] recipientIdBytes = new byte[ProtocolDefines.IDENTITY_LEN];
		System.arraycopy(data, 0, recipientIdBytes, 0, ProtocolDefines.IDENTITY_LEN);
		final String recipientId = new String(recipientIdBytes, StandardCharsets.UTF_8);

		// Message ID
		final MessageId messageId = new MessageId(data, ProtocolDefines.IDENTITY_LEN /* offset */);

		// Create MessageAck instance
		final QueueMessageId ack = new QueueMessageId(messageId, recipientId);
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
			MessageBox boxmsg = MessageBox.parsePayload(payload);

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

				if (ackMessage && (boxmsg.getFlags() & ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK) == 0) {
					sendAck(boxmsg.getMessageId(), boxmsg.getFromIdentity());
				}
			}

		} catch (Exception e) {
			/* don't break connection if we cannot parse the message (may not be the server's fault) */
			logger.warn("Box message parse failed", e);
		}
	}

	private void processDeviceCookieChangeIndicationPayload() {
		if (deviceCookieManager != null) {
			deviceCookieManager.changeIndicationReceived();
			sendClearDeviceCookieChangeIndication();
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

	private boolean sendClearDeviceCookieChangeIndication() {
		logger.debug("Clearing device cookie change indication");
		final Payload clearPayload = new Payload(ProtocolDefines.PLTYPE_CLEAR_DEVICE_COOKIE_CHANGE_INDICATION, new byte[0]);
		return sendPayload(clearPayload);
	}

	private byte[] makeVouch(byte[] serverPubKeyCur, byte[] serverTempKeyPub, byte[] serverCookie, byte[] clientTempKeyPub) {
		byte[] sharedSecrets = Utils.concatByteArrays(identityStore.calcSharedSecret(serverPubKeyCur), identityStore.calcSharedSecret(serverTempKeyPub));

		ThreemaKDF kdf = new ThreemaKDF("3ma-csp");

		byte[] input = new byte[COOKIE_LEN + NaCl.PUBLICKEYBYTES];
		System.arraycopy(serverCookie, 0, input, 0, COOKIE_LEN);
		System.arraycopy(clientTempKeyPub, 0, input, COOKIE_LEN, NaCl.PUBLICKEYBYTES);
		byte[] vouchKey = kdf.deriveKey("v2", sharedSecrets);
		return Blake2b.Mac.newInstance(vouchKey, VOUCH_LEN).digest(input);
	}

	private byte[] makeCleverExtensionVersion(int extensionsBoxLength) throws IOException {
		/* "CleVER" extension field */
		if (extensionsBoxLength > Short.MAX_VALUE) {
			throw new IllegalArgumentException("Extensions box is too long");
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream(VERSION_LEN);
		bos.write(ProtocolExtension.VERSION_MAGIC_STRING.getBytes());
		EndianUtils.writeSwappedShort(bos, (short) extensionsBoxLength);
		return bos.toByteArray();
	}

	private byte[] makeExtensions() throws IOException {
		/* Client info (0x00) and Message payload version (0x02) extensions */
		ProtocolExtension clientInfo = new ProtocolExtension(ProtocolExtension.CLIENT_INFO_TYPE, version.getFullVersion().getBytes());
		ProtocolExtension messagePayloadVersion = new ProtocolExtension(ProtocolExtension.MESSAGE_PAYLOAD_VERSION_TYPE, new byte[] {ProtocolExtension.MESSAGE_PAYLOAD_VERSION});

		/* Device cookie extension (0x03) */
		ProtocolExtension deviceCookie = new ProtocolExtension(ProtocolExtension.DEVICE_COOKIE_TYPE, deviceCookieManager.obtainDeviceCookie());

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write(clientInfo.getBytes());
		bos.write(messagePayloadVersion.getBytes());
		bos.write(deviceCookie.getBytes());
		return bos.toByteArray();
	}

	public NonceFactory getNonceFactory() {
		return this.nonceFactory;
	}
}
