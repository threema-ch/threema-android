/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.activities.DistributionListAddActivity;
import ch.threema.app.adapters.DistributionListAdapter;
import ch.threema.app.services.DistributionListService;
import ch.threema.storage.models.DistributionListModel;

public class DistributionListFragment extends RecipientListFragment {
	@Override
	protected boolean isMultiSelectAllowed() {
		return false;
	}

	@Override
	protected String getBundleName() {
		return "DistListState";
	}

	@Override
	protected int getEmptyText() {
		return R.string.no_matching_distribution_lists;
	}

	@Override
	protected int getAddIcon() {
		return R.drawable.ic_bullhorn_outline;
	}

	@Override
	protected int getAddText() {
		return R.string.title_add_distribution_list;
	}

	@Override
	protected Intent getAddIntent() {
		return new Intent(getActivity(), DistributionListAddActivity.class);
	}

	@SuppressLint("StaticFieldLeak")
	protected void createListAdapter(ArrayList<Integer> checkedItemPositions) {
		new AsyncTask<Void, Void, List<DistributionListModel>>() {
			@Override
			protected List<DistributionListModel> doInBackground(Void... voids) {
				return distributionListService.getAll(new DistributionListService.DistributionListFilter() {
					@Override
					public boolean sortingByDate() {
						return true;
					}

					@Override
					public boolean sortingAscending() {
						return false;
					}

					@Override
					public boolean showHidden() {
						return false;
					}
				});
			}

			@Override
			protected void onPostExecute(List<DistributionListModel> distributionListModels) {
				adapter = new DistributionListAdapter(
					activity,
					distributionListModels,
					checkedItemPositions,
					distributionListService
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
