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

package ch.threema.domain.protocol.csp.messages.voip;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;

import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class VoipCallAnswerMessage extends VoipMessage {

	private static final Logger logger = LoggingUtil.getThreemaLogger("VoipCallAnswerMessage");

	private VoipCallAnswerData callAnswerData;

	public VoipCallAnswerMessage() {
		super();
	}

	public VoipCallAnswerMessage setData(VoipCallAnswerData callAnswerData) {
		this.callAnswerData = callAnswerData;
		return this;
	}

	public VoipCallAnswerData getData() {
		return this.callAnswerData;
	}

	@Override
	public byte[] getBody() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			this.callAnswerData.write(bos);
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_VOIP_CALL_ANSWER;
	}

	@Override
	public boolean allowUserProfileDistribution() {
		// True only if we're accepting the call
		final VoipCallAnswerData answerData = this.callAnswerData;
		if (answerData == null) {
			return false;
		}
		final Byte action = answerData.getAction();
		return action != null && action == VoipCallAnswerData.Action.ACCEPT;
	}

	@Override
	public boolean exemptFromBlocking() {
		return false;
	}
}
