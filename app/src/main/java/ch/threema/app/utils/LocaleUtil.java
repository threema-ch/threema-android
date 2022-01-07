/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

import android.content.Context;
import android.text.format.DateUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.core.os.ConfigurationCompat;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.PreferenceService;

public class LocaleUtil {
	public static final String UTF8_ENCODING = "UTF-8";

	private LocaleUtil() {
		// Forbidden being instantiated.
	}

	@NonNull
	public static String getLanguage() {
		try {
			return URLEncoder.encode(Locale.getDefault().getLanguage(), UTF8_ENCODING);
		} catch (UnsupportedEncodingException ignored) { }
		return "en";
	}

	@NonNull
	public static String getAppLanguage() {
		PreferenceService preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
		if (preferenceService != null) {
			String localeOverride = preferenceService.getLocaleOverride();

			if (!TestUtil.empty(localeOverride)) {
				return localeOverride;
			}
		}
		return LocaleUtil.getLanguage();
	}

	/**
	 * Return the current locale.
	 */
	public static Locale getCurrentLocale(@NonNull Context context) {
		return ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
	}

	public static String formatTimeStampStringAbsolute(Context context, long when) {
		return DateUtils.formatDateTime(context, when, DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_ABBREV_ALL);
	}

	public static String formatTimeStampString(Context context, long when, boolean fullFormat) {
		// TODO: optimize - currently extremely slow
		return formatTimeStampStringRelative(context, when, fullFormat);
	}

	private static String formatTimeStampStringRelative(Context context, long when, boolean fullFormat) {
		String time = DateUtils.formatDateTime(context, when, DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_TIME);

		if (DateUtils.isToday(when)) {
			return time;
		}

		if (fullFormat) {
			return DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, 0).toString() + ", " + time;
		}

		return DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_ABBREV_ALL).toString();
	}

	public static String formatDateRelative(Context context, long when) {
		return DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_SHOW_DATE).toString();
	}

	public static String formatTimerText(long elapsedTime, boolean showMs) {
		int minutes = (int) (elapsedTime / 60000);
		int seconds = (int) ((elapsedTime % 60000) / 1000);

		if (showMs) {
			int milliseconds = (int) ((elapsedTime % 60000) % 1000);
			return String.format(Locale.US, "%01d:%02d:%02d", minutes, seconds, milliseconds / 10);
		}
		return String.format(Locale.US, "%01d:%02d", minutes, seconds);
	}

	/**
	 * Normalize and uppercase a string for comparison, removing all diacritical marks
	 * @param input non-normalized string
	 * @return normalized string or original string if error occurs
	 */
	public static @NonNull String normalize(@NonNull String input) {
		String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
		if (!TestUtil.empty(normalized)) {
			String replaced = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
			if (!TestUtil.empty(replaced)) {
				return replaced.toUpperCase();
			}
		}
		return input;
	}
}
