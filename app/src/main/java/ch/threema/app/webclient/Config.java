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

package ch.threema.app.webclient;

import androidx.annotation.AnyThread;
import ch.threema.app.utils.TurnServerCache;

/**
 * WebClient configuration.
 */
@AnyThread
public class Config {
	private static final int MIN_SPARE_TURN_VALIDITY = 6*3600*1000;

	private static final TurnServerCache TURN_SERVER_CACHE = new TurnServerCache("web", MIN_SPARE_TURN_VALIDITY);

	public static TurnServerCache getTurnServerCache() {
		return TURN_SERVER_CACHE;
	}

	private Config() {
		// This class only contains static fields and should not be instantiated
	}
}
