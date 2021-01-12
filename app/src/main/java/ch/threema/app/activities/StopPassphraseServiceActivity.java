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

package ch.threema.app.activities;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PassphraseService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.client.ThreemaConnection;
import ch.threema.localcrypto.MasterKey;

/**
 * Simple activity to stop passphrase service, lock master key and finish the app removing it from recents list - to be used from the persistent notification
 */
public class StopPassphraseServiceActivity extends Activity {
	private static final Logger logger = LoggerFactory.getLogger(StopPassphraseServiceActivity.class);

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		MasterKey masterKey = ThreemaApplication.getMasterKey();
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		ThreemaConnection threemaConnection = null;
		NotificationService notificationService = null;

		if (serviceManager != null) {
			threemaConnection = serviceManager.getConnection();
			notificationService = serviceManager.getNotificationService();
		}

		if (masterKey.isProtected()) {
			if (!masterKey.isLocked()) {
				if (threemaConnection != null && threemaConnection.isRunning()) {
					try {
						threemaConnection.stop();
					} catch (InterruptedException e) {
						logger.error("Interrupted in onCreate while stopping threema connection", e);
						Thread.currentThread().interrupt();
					}
				}

				if (notificationService != null){
					notificationService.cancelConversationNotificationsOnLockApp();
				}

				masterKey.lock();
				PassphraseService.stop(this);
				ConfigUtils.scheduleAppRestart(this, 2000, getString(R.string.passphrase_locked));
			}
		}

		if (Build.VERSION.SDK_INT >= 21) {
			finishAndRemoveTask();
		} else {
			finish();
		}
	}
}
