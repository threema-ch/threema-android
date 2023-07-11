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

package ch.threema.domain.protocol.csp.messages.ballot;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;

import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A ballot creation message.
 */
public class BallotCreateMessage extends AbstractMessage
	implements BallotCreateInterface{

	private static final Logger logger = LoggingUtil.getThreemaLogger("BallotCreateMessage");


	private BallotId ballotId;
	private String ballotCreatorId;
	private BallotData ballotData;

	public BallotCreateMessage() {
		super();
	}

	@Override
	public boolean flagSendPush() {
		return true;
	}

	@Nullable
	@Override
	public Version getMinimumRequiredForwardSecurityVersion() {
		return Version.V1_0;
	}

	@Override
	public boolean allowUserProfileDistribution() {
		return true;
	}

	@Override
	public void setBallotId(BallotId ballotId) {
		this.ballotId = ballotId;
	}

	@Override
	public void setBallotCreator(String ballotCreator) {
		this.ballotCreatorId = ballotCreator;
	}

	@Override
	public BallotId getBallotId() {
		return this.ballotId;
	}

	@Override
	public String getBallotCreator() {
		return this.ballotCreatorId;
	}

	@Override
	public void setData(BallotData ballotData) {
		this.ballotData = ballotData;
	}

	@Override
	public BallotData getData() {
		return this.ballotData;
	}

	@Override
	public byte[] getBody() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			//ballot stuff
			bos.write(this.getBallotId().getBallotId());
			this.ballotData.write(bos);
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_BALLOT_CREATE;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		sb.append(": create message, description: ");
		sb.append(this.ballotData.getDescription());

		return sb.toString();
	}

}
