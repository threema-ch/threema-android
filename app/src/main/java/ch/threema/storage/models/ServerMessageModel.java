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

package ch.threema.storage.models;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;

public class ServerMessageModel {
	/** The table name */
	public static final String TABLE = "server_messages";
	/** The message as string */
	public static final String COLUMN_MESSAGE = "message";
	/** The message type */
	public static final String COLUMN_TYPE = "type";

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({TYPE_ALERT, TYPE_ERROR})
	public @interface ServerMessageModelType {}

	public static final int TYPE_ALERT = 0;
	public static final int TYPE_ERROR = 1;

	private final String message;
	private final @ServerMessageModelType int type;

	public ServerMessageModel(String message, @ServerMessageModelType int type) {
		this.message = message;
		this.type = type;
	}

	public String getMessage() {
		return this.message;
	}

	@ServerMessageModelType
	public int getType() {
		return this.type;
	}

}
