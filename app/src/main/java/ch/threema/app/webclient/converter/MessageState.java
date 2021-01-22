/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;

import ch.threema.app.webclient.exceptions.ConversionException;

@AnyThread
public class MessageState extends Converter {
	public static final String DELIVERED = "delivered";
	public static final String READ = "read";
	public static final String SENDFAILED = "send-failed";
	public static final String SENT = "sent";
	public static final String USERACK = "user-ack";
	public static final String USERDEC = "user-dec";
	public static final String PENDING = "pending";
	public static final String SENDING = "sending";

	public static String convert(ch.threema.storage.models.MessageState state) throws ConversionException {
		try {
			switch (state) {
				case DELIVERED:
					return MessageState.DELIVERED;
				case READ:
					return MessageState.READ;
				case SENDFAILED:
					return MessageState.SENDFAILED;
				case SENT:
					return MessageState.SENT;
				case USERACK:
					return MessageState.USERACK;
				case USERDEC:
					return MessageState.USERDEC;
				case TRANSCODING:
				case PENDING:
					return MessageState.PENDING;
				case SENDING:
					return MessageState.SENDING;
				default:
					throw new ConversionException("Unknown message state: " + state);
			}
		} catch (NullPointerException e) {
			throw new ConversionException(e.toString());
		}
	}
}
