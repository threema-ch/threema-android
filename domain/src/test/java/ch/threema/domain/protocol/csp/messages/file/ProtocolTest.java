/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.file;

import androidx.annotation.NonNull;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.crypto.NonceStoreInterface;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;

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
		groupFileMessage.setApiGroupId(groupId);
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

		ContactStore contactStore = createFakeContactStore();
		IdentityStoreInterface identityStore = createFakeIdentityStore(myIdentity);
		MessageCoder messageCoder = new MessageCoder(contactStore, identityStore);

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

		MessageBox boxmsg = messageCoder.encode(groupFileMessage, nonceFactory);
		Assert.assertNotNull("BoxMessage failed", boxmsg);

		//now decode again
		AbstractMessage decodedBoxMessage = messageCoder.decode(boxmsg, true);
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

		ContactStore contactStore = createFakeContactStore();
		IdentityStoreInterface identityStore = createFakeIdentityStore(myIdentity);
		MessageCoder messageCoder = new MessageCoder(contactStore, identityStore);

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

		MessageBox boxmsg = messageCoder.encode(fileMessage, nonceFactory);
		Assert.assertNotNull("BoxMessage failed", boxmsg);

		//now decode again
		AbstractMessage decodedBoxMessage = messageCoder.decode(boxmsg, true);
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

	private static ContactStore createFakeContactStore() {
		return new ContactStore() {
			@Override
			public Contact getContactForIdentity(@NonNull String identity, boolean fetch, boolean saveContact) {
				return new Contact(identity, new byte[256]);
			}

			@Override
			public Contact getContactForIdentity(@NonNull String identity) {
				return null;
			}

			@Override
			public void addContact(@NonNull Contact contact) { }

			@Override
			public void addContact(@NonNull Contact contact, boolean hide) { }

			@Override
			public void removeContact(@NonNull Contact contact) { }
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
			public byte[] calcSharedSecret(@NonNull byte[] publicKey) {
				return new byte[32];
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
