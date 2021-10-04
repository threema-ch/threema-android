/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.client.ThreemaFeature;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;

/**
 * Update all Contacts with Feature Level < current Feature Level
 */
public class SystemUpdateToVersion39 extends  UpdateToVersion implements UpdateSystemService.SystemUpdate {
	private static final Logger logger = LoggerFactory.getLogger(SystemUpdateToVersion39.class);

	public SystemUpdateToVersion39() {
	}

	@Override
	public boolean runDirectly() {
		return true;
	}

	@Override
	public boolean runASync() {
		// lazy get services
		if(ThreemaApplication.getServiceManager() == null) {
			logger.error("update script 39 failed, no service manager available");
			return false;
		}

		try {
			ContactService contactService = ThreemaApplication.getServiceManager().getContactService();

			// call find with fetchMissingFeatureLevel = true to fetch all contacts without current feature level
			contactService.find(new ContactService.Filter() {
				@Override
				public ContactModel.State[] states() {
					return null;
				}

				@Override
				public Integer requiredFeature() {
					return ThreemaFeature.VOIP;
				}

				@Override
				public Boolean fetchMissingFeatureLevel() {
					return true;
				}

				@Override
				public Boolean includeMyself() {
					return true;
				}

				@Override
				public Boolean includeHidden() {
					return true;
				}

				@Override
				public Boolean onlyWithReceiptSettings() {
					return false;
				}
			});
		} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
			logger.error("update script 39 failed", e);
			return false;
		}

		return true;
	}

	@Override
	public String getText() {
		return "sync feature levels";
	}
}
