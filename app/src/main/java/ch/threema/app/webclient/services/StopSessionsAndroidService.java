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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.webclient.services.instance.DisconnectContext;

/**
 * Simple service to stop all webclient sessions - to be used from the persistent notification
 */
public class StopSessionsAndroidService extends Service {
	private static final Logger logger = LoggerFactory.getLogger(StopSessionsAndroidService.class);

	private SessionService sessionService;

	@Override
	public void onCreate() {
		super.onCreate();
		try {
			sessionService = ThreemaApplication.getServiceManager().getWebClientServiceManager().getSessionService();
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (sessionService != null) {
			sessionService.stopAll(DisconnectContext.byUs(DisconnectContext.REASON_SESSION_STOPPED));
		}

		stopSelf();

		return START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
