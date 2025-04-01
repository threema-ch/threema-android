/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.AddContactActivity;
import ch.threema.app.adapters.UserListAdapter;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.storage.models.ContactModel;

public class UserListFragment extends RecipientListFragment {
    @Override
    protected boolean isMultiSelectAllowed() {
        return multiSelect || multiSelectIdentity;
    }

    @Override
    protected String getBundleName() {
        return "UserListState";
    }

    @Override
    protected int getEmptyText() {
        return R.string.no_matching_contacts;
    }

    @Override
    protected int getAddIcon() {
        return R.drawable.ic_person_add_outline;
    }

    @Override
    protected int getAddText() {
        return R.string.menu_add_contact;
    }

    @Override
    protected Intent getAddIntent() {
        Intent intent = new Intent(getActivity(), AddContactActivity.class);
        intent.putExtra(AddContactActivity.EXTRA_ADD_BY_ID, true);

        return intent;
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void createListAdapter(ArrayList<Integer> checkedItemPositions) {
        new AsyncTask<Void, Void, List<ContactModel>>() {
            @Override
            protected List<ContactModel> doInBackground(Void... voids) {
                if (ConfigUtils.isWorkBuild()) {
                    // Only show non-work contacts here, because the work contacts are shown in the
                    // work tab. Note that we exclude invalid contacts, as they cannot receive
                    // messages anyways.
                    return Functional.filter(
                        contactService.getAllDisplayed(ContactService.ContactSelection.EXCLUDE_INVALID),
                        (IPredicateNonNull<ContactModel>) value -> !value.isWork()
                    );
                } else {
                    // Exclude invalid contacts because they cannot receive messages anyways
                    return contactService.getAllDisplayed(ContactService.ContactSelection.EXCLUDE_INVALID);
                }
            }

            @Override
            protected void onPostExecute(List<ContactModel> contactModels) {
                adapter = new UserListAdapter(
                    activity,
                    contactModels,
                    null,
                    checkedItemPositions,
                    contactService,
                    blockedIdentitiesService,
                    hiddenChatsListService,
                    preferenceService,
                    UserListFragment.this,
                    Glide.with(ThreemaApplication.getAppContext())
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
