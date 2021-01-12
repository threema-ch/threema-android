/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.utils.LogUtil;
import ch.threema.localcrypto.MasterKeyLockedException;

public class SystemUpdateToVersion16 implements UpdateSystemService.SystemUpdate {
	private static final Logger logger = LoggerFactory.getLogger(SystemUpdateToVersion16.class);

	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion16( SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() {
		//update db first
		String[] messageTableColumnNames = sqLiteDatabase.rawQuery("SELECT * FROM contacts LIMIT 0", null).getColumnNames();

		boolean hasField = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
			@Override
			public boolean apply(@NonNull String type) {
				return type.equals("color");
			}
		}) != null;


		if(!hasField) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN color INT DEFAULT 0");
		}

		return true;
	}

	@Override
	public boolean runASync() {

		ServiceManager s = ThreemaApplication.getServiceManager();
		if(s != null) {
			try {
				ContactService c = s.getContactService();
				if(c != null) {
					return c.rebuildColors();
				}
			} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
				logger.error("Exception", e);
			}
		}
		return false;
	}

	@Override
	public String getText() {
		return "version 16";
	}
}
