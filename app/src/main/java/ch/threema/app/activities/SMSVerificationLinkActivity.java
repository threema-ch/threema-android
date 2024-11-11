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

package ch.threema.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import org.slf4j.Logger;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public class SMSVerificationLinkActivity extends AppCompatActivity {
	private static final Logger logger = LoggingUtil.getThreemaLogger("SMSVerificationLinkActivity");

	private UserService userService;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Integer resultText = R.string.verify_failed_summary;

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		if (serviceManager != null) {
			this.userService = serviceManager.getUserService();
			if (this.userService != null) {
				if (this.userService.getMobileLinkingState() == UserService.LinkingState_PENDING) {
					Intent intent = getIntent();
					Uri data = intent.getData();
					if (data != null) {
						final String code = data.getQueryParameter("code");

						if (!TestUtil.isEmptyOrNull(code)) {
							resultText = null;

							new AsyncTask<Void, Void, Boolean>() {
								@Override
								protected Boolean doInBackground(Void... params) {
									try {
										userService.verifyMobileNumber(code);
										return true;
									} catch (Exception x) {
										logger.error("Exception", x);
									}
									return false;
								}

								@Override
								protected void onPostExecute(Boolean result) {
									showConfirmation(result ? R.string.verify_success_text : R.string.verify_failed_summary);
								}
							}.execute();
						}
					}
				} else if (this.userService.getMobileLinkingState() == UserService.LinkingState_LINKED) {
					// already linked
					resultText = R.string.verify_success_text;
				} else if (this.userService.getMobileLinkingState() == UserService.LinkingState_NONE) {
					resultText = R.string.verify_failed_not_linked;
				}
			}
		}
		showConfirmation(resultText);
		finish();
	}

	private void showConfirmation(Integer resultText) {
		if (resultText != null) {
			@StringRes int resId = resultText;

			Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
		}
	}
}
