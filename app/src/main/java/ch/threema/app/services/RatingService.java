/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.services;

import android.net.Uri;

import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.SecureRandom;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.ServerAddressProvider;

/**
 * Send ratings to the threema server
 */
public class RatingService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("RatingService");

	@NonNull
	private final PreferenceService preferenceService;
	@NonNull
	private final ServerAddressProvider serverAddressProvider;

	public RatingService(
		@NonNull PreferenceService preferenceService,
		@NonNull ServerAddressProvider serverAddressProvider
	) {
		this.preferenceService = preferenceService;
		this.serverAddressProvider = serverAddressProvider;
	}

	private String getRatingUrl(int rating) throws ThreemaException {
		return serverAddressProvider.getAppRatingUrl().replace("{rating}", Integer.toString(rating));
	}

	@WorkerThread
	public boolean sendRating(int rating, @NonNull String text, @NonNull String version) {
		String ref = this.preferenceService.getRandomRatingRef();
		boolean success = false;

		if (TestUtil.isEmptyOrNull(ref)) {
			// Create a new random ref
			byte[] ratingRef = new byte[32];
			SecureRandom rnd = new SecureRandom();
			rnd.nextBytes(ratingRef);
			ref = Utils.byteArrayToHexString(ratingRef);

			// Save to preferences
			this.preferenceService.setRandomRatingRef(ref);
		}

		// Append app version to rating text
		String textWithVersion = text.strip() + "\n\n---\n" + version;

		HttpsURLConnection connection = null;
		try {
			byte[] query = new Uri.Builder()
				.appendQueryParameter("ref", ref)
				.appendQueryParameter("feedback", textWithVersion)
				.build().getEncodedQuery().getBytes();

			URL url = new URL(this.getRatingUrl(rating));
			connection = (HttpsURLConnection) url.openConnection();
			connection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(url.getHost()));
			connection.setDoOutput(true);
			try (OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream())) {
				outputStream.write(query);
				outputStream.flush();
			}

			// Warning: This implicitly opens in/err streams!
			final int responseCode = connection.getResponseCode();
			if (responseCode != HttpsURLConnection.HTTP_NO_CONTENT) {
				throw new ThreemaException("Failed to create rating (code " + responseCode + ")");
			}

			success = true;
		} catch (Exception e) {
			// Log to Logfile and ignore
			logger.error("Could not send rating", e);
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}

		return success;
	}
}
