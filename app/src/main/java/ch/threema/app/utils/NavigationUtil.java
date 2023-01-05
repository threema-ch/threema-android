/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

package ch.threema.app.utils;

import android.app.Activity;
import android.content.Intent;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import ch.threema.app.R;
import ch.threema.app.activities.PinLockActivity;
import ch.threema.base.utils.LoggingUtil;

public class NavigationUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("NavigationUtil");

	public static void navigateUpToHome(@NonNull Activity activity) {
		// navigate to home and get rid of the backstack (since we may have pulled the rug from under our feet)
		// use this, if there are intent filters to get to this activity
		Intent upIntent = NavUtils.getParentActivityIntent(activity);
		if (upIntent != null && (NavUtils.shouldUpRecreateTask(activity, upIntent) || activity.isTaskRoot())) {
			TaskStackBuilder.create(activity)
					.addNextIntentWithParentStack(upIntent)
					.startActivities();
			activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
		} else {
			try {
				NavUtils.navigateUpFromSameTask(activity);
				activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
			} catch (IllegalArgumentException e) {
				logger.info("Missing parent activity entry in manifest for " + activity.getComponentName());
				logger.error("Exception", e);
			}
		}
	}

	public static void navigateToLauncher(Activity activity) {
		if (activity != null) {
			// go to launcher home!
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_HOME);
			try {
				activity.startActivity(intent);
				if (!(activity instanceof PinLockActivity)) {
					activity.finish();
				}
			} catch (RuntimeException ignored) {}
		}
	}
}
