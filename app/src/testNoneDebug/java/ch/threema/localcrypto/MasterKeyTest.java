/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
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

package ch.threema.localcrypto;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;

public class MasterKeyTest {

	private static final int TEST_BYTES = 65521;     /* prime number to exercise padding */

	private static final String KEY_FILE_NAME = UUID.randomUUID().toString();
	private static final byte[] EXPECTED_MASTER_KEY = new byte[]{(byte)0x49, (byte)0x4b, (byte)0xe0, (byte)0xbe, (byte)0x35, (byte)0xaf, (byte)0x77, (byte)0xbb, (byte)0x12, (byte)0x87, (byte)0x94, (byte)0x1d, (byte)0x70, (byte)0x32, (byte)0x81, (byte)0x10, (byte)0xaf, (byte)0x2e, (byte)0xd0, (byte)0xae, (byte)0x5d, (byte)0x19, (byte)0x86, (byte)0x5b, (byte)0x53, (byte)0x72, (byte)0x25, (byte)0xe9, (byte)0x17, (byte)0x83, (byte)0x69, (byte)0x0a};
	private static final byte[] KEY_FILE_DATA = new byte[]{(byte)0x02, (byte)0xb6, (byte)0xaa, (byte)0x7f, (byte)0x45, (byte)0x7f, (byte)0x64, (byte)0xd5, (byte)0xff, (byte)0x56, (byte)0x41, (byte)0x6c, (byte)0x05, (byte)0xa1, (byte)0x27, (byte)0xb9, (byte)0xf7, (byte)0xfa, (byte)0x11, (byte)0xc3, (byte)0x14, (byte)0x2a, (byte)0xf0, (byte)0xaf, (byte)0x33, (byte)0x57, (byte)0x65, (byte)0x05, (byte)0x0e, (byte)0xb3, (byte)0xf3, (byte)0x5c, (byte)0x4b, (byte)0xe6, (byte)0x30, (byte)0xf8, (byte)0x49, (byte)0x31, (byte)0x4f, (byte)0xe5, (byte)0x7b, (byte)0xb9, (byte)0xff, (byte)0x7f, (byte)0xe0};
	private static final byte[] LEGACY_KEY_FILE_DATA = new byte[]{(byte)0x01, (byte)0x52, (byte)0x56, (byte)0xc2, (byte)0xae, (byte)0xaa, (byte)0xc5, (byte)0x7b, (byte)0x3c, (byte)0xa4, (byte)0xec, (byte)0xb5, (byte)0x0c, (byte)0xb2, (byte)0xe0, (byte)0x04, (byte)0xe0, (byte)0x42, (byte)0xe1, (byte)0x49, (byte)0x9e, (byte)0x3c, (byte)0x67, (byte)0x6f, (byte)0xd5, (byte)0xe9, (byte)0x69, (byte)0x20, (byte)0x69, (byte)0x79, (byte)0x7a, (byte)0x9d, (byte)0x9f, (byte)0xe6, (byte)0x30, (byte)0xf8, (byte)0x49, (byte)0x31, (byte)0x4f, (byte)0xe5, (byte)0x7b, (byte)0xb9, (byte)0xff, (byte)0x7f, (byte)0xe0};
	private static final String KEY_FILE_PASSWORD = "superSecretPassword";
	private static final String KEY_FILE_PASSWORD2 = "superSecretPassword2";
	private static final long RANDOM_SEED = 123456789;

	private MasterKey masterKey;
	private File masterKeyFile;

	@Before
	public void setUp() throws Exception {

		// Write test key file data to file
		this.masterKeyFile = new File(KEY_FILE_NAME);
		FileOutputStream fos = new FileOutputStream(this.masterKeyFile);
		fos.write(KEY_FILE_DATA);
		fos.close();

		this.masterKey = new MasterKey(this.masterKeyFile, null, false);

		Assert.assertTrue(this.masterKey.unlock(KEY_FILE_PASSWORD.toCharArray()));
	}

	@After
	public void tearDown() {
		this.masterKey = null;

		// Remove test file
		if (this.masterKeyFile.exists()) {
			this.masterKeyFile.delete();
		}

	}

	@Test
	public void testExpectedMasterKey() throws Exception {
		Assert.assertArrayEquals(masterKey.getKey(), EXPECTED_MASTER_KEY);
	}

	@Test
	public void testCipherStream() throws Exception {

		// Encrypt some data with CipherOutputStream, then read it back with CipherInputStream
		Random random = new Random(RANDOM_SEED);
		byte[] testBytes = new byte[TEST_BYTES];
		random.nextBytes(testBytes);

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		CipherOutputStream cos = masterKey.getCipherOutputStream(bos);
		cos.write(testBytes);
		cos.close();

		/* Note: CipherInputStream processes data in 512 byte chunks; therefore we need to
		   use DataInputStream.readFully() to ensure we read all the data */
		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		CipherInputStream cis = masterKey.getCipherInputStream(bis);
		DataInputStream dis = new DataInputStream(cis);

		byte[] testBytes2 = new byte[TEST_BYTES];
		dis.readFully(testBytes2);
		dis.close();

		Assert.assertArrayEquals(testBytes, testBytes2);
	}

	/**
	 * Creating a cipher input stream from a file with only a few bytes in it should result
	 * in an IOException.
	 */
	@Test
	public void testGetCipherInputStreamInvalid() throws IOException, MasterKeyLockedException {
		final Path path = Files.createTempFile("3ma", "masterkeytest");

		// Write garbage to file
		final OutputStream os = new FileOutputStream(path.toFile());
		os.write(new byte[] { 1, 2, 3 });
		os.close();

		final FileInputStream fis = new FileInputStream(path.toFile());

		try {
			this.masterKey.getCipherInputStream(fis);
			Assert.fail("IOException not thrown");
		} catch (IOException e) {
			assertEquals("Bad encrypted file (invalid IV length 3)", e.getMessage());
		}
	}

	/**
	 * Creating a cipher input stream from an empty file should result in an EmptyStreamException.
	 */
	@Test
	public void testGetCipherInputStreamEmpty() throws IOException, MasterKeyLockedException {
		final Path path = Files.createTempFile("3ma", "masterkeytest");
		final FileInputStream fis = new FileInputStream(path.toFile());

		try {
			this.masterKey.getCipherInputStream(fis);
			Assert.fail("IOException not thrown");
		} catch (IOException e) {
			assertEquals("Bad encrypted file (empty)", e.getMessage());
		}
	}

	@Test
	public void testChangePassword() throws Exception {
		// Change password on our MasterKey, then re-read it
		masterKey.setPassphrase(KEY_FILE_PASSWORD2.toCharArray());

		masterKey = new MasterKey(new File(KEY_FILE_NAME), null, false);
		Assert.assertTrue(masterKey.isLocked());

		Assert.assertTrue(masterKey.unlock(KEY_FILE_PASSWORD2.toCharArray()));

		testExpectedMasterKey();
	}

	@Test
	public void testLegacyMigration() throws Exception {
		File legacyKeyFile = new File(UUID.randomUUID().toString());
		FileOutputStream fos = new FileOutputStream(legacyKeyFile);
		fos.write(LEGACY_KEY_FILE_DATA);
		fos.close();

		MasterKey legacyMasterKey = new MasterKey(legacyKeyFile, null, false);
		Assert.assertTrue(legacyMasterKey.unlock(KEY_FILE_PASSWORD.toCharArray()));

		/* Check if the file is now in new (0x02) format */
		byte[] legacyKeyFileData = IOUtils.toByteArray(new FileInputStream(legacyKeyFile));
		Assert.assertEquals((byte)0x02, legacyKeyFileData[0]);

		/* Read the file again and ensure it is correct */
		legacyMasterKey = new MasterKey(legacyKeyFile, null, false);
		Assert.assertTrue(legacyMasterKey.unlock(KEY_FILE_PASSWORD.toCharArray()));

		legacyKeyFile.delete();
	}
}
