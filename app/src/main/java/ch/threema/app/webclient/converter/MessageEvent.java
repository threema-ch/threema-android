/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2023 Threema GmbH
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
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;

/**
 * Convert a message event.
 */
@AnyThread
public class MessageEvent extends Converter {
	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		TYPE_CREATED,
		TYPE_SENT,
		TYPE_DELIVERED,
		TYPE_READ,
		TYPE_ACKED,
		TYPE_MODIFIED,
	})
	private @interface EventType {}

	public final static String KEY_TYPE = "type";
	public final static String KEY_DATE = "date";

	public final static String TYPE_CREATED = "created";
	public final static String TYPE_SENT = "sent";
	public final static String TYPE_DELIVERED = "delivered";
	public final static String TYPE_READ = "read";
	public final static String TYPE_ACKED = "acked";
	public final static String TYPE_MODIFIED = "modified";

	@NonNull
	public static MsgpackObjectBuilder convert(@EventType String type, @NonNull Date date) {
		final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(KEY_TYPE, type);
		builder.put(KEY_DATE, date.getTime() / 1000);
		return builder;
	}
}
