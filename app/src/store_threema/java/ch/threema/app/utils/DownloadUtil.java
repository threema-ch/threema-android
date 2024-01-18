/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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
import java.util.HashMap;
import java.util.Map;

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

	/**
	 * Maps the download id to the destination file if it is stored in the public external
	 * directory. Otherwise the file is not put into this map.
	 */
	public static final Map<Long, File> publicExternalDestination = new HashMap<>();

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
	 * This returns the default update filename from string resources. If the apk should be downloaded
	 * into the public external downloads directory (needed on older devices < Android N), then a
	 * filename is chosen that does not yet exist in the downloads directory.
	 *
	 * @param context the context is needed for getting the filename from resources
	 * @param toPublicExternalDirectory if true, the filename will be unique in the downloads directory
	 * @return the filename of the new apk file
	 */
	private static String getFileName(Context context, boolean toPublicExternalDirectory) {
		if (!toPublicExternalDirectory) {
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
	 * @param toPublicExternalDirectory if true, the file is downloaded into the public downloads directory. This parameter is ignored on Android versions < N
	 * @return the download id
	 */
	public static long downloadUpdate(@NonNull Context context, @Nullable String downloadUrl, boolean toPublicExternalDirectory) {
		// On older android versions we need to download it into the public external downloads directory
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			toPublicExternalDirectory = true;
		}
		if (toPublicExternalDirectory) {
			logger.info("Downloading update to public downloads directory");
		} else {
			logger.info("Downloading update to default location");
		}

		if (downloadUrl == null) {
			return -1;
		}

		Uri uri = Uri.parse(downloadUrl).buildUpon()
			.appendQueryParameter("download", "true")
			.build();

		return download(context, uri, toPublicExternalDirectory);
	}

	/**
	 * Starts the download with the download manager.
	 *
	 * @param context the application context
	 * @param url     the url of the apk file
	 * @param toPublicExternalDirectory if true, the file is downloaded into the public downloads directory
	 * @return the download id
	 */
	private static long download(
		@NonNull Context context,
		@NonNull Uri url,
		boolean toPublicExternalDirectory
	) {
		String destinationFileName = getFileName(context, toPublicExternalDirectory);
		// The destination file is only set if we download the apk into the public external dir
		File destinationFile = null;

		logger.info("Update target file name: {}", destinationFileName);
		try {
			DownloadManager.Request request = new DownloadManager.Request(url);
			request.setTitle(destinationFileName);
			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			if (toPublicExternalDirectory) {
				destinationFile = new File(getDownloadPath(), destinationFileName);
				request.setDestinationUri(Uri.fromFile(destinationFile));
			}
			// enqueue file for download
			DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
			final long id = manager.enqueue(request);
			logger.info("Enqueued update download with id {}", id);

			if (destinationFile != null) {
				publicExternalDestination.put(id, destinationFile);
			}

			return id;
		} catch (Exception e) {
			logger.error("Exception while downloading update", e);
			return -1;
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
