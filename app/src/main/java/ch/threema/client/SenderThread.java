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

import androidx.annotation.AnyThread;
import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.neilalexander.jnacl.NaCl.BOXOVERHEAD;

/**
 * Thread invoked by {@link ThreemaConnection} to send outgoing messages to the server while the
 * main ThreemaConnection thread is blocked for receiving incoming messages. Maintains a queue
 * so that new outgoing packets can be enqueued without waiting.
 */
class SenderThread extends Thread {

	private static final Logger logger = LoggerFactory.getLogger(SenderThread.class);

	private final OutputStream os;
	private final NaCl kclientTempServerTemp;
	private final NonceCounter clientNonce;

	private final BlockingQueue<Payload> sendQueue;
	private boolean running;

	/**
	 * Instantiate a new SenderThread instance.
	 *
	 * @param os The {@link OutputStream} used to send data to server.
	 * @param kclientTempServerTemp The NaCl keypair used to communicate securely with the server.
	 * @param clientNonce The nonce counter associated with this server connection.
	 */
	public SenderThread(OutputStream os, NaCl kclientTempServerTemp, NonceCounter clientNonce) {
		super("SenderThread");

		this.os = os;
		this.kclientTempServerTemp = kclientTempServerTemp;
		this.clientNonce = clientNonce;

		this.sendQueue = new LinkedBlockingQueue<>();
		this.running = true;
	}

	/**
	 * Enqueue a new payload to be sent to the server as soon as possible. Note that the payload may
	 * be lost if the server connection breaks.
	 */
	@AnyThread
	public void sendPayload(Payload payload) {
		this.sendQueue.add(payload);
	}

	@Override
	public void run() {
		logger.info("Started");

		while (running) {
			// Note: The `sendQueue.take()` method will check for interruptions,
			// so we don't need to explicitly check `Thread.interrupted()` here.
			try {
				logger.info("Get payload from SendQueue.");
				Payload payload = sendQueue.take();
				logger.info("{} entries left", sendQueue.size());

				/* encrypt payload */
				byte[] pktdata = payload.makePacket();
				if (pktdata.length > (ProtocolDefines.MAX_PKT_LEN - BOXOVERHEAD)) {
					logger.info("Packet is too big ({}) - cannot send", pktdata.length);
					continue;
				}
				byte[] pktBox = kclientTempServerTemp.encrypt(pktdata, clientNonce.nextNonce());

				EndianUtils.writeSwappedShort(os, (short)pktBox.length);
				os.write(pktBox);
				os.flush();

				logger.info(
					"Message payload successfully sent. Size = {} - Type = {}",
					pktBox.length,
					Utils.byteToHex((byte) payload.getType(), true, true)
				);
			} catch (InterruptedException e) {
				logger.info("Interrupted");
				break;
			} catch (IOException e) {
				logger.info("Exception in sender thread", e);
				break;
			}
		}
		logger.info("Ended");
	}

	/**
	 * Shut down the sender thread.
	 */
	public void shutdown() {
		running = false;
		interrupt();
	}
}
