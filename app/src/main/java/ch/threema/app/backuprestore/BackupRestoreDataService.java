/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.app.backuprestore;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import ch.threema.base.ThreemaException;

public interface BackupRestoreDataService {

	interface BackupData {

		//backup file
		File getFile();

		/**
		 * Identity backup
		 * @return
		 */
		String getIdentity();

		/**
		 * Time of the backup
		 * @return
		 */
		Date getBackupTime();

		/**
		 * size of the backup file
		 * @return
		 */
		long getFileSize();
	}

	interface RestoreResult {
		long getContactSuccess();
		long getContactFailed();
		long getMessageSuccess();
		long getMessageFailed();
	}

	/**
	 * Delete Zip File from File System
	 * @param backup
	 * @return
	 * @throws IOException
	 */
	boolean deleteBackup(BackupData backup) throws IOException, ThreemaException;

	/**
	 * list of all available backups
	 * @return
	 */
	List<BackupData> getBackups();

	BackupData getBackupData(File file);
}
