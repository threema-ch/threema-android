/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.group;

import androidx.annotation.Nullable;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.protobuf.AbstractProtobufMessage;
import ch.threema.protobuf.csp.e2e.fs.Version;

public class GroupJoinRequestMessage extends AbstractProtobufMessage<GroupJoinRequestData> {

	public GroupJoinRequestMessage(GroupJoinRequestData payloadData) {
		super(ProtocolDefines.MSGTYPE_GROUP_JOIN_REQUEST, payloadData);
	}

	@Override
	@Nullable
	public Version getMinimumRequiredForwardSecurityVersion() {
		return Version.V1_2;
	}

	@Override
	public boolean allowUserProfileDistribution() {
		return true;
	}

	@Override
	public boolean exemptFromBlocking() {
		return true;
	}

	@Override
	public boolean createImplicitlyDirectContact() {
		return true;
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
}
