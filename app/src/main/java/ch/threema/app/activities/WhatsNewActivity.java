/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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
import android.text.Html;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import ch.threema.app.R;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;

public class WhatsNewActivity extends ThreemaAppCompatActivity {
	public static final String EXTRA_NO_ANIMATION = "noanim";

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		ConfigUtils.configureSystemBars(this);

		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_whatsnew);

		String appName = getString(R.string.app_name);

		((TextView) findViewById(R.id.whatsnew_title)).setText(getString(R.string.whatsnew_title, appName));
		((TextView) findViewById(R.id.whatsnew_body)).setText(Html.fromHtml(getString(R.string.whatsnew_headline, appName)));

		findViewById(R.id.next_text).setOnClickListener(v -> {
/*			startActivity(new Intent(WhatsNewActivity.this, WhatsNew2Activity.class));
			overridePendingTransition(R.anim.slide_in_right_short, R.anim.slide_out_left_short);
*/			finish();
		});

		if (!getIntent().getBooleanExtra(EXTRA_NO_ANIMATION, false)) {
			LinearLayout buttonLayout = findViewById(R.id.button_layout);
			if (savedInstanceState == null) {
				buttonLayout.setVisibility(View.GONE);
				buttonLayout.postDelayed(() -> AnimationUtil.slideInFromBottomOvershoot(buttonLayout), 200);
			}
		}
	}
}
