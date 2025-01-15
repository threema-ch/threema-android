/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaAppCompatActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.FileService;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public abstract class WizardBackgroundActivity extends ThreemaAppCompatActivity {
	private static final Logger logger = LoggingUtil.getThreemaLogger("WizardBackgroundActivity");

	protected ServiceManager serviceManager;
	protected PreferenceService preferenceService;
    protected NotificationPreferenceService notificationPreferenceService;
	protected UserService userService;
	protected FileService fileService;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (!requiredInstances()) {
			finish();
		}
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onStart() {
		super.onStart();

		HorizontalScrollView hsv = findViewById(R.id.background_image);
		// disable scrolling
		if (hsv != null) {
			hsv.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					return true;
				}
			});
		}
	}

	@Override
	protected boolean enableOnBackPressedCallback() {
		return true;
	}

	@Override
	protected void handleOnBackPressed() {
		// catch back key
	}

	private boolean requiredInstances() {
		if(!this.checkInstances()) {
			this.instantiate();
		}
		return this.checkInstances();
	}

	private boolean checkInstances() {
		return TestUtil.required(
			this.preferenceService,
			this.userService,
			this.fileService
		);
	}

	private void instantiate() {
		serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			this.preferenceService = serviceManager.getPreferenceService();
            this.notificationPreferenceService = serviceManager.getNotificationPreferenceService();
			try {
				this.userService = serviceManager.getUserService();
				this.fileService = serviceManager.getFileService();
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}
}
