/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;

public class UrlUtil {
	private static final Pattern ascii = Pattern.compile("^[\\x00-\\x7F]*$");
	private static final Pattern nonAscii = Pattern.compile("^[^\\x00-\\x7F]*$");

	/**
	 * Checks if the hostname of a given Uri consists of a mix of ascii and non-ascii characters implying a possible phishing attempt through similar looking characters
	 * @param uri Uri to check
	 * @return true if hostname consists of either ascii or non-ascii characters only, false in case of a mix of ascii and non-ascii chars or if hostname is empty
	 */
	public static boolean isLegalUri(@NonNull Uri uri) {
		final String host = uri.getHost();
		if (!TestUtil.empty(host)) {
			final String strippedHost = host.replaceAll("\\.", "");

			return ascii.matcher(strippedHost).matches() || nonAscii.matcher(strippedHost).matches();
		}
		return false;
	}

	/**
	 * A version of URLEncoder.encode that cannot fail.
	 *
	 * The encoding used is always UTF-8.
	 */
	public static String urlencode(@NonNull String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException ignored) {
			// Should never happen since UTF-8 is hardcoded.
		}
		return "";
	}
}
