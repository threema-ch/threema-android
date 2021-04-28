/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.app.services.systemupdate;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;

import androidx.core.app.NotificationManagerCompat;
import androidx.work.WorkManager;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.UpdateSystemService;

/* clean up image labeler */

public class SystemUpdateToVersion64 extends UpdateToVersion implements UpdateSystemService.SystemUpdate {
	private static final Logger logger = LoggerFactory.getLogger(SystemUpdateToVersion64.class);
	private Context context;

	public SystemUpdateToVersion64(Context context) {
		this.context = context;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		return true;
	}

	@Override
	public boolean runASync() {
		deleteMediaLabelsDatabase();

		return true;
	}

	@SuppressLint("StaticFieldLeak")
	private void deleteMediaLabelsDatabase() {
		logger.debug("deleteMediaLabelsDatabase");

		new AsyncTask<Void, Void, Exception>() {
			@Override
			protected void onPreExecute() {
				WorkManager.getInstance(ThreemaApplication.getAppContext()).cancelAllWorkByTag("ImageLabelsPeriodic");
				WorkManager.getInstance(ThreemaApplication.getAppContext()).cancelAllWorkByTag("ImageLabelsOneTime");
			}

			@Override
			protected Exception doInBackground(Void... voids) {
				try {
					final String[] files = new String[] {
						"media_items.db",
						"media_items.db-shm",
						"media_items.db-wal",
					};
					for (String filename : files) {
						final File databasePath = context.getDatabasePath(filename);
						if (databasePath.exists() && databasePath.isFile()) {
							logger.info("Removing file {}", filename);
							if (!databasePath.delete()) {
								logger.warn("Could not remove file {}", filename);
							}
						} else {
							logger.debug("File {} not found", filename);
						}
					}
				} catch (Exception e) {
					logger.error("Exception while deleting media labels database");
					return e;
				}
				return null;
			}

			@Override
			protected void onPostExecute(Exception e) {
				// remove notification channel
				String NOTIFICATION_CHANNEL_IMAGE_LABELING =  "il";
				NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
				if (notificationManagerCompat != null) {
					notificationManagerCompat.deleteNotificationChannel(NOTIFICATION_CHANNEL_IMAGE_LABELING);
				}
			}
		}.execute();
	}

	@Override
	public String getText() {
		return "delete media labels database";
	}
}
