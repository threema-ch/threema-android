/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

package ch.threema.app.utils;

import ch.threema.app.ThreemaApplication;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.AudioMessage;
import ch.threema.domain.protocol.csp.messages.ImageMessage;
import ch.threema.domain.protocol.csp.messages.VideoMessage;
import ch.threema.domain.protocol.csp.messages.GroupAudioMessage;
import ch.threema.domain.protocol.csp.messages.GroupImageMessage;
import ch.threema.domain.protocol.csp.messages.GroupVideoMessage;
import ch.threema.domain.protocol.csp.messages.file.FileMessage;
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage;

public class MessageDiskSizeUtil {
	private static final long  MEGABYTE = 1024L * 1024L;

	public static long getSize(AbstractMessage boxedMessage) {
		double mbSize = 0.01;

		if(boxedMessage instanceof VideoMessage) {
			mbSize = ((VideoMessage) boxedMessage).getDuration() * 0.15;
		}
		else if(boxedMessage instanceof AudioMessage) {
			mbSize = ((AudioMessage) boxedMessage).getDuration() * 0.05;
		}
		else if(boxedMessage instanceof ImageMessage) {
			mbSize = 2;
		}
		else if (boxedMessage instanceof FileMessage) {
			mbSize = ((FileMessage) boxedMessage).getData().getFileSize() * 1.1 / MEGABYTE;
		}
		else if(boxedMessage instanceof GroupVideoMessage) {
			mbSize = ((GroupVideoMessage) boxedMessage).getDuration() * 0.15;
		}
		else if(boxedMessage instanceof GroupAudioMessage) {
			mbSize = ((GroupAudioMessage) boxedMessage).getDuration() * 0.05;
		}
		else if(boxedMessage instanceof GroupImageMessage) {
			mbSize = 2;
		}
		else if (boxedMessage instanceof GroupFileMessage) {
			mbSize = ((GroupFileMessage) boxedMessage).getData().getFileSize() * 1.1 / MEGABYTE;
		}

		return ((long) Math.min(mbSize, ThreemaApplication.MAX_BLOB_SIZE_MB) * MEGABYTE);
	}
}
