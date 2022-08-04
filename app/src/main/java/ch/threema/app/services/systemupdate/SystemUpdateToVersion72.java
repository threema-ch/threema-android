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

package ch.threema.app.services.systemupdate;

import net.sqlcipher.database.SQLiteDatabase;

import org.slf4j.Logger;

import java.sql.SQLException;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;

/**
 * For ID colors we store the first byte of the SHA-256 hash of the contact identity.
 */
public class SystemUpdateToVersion72 implements UpdateSystemService.SystemUpdate {
	public static final int VERSION = 72;
	public static final String VERSION_STRING = "version " + VERSION;

	private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion72");

	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion72(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		final String table = "contacts";
		final String columnColor = "color";
		final String columnColorIndex = "idColorIndex";

		// Rename color column to id color index column
		sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " RENAME " + columnColor + " TO " + columnColorIndex);
		// Temporarily set value to -1 to prevent null pointer exception when loading the contacts
		sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET " + columnColorIndex + " = -1");
		return true;
	}

	@Override
	public boolean runASync() {
		ServiceManager s = ThreemaApplication.getServiceManager();
		if (s != null) {
			try {
				ContactService contactService = s.getContactService();
				if (contactService != null) {
					for (ContactModel contact : contactService.getAll(true, true)) {
						contact.initializeIdColor();
						contactService.save(contact);
					}
				}
			} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
				logger.error("Exception", e);
				return false;
			}
		}

		return true;
	}

	@Override
	public String getText() {
		return VERSION_STRING;
	}
}
