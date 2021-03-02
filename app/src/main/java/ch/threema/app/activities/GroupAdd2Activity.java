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

package ch.threema.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.storage.models.GroupModel;

public class GroupAdd2Activity extends GroupEditActivity implements ContactEditDialog.ContactEditDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(GroupAdd2Activity.class);

	private static final String DIALOG_TAG_CREATING_GROUP = "groupCreate";
	private static final String BUNDLE_GROUP_IDENTITIES = "grId";
	private String[] groupIdentities;

	@Override
	public int getLayoutResource() {
		return R.layout.activity_group_add2;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		logger.debug("onCreate");
		super.onCreate(savedInstanceState);

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setTitle("");
		}

		this.groupIdentities = IntentDataUtil.getContactIdentities(getIntent());

		if (savedInstanceState == null) {
			launchGroupSetNameAndAvatarDialog();
		} else {
			groupIdentities = savedInstanceState.getStringArray(BUNDLE_GROUP_IDENTITIES);
		}
	}

	private void createGroup(final String groupName, final String[] groupIdentities, final File avatarFile) {
		new AsyncTask<Void, Void, GroupModel>() {
			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(R.string.creating_group, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_CREATING_GROUP);
			}

			@Override
			protected GroupModel doInBackground(Void... params) {
				try {
					Bitmap avatar = avatarFile != null ? BitmapFactory.decodeFile(avatarFile.getPath()) : null;
					return groupService.createGroup(
							groupName,
							groupIdentities,
							avatar
					);
				} catch (Exception x) {
					logger.error("Exception", x);
				}
				return null;
			}

			@Override
			protected void onPostExecute(GroupModel newModel) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_CREATING_GROUP, true);

				if (newModel != null) {
					creatingGroupDone(newModel);
				} else {
					Toast.makeText(GroupAdd2Activity.this, getString(R.string.error_creating_group) + ": " + getString(R.string.internet_connection_required), Toast.LENGTH_LONG).show();
					setResult(RESULT_CANCELED);
					finish();
				}
			}
		}.execute();
	}

	private void creatingGroupDone(GroupModel newModel) {
		Toast.makeText(ThreemaApplication.getAppContext(), getString(R.string.group_created_confirm), Toast.LENGTH_LONG).show();

		Intent intent = new Intent(this, ComposeMessageActivity.class);
		intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, newModel.getId());
		setResult(RESULT_OK);
		startActivity(intent);
		finish();
	}

	@Override
	public void onYes(String tag, String text1, String text2, File avatarFile) {
		createGroup(text1, this.groupIdentities, avatarFile);
	}

	@Override
	public void onNo(String tag) {
		finish();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putSerializable(BUNDLE_GROUP_IDENTITIES, groupIdentities);

		super.onSaveInstanceState(outState);
	}
}
