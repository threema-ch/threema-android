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

package ch.threema.app.backuprestore.csv;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import ch.threema.app.backuprestore.BackupRestoreDataService;
import ch.threema.app.services.FileService;
import ch.threema.base.ThreemaException;

public class BackupRestoreDataServiceImpl implements BackupRestoreDataService {
	private static final Logger logger = LoggerFactory.getLogger("BackupRestoreDataServiceImpl");

	private final Context context;
	private final FileService fileService;


	public BackupRestoreDataServiceImpl(
			Context context,
			FileService fileService) {
		this.context = context;
		this.fileService = fileService;
	}

	@Override
	public boolean deleteBackup(BackupData backupData) throws IOException, ThreemaException {
		logger.info("Deleting backup");
		this.fileService.remove(backupData.getFile(), true);
		return true;
	}

	@Override
	public List<BackupData> getBackups() {
		File[] files = this.fileService.getBackupPath().listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith(".zip");
			}
		});

		List<BackupData> result = new ArrayList<BackupData>();

		if (files != null) {
			for (final File f : files) {
				BackupData data = this.getBackupData(f);
				if (data != null && data.getIdentity() != null) {
					result.add(data);
				}
			}
		}

		Collections.sort(result, new Comparator<BackupData>() {
			@Override
			public int compare(BackupData lhs, BackupData rhs) {
				return rhs.getBackupTime().compareTo(lhs.getBackupTime());
			}
		});
		return result;
	}

	@Override
	public BackupData getBackupData(final File file) {
		if (file != null && file.exists()) {
			String[] pieces = file.getName().split("_");
			String idPart = null;
			Date datePart = null;

			if (pieces.length > 2 && pieces[0].equals("threema-backup")) {
				idPart = pieces[1];

				try {
					datePart = new Date();
					datePart.setTime(Long.valueOf(pieces[2]));
				} catch (NumberFormatException e) {
					idPart = null;
					datePart = null;
					logger.error("Exception", e);
				}
			}

			final String identity = idPart;
			final Date time = datePart;
			final long size = file.length();

			return new BackupData() {
				@Override
				public File getFile() {
						return file;
					}

				@Override
				public String getIdentity() {
						return identity;
					}

				@Override
				public Date getBackupTime() {
						return time;
					}

				@Override
				public long getFileSize() {
						return size;
					}
			};

		}
		return null;
	}


}
