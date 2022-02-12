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

package ch.threema.app.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.receivers.AlarmManagerBroadcastReceiver;
import ch.threema.base.utils.LoggingUtil;

public class LifetimeServiceImpl implements LifetimeService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("LifetimeServiceImpl");

	public static final String REQUEST_CODE_KEY = "requestCode";
	public static final int REQUEST_CODE_RELEASE = 1;
	public static final int REQUEST_CODE_RESEND = 2;
	public static final int REQUEST_CODE_POLL = 3;
	public static final int REQUEST_LOCK_APP = 4;

	private static final int MESSAGE_SEND_TIME = 30*1000;
	private static final int MESSAGE_RESEND_INTERVAL = 5*60*1000;   /* 5 minutes */

	private final Context context;
	private final AlarmManager alarmManager;

	private boolean active = false;
	private int refCount = 0;
	private long lingerUntil = 0;   /* time (in SystemClock.elapsedRealtime()) until which the connection must stay active in any case */
	private long pollingInterval;
	private PollingHelper pollingHelper;

	private DownloadService downloadService;

	private final List<LifetimeServiceListener> listeners = new ArrayList<>();

	public LifetimeServiceImpl(Context context) {
		this.context = context;
		this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		try {
			this.downloadService = ThreemaApplication.getServiceManager().getDownloadService();
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@Override
	public synchronized void acquireConnection(String source) {
		refCount++;

		logger.info("acquireConnection: source = {}, refCount = {}", source, refCount);

		if (!active) {
			try {
				//do not start a connection if a restore is in progress
				if (RestoreService.isRunning()) {
					throw new Exception("restore in progress");
				}
				if (BackupService.isRunning()) {
					throw new Exception("backup in progress");
				}
				ThreemaApplication.getServiceManager().startConnection();
				logger.debug("connection started");
				active = true;

			} catch (Exception e) {
				logger.error("startConnection: failed or skipped", e);
			}
		} else {
			logger.info("another connection is already active");
		}
	}

	@Override
	public synchronized void releaseConnection(String source) {
		if (refCount == 0) {
			logger.debug("releaseConnection: refCount is already 0! (source = " + source + ")");
			return;
		}
		refCount--;

		logger.info("releaseConnection: source = {}, refCount = {}", source, refCount);

		cleanupConnection();
	}

	@Override
	public synchronized void releaseConnectionLinger(String source, long timeoutMs) {
		if (refCount == 0) {
			logger.debug("releaseConnectionLinger: refCount is already 0! (source = " + source + ")");
			return;
		}
		refCount--;

		logger.info("releaseConnectionLinger: source = {}, timeout = {}", source, timeoutMs);

		long newLingerUntil = SystemClock.elapsedRealtime() + timeoutMs;

		if (newLingerUntil > lingerUntil) {
			/* must re-schedule alarm */
			lingerUntil = newLingerUntil;

			cancelAlarm(REQUEST_CODE_RELEASE);
			scheduleAlarm(REQUEST_CODE_RELEASE, lingerUntil, false);
		}
	}

	@Override
	public void setPollingInterval(long intervalMs) {
		// account for inexact repeating - which is the default in API 19+
		// "Your alarm's first trigger will not be before the requested time, but it might not occur for almost a full interval after that time"
		if (pollingInterval == intervalMs)
			return;

		pollingInterval = intervalMs;

		if (pollingInterval == 0) {
			/* polling now disabled - cancel alarm */
			cancelAlarm(REQUEST_CODE_POLL);
			logger.info("Polling disabled");
		} else {
			/* polling now enabled - (re)start alarm */
			scheduleRepeatingAlarm(REQUEST_CODE_POLL, SystemClock.elapsedRealtime() + pollingInterval, pollingInterval);
			logger.info("Polling enabled. Interval: {}", pollingInterval);
		}
	}

	public synchronized void alarm(Intent intent) {
		int requestCode = intent.getIntExtra("requestCode", 0);
		long time = System.currentTimeMillis();

		logger.info("Alarm type " + requestCode + " (handling) START");

		switch (requestCode) {
			case REQUEST_CODE_RELEASE:
				cleanupConnection();
				break;
			case REQUEST_CODE_RESEND:
				/* resend attempt - acquire connection for a moment */
				acquireConnection("resend_alarm");
				releaseConnectionLinger("resend_alarm", MESSAGE_SEND_TIME);
				break;
			case REQUEST_CODE_POLL:
				if (pollingHelper == null)
					pollingHelper = new PollingHelper(context, "alarm");
				if (pollingHelper.poll()) {
					updateLastPollTimestamp();
				}

				break;
			case REQUEST_LOCK_APP:
				lockApp();
				break;
		}
		logger.info("Alarm type " + requestCode + " (handling) DONE. Duration = " + (System.currentTimeMillis() - time) + "ms");
	}

	@Override
	public synchronized boolean isActive() {
		return active;
	}

	@Override
	public void addListener(LifetimeServiceListener listener) {
		synchronized (this.listeners) {
			if (!this.listeners.contains(listener)) {
				this.listeners.add(listener);
			}
		}
	}

	@Override
	public void removeListener(LifetimeServiceListener listener) {
		synchronized (this.listeners) {
			this.listeners.remove(listener);
		}

	}

	@Override
	public void clearListeners() {
		synchronized (this.listeners) {
			this.listeners.clear();
		}
	}

	private void cleanupConnection() {
		boolean interrupted = false;
		if (refCount == 0) {
			long curTime = SystemClock.elapsedRealtime();

			if (!active) {
				logger.info("cleanupConnection: connection not active");
			} else if (lingerUntil > curTime && !ThreemaApplication.isIsDeviceIdle()) {
				logger.info("cleanupConnection: connection must linger for another " + (lingerUntil - curTime) + " milliseconds");
			} else if (downloadService != null && downloadService.isDownloading()) {
				logger.info("cleanupConnection: still downloading - linger on");
				cancelAlarm(REQUEST_CODE_RELEASE);
				releaseConnectionLinger("ongoing_download", MESSAGE_SEND_TIME);
			} else {
				try {
					ThreemaApplication.getServiceManager().stopConnection();
				} catch (InterruptedException e) {
					logger.error("Interrupted while stopping connection");
					interrupted = true;
					// Connection cleanup is important, so swallow the interruption here.
				}

				synchronized (this.listeners) {
					Iterator<LifetimeServiceListener> listIterator = this.listeners.iterator();
					while(listIterator.hasNext()) {
						LifetimeServiceListener l = listIterator.next();
						if(l.connectionStopped()) {
							listIterator.remove();
						}
					}
				}

				active = false;
				logger.info("cleanupConnection: connection closed");

				/* check if any messages remain in the queue */
				try {
					int queueSize = ThreemaApplication.getServiceManager().getMessageQueue().getQueueSize();
					if (queueSize > 0) {
						long resendTime = SystemClock.elapsedRealtime() + MESSAGE_RESEND_INTERVAL;
						logger.info(queueSize + " messages remaining in queue; scheduling resend at " + new Date(resendTime).toString());
						scheduleAlarm(REQUEST_CODE_RESEND, resendTime, false);
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				}
			}
		} else {
			logger.info("cleanupConnection: refCount = {} - not cleaning up", refCount);
		}

		if (interrupted) {
			// Re-set interrupted flag
			Thread.currentThread().interrupt();
		}
	}

	private void scheduleAlarm(int requestCode, long triggerAtMillis, boolean whenIdle) {
		long curTime = SystemClock.elapsedRealtime();

		logger.info("Alarm type " + requestCode + " schedule in " + (triggerAtMillis - curTime) + "ms");

		try {
			alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis,
					makePendingIntentForRequestCode(requestCode));
		} catch (Exception e) {
			// KD Interactive C15100m (Pixi3-7_KD), 1024MB RAM, Android 5.0 throws SecurityException here
			logger.error("Exception", e);
		}
	}

	private void scheduleRepeatingAlarm(int requestCode, long triggerAtMillis, long intervalMillis) {
		alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, intervalMillis,
				makePendingIntentForRequestCode(requestCode));
	}

	private void cancelAlarm(int requestCode) {

		logger.info("Alarm type " + requestCode + " cancel");
		alarmManager.cancel(makePendingIntentForRequestCode(requestCode));
	}

	private PendingIntent makePendingIntentForRequestCode(int requestCode) {
		Intent intent = new Intent(context, AlarmManagerBroadcastReceiver.class);
		intent.putExtra("requestCode", requestCode);

		return PendingIntent.getBroadcast(
				context,
				requestCode,
				intent,
				0);
	}

	/**
	 * We want to know when the last successful polling happened. Store it in the preferences.
	 */
	private void updateLastPollTimestamp() {
		try {
			final PreferenceService preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
			if (preferenceService != null) {
				long timestamp = System.currentTimeMillis();
				preferenceService.setLastSuccessfulPollTimestamp(timestamp);
				logger.debug("Updated last poll timestamp");
			}
		} catch (Exception e) {
			//
		}
	}

	private void lockApp(){
		try {
			final LockAppService lockAppService = ThreemaApplication.getServiceManager().getLockAppService();
			if (lockAppService != null) {
				lockAppService.lock();
			}
		} catch (Exception e) {
			logger.warn("Exception: Could not lock app ", e);
		}
	}
}
