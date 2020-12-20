/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
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

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.preference.SettingsActivity;
import ch.threema.app.utils.AnimationUtil;

public class AboutActivity extends ThreemaToolbarActivity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.menu_about);
		}

		ImageView threemaLogo = findViewById(R.id.threema_logo);
		AnimationUtil.bubbleAnimate(threemaLogo, 200);

		// Enable developer menu
		if (BuildConfig.DEBUG) {
			this.preferenceService.setShowDeveloperMenu(true);
			Toast
				.makeText(this, "You are now a craaazy developer!", Toast.LENGTH_LONG)
				.show();
		}
	}

	public int getLayoutResource() {
		return R.layout.activity_about;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				break;
		}
		return false;
	}
}
