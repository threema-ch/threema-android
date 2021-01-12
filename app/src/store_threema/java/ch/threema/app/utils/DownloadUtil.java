/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import androidx.annotation.NonNull;
import ch.threema.app.R;

/**
 * APK Downloader.
 * Only used in Threema Shop version.
 */
public class DownloadUtil  {
	private static final Logger logger = LoggerFactory.getLogger(DownloadUtil.class);

	private static DownloadState newestApkDownload;

	public interface DownloadState {
		long getDownloadId();
		File getDestinationFile();
	}

	private static @NonNull File init() {
		File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

		downloadPath.mkdirs();

		return downloadPath;
	}


	public static DownloadState getNewestApkDownloadState(long extraDownloadId) {
		if(newestApkDownload != null && newestApkDownload.getDownloadId() == extraDownloadId) {
			return newestApkDownload;
		}
		return null;
	}

	public static DownloadState downloadUpdate(@NonNull Context context, String downloadUrl) {
		logger.info("Downloading update");

		if (downloadUrl == null) {
			return null;
		}

		final File downloadPath = init();
		logger.debug("Update download path: {}", downloadPath.toString());
		final Uri uri = Uri.parse(downloadUrl).buildUpon()
				.appendQueryParameter("download", "true")
				.build();

		File temporaryAPKFile;
		int download = 0;
		do {
			temporaryAPKFile = new File(downloadPath.getPath()
					+ "/"
					+ (download > 0 ? download + "-" : "")
					+ context.getString(R.string.shop_download_filename));
			download++;
		}
		while (temporaryAPKFile.exists());

		newestApkDownload = download(context, uri, temporaryAPKFile.getName());
		return newestApkDownload;
	}

	private static DownloadState download(
		@NonNull Context context,
		@NonNull Uri url,
		@NonNull final String destinationFileName
	) {
		logger.info("Update target file name: {}", destinationFileName);
		try {
			final File downloadPath = init();

			DownloadManager.Request request = new DownloadManager.Request(url);
			request.setTitle(destinationFileName);
			request.allowScanningByMediaScanner();
			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			request.setDestinationUri(Uri.fromFile(new File(downloadPath, destinationFileName)));
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
				public File getDestinationFile() {
					return new File(downloadPath.getPath(), destinationFileName);
				}
			};
		} catch (Exception e) {
			logger.error("Exception while downloading update", e);
			return null;
		}
	}
}
