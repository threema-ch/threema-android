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

package ch.threema.domain.onprem;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base64;
import ch.threema.domain.protocol.ProtocolStrings;

public class OnPremConfigFetcher {

	private static final int UNAUTHORIZED_MIN_RETRY_INTERVAL = 180000;

	private final URL configUrl;
	private final String[] trustedPublicKeys;

	private OnPremConfig cachedConfig;
	private Date lastUnauthorized = null;

	public OnPremConfigFetcher(URL configUrl, String[] trustedPublicKeys) {
		this.configUrl = configUrl;
		this.trustedPublicKeys = trustedPublicKeys;
	}

	public synchronized OnPremConfig fetch() throws ThreemaException {
		if (this.cachedConfig != null && cachedConfig.getValidUntil() > System.currentTimeMillis()) {
			return this.cachedConfig;
		}

		if (lastUnauthorized != null && (new Date().getTime() - lastUnauthorized.getTime()) < UNAUTHORIZED_MIN_RETRY_INTERVAL) {
			// Previous attempt led to 401 unauthorized. Enforce a minimum interval before trying again,
			// to prevent triggering rate limit.
			throw new UnauthorizedFetchException("Cannot fetch OnPrem config (check username/password) - retry delayed");
		}

		HttpsURLConnection uc = null;
		try {
			uc = (HttpsURLConnection)configUrl.openConnection();
			if (configUrl.getUserInfo() != null) {
				String basicAuth = "Basic " + Base64.encodeBytes(URLDecoder.decode(configUrl.getUserInfo(), "UTF-8").getBytes());
				uc.setRequestProperty("Authorization", basicAuth);
				uc.setRequestProperty("User-Agent", ProtocolStrings.USER_AGENT);
			}

			String oppfData = IOUtils.toString(uc.getInputStream(), StandardCharsets.UTF_8);
			OnPremConfigVerifier verifier = new OnPremConfigVerifier(trustedPublicKeys);
			JSONObject obj = verifier.verify(oppfData);
			OnPremConfigParser parser = new OnPremConfigParser();
			this.cachedConfig = parser.parse(obj);
			return this.cachedConfig;
		} catch (LicenseExpiredException e) {
			throw new ThreemaException("OnPrem license has expired");
		} catch (Exception e) {
			Date newLastUnauthorized = null;
			try {
				if (uc != null && (uc.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED || uc.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN)) {
					newLastUnauthorized = new Date();
				}
			} catch (IOException | NullPointerException ignored) {
				// After a cold boot without internet connectivity `uc.getResponseCode()` can throw a NPE
				// Ignore this exception. A `ThreemaException` wrapping the original exception
				// will be thrown later on.
			}

			if (newLastUnauthorized != null) {
				lastUnauthorized = newLastUnauthorized;
				throw new UnauthorizedFetchException("Cannot fetch OnPrem config (unauthorized; check username/password)");
			}

			throw new ThreemaException("Cannot fetch OnPrem config (check connectivity)", e);
		}
	}
}
