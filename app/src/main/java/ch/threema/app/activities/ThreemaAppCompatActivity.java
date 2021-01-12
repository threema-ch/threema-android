/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

package ch.threema.app.activities;

import android.content.res.Configuration;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import ch.threema.app.R;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.utils.ConfigUtils;

public abstract class ThreemaAppCompatActivity extends AppCompatActivity {

	@Override
	protected void onResume() {
		if (BackupService.isRunning() || RestoreService.isRunning()) {
			Toast.makeText(this,  R.string.backup_restore_in_progress, Toast.LENGTH_LONG).show();
			finish();
		} else {
			if (ConfigUtils.refreshDeviceTheme(this)) {
				ConfigUtils.recreateActivity(this);
			}
		}
		try {
			super.onResume();
		} catch (IllegalArgumentException ignored) {}
	}


	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
/*		if (ConfigUtils.refreshDeviceTheme(this)) {
			ConfigUtils.recreateActivity(this);
		}
*/	}
}
