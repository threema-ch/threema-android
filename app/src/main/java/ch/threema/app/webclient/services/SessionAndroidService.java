/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

package ch.threema.app.webclient.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import org.slf4j.Logger;

import androidx.annotation.MainThread;
import androidx.core.app.NotificationCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DummyActivity;
import ch.threema.app.notifications.NotificationBuilderWrapper;
import ch.threema.app.services.NotificationService;
import ch.threema.app.webclient.activities.SessionsActivity;
import ch.threema.base.utils.LoggingUtil;

@MainThread
public class SessionAndroidService extends Service {
	private static final Logger logger = LoggingUtil.getThreemaLogger("SessionAndroidService");
	private static final int WEBCLIENT_ACTIVE_NOTIFICATION_ID = 23329;

	public static final String ACTION_START = "start";
	public static final String ACTION_STOP = "stop";
	public static final String ACTION_UPDATE = "update";
	public static final String ACTION_FORCE_STOP = "force_stop";

	private SessionService sessionService;

	// Binder given to clients
	private static boolean isRunning = false, isStopping = false;

	@Override
	public IBinder onBind(Intent intent) { return null; }

	@Override
	synchronized public void onCreate() {
		logger.trace("onCreate");

		super.onCreate();

		// Initialization of the session service may lock the app for a while, so we display
		// a temporary notification before getting the service, in order to avoid a
		// "Context.startForegroundService() did not then call Service.startForeground()" exception
		final NotificationCompat.Builder builder = new NotificationBuilderWrapper(
			this,
			NotificationService.NOTIFICATION_CHANNEL_WEBCLIENT,
			null
		);
		builder.setContentTitle(getString(R.string.webclient))
			.setContentText(getString(R.string.please_wait))
			.setSmallIcon(R.drawable.ic_web_notification)
			.setPriority(Notification.PRIORITY_LOW)
			.setLocalOnly(true);
		startForeground(WEBCLIENT_ACTIVE_NOTIFICATION_ID, builder.build());
		logger.info("startForeground called");

		// Instantiate session service
		try {
			sessionService = ThreemaApplication.getServiceManager().getWebClientServiceManager().getSessionService();
		} catch (Exception e) {
			logger.error("Session service could not be initialized (passphrase locked?). Can't start web client", e);
			stopSelf();
			return;
		}

		updateNotification();

		isRunning = true;
	}

	@Override
	synchronized public int onStartCommand(Intent intent, int flags, int startId) {
		logger.trace("onStartCommand");

		if (intent == null || intent.getAction() == null) {
			return START_NOT_STICKY;
		}

		if (isStopping) {
			return START_NOT_STICKY;
		}

		switch (intent.getAction()) {
			case ACTION_START:
				logger.info( "ACTION_START");
				break;
			case ACTION_STOP:
				logger.info( "ACTION_STOP");
				// fallthrough
			case ACTION_UPDATE:
				logger.info( "ACTION_UPDATE");
				if (sessionService.getRunningSessionsCount() <= 0) {
					logger.info( "No more running sessions");
					isRunning = false;
					isStopping = true;
					stopSelf();
				} else {
					updateNotification();
				}
				break;
			case ACTION_FORCE_STOP:
				logger.info( "ACTION_FORCE_STOP");
				isRunning = false;
				isStopping = true;
				stopSelf();
		}
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		logger.trace("onDestroy");

		removeNotification();

		stopForeground(true);
		logger.info( "stopForeground");

		isRunning = false;

		super.onDestroy();

		isStopping = false;

		logger.info( "Service destroyed");
	}

	@Override
	public void onLowMemory() {
		logger.info("onLowMemory");
		super.onLowMemory();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		logger.info("onTaskRemoved");

		Intent intent = new Intent(this, DummyActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	private Notification getNotification() {
		Intent contentIntent = new Intent(this, SessionsActivity.class);
		PendingIntent contentPendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Intent stopIntent = new Intent(this, StopSessionsAndroidService.class);
		PendingIntent stopPendingIntent = PendingIntent.getService(this, (int) System.currentTimeMillis(), stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder builder = new NotificationBuilderWrapper(this, NotificationService.NOTIFICATION_CHANNEL_WEBCLIENT, null);
		builder.setContentTitle(getString(R.string.webclient))
				.setContentText(String.format(getString(R.string.webclient_running_sessions), sessionService.getRunningSessionsCount()))
				.setSmallIcon(R.drawable.ic_web_notification)
				.setPriority(Notification.PRIORITY_LOW)
				.setContentIntent(contentPendingIntent)
				.setLocalOnly(true)
				.addAction(R.drawable.ic_close_white_24dp, getString(R.string.webclient_session_stop_all), stopPendingIntent);

		return builder.build();
	}

	private void updateNotification() {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (notificationManager != null) {
			notificationManager.notify(WEBCLIENT_ACTIVE_NOTIFICATION_ID, getNotification());
		}
	}

	private void removeNotification() {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (notificationManager != null) {
			notificationManager.cancel(WEBCLIENT_ACTIVE_NOTIFICATION_ID);
		}
	}

	public static boolean isRunning() {
		return isRunning;
	}
}
