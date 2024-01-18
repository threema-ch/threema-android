/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.base.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;

public class LoggingUtil {
	/**
	 * Get a logger instance. Should be used by all Threema code like this:
	 *
	 * 	 private static final Logger logger = LoggingUtil.getLogger("VoipCallService");
	 */
	public static Logger getThreemaLogger(@NonNull String name) {
		return LoggerFactory.getLogger("ch.threema." + name);
	}

	/**
	 * Clean up a tag, strip unnecessary package prefixes.
	 */
	@NonNull
	public static String cleanTag(@NonNull String tag, String[] prefixes) {
		for (String prefix : prefixes) {
			if (tag.startsWith(prefix)) {
				return tag.substring(prefix.length());
			}
		}
		return tag;
	}
}
