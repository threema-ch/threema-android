/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

package ch.threema.app.threemasafe;

import android.app.Activity;
import android.content.Context;
import android.text.format.DateUtils;

import java.io.IOException;
import java.util.ArrayList;

import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;

public interface ThreemaSafeService {

	int ERROR_CODE_OK = 0;
	int ERROR_CODE_SERVER_FAIL = 1;
	int ERROR_CODE_JSON_FAIL = 2;
	int ERROR_CODE_GZIP_FAIL = 3;
	int ERROR_CODE_UPLOAD_FAIL = 4;
	int ERROR_CODE_HASH_FAIL = 5;
	int ERROR_CODE_SIZE_EXCEEDED = 6;

	long SCHEDULE_PERIOD = DateUtils.DAY_IN_MILLIS;

	int BACKUP_ID_LENGTH = 32;

	@Nullable
	byte[] deriveMasterKey(String password, String identity);

	boolean storeMasterKey(byte[] masterKey);

	byte[] getThreemaSafeBackupId();

	byte[] getThreemaSafeEncryptionKey();

	byte[] getThreemaSafeMasterKey();

	ThreemaSafeServerTestResponse testServer(ThreemaSafeServerInfo serverInfo) throws ThreemaException;

	boolean scheduleUpload();

	void unscheduleUpload();

	boolean isUploadDue();

	void setEnabled(boolean enabled);

	void uploadNow(Context context, boolean force);

	void createBackup(boolean force) throws ThreemaException;

	void deleteBackup() throws ThreemaException;

	void restoreBackup(String identity, String password, ThreemaSafeServerInfo serverInfo) throws ThreemaException, IOException;

	@Nullable ArrayList<String> searchID(String phone, String email);

	/**
	 * Launch the password dialog to setup Threema Safe.
	 *
	 * @param activity         the activity that starts the threema safe config activity
	 * @param openHomeActivity if set to true, the home activity is started after successfully choosing a password
	 */
	void launchForcedPasswordDialog(Activity activity, boolean openHomeActivity);
}
