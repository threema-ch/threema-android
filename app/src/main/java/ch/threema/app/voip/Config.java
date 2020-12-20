/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

package ch.threema.app.voip;

import androidx.annotation.NonNull;
import ch.threema.app.utils.TurnServerCache;

/**
 * VoIP configuration.
 */
public class Config {
	private static final int MIN_SPARE_TURN_VALIDITY = 3600*1000;

	// Hardware AEC Blacklist (Manufacturer;Model)
	@NonNull public static String[] HW_AEC_BLACKLIST = new String[] {
		"Fairphone;FP2",
		"ZUK;ZUK Z1" // Ticket #286367
	};

	private static final TurnServerCache TURN_SERVER_CACHE = new TurnServerCache("voip", MIN_SPARE_TURN_VALIDITY);

	public static TurnServerCache getTurnServerCache() {
		return TURN_SERVER_CACHE;
	}
}
