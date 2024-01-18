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

package ch.threema.domain.protocol.csp.messages.fs;

import androidx.annotation.Nullable;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.protobuf.AbstractProtobufMessage;
import ch.threema.protobuf.csp.e2e.fs.Version;

public class ForwardSecurityEnvelopeMessage extends AbstractProtobufMessage<ForwardSecurityData> {

	private boolean allowSendingProfile;

	public ForwardSecurityEnvelopeMessage(ForwardSecurityData payloadData) {
		super(ProtocolDefines.MSGTYPE_FS_ENVELOPE, payloadData);
	}

	@Nullable
	@Override
	public Version getMinimumRequiredForwardSecurityVersion() {
		// Do not allow encapsulating forward security envelope messages
		return null;
	}

	@Override
	public boolean allowUserProfileDistribution() {
		return allowSendingProfile;
	}

	public void setAllowSendingProfile(boolean allowSendingProfile) {
		this.allowSendingProfile = allowSendingProfile;
	}

	@Override
	public boolean exemptFromBlocking() {
		// Note that checking for exemption from blocking should never happen on forward security
		// envelope messages.
		throw new IllegalStateException("Cannot check for exemption from blocking of fs envelopes");
	}

	@Override
	public boolean flagSendPush() {
		return (getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_SEND_PUSH) != 0;
	}

	@Override
	public boolean flagNoServerQueuing() {
		return (getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_SERVER_QUEUING) != 0;
	}

	@Override
	public boolean flagNoServerAck() {
		return (getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK) != 0;
	}

	@Override
	public boolean flagGroupMessage() {
		return (getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_GROUP) != 0;
	}

	@Override
	public boolean flagShortLivedServerQueuing() {
		return (getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_SHORT_LIVED) != 0;
	}

	@Override
	public boolean flagNoDeliveryReceipts() {
		return (getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS) != 0;
	}
}
