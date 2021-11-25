/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base64;

public class OnPremConfigFetcher {

	private final URL configUrl;
	private final String[] trustedPublicKeys;

	private OnPremConfig cachedConfig;

	public OnPremConfigFetcher(URL configUrl, String[] trustedPublicKeys) {
		this.configUrl = configUrl;
		this.trustedPublicKeys = trustedPublicKeys;
	}

	public synchronized OnPremConfig fetch() throws ThreemaException {
		if (this.cachedConfig != null) {
			return this.cachedConfig;
		}

		try {
			URLConnection uc = configUrl.openConnection();
			if (configUrl.getUserInfo() != null) {
				String basicAuth = "Basic " + Base64.encodeBytes(URLDecoder.decode(configUrl.getUserInfo(), "UTF-8").getBytes());
				uc.setRequestProperty("Authorization", basicAuth);
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
			throw new ThreemaException("Cannot fetch OnPrem config (check username/password)", e);
		}
	}
}
