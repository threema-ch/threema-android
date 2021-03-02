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

package ch.threema.client.file;

import ch.threema.base.Contact;
import ch.threema.base.ThreemaException;
import ch.threema.client.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

public class ProtocolTest {

	/**
	 * Encrypt a file for a group.
	 */
	@Test
	public void groupTest() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		//create a new file message
		final String myIdentity = "TESTTEST";
		final String toIdentity = "ABCDEFGH";

		byte[] blobIdFile = new byte[ProtocolDefines.BLOB_ID_LEN];
		byte[] blobIdThumbnail = new byte[ProtocolDefines.BLOB_ID_LEN];
		byte[] key = new byte[ProtocolDefines.BLOB_KEY_LEN];

		GroupId groupId = new GroupId(new byte[ProtocolDefines.GROUP_ID_LEN]);
		String groupCreator = myIdentity;

		GroupFileMessage groupFileMessage = new GroupFileMessage();
		groupFileMessage.setFromIdentity(toIdentity);
		groupFileMessage.setToIdentity(myIdentity);
		groupFileMessage.setGroupId(groupId);
		groupFileMessage.setGroupCreator(groupCreator);
		FileData data = new FileData();
		data
				.setFileBlobId(blobIdFile)
				.setThumbnailBlobId(blobIdThumbnail)
				.setEncryptionKey(key)
				.setMimeType("image/jpg")
				.setFileName("therme.jpg")
				.setFileSize(123)
				.setRenderingType(FileData.RENDERING_DEFAULT);
		groupFileMessage.setData(data);

		ContactStoreInterface contactStore = createFakeContactStore();
		IdentityStoreInterface identityStore = createFakeIdentityStore(myIdentity);

		NonceFactory nonceFactory = new NonceFactory(new NonceStoreInterface() {
			@Override
			public boolean exists(byte[] nonce) {
				return false;
			}

			@Override
			public boolean store(byte[] nonce) {
				return true;
			}
		});

		BoxedMessage boxmsg = groupFileMessage.makeBox(contactStore, identityStore, nonceFactory);
		Assert.assertNotNull("BoxMessage failed", boxmsg);

		//now decode again
		AbstractMessage decodedBoxMessage = AbstractMessage.decodeFromBox(
				boxmsg,
				contactStore,
				identityStore,
				true);
		Assert.assertNotNull("decodedBox failed", decodedBoxMessage);
		Assert.assertTrue(decodedBoxMessage instanceof GroupFileMessage);

		GroupFileMessage groupFileMessageDecoded = (GroupFileMessage)decodedBoxMessage;
		FileData fileData = groupFileMessageDecoded.getData();
		Assert.assertNotNull(fileData);

		Assert.assertArrayEquals(blobIdFile, fileData.getFileBlobId());
		Assert.assertArrayEquals(blobIdThumbnail, fileData.getThumbnailBlobId());
		Assert.assertArrayEquals(key, fileData.getEncryptionKey());
		Assert.assertEquals("image/jpg", fileData.getMimeType());
		Assert.assertEquals("therme.jpg", fileData.getFileName());
		Assert.assertEquals(123, fileData.getFileSize());
		Assert.assertEquals(FileData.RENDERING_DEFAULT, fileData.getRenderingType());
	}

	@Test
	public void identityTest() throws ThreemaException, MissingPublicKeyException, BadMessageException {
		//create a new file message
		final String myIdentity = "TESTTEST";
		final String toIdentity = "ABCDEFGH";

		byte[] blobIdFile = new byte[ProtocolDefines.BLOB_ID_LEN];
		byte[] blobIdThumbnail = new byte[ProtocolDefines.BLOB_ID_LEN];
		byte[] key = new byte[ProtocolDefines.BLOB_KEY_LEN];

		FileMessage fileMessage = new FileMessage();
		fileMessage.setFromIdentity(toIdentity);
		fileMessage.setToIdentity(myIdentity);
		FileData data = new FileData();
		data
				.setFileBlobId(blobIdFile)
				.setThumbnailBlobId(blobIdThumbnail)
				.setEncryptionKey(key)
				.setMimeType("image/jpg")
				.setFileName("therme.jpg")
				.setFileSize(123)
				.setRenderingType(FileData.RENDERING_MEDIA);
		fileMessage.setData(data);

		ContactStoreInterface contactStore = createFakeContactStore();

		IdentityStoreInterface identityStore = createFakeIdentityStore(myIdentity);

		NonceFactory nonceFactory = new NonceFactory(new NonceStoreInterface() {
			@Override
			public boolean exists(byte[] nonce) {
				return false;
			}

			@Override
			public boolean store(byte[] nonce) {
				return true;
			}
		});

		BoxedMessage boxmsg = fileMessage.makeBox(contactStore, identityStore, nonceFactory);
		Assert.assertNotNull("BoxMessage failed", boxmsg);

		//now decode again
		AbstractMessage decodedBoxMessage = AbstractMessage.decodeFromBox(boxmsg, contactStore, identityStore, true);
		Assert.assertNotNull("decodedBox failed", decodedBoxMessage);
		Assert.assertTrue(decodedBoxMessage instanceof FileMessage);

		FileMessage fileMessageDecoded = (FileMessage)decodedBoxMessage;
		FileData fileData = fileMessageDecoded.getData();
		Assert.assertNotNull(fileData);

		Assert.assertTrue(Arrays.equals(blobIdFile, fileData.getFileBlobId()));
		Assert.assertTrue(Arrays.equals(blobIdThumbnail, fileData.getThumbnailBlobId()));
		Assert.assertTrue(Arrays.equals(key, fileData.getEncryptionKey()));
		Assert.assertEquals("image/jpg", fileData.getMimeType());
		Assert.assertEquals("therme.jpg", fileData.getFileName());
		Assert.assertEquals(123, fileData.getFileSize());
		Assert.assertEquals(FileData.RENDERING_MEDIA, fileData.getRenderingType());
	}

	private static ContactStoreInterface createFakeContactStore() {
		return new ContactStoreInterface() {
			@Override
			public byte[] getPublicKeyForIdentity(String identity, boolean fetch) {
				return new byte[256];
			}

			@Override
			public Contact getContactForIdentity(String identity) {
				return null;
			}

			@Override
			public Collection<Contact> getAllContacts() {
				return null;
			}

			@Override
			public void addContact(Contact contact) { }

			@Override
			public void hideContact(Contact contact, boolean hide) { }

			@Override
			public void removeContact(Contact contact) { }

			@Override
			public void addContactStoreObserver(ContactStoreObserver observer) { }

			@Override
			public void removeContactStoreObserver(ContactStoreObserver observer) { }
		};
	}

	private static IdentityStoreInterface createFakeIdentityStore(final String myIdentity) {
		return new IdentityStoreInterface() {
			@Override
			public byte[] encryptData(byte[] plaintext, byte[] nonce, byte[] receiverPublicKey) {
				return plaintext;
			}

			@Override
			public byte[] decryptData(byte[] ciphertext, byte[] nonce, byte[] senderPublicKey) {
				return ciphertext;
			}

			@Override
			public String getIdentity() {
				return myIdentity;
			}

			@Override
			public String getServerGroup() {
				return null;
			}

			@Override
			public byte[] getPublicKey() {
				return new byte[256];
			}

			@Override
			public String getPublicNickname() {
				return null;
			}

			@Override
			public void storeIdentity(String identity, String serverGroup, byte[] publicKey, byte[] privateKey) { }
		};
	}
}
