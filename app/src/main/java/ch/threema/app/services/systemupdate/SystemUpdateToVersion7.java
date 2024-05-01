/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

import android.database.Cursor;
import android.os.Environment;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.slf4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.base.utils.LoggingUtil;

public class SystemUpdateToVersion7 implements UpdateSystemService.SystemUpdate {
	private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion7");

	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion7(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() {

		//update db first
		String[] messageTableColumnNames = sqLiteDatabase.rawQuery("SELECT * FROM message LIMIT 0", null).getColumnNames();


		boolean hasUidField = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
			@Override
			public boolean apply(@NonNull String type) {
				return type.equals("uid");
			}
		}) != null;


		if(!hasUidField) {
			//update the message model with the uid and move every file to the new filename rule
			sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN uid VARCHAR(50) DEFAULT NULL");
		}

		return true;
	}

	@Override
	public boolean runAsync() {
		FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.startsWith(".") && filename.contains("-");
			}
		};

		File appPath = null;
		try {
			appPath = ThreemaApplication.getServiceManager().getFileService().getAppDataPath();
		} catch (FileSystemNotPresentException e) {
			logger.error("Exception", e);
			return false;
		}

		HashMap<Integer, List<File>> fileIndex = new HashMap<Integer, List<File>>();
		for(String path: new String[]{Environment.getExternalStorageDirectory() + "/.threema", Environment.getExternalStorageDirectory() + "/Threema/.threema"}) {
			File pathFile = new File(path);
			if(!pathFile.exists()) {
				continue;
			}
			for(File file : pathFile.listFiles(filter)) {
				String[] pieces = file.getName().substring(1).split("-");
				if(pieces.length >= 2) {
					try {
						Integer key = Integer.parseInt(pieces[0]);


						if(!fileIndex.containsKey(key)) {
							fileIndex.put(key, new ArrayList<File>());
						}
						fileIndex.get(key).add(file);
					} catch(NumberFormatException e) {
						//do nothing!!
					}
				}
			}
		}

		Cursor messages = sqLiteDatabase.rawQuery("SELECT id FROM message", null);
		while(messages.moveToNext()) {
			final int id = messages.getInt(0);
			String uid = UUID.randomUUID().toString();

			if(fileIndex.containsKey(id) && fileIndex.get(id).size() > 0) {
				for(File ftm: fileIndex.get(id)) {
					String postFix = ftm.getName().substring(String.valueOf(id).length() + 2);
					File newFileToMerge = new File(appPath.getPath() + "/." + uid + "-" + postFix);
					if (!ftm.renameTo(newFileToMerge)) {
						logger.debug("Unable to rename file");
					}
				}
			}

			sqLiteDatabase.rawExecSQL("UPDATE message SET uid = '" + uid + "' WHERE id = " + String.valueOf(id));
		}
		messages.close();

		return true;
	}

	@Override
	public String getText() {
		return "version 7";
	}
}
