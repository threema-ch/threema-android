/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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

public class VoipICECandidatesMessage extends VoipMessage {

	private static final Logger logger = LoggingUtil.getThreemaLogger("VoipICECandidatesMessage");

	private VoipICECandidatesData iceCandidatesData;

	public VoipICECandidatesMessage() {
		super();
	}

	public VoipICECandidatesMessage setData(VoipICECandidatesData iceCandidatesData) {
		this.iceCandidatesData = iceCandidatesData;
		return this;
	}

	public VoipICECandidatesData getData() {
		return this.iceCandidatesData;
	}

	@Override
	public byte[] getBody() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			this.iceCandidatesData.write(bos);
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_VOIP_ICE_CANDIDATES;
	}

	@Override
	public boolean exemptFromBlocking() {
		return false;
	}
}
