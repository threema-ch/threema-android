/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.base;

import java.util.HashMap;
import java.util.Map;

/**
 * Lists the levels of trust that a user may have in the validity of the public key for a given contact.
 */
public enum VerificationLevel {
	UNVERIFIED(0), SERVER_VERIFIED(1), FULLY_VERIFIED(2);

	private final int code;
	private static final Map<Integer, VerificationLevel> _map = new HashMap<Integer, VerificationLevel>();

	VerificationLevel(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	static {
		for (VerificationLevel verificationLevel : VerificationLevel.values())
			_map.put(verificationLevel.code, verificationLevel);
	}

	public static VerificationLevel from(int value) {
		return _map.get(value);
	}
}
