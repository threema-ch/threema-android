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

import java.nio.charset.StandardCharsets;

import androidx.annotation.Nullable;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A message that has plain text as its contents.
 */
public class TextMessage extends AbstractMessage {
	private String text;

	public TextMessage() {
		super();
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_TEXT;
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
	public boolean exemptFromBlocking() {
		return false;
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
		return true;
	}

	@Override
	public boolean bumpLastUpdate() {
		return true;
	}

	@Override
	public byte[] getBody() {
        return text.getBytes(StandardCharsets.UTF_8);
    }

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
}
