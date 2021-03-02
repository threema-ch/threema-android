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

package ch.threema.client.ballot;

import ch.threema.base.ThreemaException;
import ch.threema.client.ProtocolDefines;
import ch.threema.client.Utils;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Wrapper class for ballot IDs (consisting of 8 bytes, chosen by the ballot creator and not guaranteed
 * to be unique across multiple ballot creators).
 */
public class BallotId {

	private final byte[] ballotId;

	public BallotId() {
		ballotId = new byte[ProtocolDefines.BALLOT_ID_LEN];
		SecureRandom rnd = new SecureRandom();
		rnd.nextBytes(ballotId);
	}

	public BallotId(byte[] ballotId) throws ThreemaException {
		if (ballotId.length != ProtocolDefines.BALLOT_ID_LEN)
			/* Invalid ballot ID length */
			throw new ThreemaException("TM028");

		this.ballotId = ballotId;
	}

	public BallotId(byte[] data, int offset) {
		ballotId = new byte[ProtocolDefines.BALLOT_ID_LEN];
		System.arraycopy(data, offset, ballotId, 0, ProtocolDefines.BALLOT_ID_LEN);
	}

	public byte[] getBallotId() {
		return ballotId;
	}

	@Override
	public String toString() {
		return Utils.byteArrayToHexString(ballotId);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != getClass())
			return false;

		return Arrays.equals(ballotId, ((BallotId)obj).ballotId);
	}

	@Override
	public int hashCode() {
		/* ballot IDs are usually random, so just taking the first four bytes is fine */
		return ballotId[0] << 24 | (ballotId[1] & 0xFF) << 16 | (ballotId[2] & 0xFF) << 8 | (ballotId[3] & 0xFF);
	}
}
