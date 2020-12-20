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

package ch.threema.app.services;

import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.SecureRandom;

import javax.net.ssl.HttpsURLConnection;

import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.Utils;

/**
 * Send ratings to the threema server
 */
public class RatingService {
	private static final Logger logger = LoggerFactory.getLogger(RatingService.class);

	private final PreferenceService preferenceService;

	public RatingService(PreferenceService preferenceService) {
		this.preferenceService = preferenceService;
	}

	private String getRatingUrl(int rating) {
		return "https://threema.ch/app-rating/android/" + rating;
	}

	public boolean sendRating(int rating, String text) {
		String ref = this.preferenceService.getRandomRatingRef();
		boolean success = false;

		if (TestUtil.empty(ref)) {
			// Create a new random ref
			byte[] ratingRef = new byte[32];
			SecureRandom rnd = new SecureRandom();
			rnd.nextBytes(ratingRef);
			ref = Utils.byteArrayToHexString(ratingRef);

			// Save to preferences
			this.preferenceService.setRandomRatingRef(ref);
		}


		HttpsURLConnection connection = null;
		try {
			byte[] query = new Uri.Builder()
				.appendQueryParameter("ref", ref)
				.appendQueryParameter("feedback", text)
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
			logger.error("Exception", e);
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}

		return success;
	}
}
