/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import ch.threema.app.R;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;

import static ch.threema.app.activities.WhatsNewActivity.EXTRA_NO_ANIMATION;

public class WhatsNew2Activity extends ThreemaAppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		ConfigUtils.configureActivityTheme(this);

		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_whatsnew2);

		((TextView) findViewById(R.id.whatsnew_body)).setText(Html.fromHtml(getString(R.string.whatsnew2_body, getString(R.string.app_name))));

		findViewById(R.id.ok_button).setOnClickListener(v -> {
			finish();
			overridePendingTransition(R.anim.slide_in_right_short, R.anim.slide_out_left_short);
		});

		if (!getIntent().getBooleanExtra(EXTRA_NO_ANIMATION, false)) {
			LinearLayout buttonLayout = findViewById(R.id.button_layout);
			if (savedInstanceState == null) {
				buttonLayout.setVisibility(View.GONE);
				buttonLayout.postDelayed(() -> AnimationUtil.slideInFromBottomOvershoot(buttonLayout), 200);
			}
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onBackPressed() {
		Intent intent = new Intent(WhatsNew2Activity.this, WhatsNewActivity.class);
		intent.putExtra(EXTRA_NO_ANIMATION, true);
		startActivity(intent);
		overridePendingTransition(R.anim.slide_in_left_short, R.anim.slide_out_right_short);
		finish();
	}
}
