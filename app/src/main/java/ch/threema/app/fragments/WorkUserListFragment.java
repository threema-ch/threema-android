/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.activities.DirectoryActivity;
import ch.threema.app.adapters.UserListAdapter;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.storage.models.ContactModel;

public class WorkUserListFragment extends RecipientListFragment {
	@Override
	protected boolean isMultiSelectAllowed() {
		return multiSelect;
	}

	@Override
	protected String getBundleName() {
		return "WorkerUserListState";
	}

	@Override
	protected int getEmptyText() {
		return R.string.no_matching_work_contacts;
	}

	@Override
	protected int getAddIcon() {
		return 0;
	}

	@Override
	protected int getAddText() {
		return 0;
	}

	@Override
	protected Intent getAddIntent() {
		return null;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);

		if (ConfigUtils.isWorkRestricted() && !multiSelect && view != null && ConfigUtils.isWorkDirectoryEnabled()) {
			ListView listView = view.findViewById(android.R.id.list);

			RelativeLayout header = (RelativeLayout) getLayoutInflater().inflate(R.layout.item_user_list_directory_header, listView, false);
			((TextView) header.findViewById(R.id.name)).setText(preferenceService.getWorkOrganization().getName());
			((ImageView) header.findViewById(R.id.avatar)).setImageResource(R.drawable.ic_business);
			header.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(getContext(), DirectoryActivity.class);
					intent.putExtra(DirectoryActivity.EXTRA_ANIMATE_OUT, true);
					startActivity(intent);
					if (getActivity() != null) {
						getActivity().overridePendingTransition(R.anim.slide_in_right_short, R.anim.slide_out_left_short);
					}
				}
			});

			listView.addHeaderView(header, null, false);
		}
		return view;
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	protected void createListAdapter(ArrayList<Integer> checkedItemPositions) {
		new AsyncTask<Void, Void, List<ContactModel>>() {
			@Override
			protected List<ContactModel> doInBackground(Void... voids) {
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

				return Functional.filter(contactService.find(new ContactService.Filter() {
					@Override
					public ContactModel.State[] states() {
						return contactStates;
					}

					@Override
					public Integer requiredFeature() {
						return null;
					}

					public Boolean fetchMissingFeatureLevel() {
						return false;
					}

					@Override
					public Boolean includeMyself() {
						return false;
					}

					@Override
					public Boolean includeHidden() {
						return false;
					}
				}), new IPredicateNonNull<ContactModel>() {
					@Override
					public boolean apply(@NonNull ContactModel type) {
						return type.isWork();
					}
				});
			}

			@Override
			protected void onPostExecute(List<ContactModel> contactModels) {
				adapter = new UserListAdapter(
					activity,
					contactModels,
					null,
					checkedItemPositions,
					contactService,
					blacklistService,
					hiddenChatsListService
				);
				setListAdapter(adapter);
				if (listInstanceState != null) {
					if (isAdded() && getView() != null && getActivity() != null) {
						getListView().onRestoreInstanceState(listInstanceState);
					}
					listInstanceState = null;
					restoreCheckedItems(checkedItemPositions);
				}
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
}
