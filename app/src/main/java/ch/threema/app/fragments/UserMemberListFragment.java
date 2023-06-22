/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

package ch.threema.app.fragments;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.adapters.UserListAdapter;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.storage.models.ContactModel;

public class UserMemberListFragment extends MemberListFragment {

	@Override
	protected String getBundleName() {
		return "UserMemberListState";
	}

	@Override
	protected int getEmptyText() {
		return R.string.no_matching_contacts;
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	protected void createListAdapter(ArrayList<Integer> checkedItemPositions, final ArrayList<String> preselectedIdentities, final ArrayList<String> excludedIdentities, boolean groups, boolean profilePics) {
		new AsyncTask<Void, Void, List<ContactModel>>() {
			@Override
			protected List<ContactModel> doInBackground(Void... voids) {
				List<ContactModel> contactModels;

				if (groups) {
					final ContactModel.State[] contactStates;
					if (preferenceService.showInactiveContacts()) {
						contactStates = new ContactModel.State[]{
							ContactModel.State.ACTIVE,
							ContactModel.State.INACTIVE
						};
					} else {
						contactStates = new ContactModel.State[]{
							ContactModel.State.ACTIVE
						};
					}

					contactModels = contactService.find(new ContactService.Filter() {
						@Override
						public ContactModel.State[] states() {
							return contactStates;
						}

						@Override
						public Integer requiredFeature() {
							//required!
							return ThreemaFeature.GROUP_CHAT;
						}

						@Override
						public Boolean fetchMissingFeatureLevel() {
							return true;
						}

						@Override
						public Boolean includeMyself() {
							return false;
						}

						@Override
						public Boolean includeHidden() {
							return false;
						}

						@Override
						public Boolean onlyWithReceiptSettings() {
							return false;
						}
					});
				} else if (profilePics) {
					contactModels = contactService.getCanReceiveProfilePics();
				} else {
					// Don't include invalid contacts because they should not be added to groups
					contactModels = contactService.getAllDisplayed(ContactService.ContactSelection.EXCLUDE_INVALID);
				}

				if (ConfigUtils.isWorkBuild()) {
					contactModels = Functional.filter(contactModels, new IPredicateNonNull<ContactModel>() {
						@Override
						public boolean apply(@NonNull ContactModel type) {
							return !type.isWork();
						}
					});
				}

				if (excludedIdentities != null) {
					//exclude existing ids
					contactModels = Functional.filter(contactModels, new IPredicateNonNull<ContactModel>() {
						@Override
						public boolean apply(@NonNull ContactModel contactModel) {
							return !excludedIdentities.contains(contactModel.getIdentity());
						}
					});
				}
				return contactModels;
			}

			@Override
			protected void onPostExecute(List<ContactModel> contactModels) {
				adapter = new UserListAdapter(
					activity,
					contactModels,
					preselectedIdentities,
					checkedItemPositions,
					contactService,
					blacklistService,
					hiddenChatsListService,
					preferenceService
				);
				setListAdapter(adapter);
				if (listInstanceState != null) {
					if (isAdded() && getView() != null && getActivity() != null) {
						getListView().onRestoreInstanceState(listInstanceState);
					}
					listInstanceState = null;
				}
				onAdapterCreated();
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
}
