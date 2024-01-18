/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;

public abstract class ThreemaAppCompatActivity extends AppCompatActivity {

	private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaAppCompatActivity");

	protected int savedDayNightMode;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		savedDayNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		ConfigUtils.setCurrentDayNightMode(savedDayNightMode == UI_MODE_NIGHT_YES ? MODE_NIGHT_YES : MODE_NIGHT_NO);

		// Enable the on back pressed callback if the activity uses custom back navigation
		if (enableOnBackPressedCallback()) {
			getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
				@Override
				public void handleOnBackPressed() {
					ThreemaAppCompatActivity.this.handleOnBackPressed();
				}
			});
		}
	}

	@Override
	protected void onResume() {
		if (BackupService.isRunning() || RestoreService.isRunning()) {
			Intent intent = new Intent(this, BackupRestoreProgressActivity.class);
			startActivity(intent);
			finish();
		}
		try {
			super.onResume();
		} catch (IllegalArgumentException ignored) {}
	}


	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		int newDayNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		if (savedDayNightMode != newDayNightMode) {
			savedDayNightMode = newDayNightMode;
			ConfigUtils.setCurrentDayNightMode(newDayNightMode == UI_MODE_NIGHT_YES ? MODE_NIGHT_YES : MODE_NIGHT_NO);

			// Reset avatar cache on theme change so that the default avatars are loaded with the correct (themed) color
			ServiceManager sm = ThreemaApplication.getServiceManager();
			if (sm != null) {
				try {
					sm.getAvatarCacheService().clear();
				} catch (FileSystemNotPresentException e) {
					logger.error("Couldn't get avatar cache service to reset cached avatars", e);
				}
			}
			recreate();
		}
		super.onConfigurationChanged(newConfig);
	}

	/**
	 * If an activity overrides this and returns {@code true}, then a lifecycle-aware on back
	 * pressed callback is added that calls {@link #handleOnBackPressed()} when the back button has
	 * been pressed.
	 *
	 * @return {@code true} if the back navigation should be intercepted, {@code false} otherwise
	 */
	protected boolean enableOnBackPressedCallback() {
		return false;
	}

	/**
	 * Handle an on back pressed event. This method gets only called when the on back pressed
	 * callback is registered. This only happens when {@link #enableOnBackPressedCallback()} returns
	 * {@code true}. Note that this method is lifecycle aware, i.e., it is not called if the
	 * activity has been destroyed.
	 */
	protected void handleOnBackPressed() {
		// Nothing to do here
	}
}
