/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import org.slf4j.Logger;

import java.io.File;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.base.utils.LoggingUtil;

/**
 * APK Downloader. Only used in Threema Shop version.
 * For Android N and newer, the apk file location is managed by the download manager. The deletion
 * of the file is done automatically by the system. For older Android versions, the file is saved in
 * the Downloads folder (requires permission) and deleted again after installation in the
 * UpdateReceiver.
 */
public class DownloadUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DownloadUtil");

	private static DownloadState newestDownloadState;

	/**
	 * Contains the necessary information to find the downloaded apk
	 */
	public interface DownloadState {
		/**
		 * The id that can be used to check if the correct download has succeeded (or failed)
		 */
		long getDownloadId();

		/**
		 * The destination uri of the apk (is used from >= Android N)
		 */
		Uri getDestinationUri();

		/**
		 * The destination file of the apk (is used until Android M)
		 */
		File getDestinationFile();
	}

	public static DownloadState getNewestDownloadState(long extraDownloadId) {
		if (newestDownloadState != null && newestDownloadState.getDownloadId() == extraDownloadId) {
			return newestDownloadState;
		}
		return null;
	}

	/**
	 * Get the external download path. This is NOT used in Android version N or higher.
	 *
	 * @return the download file path
	 */
	private static @NonNull
	File getDownloadPath() {
		File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

		if (!downloadPath.mkdirs()) {
			logger.warn("Could not create downloadPath directory at {}", downloadPath.getPath());
		}

		return downloadPath;
	}

	/**
	 * For Android N and higher, this returns the default update filename from string resources. For
	 * older Android versions, it returns a filename that does not exist yet in the download path.
	 *
	 * @param context the context is needed for getting
	 * @return the filename of the new apk file
	 */
	private static String getFileName(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return context.getString(R.string.shop_download_filename);
		}

		File temporaryAPKFile;
		int download = 0;
		do {
			temporaryAPKFile = new File(getDownloadPath().getPath()
				+ "/"
				+ (download > 0 ? download + "-" : "")
				+ context.getString(R.string.shop_download_filename));
			download++;
		}
		while (temporaryAPKFile.exists());

		return temporaryAPKFile.getName();
	}

	/**
	 * Starts the download and provides the download state info. If starting the download fails,
	 * null is returned.
	 *
	 * @param context     the application context
	 * @param downloadUrl the download URL
	 * @return the download state on success, null otherwise
	 */
	public static @Nullable
	DownloadState downloadUpdate(@NonNull Context context, @Nullable String downloadUrl) {
		logger.info("Downloading update");

		if (downloadUrl == null) {
			return null;
		}

		Uri uri = Uri.parse(downloadUrl).buildUpon()
			.appendQueryParameter("download", "true")
			.build();

		return newestDownloadState = download(context, uri);
	}

	/**
	 * Starts the download with the download manager.
	 *
	 * @param context the application context
	 * @param url     the url of the apk file
	 * @return the download state on success, null otherwise
	 */
	private static DownloadState download(
		@NonNull Context context,
		@NonNull Uri url
	) {
		String destinationFileName = getFileName(context);

		logger.info("Update target file name: {}", destinationFileName);
		try {
			DownloadManager.Request request = new DownloadManager.Request(url);
			request.setTitle(destinationFileName);
			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
				request.setDestinationUri(Uri.fromFile(new File(getDownloadPath(), destinationFileName)));
			}
			// enqueue file for download
			DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
			final long id = manager.enqueue(request);
			logger.info("Enqueued update download");

			return new DownloadState() {
				@Override
				public long getDownloadId() {
					return id;
				}

				@Override
				public Uri getDestinationUri() {
					return manager.getUriForDownloadedFile(id);
				}

				@Override
				public File getDestinationFile() {
					return new File(getDownloadPath().getPath(), destinationFileName);
				}
			};
		} catch (Exception e) {
			logger.error("Exception while downloading update", e);
			return null;
		}
	}

	/**
	 * Deletes old Threema APKs in the downloads directory. For Android 11 and newer, the files
	 * cannot be deleted due to scoped storage. Newer app updates are deleted automatically by the
	 * system and therefore this method is no longer needed.
	 *
	 * @param context needed to get apk file name from string resources
	 */
	@AnyThread
	public static void deleteOldAPKs(@NonNull Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			return;
		}

		if (!ConfigUtils.isPermissionGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
			return;
		}

		new Thread(() -> {
			File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			File temporaryAPKFile = new File(downloadPath.getPath() + "/" + context.getString(R.string.shop_download_filename));

			int download = 0;

			while (temporaryAPKFile.exists()) {
				try {
					FileUtil.deleteFileOrWarn(temporaryAPKFile, "download file", logger);
				} catch (SecurityException e) {
					logger.error("could not delete old apk file", e);
				}
				download++;

				temporaryAPKFile = new File(downloadPath.getPath()
					+ "/"
					+ (download > 0 ? download + "-" : "")
					+ context.getString(R.string.shop_download_filename));
			}
		}, "deleteOldAPKs").start();
	}
}
