/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;

import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * Request current group information to be sent back.
 */
public class GroupSyncRequestMessage extends AbstractGroupMessage {

	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupRequestSyncMessage");

	public GroupSyncRequestMessage() {
		super();
	}

	@Override
	@Nullable
	public Version getMinimumRequiredForwardSecurityVersion() {
		return Version.V1_2;
	}

	@Override
	public boolean allowUserProfileDistribution() {
		return false;
	}

	@Override
	public boolean exemptFromBlocking() {
		return true;
	}

	@Override
	public boolean createImplicitlyDirectContact() {
		return false;
	}

	@Override
	public boolean protectAgainstReplay() {
		return true;
	}

	@Override
	public boolean reflectIncoming() {
		return true;
	}

	@Override
	public boolean reflectOutgoing() {
		return true;
	}

	@Override
	public boolean sendAutomaticDeliveryReceipt() {
		return false;
	}

	@Override
	public boolean bumpLastUpdate() {
		return false;
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_GROUP_REQUEST_SYNC;
	}

	@Override
	public byte[] getBody() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write(getApiGroupId().getGroupId());
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
	}
}
