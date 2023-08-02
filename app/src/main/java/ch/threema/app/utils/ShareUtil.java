/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2023 Threema GmbH
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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.FileService;
import ch.threema.app.services.UserService;
import ch.threema.logging.backend.DebugLogFileBackend;
import ch.threema.storage.models.ContactModel;

import static androidx.core.content.ContextCompat.startActivity;

public class ShareUtil {
	public static void shareContact(Context context, ContactModel contact) {
		UserService userService = null;
		try {
			userService = ThreemaApplication.getServiceManager().getUserService();
		} catch (Exception ignored) {}

		if (context != null && userService != null) {
			String contactName = contact != null ? NameUtil.getDisplayName(contact) : context.getString(R.string.title_mythreemaid);
			String identity = contact != null ? contact.getIdentity() : userService.getIdentity();

			Intent shareIntent = new Intent(Intent.ACTION_SEND);
			shareIntent.setType("text/plain");
			shareIntent.putExtra(Intent.EXTRA_TEXT, contactName + ": https://" + BuildConfig.contactActionUrl + "/" + identity);

			try {
				ActivityCompat.startActivity(context, Intent.createChooser(shareIntent, context.getString(R.string.share_via)), null);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(context, R.string.no_activity_for_mime_type, Toast.LENGTH_LONG).show();
			}
		}
	}

	public static void shareTextString(Context context, String text) {
		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType("text/plain");
		shareIntent.putExtra(Intent.EXTRA_TEXT, text);
		startActivity(context, Intent.createChooser(shareIntent, context.getString(R.string.share_via)), null);
	}

	/**
	 * Share the logfile with another application.
	 *
	 * @param context     the context
	 * @param fileService the file service
	 */
	public static void shareLogfile(@NonNull Context context, @NonNull FileService fileService) {
		File zipFile = DebugLogFileBackend.getZipFile(fileService);
		if (zipFile == null) {
			Toast.makeText(context, context.getResources().getString(R.string.try_again), Toast.LENGTH_SHORT).show();
			return;
		}

		Uri uriToLogfile = fileService.getShareFileUri(zipFile, "debug_log.zip");

		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType("application/zip");
		shareIntent.putExtra(Intent.EXTRA_STREAM, uriToLogfile);
		shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

		startActivity(context, Intent.createChooser(shareIntent, context.getResources().getString(R.string.share_via)), null);
	}
}
