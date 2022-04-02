/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

package ch.threema.app.asynctasks;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import org.slf4j.Logger;

import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public class LinkWithEmailAsyncTask extends AsyncTask<Void, Void, String> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("LinkWithEmailAsyncTask");
	private static final String DIALOG_TAG_PROGRESS = "lpr";

	private UserService userService;
	private final Runnable runOnCompletion;
	private final String emailAddress;
	private final FragmentManager fragmentManager;
	private final Context context;

	private static final int MODE_NONE = 0;
	private static final int MODE_LINK = 1;
	private static final int MODE_UNLINK = 2;
	private static final int MODE_CHECK = 3;

	private int linkingMode = MODE_NONE;

	public LinkWithEmailAsyncTask(Context context, FragmentManager fragmentManager, String emailAddress, Runnable runOnCompletion) {
		this.fragmentManager = fragmentManager;
		this.emailAddress = emailAddress;
		this.runOnCompletion = runOnCompletion;
		this.context = context;

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		try {
			this.userService = serviceManager.getUserService();
		} catch (Exception e) {
			//
		}
	}

	@Override
	protected void onPreExecute() {
		@StringRes int dialogText = 0;

		if (TestUtil.empty(emailAddress)) {
			if (userService.getEmailLinkingState() != UserService.LinkingState_NONE) {
				linkingMode = MODE_UNLINK;
				dialogText = R.string.unlinking_email;
			}
		} else {
			if (userService.getEmailLinkingState() != UserService.LinkingState_NONE && userService.getLinkedEmail().equals(emailAddress)) {
				linkingMode = MODE_CHECK;
			} else {
				linkingMode = MODE_LINK;
				dialogText = R.string.wizard2_email_linking;
			}
		}

		if (dialogText != 0) {
			GenericProgressDialog.newInstance(dialogText, R.string.please_wait).show(fragmentManager, DIALOG_TAG_PROGRESS);
		}
	}

	@Override
	protected String doInBackground(Void... params) {
		if (this.userService == null) {
			logger.error("UserService not available");
			return null;
		}

		String resultString = null;

		switch (linkingMode) {
			case MODE_UNLINK:
				try {
					userService.unlinkEmail();
				} catch (Exception x) {
					logger.error("exception", x);
					resultString = String.format(context.getString(R.string.an_error_occurred_more), x.getMessage());
				}
				break;
			case MODE_CHECK:
				resultString = context.getString(R.string.email_already_linked);
				break;
			case MODE_LINK:
				try {
					userService.linkWithEmail(emailAddress);
				} catch (Exception x) {
					resultString = String.format(context.getString(R.string.an_error_occurred_more), x.getMessage());
				}
				break;
			default:
				break;
		}
		return resultString;
	}

	@Override
	protected void onPostExecute(String resultString) {
		DialogUtil.dismissDialog(fragmentManager, DIALOG_TAG_PROGRESS, true);

		if (resultString != null) {
			Toast.makeText(ThreemaApplication.getAppContext(), resultString, Toast.LENGTH_LONG).show();
		}

		if (runOnCompletion != null) {
			runOnCompletion.run();
		}
	}
}
