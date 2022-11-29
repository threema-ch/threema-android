/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Arrays;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.NotificationService;
import ch.threema.base.utils.Base64;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.connection.RogueDeviceMonitor;
import ch.threema.storage.models.ServerMessageModel;

public class RogueDeviceMonitorImpl implements RogueDeviceMonitor {
	private static final int MAX_STORED_EPHEMERAL_KEY_HASHES = 1000;

	private static final Logger logger = LoggingUtil.getThreemaLogger("RogueDeviceMonitor");

	private final ServiceManager serviceManager;
	private boolean skipNextCheck;

	public RogueDeviceMonitorImpl(ServiceManager serviceManager) {
		this.serviceManager = serviceManager;
	}

	@Override
	public void recordEphemeralKeyHash(byte[] ephemeralKeyHash, boolean postLogin) {
		JSONArray array = getStoredHashes();

		logger.info("Record hash (post login: {}): {}", postLogin, Base64.encodeBytes(ephemeralKeyHash));

		if (array.length() == 0) {
			// We are in a "virgin" state, i.e. don't have any hashes recorded yet.
			if (postLogin) {
				// We are post-login, so we can be sure "our" hash has been stored on the server,
				// but the server may still send a hash from the previous connection (before we
				// started recording), so skip the next check to prevent a false alarm.
				skipNextCheck = true;
			} else {
				// We are pre-login, so don't record this new first hash yet, as we want to be sure
				// one of "our" hashes has actually made it to the server.
				return;
			}
		}

		// Check if we have already recorded this hash (e.g. pre-login)
		try {
			for (int i = 0; i < array.length(); i++) {
				if (Arrays.equals(ephemeralKeyHash, Base64.decode(array.getString(i)))) {
					return;
				}
			}
		} catch (JSONException | IOException e) {
			logger.error("Exception", e);
		}

		// Prevent unbounded growth (e.g. if server does not send indications)
		while (array.length() >= MAX_STORED_EPHEMERAL_KEY_HASHES) {
			array.remove(0);
		}
		array.put(Base64.encodeBytes(ephemeralKeyHash));
		storeHashes(array);
	}

	@Override
	public void checkEphemeralKeyHash(byte[] ephemeralKeyHash) {
		logger.info("Check hash: {}", Base64.encodeBytes(ephemeralKeyHash));

		if (skipNextCheck) {
			logger.info("Skipping check");
			skipNextCheck = false;
			return;
		}

		JSONArray array = getStoredHashes();
		if (array.length() == 0) {
			// First connection, no key hashes recorded yet
			return;
		}
		boolean found = false;
		try {
			int i;
			for (i = 0; i < array.length(); i++) {
				if (Arrays.equals(ephemeralKeyHash, Base64.decode(array.getString(i)))) {
					found = true;
					break;
				}
			}

			if (found) {
				// We can now remove all older hashes
				while (i > 0) {
					array.remove(0);
					i--;
				}
				storeHashes(array);
			} else {
				logger.info("Hash not found, showing warning message");
				NotificationService n = serviceManager.getNotificationService();
				if (n != null) {
					n.showServerMessage(new ServerMessageModel(ThreemaApplication.getAppContext().getString(R.string.rogue_device_warning), ServerMessageModel.Type.ALERT));
				}
			}
		} catch (JSONException | IOException e) {
			logger.error("Exception", e);
		}
	}

	private JSONArray getStoredHashes() {
		JSONArray array = serviceManager.getPreferenceStore().getJSONArray(ThreemaApplication.getAppContext().getString(R.string.preferences__last_ephemeral_key_hashes), true);
		logger.info("Currently stored hashes: {}", array);
		return array;
	}

	private void storeHashes(JSONArray array) {
		logger.info("New stored hashes: {}", array);
		serviceManager.getPreferenceStore().save(ThreemaApplication.getAppContext().getString(R.string.preferences__last_ephemeral_key_hashes), array, true);
	}
}
