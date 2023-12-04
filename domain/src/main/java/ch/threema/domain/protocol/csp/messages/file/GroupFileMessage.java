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

package ch.threema.domain.protocol.csp.messages.file;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.protobuf.csp.e2e.fs.Version;

public class GroupFileMessage extends AbstractGroupMessage
	implements FileMessageInterface {

	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupFileMessage");

	private FileData fileData;

	public GroupFileMessage() {
		super();
	}

	@Override
	public boolean flagSendPush() {
		return true;
	}

	@Override
	@Nullable
	public Version getMinimumRequiredForwardSecurityVersion() {
		return null;
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
	public void setData(FileData ballotData) {
		this.fileData = ballotData;
	}

	@Override
	public FileData getData() {
		return this.fileData;
	}

	@Override
	public byte[] getBody() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			//ballot stuff
			bos.write(this.getGroupCreator().getBytes(StandardCharsets.US_ASCII));
			bos.write(this.getApiGroupId().getGroupId());
			this.fileData.write(bos);
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_GROUP_FILE;
	}
}
