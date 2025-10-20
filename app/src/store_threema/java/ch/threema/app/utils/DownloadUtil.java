/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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
 * The apk file location is managed by the download manager. The deletion
 * of the file should be done automatically by the system, but to be on the safe side
 * we also try to delete it outselves by calling `deleteOldAPKs` after installation in the
 * UpdateReceiver.
 */
public class DownloadUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("DownloadUtil");

    /**
     * Starts the download and provides the download state info.
     *
     * @param context                   the application context
     * @param downloadUrl               the download URL
     */
    public static void downloadUpdate(@NonNull Context context, @NonNull String downloadUrl) {
        logger.info("Downloading update");
        Uri uri = Uri.parse(downloadUrl).buildUpon()
            .appendQueryParameter("download", "true")
            .build();

        download(context, uri);
    }

    /**
     * Starts the download with the download manager.
     *
     * @param context                   the application context
     * @param url                       the url of the apk file
     */
    private static void download(
        @NonNull Context context,
        @NonNull Uri url
    ) {
        String destinationFileName = context.getString(R.string.shop_download_filename);
        DownloadManager.Request request = new DownloadManager.Request(url);
        request.setTitle(destinationFileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        // enqueue file for download
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        final long id = manager.enqueue(request);
        logger.info("Enqueued update download with id {}", id);
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

        RuntimeUtil.runOnWorkerThread(() -> {
            File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File temporaryAPKFile = new File(downloadPath.getPath(), context.getString(R.string.shop_download_filename));
            if (temporaryAPKFile.exists()) {
                try {
                    FileUtil.deleteFileOrWarn(temporaryAPKFile, "download file", logger);
                } catch (SecurityException e) {
                    logger.error("could not delete old apk file", e);
                }
            }
        });
    }
}
