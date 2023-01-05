/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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
import androidx.annotation.NonNull;
import ch.threema.app.webclient.exceptions.ConversionException;

@AnyThread
public class MessageType extends Converter {
	public final static String TEXT = "text";
	public final static String IMAGE = "image";
	public final static String VIDEO = "video";
	public final static String VOICEMESSAGE = "audio";
	public final static String LOCATION = "location";
	public final static String CONTACT = "contact";
	public final static String STATUS = "status";
	public final static String BALLOT = "ballot";
	public final static String FILE = "file";
	public final static String VOIP_STATUS= "voipStatus";

	public static String convert(@NonNull ch.threema.storage.models.MessageType messageType) throws ConversionException {
		try {
			switch (messageType) {
				case TEXT:
					return MessageType.TEXT;
				case IMAGE:
					return MessageType.IMAGE;
				case VIDEO:
					return MessageType.VIDEO;
				case VOICEMESSAGE:
					return MessageType.VOICEMESSAGE;
				case LOCATION:
					return MessageType.LOCATION;
				case CONTACT:
					return MessageType.CONTACT;
				case STATUS:
					return MessageType.STATUS;
				case VOIP_STATUS:
					return MessageType.VOIP_STATUS;
				case BALLOT:
					return MessageType.BALLOT;
				case FILE:
					return MessageType.FILE;
				default:
					throw new ConversionException("Unknown message type: " + messageType);
			}
		} catch (NullPointerException e) {
			throw new ConversionException(e.toString());
		}
	}
}
