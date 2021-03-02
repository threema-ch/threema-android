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

package ch.threema.base;

import ch.threema.client.Utils;

/**
 * Base class for contacts to be used in Threema.
 */
public class Contact {
	public static final int CONTACT_NAME_MAX_LENGTH_BYTES = 256;

	private final String identity;
	private final byte[] publicKey;

	private String firstName;
	private String lastName;

	private VerificationLevel verificationLevel;

	public Contact(String identity, byte[] publicKey) {
		this.identity = identity;
		this.publicKey = publicKey;
		this.verificationLevel = VerificationLevel.UNVERIFIED;
	}

	public String getIdentity() {
		return identity;
	}

	public byte[] getPublicKey() {
		return publicKey;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = Utils.truncateUTF8String(firstName, CONTACT_NAME_MAX_LENGTH_BYTES);
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = Utils.truncateUTF8String(lastName, CONTACT_NAME_MAX_LENGTH_BYTES);
	}

	public VerificationLevel getVerificationLevel() {
		return verificationLevel;
	}

	public void setVerificationLevel(VerificationLevel verificationLevel) {
		this.verificationLevel = verificationLevel;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(identity);
		sb.append(" (");
		sb.append(Utils.byteArrayToHexString(publicKey));
		sb.append(")");

		if (firstName != null || lastName != null) {
			sb.append(": ");
			sb.append(firstName);
			sb.append(" ");
			sb.append(lastName);
		}

		return sb.toString();
	}
}
