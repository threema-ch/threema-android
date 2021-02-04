/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.app.activities.wizard;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import ch.threema.app.R;
import ch.threema.app.ui.AnimationDrawableCallback;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;

public class WizardStartActivity extends WizardBackgroundActivity {
	private static final Logger logger = LoggerFactory.getLogger(WizardStartActivity.class);

	boolean doFinish = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wizard_start);

		final ImageView imageView = findViewById(R.id.wizard_animation);
		imageView.setBackgroundResource(R.drawable.animation_wizard1);
		if (!RuntimeUtil.isInTest() && !ConfigUtils.isWorkRestricted()) {
			imageView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					((AnimationDrawable) v.getBackground()).stop();
					launchNextActivity(null);
				}
			});
			imageView.getRootView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
				AnimationDrawable frameAnimation = (AnimationDrawable) imageView.getBackground();
				frameAnimation.setOneShot(true);
				frameAnimation.setCallback(new AnimationDrawableCallback(frameAnimation, imageView) {
					@Override
					public void onAnimationAdvanced(int currentFrame, int totalFrames) {
					}

					@Override
					public void onAnimationCompleted() {
						ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
							// the context of the activity
							WizardStartActivity.this,

							new Pair<>(findViewById(R.id.wizard_animation),
								getString(R.string.transition_name_dots)),
							new Pair<>(findViewById(R.id.wizard_footer),
								getString(R.string.transition_name_logo))
						);
						launchNextActivity(options);
					}
				});
				frameAnimation.start();
			});
		} else {
			launchNextActivity(null);
		}
	}

	private void launchNextActivity(ActivityOptionsCompat options) {
		Intent intent;

		if (userService != null && userService.hasIdentity()) {
			intent = new Intent(this, WizardBaseActivity.class);
			options = null;
		} else {
			intent = new Intent(this, WizardIntroActivity.class);
		}

		if (options != null) {
			try {
				ActivityCompat.startActivity(this, intent, options.toBundle());
			} catch (Exception e) {
				// http://stackoverflow.com/questions/31026745/rjava-lang-illegalargumentexception-on-startactivityintent-bundle-animantion
				logger.error("Exception", e);
				startActivity(intent);
			}
		} else {
			startActivity(intent);
			overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
		}
		doFinish = true;
	}

	@Override
	public void onStop() {
		super.onStop();
		if (doFinish)
			finish();
	}
}
