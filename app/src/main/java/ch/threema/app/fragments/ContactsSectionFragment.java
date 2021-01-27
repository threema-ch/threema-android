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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.util.Pair;
import androidx.core.view.MenuItemCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.AddContactActivity;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.ContactDetailActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.adapters.ContactListAdapter;
import ch.threema.app.asynctasks.DeleteContactAsyncTask;
import ch.threema.app.dialogs.BottomSheetAbstractDialog;
import ch.threema.app.dialogs.BottomSheetGridDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.jobs.WorkSyncService;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.PreferenceListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.AvatarCacheService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.BottomSheetItem;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.LockingSwipeRefreshLayout;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.workers.IdentityStatesWorker;
import ch.threema.base.ThreemaException;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;

import static android.view.MenuItem.SHOW_AS_ACTION_ALWAYS;
import static android.view.MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW;
import static android.view.MenuItem.SHOW_AS_ACTION_NEVER;

public class ContactsSectionFragment
		extends MainFragment
		implements
		SwipeRefreshLayout.OnRefreshListener,
		ListView.OnItemClickListener,
		ContactListAdapter.AvatarListener,
		GenericAlertDialog.DialogClickListener,
		BottomSheetAbstractDialog.BottomSheetDialogCallback {
	private static final Logger logger = LoggerFactory.getLogger(ContactsSectionFragment.class);

	private static final int PERMISSION_REQUEST_REFRESH_CONTACTS = 1;
	private static final String DIALOG_TAG_REALLY_DELETE_CONTACTS = "rdc";
	private static final String DIALOG_TAG_SHARE_WITH = "wsw";

	private final String RUN_ON_ACTIVE_SHOW_LOADING = "show_loading";
	private final String RUN_ON_ACTIVE_HIDE_LOADING = "hide_loading";
	private final String RUN_ON_ACTIVE_UPDATE_LIST = "update_list";
	private final String RUN_ON_ACTIVE_REFRESH_LIST = "refresh_list";
	private final String RUN_ON_ACTIVE_REFRESH_PULL_TO_REFRESH = "pull_to_refresh";

	private final String BUNDLE_FILTER_QUERY_C = "BundleFilterC";

	private ResumePauseHandler resumePauseHandler;
	private ListView listView;
	private Chip contactsCounterChip;
	private LockingSwipeRefreshLayout swipeRefreshLayout;
	private ServiceManager serviceManager;
	private SearchView searchView;
	private MenuItem searchMenuItem;
	private ContactListAdapter contactListAdapter;
	private ActionMode actionMode = null;
	private ExtendedFloatingActionButton floatingButtonView;
	private EmojiTextView stickyInitialView;
	private FrameLayout stickyInitialLayout;

	private SynchronizeContactsService synchronizeContactsService;
	private ContactService contactService;
	private PreferenceService preferenceService;
	private LockAppService lockAppService;

	private String filterQuery;

	/**
	 * Simple POJO to hold the number of contacts that were added in the last 24h / 30d.
	 */
	private static class RecentlyAddedCounts {
		int last24h = 0;
		int last30d = 0;
	}

	// Contacts changed receiver
	private BroadcastReceiver contactsChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (resumePauseHandler != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_REFRESH_LIST, runIfActiveUpdateList);
			}
		}
	};

	private void startSwipeRefresh() {
		if (swipeRefreshLayout != null) {
			swipeRefreshLayout.setRefreshing(true);
		}
	}

	private void stopSwipeRefresh() {
		if (swipeRefreshLayout != null) {
			swipeRefreshLayout.setRefreshing(false);
		}
	}

	private final ResumePauseHandler.RunIfActive runIfActiveShowLoading = new ResumePauseHandler.RunIfActive() {
		@Override
		public void runOnUiThread() {
			// do nothing
		}
	};

	private final ResumePauseHandler.RunIfActive runIfActiveClearCacheAndRefresh = new ResumePauseHandler.RunIfActive() {
		@Override
		public void runOnUiThread() {
			if (synchronizeContactsService != null && !synchronizeContactsService.isSynchronizationInProgress()) {

				stopSwipeRefresh();

				if (serviceManager != null) {
					try {
						AvatarCacheService avatarCacheService = serviceManager.getAvatarCacheService();
						if (avatarCacheService != null) {
							//clear the cache
							avatarCacheService.clear();
						}
					} catch (FileSystemNotPresentException e) {
						logger.error("Exception", e);
					}
				}
				updateList();
			}
		}
	};

	private final ResumePauseHandler.RunIfActive runIfActiveUpdateList = new ResumePauseHandler.RunIfActive() {
		@Override
		public void runOnUiThread() {
			if (synchronizeContactsService == null || !synchronizeContactsService.isSynchronizationInProgress()) {
				updateList();
			}
		}
	};

	private final ResumePauseHandler.RunIfActive runIfActiveUpdatePullToRefresh = new ResumePauseHandler.RunIfActive() {
		@Override
		public void runOnUiThread() {
			if (TestUtil.required(swipeRefreshLayout, preferenceService)) {
				swipeRefreshLayout.setEnabled(true);
			}
		}
	};

	private final ResumePauseHandler.RunIfActive runIfActiveCreateList = new ResumePauseHandler.RunIfActive() {
		@Override
		public void runOnUiThread() {
			createListAdapter();
		}
	};

	private final SynchronizeContactsListener synchronizeContactsListener = new SynchronizeContactsListener() {
		@Override
		public void onStarted(SynchronizeContactsRoutine startedRoutine) {
			//only show loading on "full sync"
			if (resumePauseHandler != null && swipeRefreshLayout != null && startedRoutine.fullSync()) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_SHOW_LOADING, runIfActiveShowLoading);
			}
		}

		@Override
		public void onFinished(SynchronizeContactsRoutine finishedRoutine) {
			if (resumePauseHandler != null && swipeRefreshLayout != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_HIDE_LOADING, runIfActiveClearCacheAndRefresh);
			}
		}

		@Override
		public void onError(SynchronizeContactsRoutine finishedRoutine) {
			if (resumePauseHandler != null && swipeRefreshLayout != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_HIDE_LOADING, runIfActiveClearCacheAndRefresh);
			}
		}
	};

	private final ContactSettingsListener contactSettingsListener = new ContactSettingsListener() {
		@Override
		public void onSortingChanged() {
			if (resumePauseHandler != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_REFRESH_LIST, runIfActiveCreateList);
			}
		}

		@Override
		public void onNameFormatChanged() {
			if (resumePauseHandler != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_REFRESH_LIST, runIfActiveUpdateList);
			}
		}

		@Override
		public void onAvatarSettingChanged() {
			if (resumePauseHandler != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_REFRESH_LIST, runIfActiveUpdateList);
			}
		}

		@Override
		public void onInactiveContactsSettingChanged() {
			if (resumePauseHandler != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_REFRESH_LIST, runIfActiveUpdateList);
			}
		}

		@Override
		public void onNotificationSettingChanged(String uid) {

		}
	};

	private final ContactListener contactListener = new ContactListener() {
		@Override
		public void onModified(ContactModel modifiedContactModel) {
			logger.debug("*** onModified " + modifiedContactModel.getIdentity());
			if (resumePauseHandler != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_UPDATE_LIST, runIfActiveUpdateList);
			}
		}

		@Override
		public void onAvatarChanged(ContactModel contactModel) {
			logger.debug("*** onAvatarChanged -> onModified " + contactModel.getIdentity());
			this.onModified(contactModel);
		}

		@Override
		public void onNew(final ContactModel createdContactModel) {
			if (resumePauseHandler != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_UPDATE_LIST, runIfActiveUpdateList);
			}
		}

		@Override
		public void onRemoved(ContactModel removedContactModel) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (searchView != null && searchMenuItem != null && searchMenuItem.isActionViewExpanded()) {
						filterQuery = null;
						searchMenuItem.collapseActionView();
					}
				}
			});

			if (resumePauseHandler != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_UPDATE_LIST, runIfActiveCreateList);
			}
		}

		@Override
		public boolean handle(String identity) {
			return true;
		}
	};

	private final PreferenceListener preferenceListener = new PreferenceListener() {
		@Override
		public void onChanged(String key, Object value) {
			if (TestUtil.compare(key, getString(R.string.preferences__sync_contacts))) {
				if (resumePauseHandler != null) {
					resumePauseHandler.runOnActive(RUN_ON_ACTIVE_REFRESH_PULL_TO_REFRESH, runIfActiveUpdatePullToRefresh);
				}
			}
		}
	};

	/**
	 * An AsyncTask that fetches contacts and add counts in the background.
	 *
	 * NOTE: The ContactService needs to be passed in as a parameter!
	 */
	private abstract static class FetchContactsTask extends AsyncTask<ContactService, Void, Pair<List<ContactModel>, RecentlyAddedCounts>> {
		@Override
		protected Pair<List<ContactModel>, RecentlyAddedCounts> doInBackground(ContactService... contactServices) {
			final ContactService contactService = contactServices[0];

			// Fetch contacts
			final List<ContactModel> allContacts = contactService.getAll();

			// Count new contacts
			final RecentlyAddedCounts counts = new RecentlyAddedCounts();
			long now = System.currentTimeMillis();
			long delta24h = 1000L * 3600 * 24;
			long delta30d = delta24h * 30;
			for (ContactModel contact : allContacts) {
				final Date dateCreated = contact.getDateCreated();
				if (dateCreated == null) {
					continue;
				}
				if (now - dateCreated.getTime() < delta24h) {
					counts.last24h += 1;
				}
				if (now - dateCreated.getTime() < delta30d) {
					counts.last30d += 1;
				}
			}

			return new Pair<>(allContacts, counts);
		}
	}

	@Override
	public void onResume() {
		logger.debug("*** onResume");
		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onResume();
		}

		if (this.swipeRefreshLayout != null) {
			this.swipeRefreshLayout.setEnabled(this.listView != null && this.listView.getFirstVisiblePosition() == 0);
			stopSwipeRefresh();
		}
		super.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();
		logger.debug("*** onPause");

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onPause();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.debug("*** onCreate");

		setRetainInstance(true);
		setHasOptionsMenu(true);

		setupListeners();

		this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this.getActivity());

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.runOnActive(RUN_ON_ACTIVE_REFRESH_PULL_TO_REFRESH, runIfActiveUpdatePullToRefresh);
		}
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);
		logger.debug("*** onAttach");
	}

	@Override
	public void onDestroy() {
		logger.debug("*** onDestroy");

		removeListeners();

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onDestroy(this);
		}

		super.onDestroy();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		logger.debug("*** onHiddenChanged: " + hidden);
		if (hidden) {
			if (actionMode != null) {
				actionMode.finish();
			}

			if (this.searchView != null && this.searchView.isShown() && this.searchMenuItem != null) {
				this.searchMenuItem.collapseActionView();
			}
			if (this.resumePauseHandler != null) {
				this.resumePauseHandler.onPause();
			}
		} else {
			if (this.resumePauseHandler != null) {
				this.resumePauseHandler.onResume();
			}
		}
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		super.onPrepareOptionsMenu(menu);

		// move search item to popup if the lock item is visible
		if (lockAppService.isLockingEnabled()) {
			this.searchMenuItem.setShowAsAction(SHOW_AS_ACTION_NEVER | SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		} else {
			this.searchMenuItem.setShowAsAction(SHOW_AS_ACTION_ALWAYS | SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		logger.debug("*** onCreateOptionsMenu");
		searchMenuItem = menu.findItem(R.id.menu_search_contacts);

		if (searchMenuItem == null) {
			inflater.inflate(R.menu.fragment_contacts, menu);

			// Associate searchable configuration with the SearchView
			if (getActivity() != null && this.isAdded()) {
				SearchManager searchManager =
					(SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);

				this.searchMenuItem = menu.findItem(R.id.menu_search_contacts);
				this.searchView = (SearchView) searchMenuItem.getActionView();

				if (this.searchView != null && searchManager != null) {
					SearchableInfo mSearchableInfo = searchManager.getSearchableInfo(getActivity().getComponentName());
					if (this.searchView != null) {
						if (!TestUtil.empty(filterQuery)) {
							// restore filter
							MenuItemCompat.expandActionView(searchMenuItem);
							this.searchView.post(new Runnable() {
								@Override
								public void run() {
									searchView.setQuery(filterQuery, true);
									searchView.clearFocus();
								}
							});
						}
						this.searchView.setSearchableInfo(mSearchableInfo);
						this.searchView.setQueryHint(getString(R.string.hint_filter_list));
						this.searchView.setOnQueryTextListener(queryTextListener);
					}
				}
			}
		}
		super.onCreateOptionsMenu(menu, inflater);
	}

	final SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
		@Override
		public boolean onQueryTextChange(String query) {
			if (contactListAdapter != null && contactListAdapter.getFilter() != null) {
				filterQuery = query;
				contactListAdapter.getFilter().filter(query);
			}
			return true;
		}

		@Override
		public boolean onQueryTextSubmit(String query) {
			return true;
		}
	};

	@SuppressLint("StaticFieldLeak")
	protected void createListAdapter() {
		if (getActivity() == null) {
			return;
		}

		if (!this.requiredInstances()) {
			return;
		}

		new FetchContactsTask() {
			@Override
			protected void onPostExecute(Pair<List<ContactModel>, RecentlyAddedCounts> result) {
				final List<ContactModel> contactModels = result.first;
				final RecentlyAddedCounts counts = result.second;
				if (contactModels != null) {
					updateContactsCounter(contactModels.size(), counts);
					if (contactModels.size() > 0) {
						((EmptyView) listView.getEmptyView()).setup(R.string.no_matching_contacts);
					}

					if (isAdded() && getContext() != null) {
						contactListAdapter = new ContactListAdapter(
								getContext(),
								contactModels,
								contactService,
								serviceManager.getPreferenceService(),
								serviceManager.getBlackListService(),
								ContactsSectionFragment.this
						);
						listView.setAdapter(contactListAdapter);
					}
				}
			}
		}.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, this.contactService);
	}

	@SuppressLint("StaticFieldLeak")
	private void updateList() {
		if (!this.requiredInstances()) {
			logger.error("could not instantiate required objects");
			return;
		}

		if (contactListAdapter != null) {
			new FetchContactsTask() {
				@Override
				protected void onPostExecute(Pair<List<ContactModel>, RecentlyAddedCounts> result) {
					final List<ContactModel> contactModels = result.first;
					final RecentlyAddedCounts counts = result.second;

					if (contactModels != null && contactListAdapter != null && isAdded()) {
						updateContactsCounter(contactModels.size(), counts);
						contactListAdapter.updateData(contactModels);
					}
				}
			}.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, this.contactService);
		}
	}

	private void updateContactsCounter(int numContacts, @Nullable RecentlyAddedCounts counts) {
		if (getActivity() != null && listView != null && isAdded()) {
			if (contactsCounterChip != null) {
				if (numContacts > 1) {
					final StringBuilder builder = new StringBuilder();
					builder.append(numContacts).append(" ").append(getString(R.string.title_section2));
					if (counts != null) {
						builder.append(" (+").append(counts.last30d).append(" / ").append(getString(R.string.thirty_days_abbrev)).append(")");
					}
					contactsCounterChip.setText(builder.toString());
					contactsCounterChip.setVisibility(View.VISIBLE);
				} else {
					contactsCounterChip.setVisibility(View.GONE);
				}
			}
		}
	}

	final protected boolean requiredInstances() {
		if (!this.checkInstances()) {
			this.instantiate();
		}
		return this.checkInstances();
	}

	protected boolean checkInstances() {
		return TestUtil.required(
			this.serviceManager,
			this.contactListener,
			this.preferenceService,
			this.synchronizeContactsService,
			this.lockAppService);
	}

	protected void instantiate() {
		this.serviceManager = ThreemaApplication.getServiceManager();

		if (this.serviceManager != null) {
			try {
				this.contactService = this.serviceManager.getContactService();
				this.preferenceService = this.serviceManager.getPreferenceService();
				this.synchronizeContactsService = this.serviceManager.getSynchronizeContactsService();
				this.lockAppService = this.serviceManager.getLockAppService();
			} catch (MasterKeyLockedException e) {
				logger.debug("Master Key locked!");
			} catch (ThreemaException e) {
				logger.error("Exception", e);
			}
		}
	}

	private void onFABClicked(View v) {
		Intent intent = new Intent(getActivity(), AddContactActivity.class);
		intent.putExtra(AddContactActivity.EXTRA_ADD_BY_ID, true);
		startActivity(intent);
		getActivity().overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View fragmentView = getView();

		logger.debug("*** onCreateView");
		if (fragmentView == null) {
			fragmentView = inflater.inflate(R.layout.fragment_contacts, container, false);

			if (!this.requiredInstances()) {
				logger.error("could not instantiate required objects");
			}

			listView = fragmentView.findViewById(android.R.id.list);
			listView.setOnItemClickListener(this);
			listView.setDividerHeight(0);
			listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
			listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
				MenuItem shareItem;

				@Override
				public void onItemCheckedStateChanged(android.view.ActionMode mode, int position, long id, boolean checked) {
					if (shareItem != null) {
						final int count = listView.getCheckedItemCount();
						if (count > 0) {
							mode.setTitle(Integer.toString(count));
							shareItem.setVisible(count == 1);
						}
					}
				}

				@Override
				public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
					mode.getMenuInflater().inflate(R.menu.action_contacts_section, menu);
					actionMode = mode;

					ConfigUtils.themeMenu(menu, ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorAccent));

					return true;
				}

				@Override
				public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
					shareItem = menu.findItem(R.id.menu_contacts_share);
					mode.setTitle(Integer.toString(listView.getCheckedItemCount()));

					return true;
				}

				@Override
				public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {
					switch (item.getItemId()) {
						case R.id.menu_contacts_remove:
							deleteSelectedContacts();
							return true;
						case R.id.menu_contacts_share:
							HashSet<ContactModel> contactModels = contactListAdapter.getCheckedItems();
							if (contactModels.size() == 1) {
								ShareUtil.shareContact(getActivity(), contactModels.iterator().next());
							}
							return true;
						default:
							return false;
					}
				}

				@Override
				public void onDestroyActionMode(android.view.ActionMode mode) {
					actionMode = null;
				}
			});

			if (!ConfigUtils.isWorkBuild()) {
				View headerView = View.inflate(getActivity(), R.layout.header_contact_section, null);
				listView.addHeaderView(headerView, null, false);

				View footerView = View.inflate(getActivity(), R.layout.footer_contact_section, null);
				this.contactsCounterChip = footerView.findViewById(R.id.contact_counter_text);
				listView.addFooterView(footerView, null, false);

				headerView.findViewById(R.id.share_container).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						shareInvite();
					}
				});
			}

			this.swipeRefreshLayout = fragmentView.findViewById(R.id.swipe_container);
			this.swipeRefreshLayout.setOnRefreshListener(this);
			this.swipeRefreshLayout.setDistanceToTriggerSync(getResources().getConfiguration().screenHeightDp / 3);
			this.swipeRefreshLayout.setColorSchemeResources(R.color.accent_light);
			this.swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);

			this.floatingButtonView = fragmentView.findViewById(R.id.floating);
			this.floatingButtonView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onFABClicked(v);
				}
			});

			this.stickyInitialView = fragmentView.findViewById(R.id.initial_sticky);
			this.stickyInitialLayout = fragmentView.findViewById(R.id.initial_sticky_layout);
		}
		return fragmentView;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		logger.debug("*** onViewCreated");

		if (getActivity() != null && listView != null) {
			// add text view if contact list is empty
			EmptyView emptyView = new EmptyView(getActivity());
			emptyView.setup(R.string.no_contacts);

			((ViewGroup) listView.getParent()).addView(emptyView);
			listView.setEmptyView(emptyView);
			listView.setOnScrollListener(new AbsListView.OnScrollListener() {
				private int previousFirstVisibleItem = -1;

				@Override
				public void onScrollStateChanged(AbsListView view, int scrollState) {
				}

				@Override
				public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
					if (swipeRefreshLayout != null) {
						if (view != null && view.getChildCount() > 0) {
							swipeRefreshLayout.setEnabled(firstVisibleItem == 0 && view.getChildAt(0).getTop() == 0);
						} else {
							swipeRefreshLayout.setEnabled(false);
						}
					}

					if (view != null) {
						if (contactListAdapter != null) {
							int direction = 0;

							if (floatingButtonView != null) {
								if (firstVisibleItem == 0) {
									floatingButtonView.extend();
								} else {
									floatingButtonView.shrink();
								}
							}

							int headerCount = listView.getHeaderViewsCount();
							firstVisibleItem -= headerCount;

							if (firstVisibleItem != previousFirstVisibleItem) {
								if (previousFirstVisibleItem != -1 && firstVisibleItem != -1) {
									if (previousFirstVisibleItem < firstVisibleItem) {
										// Scroll Down
										direction = 1;
									}
									if (previousFirstVisibleItem > firstVisibleItem) {
										// Scroll Up
										direction = -1;
									}


									stickyInitialView.setText(contactListAdapter.getInitial(firstVisibleItem));

									String currentInitial = contactListAdapter.getInitial(firstVisibleItem);
									String previousInitial = contactListAdapter.getInitial(previousFirstVisibleItem);
									String nextInitial = "";
									if (direction == 1 && firstVisibleItem < contactListAdapter.getCount()) {
										nextInitial = contactListAdapter.getInitial(firstVisibleItem + 1);
									} else if (direction == -1 && firstVisibleItem > 0) {
										nextInitial = contactListAdapter.getInitial(firstVisibleItem - 1);
									}

									if (direction == 1) {
										stickyInitialLayout.setVisibility(nextInitial.equals(currentInitial) ? View.VISIBLE : View.GONE);
									} else {
										stickyInitialLayout.setVisibility(previousInitial.equals(currentInitial) ? View.VISIBLE : View.GONE);
									}
								} else {
									stickyInitialLayout.setVisibility(View.GONE);
								}
							}
							previousFirstVisibleItem = firstVisibleItem;
						}
					}
				}
			});
		}

		if (savedInstanceState != null) {
			if (TestUtil.empty(this.filterQuery)) {
				this.filterQuery = savedInstanceState.getString(BUNDLE_FILTER_QUERY_C);
			}
		}

		// fill adapter with data
		createListAdapter();

		// register a receiver that will receive info about changed contacts from contact sync
		IntentFilter filter = new IntentFilter();
		filter.addAction(IntentDataUtil.ACTION_CONTACTS_CHANGED);
		LocalBroadcastManager.getInstance(getContext()).registerReceiver(contactsChangedReceiver, filter);
	}


	@Override
	public void onDestroyView() {
		LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(contactsChangedReceiver);

		searchView = null;
		searchMenuItem = null;
		contactListAdapter = null;

		super.onDestroyView();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case ThreemaActivity.ACTIVITY_ID_ADD_CONTACT:
				if (actionMode != null) {
					actionMode.finish();
				}
				break;
			case ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL:
				break;
			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public void onRefresh() {
		if (actionMode != null) {
			actionMode.finish();
		}

		startSwipeRefresh();

		new Handler(Looper.getMainLooper()).postDelayed(this::stopSwipeRefresh, 2000);

		try {
			WorkManager.getInstance(requireContext()).enqueue(new OneTimeWorkRequest.Builder(IdentityStatesWorker.class).build());
		} catch (IllegalStateException ignored) {}

		if (this.preferenceService.isSyncContacts() && ConfigUtils.requestContactPermissions(getActivity(), this, PERMISSION_REQUEST_REFRESH_CONTACTS)) {
			if (this.synchronizeContactsService != null) {
				synchronizeContactsService.instantiateSynchronizationAndRun();
			}
		}

		if (ConfigUtils.isWorkBuild()) {
			WorkSyncService.enqueueWork(getActivity(), new Intent(), true);
		}
	}

	private void openConversationForIdentity(View v, String identity) {
		Intent intent = new Intent(getActivity(), ComposeMessageActivity.class);
		intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
		intent.putExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, Boolean.TRUE);

		AnimationUtil.startActivityForResult(getActivity(), v, intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
	}


	@Override
	public void onSaveInstanceState(Bundle outState) {
		logger.info("saveInstance");

		if (!TestUtil.empty(filterQuery)) {
			outState.putString(BUNDLE_FILTER_QUERY_C, filterQuery);
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onBackPressed() {
		if (actionMode != null) {
			actionMode.finish();
			return true;
		}
		if (this.searchView != null && this.searchView.isShown() && this.searchMenuItem != null) {
			MenuItemCompat.collapseActionView(this.searchMenuItem);
			return true;
		}
		return false;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		ContactModel contactModel = contactListAdapter.getClickedItem(v);
		if (contactModel != null) {
			String identity;
			identity = contactModel.getIdentity();
			if (identity != null) {
				openConversationForIdentity(v, identity);
			}
		}
	}

	@Override
	public void onAvatarClick(View view, int position) {
		if (contactListAdapter == null) {
			return;
		}

		View listItemView = (View) view.getParent();

		if (contactListAdapter.getCheckedItemCount() > 0) {
			// forward click on avatar to relevant list item
			position += listView.getHeaderViewsCount();
			listView.setItemChecked(position, !listView.isItemChecked(position));

			return;
		}

		Intent intent = new Intent(getActivity(), ContactDetailActivity.class);
		intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, contactListAdapter.getClickedItem(listItemView).getIdentity());
		AnimationUtil.startActivityForResult(getActivity(), view, intent, ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL);
	}

	@Override
	public boolean onAvatarLongClick(View view, int position) {
		/*
		if (contactListAdapter != null && contactListAdapter.getCheckedItemCount() == 0) {
			position += listView.getHeaderViewsCount();
			listView.setItemChecked(position, true);
		}
		*/
		return true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String permissions[], @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_REFRESH_CONTACTS:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					this.onRefresh();
				} else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
					ConfigUtils.showPermissionRationale(getContext(), getView(), R.string.permission_contacts_required);
				}
		}
	}

	private void setupListeners() {
		logger.debug("*** setup listeners");

		//set listeners
		ListenerManager.contactListeners.add(this.contactListener);
		ListenerManager.contactSettingsListeners.add(this.contactSettingsListener);
		ListenerManager.synchronizeContactsListeners.add(this.synchronizeContactsListener);
		ListenerManager.preferenceListeners.add(this.preferenceListener);
	}

	private void removeListeners() {
		logger.debug("*** remove listeners");

		ListenerManager.contactListeners.remove(this.contactListener);
		ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
		ListenerManager.synchronizeContactsListeners.remove(this.synchronizeContactsListener);
		ListenerManager.preferenceListeners.remove(this.preferenceListener);
	}

	@SuppressLint("StringFormatInvalid")
	private void deleteSelectedContacts() {
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.delete_contact_action,
				String.format(getString(R.string.really_delete_contacts_message), contactListAdapter.getCheckedItemCount()),
				R.string.ok,
				R.string.cancel);

		dialog.setTargetFragment(this, 0);
		dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_CONTACTS);
	}

	@Override
	public void onYes(String tag, Object data) {
		switch(tag) {
			case DIALOG_TAG_REALLY_DELETE_CONTACTS:
				reallyDeleteContacts();
				break;
			default:
				break;
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void reallyDeleteContacts() {
		new DeleteContactAsyncTask(getFragmentManager(), contactListAdapter.getCheckedItems(), contactService, new DeleteContactAsyncTask.DeleteContactsPostRunnable() {
			@Override
			public void run() {
				if (isAdded()) {
					if (failed > 0) {
						Toast.makeText(getActivity(), String.format(getString(R.string.some_contacts_not_deleted), failed), Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(getActivity(), R.string.contacts_deleted, Toast.LENGTH_LONG).show();
					}
				}

				if (actionMode != null) {
					actionMode.finish();
				}
			}
		}).execute();
	}

	@Override
	public void onNo(String tag, Object data) { }

	@Override
	public void onSelected(String tag) {
		if (!TestUtil.empty(tag)) {
			sendInvite(tag);
		}
	}

	public void shareInvite() {
		final PackageManager packageManager = getContext().getPackageManager();
		if (packageManager == null) return;

		Intent messageIntent = new Intent(Intent.ACTION_SEND);
		messageIntent.setType("text/plain");
		@SuppressLint("WrongConstant") final List<ResolveInfo> messageApps = packageManager.queryIntentActivities(messageIntent, 0x00020000);

		if (!messageApps.isEmpty()) {
			ArrayList<BottomSheetItem> items = new ArrayList<>();

			for (int i = 0; i < messageApps.size(); i++) {
				ResolveInfo resolveInfo = messageApps.get(i);
				if (resolveInfo != null) {
					CharSequence label = resolveInfo.loadLabel(packageManager);
					Drawable icon = resolveInfo.loadIcon(packageManager);

					if (label != null && icon != null) {
						Bitmap bitmap = BitmapUtil.getBitmapFromVectorDrawable(icon, null);
						if (bitmap != null) {
							items.add(new BottomSheetItem(bitmap, label.toString(), messageApps.get(i).activityInfo.packageName));
						}
					}
				}
			}

			BottomSheetGridDialog dialog = BottomSheetGridDialog.newInstance(R.string.invite_via, items);
			dialog.setTargetFragment(this, 0);
			dialog.show(getFragmentManager(), DIALOG_TAG_SHARE_WITH);
		}
	}

	private void sendInvite(String packageName) {
		// is this an SMS app? if it holds the SEND_SMS permission, it most probably is.
		boolean isShortMessage = ConfigUtils.checkManifestPermission(getContext(), packageName, "android.permission.SEND_SMS");

		if (packageName.contains("twitter")) {
			isShortMessage = true;
		}

		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.setPackage(packageName);

		UserService userService = ThreemaApplication.getServiceManager().getUserService();

		if (isShortMessage) {
			/* short version */
			String messageBody = String.format(getString(R.string.invite_sms_body), getString(R.string.app_name), userService.getIdentity());
			intent.putExtra(Intent.EXTRA_TEXT, messageBody);
		} else {
			/* long version */
			String messageBody = String.format(getString(R.string.invite_email_body), getString(R.string.app_name), userService.getIdentity());
			intent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.invite_email_subject));
			intent.putExtra(Intent.EXTRA_TEXT, messageBody);
		}
		if (intent.resolveActivity(getContext().getPackageManager()) != null) {
			try {
				startActivity(intent);
			} catch (SecurityException e) {
				logger.error("Exception", e);
			}
		}
	}

	public void onLogoClicked() {
		if (this.listView != null) {
			// this stops the fling
			this.listView.smoothScrollBy(0, 0);
			this.listView.setSelection(0);
		}
	}
}
