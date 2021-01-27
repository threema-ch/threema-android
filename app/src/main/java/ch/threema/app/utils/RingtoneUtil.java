/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;

public class RingtoneUtil {
	public static final Uri THREEMA_CALL_RINGTONE_URI = Uri.parse("android.resource://"+ BuildConfig.APPLICATION_ID + "/" + R.raw.threema_call);
	public static final String THREEMA_CALL_RINGTONE_TITLE = "Threema Call";

	public static String getRingtoneNameFromUri(Context context, Uri uri) {
		if (uri != null) {
			if (uri.equals(THREEMA_CALL_RINGTONE_URI)) {
				return THREEMA_CALL_RINGTONE_TITLE;
			}
			Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
			if (ringtone != null) {
				try {
					return ringtone.getTitle(context);
				} catch (SecurityException | IllegalArgumentException e) {
					return context.getString(R.string.no_filename);
				}
			}
		}
		return context.getString(R.string.ringtone_none);
	}
}
