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

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.ListFragment;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.HashSet;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.GroupAddActivity;
import ch.threema.app.activities.MemberChooseActivity;
import ch.threema.app.activities.ProfilePicRecipientsActivity;
import ch.threema.app.adapters.FilterResultsListener;
import ch.threema.app.adapters.FilterableListAdapter;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.utils.LogUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ContactModel;

public abstract class MemberListFragment extends ListFragment implements FilterResultsListener {
	public static final String BUNDLE_ARG_PRESELECTED = "pres";
	public static final String BUNDLE_ARG_EXCLUDED = "excl";

	protected ContactService contactService;
	protected GroupService groupService;
	protected DistributionListService distributionListService;
	protected ConversationService conversationService;
	protected PreferenceService preferenceService;
	protected BlockedIdentitiesService blockedIdentitiesService;
	protected DeadlineListService hiddenChatsListService;
	protected Activity activity;
	protected Parcelable listInstanceState;
	protected ExtendedFloatingActionButton floatingActionButton;
	protected ArrayList<String> preselectedIdentities = new ArrayList<>();
	protected ArrayList<String> excludedIdentities = new ArrayList<>();
	protected CircularProgressIndicator progressBar;
	protected View topLayout;
	protected FilterableListAdapter adapter;
	private SelectionListener selectionListener;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		activity = getActivity();
		selectionListener = (MemberChooseActivity) activity;

		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		try {
			contactService = serviceManager.getContactService();
			groupService = serviceManager.getGroupService();
			distributionListService = serviceManager.getDistributionListService();
			blockedIdentitiesService = serviceManager.getBlockedIdentitiesService();
			conversationService = serviceManager.getConversationService();
			preferenceService = serviceManager.getPreferenceService();
			hiddenChatsListService = serviceManager.getHiddenChatsListService();
		} catch (ThreemaException e) {
			LogUtil.exception(e, getActivity());
			return null;
		}

		Bundle bundle = getArguments();
		if (bundle != null) {
			preselectedIdentities = bundle.getStringArrayList(BUNDLE_ARG_PRESELECTED);
			excludedIdentities = bundle.getStringArrayList(BUNDLE_ARG_EXCLUDED);
		}

		topLayout = inflater.inflate(R.layout.fragment_list, container, false);
		return topLayout;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		ArrayList<Integer> checkedItemPositions = null;

		// recover after rotation
		if (savedInstanceState != null) {
			this.listInstanceState = savedInstanceState.getParcelable(getBundleName());
			checkedItemPositions = savedInstanceState.getIntegerArrayList(getBundleName() + "c");
		}

		createListAdapter(checkedItemPositions, preselectedIdentities, excludedIdentities, activity instanceof GroupAddActivity, activity instanceof ProfilePicRecipientsActivity);
		preselectedIdentities = null;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		getListView().setDividerHeight(0);
		getListView().setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		getListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);

		progressBar = view.findViewById(R.id.progress);

		floatingActionButton = view.findViewById(R.id.floating);
		floatingActionButton.hide();
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		selectionListener.onSelectionChanged();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		try {
			ListView listView = getListView();

			if (listView != null) {
				outState.putParcelable(getBundleName(), listView.onSaveInstanceState());
				// save checked items, if any
				if (listView.getChoiceMode() == AbsListView.CHOICE_MODE_MULTIPLE && getAdapter().getCheckedItemCount() > 0) {
					outState.putIntegerArrayList(getBundleName() + "c", getAdapter().getCheckedItemPositions());
				}
			}
		} catch (Exception e) {
			// getListView may cause IllegalStateException
		}

		super.onSaveInstanceState(outState);
	}

	protected void onAdapterCreated() {
		selectionListener.onSelectionChanged();
	}

	public HashSet<ContactModel> getSelectedContacts() {
		if (getAdapter() != null) {
			return (HashSet<ContactModel>) getAdapter().getCheckedItems();
		}
		return new HashSet<>();
	}

	public FilterableListAdapter getAdapter() { return adapter; }

	void setListAdapter(FilterableListAdapter adapter) {
		super.setListAdapter(adapter);

		if (isAdded()) {
			try {
				progressBar.setVisibility(View.GONE);

				// add text view if contact list is empty
				EmptyView emptyView = new EmptyView(activity);
				emptyView.setup(getEmptyText());
				((ViewGroup) getListView().getParent()).addView(emptyView);
				getListView().setEmptyView(emptyView);
			} catch (IllegalStateException ignored) {}
		}
	}

	@Override
	public void onResultsAvailable(int count) {
		if (isAdded() && activity != null) {
			((MemberChooseActivity) activity).onQueryResultChanged(this, count);
		}
	}

	public interface SelectionListener {
		void onSelectionChanged();
	}

	protected abstract void createListAdapter(ArrayList<Integer> checkedItems, ArrayList<String> preselectedIdentities, ArrayList<String> excludedIdentities, boolean group, boolean profilePics);
	protected abstract String getBundleName();
	protected abstract @StringRes int getEmptyText();
}
