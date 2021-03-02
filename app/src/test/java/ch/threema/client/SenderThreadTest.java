/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2020-2021 Threema GmbH
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

import com.neilalexander.jnacl.NaCl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Random;

public class SenderThreadTest {
	private SenderThread senderThread;

	@Before
	public void setUpTest() {
		// Server output stream
		final OutputStream bos = new ByteArrayOutputStream();

		// NaCl keypair
		final byte[] publickey = new byte[NaCl.PUBLICKEYBYTES];
		final byte[] privatekey = new byte[NaCl.SECRETKEYBYTES];
		NaCl.genkeypair(publickey, privatekey);
		final NaCl nacl = new NaCl(privatekey, publickey);

		// Nonce counter
		final byte[] cookie = new byte[ProtocolDefines.COOKIE_LEN];
		(new Random()).nextBytes(cookie); // Note: This is just a test, so no CSRNG is required
		final NonceCounter nonceCounter = new NonceCounter(cookie);

		// Create thread
		this.senderThread = new SenderThread(bos, nacl, nonceCounter);
	}

	/**
	 * It should be possible to cleanly stop the sender thread through {@link SenderThread#shutdown}.
	 */
	@Test
	public void testInterruptibleShutdown() throws InterruptedException {
		// Start thread
		Assert.assertFalse("Thread was started before calling start()", this.senderThread.isAlive());
		this.senderThread.start();
		Assert.assertTrue("Thread is not running after start()", this.senderThread.isAlive());

		// Interrupt thread
		this.senderThread.shutdown();
		this.senderThread.join(10000);
		Assert.assertFalse("Thread was not shut down after 10s", this.senderThread.isAlive());
	}

	/**
	 * It should be possible to cleanly stop the sender thread through {@link Thread#interrupt}.
	 */
	@Test
	public void testInterruptibleInterrupt() throws InterruptedException {
		// Start thread
		Assert.assertFalse("Thread was started before calling start()", this.senderThread.isAlive());
		this.senderThread.start();
		Assert.assertTrue("Thread is not running after start()", this.senderThread.isAlive());

		// Interrupt thread
		this.senderThread.interrupt();
		this.senderThread.join(10000);
		Assert.assertFalse("Thread was not shut down after 10s", this.senderThread.isAlive());
	}
}
