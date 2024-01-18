/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

package ch.threema.storage.models.data;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({
	MessageContentsType.UNDEFINED,
	MessageContentsType.TEXT,
	MessageContentsType.IMAGE,
	MessageContentsType.VIDEO,
	MessageContentsType.AUDIO,
	MessageContentsType.VOICE_MESSAGE,
	MessageContentsType.LOCATION,
	MessageContentsType.STATUS,
	MessageContentsType.BALLOT,
	MessageContentsType.FILE,
	MessageContentsType.VOIP_STATUS,
	MessageContentsType.GIF,
	MessageContentsType.CONTACT,
	MessageContentsType.GROUP_CALL_STATUS,
	MessageContentsType.FORWARD_SECURITY_STATUS,
	MessageContentsType.GROUP_STATUS
})

@Retention(RetentionPolicy.SOURCE)
public @interface MessageContentsType {
	int UNDEFINED = 0;
	int TEXT = 1;
	int IMAGE = 2;
	int VIDEO = 3;
	int AUDIO = 4;
	int VOICE_MESSAGE = 5;
	int LOCATION = 6;
	int STATUS = 7;
	int BALLOT = 8;
	int FILE = 9;
	int VOIP_STATUS = 10;
	int GIF = 11;
	int CONTACT = 12;
	int GROUP_CALL_STATUS = 13;
	int FORWARD_SECURITY_STATUS = 14;
	int GROUP_STATUS = 15;
}

