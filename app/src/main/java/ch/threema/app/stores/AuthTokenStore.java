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

package ch.threema.app.stores;

import android.os.SystemClock;
import android.text.format.DateUtils;

import androidx.annotation.Nullable;
import ch.threema.domain.stores.TokenStoreInterface;

/**
 * Stores the auth token used for onprem. It is cached for 24 hours. After 24 hours,
 * {@link #getToken()} returns null.
 */
public class AuthTokenStore implements TokenStoreInterface {

	@Nullable
	private String authToken;

	private long ttl = 0;

	@Override
	public String getToken() {
		// If the time to live is in the past, we set the token to null
		if (ttl < SystemClock.elapsedRealtime()) {
			authToken = null;
		}

		return authToken;
	}

	@Override
	public void storeToken(@Nullable String authToken) {
		this.authToken = authToken;
		// Set the ttl to 24 hours in the future
		this.ttl = SystemClock.elapsedRealtime() + DateUtils.DAY_IN_MILLIS;
	}
}
