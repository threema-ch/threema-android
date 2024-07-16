/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.domain.helpers;

import ch.threema.domain.models.Contact;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.base.utils.Utils;
import com.neilalexander.jnacl.NaCl;

import java.util.Arrays;
import java.util.Objects;

public class DummyUsers {
	public static final User ALICE = new User("000ALICE", Utils.hexStringToByteArray("6eda2ebb8527ff5bd0e8719602f710c13e162a3be612de0ad2a2ff66f5050630"));
	public static final User BOB = new User("00000BOB", Utils.hexStringToByteArray("533058227925006d86bb8dd88b0442ed73fbc49216b6e94b0870a7761d979eca"));
	public static final User CAROL = new User("000CAROL", Utils.hexStringToByteArray("2ac0f894ef1504d63eef743ffd3cdd2a0604689f2bed6d10cc7895b589f4f821"));
	public static final User DAVE = new User("0000DAVE", Utils.hexStringToByteArray("2b3d181bbf1eb84a01326c5dc79c70be32688cb3a797a2a0acdd6c067b614b44"));

	public static IdentityStoreInterface getIdentityStoreForUser(User user) {
		return new InMemoryIdentityStore(user.identity, null, user.privateKey, user.identity);
	}

	public static Contact getContactForUser(User user) {
		return new DummyContact(user.identity, NaCl.derivePublicKey(user.privateKey));
	}

	public static class User {
		final String identity;
		final byte[] privateKey;

		User(String identity, byte[] privateKey) {
			this.identity = identity;
			this.privateKey = privateKey;
		}

		public String getIdentity() {
			return identity;
		}

		public byte[] getPrivateKey() {
			return privateKey;
		}
	}

	public static class DummyContact extends Contact {
		public DummyContact(String identity, byte[] publicKey) {
			super(identity, publicKey, VerificationLevel.UNVERIFIED);
		}

		// equals needed for Mockito
		@Override
		public boolean equals(Object other) {
			if (this == other) return true;
			if (other == null || getClass() != other.getClass()) return false;
			Contact contact = (Contact) other;
			return Objects.equals(getIdentity(), contact.getIdentity()) && Arrays.equals(getPublicKey(), contact.getPublicKey());
		}
	}
}
