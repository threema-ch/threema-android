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

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import ch.threema.app.R;

public class StringConversionUtil {

	public static byte[] stringToByteArray(String s) {
		if(s == null) {
			return new byte[0];
		}
		return s.getBytes();
	}

	public static String byteArrayToString(byte[] bytes) {
		return new String(bytes);
	}

	public static @NonNull String secondsToString(long fullSeconds, boolean longFormat) {
		String[] pieces = secondsToPieces(fullSeconds);

		if (longFormat || !pieces[0].equals("00")) {
			return pieces[0] + ":" + pieces[1] + ":" + pieces[2];
		}
		else {
			return pieces[1] + ":" + pieces[2];
		}
	}

	private static @NonNull	String[] secondsToPieces(long fullSeconds) {
		String[] pieces = new String[3];

		pieces[0] = xDigit((int) ((float)fullSeconds / 3600), 2);
		pieces[1] = xDigit((int) (((float)fullSeconds % 3600) / 60), 2);
		pieces[2] = xDigit((int) ((float)fullSeconds % 60), 2);

		return pieces;
	}

	public static String getDurationStringHuman(Context context, long fullSeconds) {
		long minutes = TimeUnit.SECONDS.toMinutes(fullSeconds) % TimeUnit.HOURS.toMinutes(1);
		long seconds = TimeUnit.SECONDS.toSeconds(fullSeconds) % TimeUnit.MINUTES.toSeconds(1);

		if (minutes == 0) {
			return seconds + " " + context.getString(R.string.seconds);
		}

		return minutes + " " + context.getString(R.string.minutes) +
				context.getString(R.string.and) + " " + seconds + " " + context.getString(R.string.seconds);
	}

	public static String getDurationString(long milliseconds) {
		if (milliseconds > DateUtils.HOUR_IN_MILLIS) {
			return String.format(Locale.US, "%d:%02d:%02d",
				TimeUnit.MILLISECONDS.toHours(milliseconds),
				TimeUnit.MILLISECONDS.toMinutes(milliseconds) % TimeUnit.HOURS.toMinutes(1),
				TimeUnit.MILLISECONDS.toSeconds(milliseconds) % TimeUnit.MINUTES.toSeconds(1));
		} else {
			return String.format(Locale.US, "%02d:%02d",
				TimeUnit.MILLISECONDS.toMinutes(milliseconds) % TimeUnit.HOURS.toMinutes(1),
				TimeUnit.MILLISECONDS.toSeconds(milliseconds) % TimeUnit.MINUTES.toSeconds(1));
		}
	}

	public static String xDigit(int number, int digits) {
		String res = String.valueOf(number);
		while(res.length() < digits) {
			res = "0" + res;
		}

		return res;
	}

	/**
	 * Join a string array using a delimiter.
	 * Ignore empty elements.
	 */
	public static String join(CharSequence delimiter, String... pieces) {
		final StringBuilder builder = new StringBuilder();
		for (String p: pieces) {
			if (!TestUtil.empty(p)) {
				if (builder.length() > 0) {
					builder.append(delimiter);
				}
				builder.append(p);
			}
		}
		return builder.toString();
	}
}
