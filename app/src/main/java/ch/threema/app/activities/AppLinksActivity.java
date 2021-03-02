/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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
import android.os.Bundle;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.asynctasks.AddContactAsyncTask;
import ch.threema.app.services.LockAppService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.HiddenChatUtil;
import ch.threema.client.ProtocolDefines;

public class AppLinksActivity extends ThreemaToolbarActivity {
	private static final Logger logger = LoggerFactory.getLogger(AppLinksActivity.class);

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		checkLock();
	}

	@Override
	public int getLayoutResource() {
		// invisible activity
		return 0;
	}

	@Override
	protected boolean isPinLockable() {
		// we handle pin locking ourselves
		return false;
	}

	private void checkLock() {
		LockAppService lockAppService;
		try {
			lockAppService = ThreemaApplication.getServiceManager().getLockAppService();
		} catch (Exception e) {
			finish();
			return;
		}

		if (lockAppService != null) {
			if (lockAppService.isLocked()) {
				HiddenChatUtil.launchLockCheckDialog(this, preferenceService);
			} else {
				handleIntent();
			}
		} else {
			finish();
		}
	}

	private void handleIntent() {
		String appLinkAction = getIntent().getAction();
		final Uri appLinkData = getIntent().getData();
		if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData != null){
			final String threemaId = appLinkData.getLastPathSegment();
			if (threemaId != null && threemaId.length() == ProtocolDefines.IDENTITY_LEN) {
				new AddContactAsyncTask(null, null, threemaId, false, () -> {
					String text = appLinkData.getQueryParameter("text");

					Intent intent = new Intent(AppLinksActivity.this, text != null ?
							ComposeMessageActivity.class :
							ContactDetailActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, threemaId);
					intent.putExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, Boolean.TRUE);

					if (text != null) {
						text = text.trim();
						intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, text);
					}

					startActivity(intent);
				}).execute();
			} else {
				Toast.makeText(this, R.string.invalid_threema_id, Toast.LENGTH_LONG).show();
			}
		}
		finish();
	}

	@Override
	public void finish() {
		super.finish();
		overridePendingTransition(0, 0);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		logger.debug("onActivityResult");
		switch (requestCode) {
			case ThreemaActivity.ACTIVITY_ID_CHECK_LOCK:
				if (resultCode == RESULT_OK) {
					handleIntent();
				} else {
					Toast.makeText(this, getString(R.string.pin_locked_cannot_send), Toast.LENGTH_LONG).show();
					finish();
				}
				break;
			case ThreemaActivity.ACTIVITY_ID_UNLOCK_MASTER_KEY:
				if (ThreemaApplication.getMasterKey().isLocked()) {
					finish();
				} else {
					ConfigUtils.recreateActivity(this, AppLinksActivity.class, getIntent().getExtras());
				}
				break;
			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}
}
