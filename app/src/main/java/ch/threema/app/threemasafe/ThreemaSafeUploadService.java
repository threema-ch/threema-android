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

package ch.threema.app.threemasafe;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.core.app.FixedJobIntentService;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.listeners.ThreemaSafeListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.base.ThreemaException;

public class ThreemaSafeUploadService extends FixedJobIntentService {
	private static final Logger logger = LoggerFactory.getLogger(ThreemaSafeUploadService.class);

	private static final int JOB_ID = 1001;
	public static final String EXTRA_FORCE_UPLOAD = "force";

	private static boolean isRunning;

	private ServiceManager serviceManager;
	private ThreemaSafeService threemaSafeService;
	private PreferenceService preferenceService;

	@Override
	public void onCreate() {
		super.onCreate();

		isRunning = true;

		try {
			serviceManager = ThreemaApplication.getServiceManager();
			threemaSafeService = serviceManager.getThreemaSafeService();
			preferenceService = serviceManager.getPreferenceService();
		} catch (Exception e) {
			//
		}
	}

	@Override
	public void onDestroy() {
		isRunning = false;

		super.onDestroy();
	}

	/**
	 * Convenience method for enqueuing work in to this service.
	 */
	public static void enqueueWork(Context context, Intent work) {
		if (isRunning()) return;
		enqueueWork(context, ThreemaSafeUploadService.class, JOB_ID, work);
	}

	public static boolean isRunning() {
		return isRunning;
	}

	@Override
	protected void onHandleWork(@NonNull Intent intent) {
		logger.debug("ThreemaSafeUploadService: onHandleWork");

		boolean force = intent.getBooleanExtra(EXTRA_FORCE_UPLOAD, false);

		if (threemaSafeService != null) {
			try {
				threemaSafeService.createBackup(force);
			} catch (ThreemaException e) {
				showWarningNotification();
				logger.error("Exception", e);
			}

			ListenerManager.threemaSafeListeners.handle(new ListenerManager.HandleListener<ThreemaSafeListener>() {
				@Override
				public void handle(ThreemaSafeListener listener) {
					listener.onBackupStatusChanged();
				}
			});
		} else {
			stopSelf();
		}
	}

	private void showWarningNotification() {
		Date backupDate = preferenceService.getThreemaSafeBackupDate();
		Date aWeekAgo = new Date(System.currentTimeMillis() - DateUtils.WEEK_IN_MILLIS);

		if (backupDate != null && backupDate.before(aWeekAgo)) {
			NotificationService notificationService = serviceManager.getNotificationService();
			if (notificationService != null) {
				notificationService.showSafeBackupFailed((int) ((System.currentTimeMillis() - backupDate.getTime()) / DateUtils.DAY_IN_MILLIS));
			}
		}
	}
}
