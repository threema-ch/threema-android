/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.receivers.AlarmManagerBroadcastReceiver;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.base.utils.LoggingUtil;
import java8.util.stream.StreamSupport;

/**
 * @see LifetimeService
 */
@AnyThread
public class LifetimeServiceImpl implements LifetimeService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("LifetimeServiceImpl");

	public static final String REQUEST_CODE_KEY = "requestCode";
	public static final int REQUEST_CODE_RELEASE = 1;
	public static final int REQUEST_CODE_RESEND = 2;
	public static final int REQUEST_LOCK_APP = 3;

	private static final int MESSAGE_SEND_TIME = 30*1000;
	private static final int MESSAGE_RESEND_INTERVAL = 5*60*1000;   /* 5 minutes */

	private final @NonNull Context context;
	private final @NonNull AlarmManager alarmManager;

	private final  @NonNull Map<String, ConnectionSlot> connectionSlots = new HashMap<>();
	private volatile boolean active = false;
	private volatile boolean paused = false;
	private long lingerUntil = 0;   /* time (in SystemClock.elapsedRealtime()) until which the connection must stay active in any case */

	private @Nullable DownloadService downloadService;

	private final List<LifetimeServiceListener> listeners = new ArrayList<>();

	/**
	 * A reserved "connection slot".
	 */
	private static class ConnectionSlot {
		final boolean unpauseable;
		public ConnectionSlot(boolean unpauseable) {
			this.unpauseable = unpauseable;
		}
	}

	public LifetimeServiceImpl(@NonNull Context context) {
		this.context = context;
		this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		try {
			this.downloadService = ThreemaApplication.getServiceManager().getDownloadService();
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@Override
	public synchronized void acquireConnection(@NonNull String sourceTag, boolean unpauseable) {
		// Ensure that a connection slot is not acquired twice
		if (this.connectionSlots.containsKey(sourceTag)) {
			logger.error("acquireConnection: tag {} already present in connectionSlots", sourceTag);
			return;
		}

		// Store connection slot
		this.connectionSlots.put(sourceTag, new ConnectionSlot(unpauseable));
		logger.info("acquireConnection: tag={}, new #slots={}", sourceTag, this.connectionSlots.size());

		// Establish connection
		this.ensureConnection();
	}

	@Override
	public synchronized void releaseConnection(@NonNull String sourceTag) {
		// Ensure that there are any connections registered
		if (this.connectionSlots.isEmpty()) {
			logger.warn("releaseConnection: #slots is already 0! (source = {})", sourceTag);
			return;
		}

		// Remove slot
		@Nullable ConnectionSlot previousSlot = this.connectionSlots.remove(sourceTag);
		if (previousSlot == null) {
			logger.error("releaseConnection: tag {} was not present in connectionSlots", sourceTag);
		}

		logger.info("releaseConnection: tag={}, #slots={}", sourceTag, this.connectionSlots.size());

		this.cleanupConnection();
	}

	@Override
	public synchronized void releaseConnectionLinger(@NonNull String sourceTag, long timeoutMs) {
		// Ensure that there are any connections registered
		if (this.connectionSlots.isEmpty()) {
			logger.warn("releaseConnectionLinger: #slots is already 0! (source={})", sourceTag);
			return;
		}

		// Remove slot
		@Nullable ConnectionSlot previousSlot = this.connectionSlots.remove(sourceTag);
		if (previousSlot == null) {
			logger.error("releaseConnectionLinger: tag {} was not present in connectionSlots", sourceTag);
		}

		logger.info("releaseConnectionLinger: tag={}, #slots={}, linger={}s",
			sourceTag, this.connectionSlots.size(), timeoutMs / 1000);

		long newLingerUntil = SystemClock.elapsedRealtime() + timeoutMs;
		if (newLingerUntil > lingerUntil) {
			/* must re-schedule alarm */
			lingerUntil = newLingerUntil;

			cancelAlarm(REQUEST_CODE_RELEASE);
			scheduleAlarm(REQUEST_CODE_RELEASE, lingerUntil);
		}
	}

	@Override
	public synchronized void ensureConnection() {
		if (this.connectionSlots.isEmpty()) {
			logger.info("ensureConnection: No connection slots active");
			return;
		}
		if (this.active) {
			logger.info("ensureConnection: A connection is already active");
			return;
		}

		// Do not start a connection if a backup or restore is in progress
		if (RestoreService.isRunning()) {
			logger.warn("ensureConnection: Skipping, restore in progress");
			return;
		}
		if (BackupService.isRunning()) {
			logger.warn("ensureConnection: Skipping, backup in progress");
			return;
		}

		// Start connection
		try {
			ThreemaApplication.getServiceManager().startConnection();
		} catch (Exception e) {
			logger.error("ensureConnection: startConnection failed", e);
			return;
		}
		logger.info("ensureConnection: Connection started");
		this.active = true;
	}

	public synchronized void alarm(Intent intent) {
		int requestCode = intent.getIntExtra("requestCode", 0);
		long time = System.currentTimeMillis();

		logger.info("Alarm type {} (handling) START", requestCode);

		switch (requestCode) {
			case REQUEST_CODE_RELEASE:
				this.cleanupConnection();
				break;
			case REQUEST_CODE_RESEND:
				/* resend attempt - acquire connection for a moment */
				acquireConnection("resendAlarm");
				releaseConnectionLinger("resendAlarm", MESSAGE_SEND_TIME);
				break;
			case REQUEST_LOCK_APP:
				lockApp();
				break;
		}
		logger.info("Alarm type {} (handling) DONE. Duration={} ms", requestCode, System.currentTimeMillis() - time);
	}

	@Override
	public synchronized boolean isActive() {
		return this.active;
	}

	@Override
	public synchronized void pause() {
		logger.info("Pausing connection");

		// Pause connection
		this.paused = true;
		this.cleanupConnection();
	}

	@Override
	public synchronized void unpause() {
		logger.info("Unpausing connection");

		// Restore connection
		if (this.paused) {
			this.paused = false;
		} else {
			logger.info("Cannot unpause, connection is not paused");
		}
		this.ensureConnection();
	}

	@Override
	public void addListener(LifetimeServiceListener listener) {
		synchronized (this.listeners) {
			if (!this.listeners.contains(listener)) {
				this.listeners.add(listener);
			}
		}
	}

	/**
	 * If the connection slots are empty or if the state is paused, disconnect.
	 */
	private synchronized void cleanupConnection() {
		boolean interrupted = false;
		long unpausableSlots = StreamSupport.stream(this.connectionSlots.values())
				.filter(slot -> slot.unpauseable)
				.count();

		// We can only disconnect if
		//
		// - there are no unpausable slots, and
		// - a pause was either requested or there are no slots at all.
		//
		// Note: This is required to circumvent a bug where a broadcast never unpauses the
		// connection, see ANDR-2337. All vital components need to acquire an unpausable
		// connection.
		if (unpausableSlots == 0 && (this.connectionSlots.isEmpty() || this.paused)) {
			long curTime = SystemClock.elapsedRealtime();

			if (!this.active) {
				logger.info("cleanupConnection: Connection not active");
			} else if (lingerUntil > curTime && !ThreemaApplication.isIsDeviceIdle()) {
				logger.info("cleanupConnection: Connection must linger for another {} milliseconds", lingerUntil - curTime);
			} else if (downloadService != null && downloadService.isDownloading()) {
				logger.info("cleanupConnection: Ctill downloading - linger on");
				cancelAlarm(REQUEST_CODE_RELEASE);
				acquireConnection("ongoingDownload");
				releaseConnectionLinger("ongoingDownload", MESSAGE_SEND_TIME);
			} else {
				try {
					ThreemaApplication.getServiceManager().stopConnection();
				} catch (InterruptedException e) {
					logger.error("Interrupted while stopping connection");
					interrupted = true;
					// Connection cleanup is important, so postpone the interruption here.
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

				this.active = false;
				logger.info("cleanupConnection: Connection closed, #slots={}", this.connectionSlots.size());

				/* check if any messages remain in the queue */
				try {
					int queueSize = ThreemaApplication.getServiceManager().getMessageQueue().getQueueSize();
					if (queueSize > 0) {
						long resendTime = SystemClock.elapsedRealtime() + MESSAGE_RESEND_INTERVAL;
						logger.info("cleanupConnection: {} messages remaining in queue; scheduling resend at {}", queueSize, new Date(resendTime));
						scheduleAlarm(REQUEST_CODE_RESEND, resendTime);
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				}
			}
		} else {
			logger.info("cleanupConnection: Not cleaning up #slots={}, #unpausable-slots={}", this.connectionSlots.size(), unpausableSlots);
		}

		if (interrupted) {
			// Re-set interrupted flag
			Thread.currentThread().interrupt();
		}
	}

	private void scheduleAlarm(int requestCode, long triggerAtMillis) {
		long curTime = SystemClock.elapsedRealtime();

		logger.info("Alarm type {} schedule in {} ms", requestCode, triggerAtMillis - curTime);

		try {
			alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis,
					makePendingIntentForRequestCode(requestCode));
		} catch (Exception e) {
			// KD Interactive C15100m (Pixi3-7_KD), 1024MB RAM, Android 5.0 throws SecurityException here
			logger.error("Exception", e);
		}
	}

	private void cancelAlarm(int requestCode) {
		logger.info("Alarm type {} cancel", requestCode);
		alarmManager.cancel(makePendingIntentForRequestCode(requestCode));
	}

	private PendingIntent makePendingIntentForRequestCode(int requestCode) {
		Intent intent = new Intent(context, AlarmManagerBroadcastReceiver.class);
		intent.putExtra(AlarmManagerBroadcastReceiver.EXTRA_REQUEST_CODE, requestCode);

		return PendingIntent.getBroadcast(
			context,
			requestCode,
			intent,
			IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE);
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
