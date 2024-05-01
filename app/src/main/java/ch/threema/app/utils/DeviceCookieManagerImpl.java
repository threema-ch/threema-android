/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

import org.slf4j.Logger;

import java.security.SecureRandom;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.NotificationService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ServerMessageModel;

public class DeviceCookieManagerImpl implements DeviceCookieManager {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DeviceCookieManagerImpl");

	private static final int DEVICE_COOKIE_SIZE = 16;

	private final ServiceManager serviceManager;
	private boolean skipNextIndication;

	public DeviceCookieManagerImpl(ServiceManager serviceManager) {
		this.serviceManager = serviceManager;
		this.skipNextIndication = false;
	}

	@Override
	public byte[] obtainDeviceCookie() {
		// TODO(ANDR-2155): When the target API level is >= 23, use Android Keystore to store the device cookie

		byte[] deviceCookie = serviceManager.getPreferenceStore().getBytes(ThreemaApplication.getAppContext().getString(R.string.preferences__device_cookie), true);
		if (deviceCookie != null && deviceCookie.length == DEVICE_COOKIE_SIZE) {
			logger.debug("Got existing device cookie {}...", Utils.byteArrayToHexString(deviceCookie).substring(0, 4));
			return deviceCookie;
		}

		// Generate and store new random device cookie
		deviceCookie = new byte[DEVICE_COOKIE_SIZE];
		SecureRandom random = new SecureRandom();
		random.nextBytes(deviceCookie);
		serviceManager.getPreferenceStore().save(ThreemaApplication.getAppContext().getString(R.string.preferences__device_cookie), deviceCookie, true);

		logger.info("Generated new device cookie {}...", Utils.byteArrayToHexString(deviceCookie).substring(0, 4));

		// Skip the next indication, as we have just generated a new cookie and
		// will get an indication for sure if this is a restored ID (where the
		// server has already stored a device cookie).
		this.skipNextIndication = true;

		return deviceCookie;
	}

	@Override
	public void changeIndicationReceived() {
		if (this.skipNextIndication) {
			logger.info("Skipping change indication because new cookie has been generated");
			this.skipNextIndication = false;
			return;
		}

		logger.info("Device cookie change indication received, showing warning message");

		ServerMessageModel serverMessageModel = new ServerMessageModel(ThreemaApplication.getAppContext().getString(R.string.rogue_device_warning), ServerMessageModel.TYPE_ALERT);
		DatabaseServiceNew databaseService = serviceManager.getDatabaseServiceNew();
		databaseService.getServerMessageModelFactory().storeServerMessageModel(serverMessageModel);

		NotificationService n = serviceManager.getNotificationService();
		if (n != null) {
			n.showServerMessage(serverMessageModel);
		}
	}

	@Override
	public void deleteDeviceCookie() {
		serviceManager.getPreferenceStore().remove(ThreemaApplication.getAppContext().getString(R.string.preferences__device_cookie));
	}
}
