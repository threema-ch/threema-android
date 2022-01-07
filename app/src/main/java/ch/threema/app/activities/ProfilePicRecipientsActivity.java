/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.services.IdListService;
import ch.threema.app.utils.LogUtil;
import ch.threema.storage.models.ContactModel;

public class ProfilePicRecipientsActivity extends MemberChooseActivity {
	private IdListService profilePicRecipientsService;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		if (!super.initActivity(savedInstanceState)) {
			return false;
		}

		try {
			this.profilePicRecipientsService = serviceManager.getProfilePicRecipientsService();
		} catch (Exception e) {
			LogUtil.exception(e, this);
			return false;
		}

		initData(savedInstanceState);

		return true;
	}

	@Override
	protected int getNotice() {
		return R.string.prefs_sum_receive_profilepics_recipients_list;
	}

	@Override
	protected boolean getAddNextButton() {
		return false;
	}

	@Override
	protected void initData(Bundle savedInstanceState) {
		if (savedInstanceState == null) {
			String[] ids = profilePicRecipientsService.getAll();

			if (ids != null && ids.length > 0) {
				preselectedIdentities = new ArrayList<>(Arrays.asList(ids));
			}
		}

		updateToolbarTitle(R.string.profile_picture, R.string.title_choose_recipient);

		initList();
	}

	@Override
	protected void menuNext(List<ContactModel> selectedContacts) {
		if (selectedContacts.size() > 0) {
			List<String> ids = new ArrayList<>(selectedContacts.size());

			for (ContactModel contactModel : selectedContacts) {
				if (contactModel != null) {
					ids.add(contactModel.getIdentity());
				}
			}

			if (ids.size() > 0) {
				profilePicRecipientsService.addAll(ids.toArray(new String[ids.size()]));
				finish();
				return;
			}
		}
		profilePicRecipientsService.removeAll();
		finish();
	}

	@Override
	public void onBackPressed() {
		this.menuNext(getSelectedContacts());
	}
}
