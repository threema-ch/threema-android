/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

package ch.threema.app.utils;

import org.slf4j.Logger;

import java.util.Date;

import androidx.annotation.NonNull;
import ch.threema.app.ThreemaApplication;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.logging.ThreemaLogger;

public class TurnServerCache {
	// Logger
	private final Logger logger = LoggingUtil.getThreemaLogger("TurnServerCache");

	private final String type;
	private final int minSpareValidity;

	private APIConnector.TurnServerInfo cachedTurnServerInfo;

	/**
	 * Create a new TURN server cache of the specified type and with a given minimum validity
	 * before the cache is refreshed.
	 *
	 * @param type TURN server type, e.g. "voip" or "web"
	 * @param minSpareValidity minimum spare validity (in ms)
	 */
	public TurnServerCache(@NonNull String type, int minSpareValidity) {
		if (this.logger instanceof ThreemaLogger) {
			((ThreemaLogger)this.logger).setPrefix("[type=" + type + "]");
		}
		logger.info("Init (type={}, minSpareValidity={})", type, minSpareValidity);
		this.type = type;
		this.minSpareValidity = minSpareValidity;
	}

	/**
	 * Start fetching TURN servers asynchronously, so they are more likely to be available
	 * without delay when a call is accepted.
	 */
	public void prefetchTurnServers() {
		logger.info("prefetchTurnServers");
		new Thread(() -> {
			try {
				getTurnServers();
			} catch (Exception ignored) {
				// ignored
			}
		}).start();
	}

	/**
	 * Get TURN servers for use with VoIP. This call may block if no cached information is available.
	 *
	 * @return TURN server URLs/credentials
	 * @throws Exception
	 */
	@NonNull
	public synchronized APIConnector.TurnServerInfo getTurnServers() throws Exception {
		if (cachedTurnServerInfo != null) {
			logger.debug("Found cached TURN server info");
			Date minExpiration = new Date(new Date().getTime() + minSpareValidity);
			if (cachedTurnServerInfo.expirationDate.getTime() > minExpiration.getTime()) {
				logger.info("Returning cached TURN server info");
				return cachedTurnServerInfo;
			}
			logger.debug("Cached TURN server info expired");
		}

		logger.info("Returning fresh TURN server info");
		cachedTurnServerInfo = ThreemaApplication
			.getServiceManager()
			.getAPIConnector()
			.obtainTurnServers(ThreemaApplication.getServiceManager().getIdentityStore(), type);

		return cachedTurnServerInfo;
	}
}
