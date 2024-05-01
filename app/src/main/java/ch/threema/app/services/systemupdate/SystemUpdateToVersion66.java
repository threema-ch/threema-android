/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

import android.Manifest;
import android.content.Context;

import org.slf4j.Logger;

import java.sql.SQLException;

import androidx.annotation.RequiresPermission;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKeyLockedException;

public class SystemUpdateToVersion66 implements UpdateSystemService.SystemUpdate {
	public static final int VERSION = 66;
	private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion66");
	private Context context;

	public SystemUpdateToVersion66(Context context) {
		this.context = context;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		return true;
	}

	@Override
	public boolean runAsync() {
		if (!ConfigUtils.isPermissionGranted(ThreemaApplication.getAppContext(), Manifest.permission.WRITE_CONTACTS)) {
			return true; // best effort
		}

		forceContactResync();

		return true;
	}

	@RequiresPermission(Manifest.permission.WRITE_CONTACTS)
	private void forceContactResync() {
		logger.info("Force a contacts resync");

		AndroidContactUtil androidContactUtil = AndroidContactUtil.getInstance();
		androidContactUtil.deleteAllThreemaRawContacts();

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			PreferenceService preferenceService = serviceManager.getPreferenceService();
			if (preferenceService != null) {
				if (preferenceService.isSyncContacts()) {
					final SynchronizeContactsService synchronizeContactService;
					try {
						synchronizeContactService = serviceManager.getSynchronizeContactsService();
						if(synchronizeContactService != null) {
							synchronizeContactService.instantiateSynchronizationAndRun();
						}
					} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
						logger.error("Exception", e);
					}
				}
			}
		}
	}

	@Override
	public String getText() {
		return "force a contacts resync";
	}
}
