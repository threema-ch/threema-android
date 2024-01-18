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

import static android.view.MenuItem.SHOW_AS_ACTION_ALWAYS;
import static android.view.MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW;
import static android.view.MenuItem.SHOW_AS_ACTION_NEVER;
import static ch.threema.app.ThreemaApplication.WORKER_WORK_SYNC;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.util.Pair;
import androidx.core.view.MenuItemCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.AddContactActivity;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.ContactDetailActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.adapters.ContactListAdapter;
import ch.threema.app.asynctasks.DeleteContactAsyncTask;
import ch.threema.app.asynctasks.EmptyChatAsyncTask;
import ch.threema.app.dialogs.BottomSheetAbstractDialog;
import ch.threema.app.dialogs.BottomSheetGridDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.dialogs.TextWithCheckboxDialog;
import ch.threema.app.dialogs.ThreemaDialogFragment;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.PreferenceListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.AvatarCacheService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.BottomSheetItem;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.LockingSwipeRefreshLayout;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.workers.IdentityStatesWorker;
import ch.threema.app.workers.WorkSyncWorker;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;

public class ContactsSectionFragment
		extends MainFragment
		implements
		SwipeRefreshLayout.OnRefreshListener,
		ListView.OnItemClickListener,
		ContactListAdapter.AvatarListener,
		SelectorDialog.SelectorDialogClickListener,
		BottomSheetAbstractDialog.BottomSheetDialogCallback,
		TextWithCheckboxDialog.TextWithCheckboxDialogClickListener,
		GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ContactsSectionFragment");

	private static final int PERMISSION_REQUEST_REFRESH_CONTACTS = 1;
	private static final String DIALOG_TAG_SHARE_WITH = "wsw";
	private static final String DIALOG_TAG_RECENTLY_ADDED_SELECTOR = "ras";
	private static final String DIALOG_TAG_REALLY_DELETE_CONTACTS = "rdc";
	private static final String DIALOG_TAG_REPORT_SPAM = "spam";

	private static final String RUN_ON_ACTIVE_SHOW_LOADING = "show_loading";
	private static final String RUN_ON_ACTIVE_HIDE_LOADING = "hide_loading";
	private static final String RUN_ON_ACTIVE_UPDATE_LIST = "update_list";
	private static final String RUN_ON_ACTIVE_REFRESH_LIST = "refresh_list";
	private static final String RUN_ON_ACTIVE_REFRESH_PULL_TO_REFRESH = "pull_to_refresh";

	private static final String BUNDLE_FILTER_QUERY_C = "BundleFilterC";
	private static final String BUNDLE_SELECTED_TAB = "tabpos";

	private static final int TAB_ALL_CONTACTS = 0;
	private static final int TAB_WORK_ONLY = 1;

	private static final int SELECTOR_TAG_CHAT = 0;
	private static final int SELECTOR_TAG_SHOW_CONTACT = 1;
	private static final int SELECTOR_TAG_REPORT_SPAM = 2;
	private static final int SELECTOR_TAG_BLOCK = 3;
	private static final int SELECTOR_TAG_DELETE = 4;

	private ResumePauseHandler resumePauseHandler;
	private ListView listView;
	private MaterialButton contactsCounterButton;
	private LockingSwipeRefreshLayout swipeRefreshLayout;
	private ServiceManager serviceManager;
	private SearchView searchView;
	private MenuItem searchMenuItem;
	private ContactListAdapter contactListAdapter;
	private ActionMode actionMode = null;
	private ExtendedFloatingActionButton floatingButtonView;
	private EmojiTextView stickyInitialView;
	private FrameLayout stickyInitialLayout;
	private TabLayout workTabLayout;

	private SynchronizeContactsService synchronizeContactsService;
	private ContactService contactService;
	private PreferenceService preferenceService;
	private LockAppService lockAppService;

	private String filterQuery;
	@SuppressLint("StaticFieldLeak")
	private final TabLayout.OnTabSelectedListener onTabSelectedListener = new TabLayout.OnTabSelectedListener() {
		@Override
		public void onTabSelected(TabLayout.Tab tab) {
			if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
				return;
			}

			if (actionMode != null) {
				actionMode.finish();
			}

			new FetchContactsTask(contactService, false, tab.getPosition(), true) {
				@Override
				protected void onPostExecute(Pair<List<ContactModel>, FetchResults> result) {
					final List<ContactModel> contactModels = result.first;

					if (contactModels != null && contactListAdapter != null) {
						contactListAdapter.updateData(contactModels);
						if (!TestUtil.empty(filterQuery)) {
							contactListAdapter.getFilter().filter(filterQuery);
						}
					}
				}
			}.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
		}

		@Override
		public void onTabUnselected(TabLayout.Tab tab) {}

		@Override
		public void onTabReselected(TabLayout.Tab tab) {}
	};

	/**
	 * Simple POJO to hold the number of contacts that were added in the last 24h / 30d.
	 */
	private static class FetchResults {
		int last24h = 0;
		int last30d = 0;
		int workCount = 0;
	}

	// Contacts changed receiver
	private final BroadcastReceiver contactsChangedReceiver = new BroadcastReceiver() {
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
			createListAdapter(null);
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
			logger.debug("onModified " + modifiedContactModel.getIdentity());
			if (resumePauseHandler != null) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_UPDATE_LIST, runIfActiveUpdateList);
			}
		}

		@Override
		public void onAvatarChanged(ContactModel contactModel) {
			logger.debug("onAvatarChanged -> onModified " + contactModel.getIdentity());
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
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_UPDATE_LIST, runIfActiveUpdateList);
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
	 */
	private static class FetchContactsTask extends AsyncTask<Void, Void, Pair<List<ContactModel>, FetchResults>> {
		ContactService contactService;
		boolean isOnLaunch, forceWork;
		int selectedTab;

		FetchContactsTask(ContactService contactService, boolean isOnLaunch, int selectedTab, boolean forceWork) {
			this.contactService = contactService;
			this.isOnLaunch = isOnLaunch;
			this.selectedTab = selectedTab;
			this.forceWork = forceWork;
		}

		@Override
		protected Pair<List<ContactModel>, FetchResults> doInBackground(Void... voids) {
			List<ContactModel> allContacts = null;

			// Count new contacts
			final FetchResults results = new FetchResults();

			if (ConfigUtils.isWorkBuild()) {
				results.workCount = contactService.countIsWork();
				if (selectedTab == TAB_WORK_ONLY) {
					if (results.workCount > 0 || forceWork) {
						allContacts = contactService.getAllDisplayedWork(ContactService.ContactSelection.INCLUDE_INVALID);
					}
				}
			}

			if (allContacts == null) {
				allContacts = contactService.getAllDisplayed(ContactService.ContactSelection.INCLUDE_INVALID);
			}

			if (!ConfigUtils.isWorkBuild()) {
				long now = System.currentTimeMillis();
				long delta24h = 1000L * 3600 * 24;
				long delta30d = delta24h * 30;
				for (ContactModel contact : allContacts) {
					final Date dateCreated = contact.getDateCreated();
					if (dateCreated == null) {
						continue;
					}
					if (now - dateCreated.getTime() < delta24h) {
						results.last24h += 1;
					}
					if (now - dateCreated.getTime() < delta30d) {
						results.last30d += 1;
					}
				}
			}
			return new Pair<>(allContacts, results);
		}
	}

	@Override
	public void onResume() {
		logger.debug("onResume");
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
		logger.debug("onPause");

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onPause();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logger.debug("onCreate");

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
		logger.debug("onAttach");
	}

	@Override
	public void onDestroy() {
		logger.debug("onDestroy");

		removeListeners();

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onDestroy(this);
		}

		super.onDestroy();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		logger.debug("onHiddenChanged: " + hidden);
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
		if (this.searchMenuItem != null) {
			if (lockAppService != null && lockAppService.isLockingEnabled()) {
				this.searchMenuItem.setShowAsAction(SHOW_AS_ACTION_NEVER | SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
			} else {
				this.searchMenuItem.setShowAsAction(SHOW_AS_ACTION_ALWAYS | SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		logger.debug("onCreateOptionsMenu");
		searchMenuItem = menu.findItem(R.id.menu_search_contacts);

		if (searchMenuItem == null) {
			inflater.inflate(R.menu.fragment_contacts, menu);

			if (getActivity() != null && this.isAdded()) {
				this.searchMenuItem = menu.findItem(R.id.menu_search_contacts);
				this.searchView = (SearchView) searchMenuItem.getActionView();

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
					this.searchView.setQueryHint(getString(R.string.hint_filter_list));
					this.searchView.setOnQueryTextListener(queryTextListener);
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

	private int getDesiredWorkTab(boolean isOnFirstLaunch, Bundle savedInstanceState) {
		if (ConfigUtils.isWorkBuild()) {
			if (!isOnFirstLaunch) {
				if (savedInstanceState != null) {
					return savedInstanceState.getInt(BUNDLE_SELECTED_TAB, TAB_ALL_CONTACTS);
				} else if (workTabLayout != null) {
					return workTabLayout.getSelectedTabPosition();
				}
			}
		}
		return TAB_ALL_CONTACTS;
	}

	@SuppressLint("StaticFieldLeak")
	protected void createListAdapter(final Bundle savedInstanceState) {
		if (getActivity() == null) {
			return;
		}

		if (!this.requiredInstances()) {
			return;
		}

		final int[] desiredTabPosition = {getDesiredWorkTab(savedInstanceState == null, savedInstanceState)};

		new FetchContactsTask(contactService, savedInstanceState == null, desiredTabPosition[0], false) {
			@Override
			protected void onPostExecute(Pair<List<ContactModel>, FetchResults> result) {
				final List<ContactModel> contactModels = result.first;
				final FetchResults counts = result.second;
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
							ContactsSectionFragment.this,
							Glide.with(getContext())
						);
						listView.setAdapter(contactListAdapter);
					}

					if (ConfigUtils.isWorkBuild()) {
						if (savedInstanceState == null && desiredTabPosition[0] == TAB_WORK_ONLY && counts.workCount == 0) {
							// fix selected tab as there is now work contact
							desiredTabPosition[0] = TAB_ALL_CONTACTS;
						}

						if (desiredTabPosition[0] != workTabLayout.getSelectedTabPosition()) {
							workTabLayout.removeOnTabSelectedListener(onTabSelectedListener);
							workTabLayout.selectTab(workTabLayout.getTabAt(selectedTab));
							workTabLayout.addOnTabSelectedListener(onTabSelectedListener);
						}
					}
				}
			}
		}.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
	}

	@SuppressLint("StaticFieldLeak")
	private void updateList() {
		if (!this.requiredInstances()) {
			logger.error("could not instantiate required objects");
			return;
		}

		int desiredTab = getDesiredWorkTab(false, null);

		if (contactListAdapter != null) {
			new FetchContactsTask(contactService, false, desiredTab, false) {
				@Override
				protected void onPostExecute(Pair<List<ContactModel>, FetchResults> result) {
					final List<ContactModel> contactModels = result.first;
					final FetchResults counts = result.second;

					if (contactModels != null && contactListAdapter != null && isAdded()) {
						updateContactsCounter(contactModels.size(), counts);
						contactListAdapter.updateData(contactModels);
					}
				}
			}.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
		}
	}

	private void updateContactsCounter(int numContacts, @Nullable FetchResults counts) {
		if (getActivity() != null && listView != null && isAdded()) {
			if (contactsCounterButton != null) {
				if (counts != null) {
					ListenerManager.contactCountListener.handle(listener -> listener.onNewContactsCountUpdated(counts.last24h));
				}
				if (numContacts > 1) {
					final StringBuilder builder = new StringBuilder();
					builder.append(ConfigUtils.getSafeQuantityString(getContext(), R.plurals.contacts_counter_label, numContacts, numContacts));
					if (counts != null) {
						builder.append(" (+").append(counts.last30d).append(" / ").append(getString(R.string.thirty_days_abbrev)).append(")");
					}
					contactsCounterButton.setText(builder.toString());
					contactsCounterButton.setVisibility(View.VISIBLE);
				} else {
					contactsCounterButton.setVisibility(View.GONE);
				}
			}
			if (ConfigUtils.isWorkBuild() && counts != null) {
				if (counts.workCount > 0) {
					showWorkTabs();
				} else {
					hideWorkTabs();
				}
			}
		}
	}

	private void showWorkTabs() {
		if (workTabLayout != null && listView != null) {
			FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
			((ViewGroup) workTabLayout.getParent()).setVisibility(View.VISIBLE);
			layoutParams.topMargin = getResources().getDimensionPixelSize(R.dimen.header_contact_section_work_height);
			listView.setLayoutParams(layoutParams);

			setStickyInitialLayoutTopMargin(layoutParams.topMargin);
		}
	}

	private void hideWorkTabs() {
		if (workTabLayout != null && listView != null) {
			FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
			if (workTabLayout.getSelectedTabPosition() != 0) {
				workTabLayout.selectTab(workTabLayout.getTabAt(0));
			}
			((ViewGroup) workTabLayout.getParent()).setVisibility(View.GONE);
			layoutParams.topMargin = 0;
			listView.setLayoutParams(layoutParams);

			setStickyInitialLayoutTopMargin(layoutParams.topMargin);
		}
	}

	private void setStickyInitialLayoutTopMargin(int margin) {
		if (stickyInitialLayout != null){
			ViewGroup.MarginLayoutParams stickyInitialLayoutLayoutParams = (ViewGroup.MarginLayoutParams) stickyInitialLayout.getLayoutParams();
			stickyInitialLayoutLayoutParams.topMargin = margin;
			stickyInitialLayout.setLayoutParams(stickyInitialLayoutLayoutParams);
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
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View headerView, fragmentView = getView();

		logger.debug("onCreateView");
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

					ConfigUtils.tintMenu(menu, ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorPrimary));

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
					int id = item.getItemId();
					if (id == R.id.menu_contacts_remove) {
						deleteContacts(contactListAdapter.getCheckedItems());
						return true;
					} else if (id == R.id.menu_contacts_share) {
						HashSet<ContactModel> contactModels = contactListAdapter.getCheckedItems();
						if (contactModels.size() == 1) {
							ShareUtil.shareContact(getActivity(), contactModels.iterator().next());
						}
						return true;
					}
					return false;
				}

				@Override
				public void onDestroyActionMode(android.view.ActionMode mode) {
					actionMode = null;
				}
			});

			this.stickyInitialView = fragmentView.findViewById(R.id.initial_sticky);
			this.stickyInitialLayout = fragmentView.findViewById(R.id.initial_sticky_layout);
			this.stickyInitialLayout.setVisibility(View.GONE);

			if (!ConfigUtils.isWorkBuild()) {
				headerView = View.inflate(getActivity(), R.layout.header_contact_section, null);
				listView.addHeaderView(headerView, null, false);

				View footerView = View.inflate(getActivity(), R.layout.footer_contact_section, null);
				this.contactsCounterButton = footerView.findViewById(R.id.contact_counter_text);
				listView.addFooterView(footerView, null, false);

				headerView.findViewById(R.id.share_container).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						shareInvite();
					}
				});
			} else {
				workTabLayout = fragmentView.findViewById(R.id.work_contacts_tab_layout);
				workTabLayout.addOnTabSelectedListener(onTabSelectedListener);
				showWorkTabs();
			}

			this.swipeRefreshLayout = fragmentView.findViewById(R.id.swipe_container);
			this.swipeRefreshLayout.setOnRefreshListener(this);
			this.swipeRefreshLayout.setDistanceToTriggerSync(getResources().getConfiguration().screenHeightDp / 3);
			this.swipeRefreshLayout.setColorSchemeResources(R.color.md_theme_light_primary);
			this.swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);

			this.floatingButtonView = fragmentView.findViewById(R.id.floating);
			this.floatingButtonView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onFABClicked(v);
				}
			});
		}
		return fragmentView;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		logger.debug("onViewCreated");

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

									if (ContactListAdapter.RECENTLY_ADDED_SIGN.equals(currentInitial)) {
										stickyInitialLayout.setVisibility(View.GONE);
									} else {
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
		createListAdapter(savedInstanceState);

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
			try {
				OneTimeWorkRequest workRequest = WorkSyncWorker.Companion.buildOneTimeWorkRequest(false, true, "WorkContactSync");
				WorkManager.getInstance(ThreemaApplication.getAppContext()).enqueueUniqueWork(WORKER_WORK_SYNC, ExistingWorkPolicy.REPLACE, workRequest);
			} catch (IllegalStateException e) {
				logger.error("Unable to schedule work sync one time work", e);
			}
		}
	}

	private void openConversationForIdentity(@Nullable View v, String identity) {
		// Close keyboard if search view is expanded
		if (searchView != null && !searchView.isIconified()) {
			EditTextUtil.hideSoftKeyboard(searchView);
		}

		Intent intent = new Intent(getActivity(), ComposeMessageActivity.class);
		intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
		intent.putExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, Boolean.TRUE);

		getActivity().startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
	}

	private void openContact(@Nullable View view, String identity) {
		Intent intent = new Intent(getActivity(), ContactDetailActivity.class);
		intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);

		getActivity().startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		logger.info("saveInstance");

		if (!TestUtil.empty(filterQuery)) {
			outState.putString(BUNDLE_FILTER_QUERY_C, filterQuery);
		}
		if (ConfigUtils.isWorkBuild() && workTabLayout != null) {
			outState.putInt(BUNDLE_SELECTED_TAB, workTabLayout.getSelectedTabPosition());
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
			String identity = contactModel.getIdentity();
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

		openContact(view, contactListAdapter.getClickedItem(listItemView).getIdentity());
	}

	@Override
	public boolean onAvatarLongClick(View view, int position) {
		return true;
	}

	@Override
	public void onRecentlyAddedClick(ContactModel contactModel) {

		String contactName = NameUtil.getDisplayNameOrNickname(contactModel, true);

		ArrayList<SelectorDialogItem> items = new ArrayList<>();
		ArrayList<Integer> tags = new ArrayList<>();

			items.add(new SelectorDialogItem(getString(R.string.chat_with, contactName), R.drawable.ic_chat_bubble));
			tags.add(SELECTOR_TAG_CHAT);

			items.add(new SelectorDialogItem(getString(R.string.show_contact), R.drawable.ic_person_outline));
			tags.add(SELECTOR_TAG_SHOW_CONTACT);

			if (!ConfigUtils.isOnPremBuild()) {
				if (
					contactModel.getAndroidContactLookupKey() == null &&
					TestUtil.empty(contactModel.getFirstName()) &&
					TestUtil.empty(contactModel.getLastName()) &&
					contactModel.getVerificationLevel() == VerificationLevel.UNVERIFIED
				) {
					MessageReceiver messageReceiver = contactService.createReceiver(contactModel);
					if (messageReceiver != null && messageReceiver.getMessagesCount() > 0) {
						items.add(new SelectorDialogItem(getString(R.string.spam_report), R.drawable.ic_outline_report_24));
						tags.add(SELECTOR_TAG_REPORT_SPAM);
					}
				}
			}

			if (serviceManager.getBlackListService().has(contactModel.getIdentity())) {
				items.add(new SelectorDialogItem(getString(R.string.unblock_contact), R.drawable.ic_block));
			} else {
				items.add(new SelectorDialogItem(getString(R.string.block_contact), R.drawable.ic_block));
			}
			tags.add(SELECTOR_TAG_BLOCK);

			items.add(new SelectorDialogItem(getString(R.string.delete_contact_action), R.drawable.ic_delete_outline));
			tags.add(SELECTOR_TAG_DELETE);

			SelectorDialog selectorDialog = SelectorDialog.newInstance(getString(R.string.last_added_contact), items, tags, getString(R.string.cancel));
			selectorDialog.setData(contactModel);
			selectorDialog.setTargetFragment(this, 0);
			selectorDialog.show(getParentFragmentManager(), DIALOG_TAG_RECENTLY_ADDED_SELECTOR);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String permissions[], @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_REFRESH_CONTACTS:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					this.onRefresh();
				} else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
					ConfigUtils.showPermissionRationale(getContext(), getView(), R.string.permission_contacts_sync_required);
				}
		}
	}

	private void setupListeners() {
		logger.debug("setup listeners");

		//set listeners
		ListenerManager.contactListeners.add(this.contactListener);
		ListenerManager.contactSettingsListeners.add(this.contactSettingsListener);
		ListenerManager.synchronizeContactsListeners.add(this.synchronizeContactsListener);
		ListenerManager.preferenceListeners.add(this.preferenceListener);
	}

	private void removeListeners() {
		logger.debug("remove listeners");

		ListenerManager.contactListeners.remove(this.contactListener);
		ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
		ListenerManager.synchronizeContactsListeners.remove(this.synchronizeContactsListener);
		ListenerManager.preferenceListeners.remove(this.preferenceListener);
	}

	private boolean showExcludeFromContactSync(Set<ContactModel> contacts) {
		if (preferenceService == null || !preferenceService.isSyncContacts()) {
			return false;
		}

		for (ContactModel contactModel : contacts) {
			if (contactModel.getAndroidContactLookupKey() != null) {
				return true;
			}
		}

		return false;
	}

	@SuppressLint("StringFormatInvalid")
	private void deleteContacts(@NonNull Set<ContactModel> contacts) {
		int contactsSelectedToDelete = contacts.size();
		final String deleteContactTitle = getString(contactsSelectedToDelete > 1 ? R.string.delete_multiple_contact_action : R.string.delete_contact_action);
		final String message = String.format(ConfigUtils.getSafeQuantityString(ThreemaApplication.getAppContext(), R.plurals.really_delete_contacts_message, contactsSelectedToDelete, contactsSelectedToDelete), contactListAdapter.getCheckedItemCount());

		ThreemaDialogFragment dialog;
		if (showExcludeFromContactSync(contacts)) {
			dialog = TextWithCheckboxDialog.newInstance(
				deleteContactTitle,
				R.drawable.ic_contact,
				message,
				R.string.exclude_contact,
				R.string.ok,
				R.string.cancel);
		} else {
			dialog = GenericAlertDialog.newInstance(
				deleteContactTitle,
				message,
				R.string.ok,
				R.string.cancel);
		}
		dialog.setTargetFragment(this, 0);
		dialog.setData(contacts);
		dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_CONTACTS);
	}

	@SuppressLint("StaticFieldLeak")
	private void reallyDeleteContacts(@NonNull Set<ContactModel> contactModels, boolean excludeFromSync) {
		new DeleteContactAsyncTask(getParentFragmentManager(), contactModels, contactService, new DeleteContactAsyncTask.DeleteContactsPostRunnable() {
			@Override
			public void run() {
				if (isAdded()) {
					if (failed > 0) {
						Toast.makeText(getActivity(), ConfigUtils.getSafeQuantityString(ThreemaApplication.getAppContext(), R.plurals.some_contacts_not_deleted, failed, failed), Toast.LENGTH_LONG).show();
					} else {
						if (contactModels.size() > 1) {
							Toast.makeText(getActivity(), R.string.contacts_deleted, Toast.LENGTH_LONG).show();
						} else {
							Toast.makeText(getActivity(), R.string.contact_deleted, Toast.LENGTH_LONG).show();
						}

						if (excludeFromSync) {
							excludeContactsFromSync(contactModels);
						}
					}
				}

				if (actionMode != null) {
					actionMode.finish();
				}
			}
		}).execute();
	}

	private void excludeContactsFromSync(@NonNull Collection<ContactModel> contactModels) {
		IdListService excludedService = serviceManager.getExcludedSyncIdentitiesService();
		if (excludedService != null) {
			for (ContactModel contactModel : contactModels) {
				if (contactModel.getAndroidContactLookupKey() != null) {
					excludedService.add(contactModel.getIdentity());
				}
			}
		}
	}

	@Override
	public void onSelected(String tag, String data) {
		if (!TestUtil.empty(tag)) {
			sendInvite(tag);
		}
	}

	public void shareInvite() {
		final PackageManager packageManager = getContext().getPackageManager();
		if (packageManager == null) return;

		Intent messageIntent = new Intent(Intent.ACTION_SEND);
		messageIntent.setType(MimeUtil.MIME_TYPE_TEXT);
		@SuppressLint({"WrongConstant", "InlinedApi"}) final List<ResolveInfo> messageApps = packageManager.queryIntentActivities(messageIntent, PackageManager.MATCH_ALL);

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
			dialog.show(getParentFragmentManager(), DIALOG_TAG_SHARE_WITH);
		}
	}

	private void sendInvite(String packageName) {
		// is this an SMS app? if it holds the SEND_SMS permission, it most probably is.
		boolean isShortMessage = ConfigUtils.checkManifestPermission(getContext(), packageName, "android.permission.SEND_SMS");

		if (packageName.contains("twitter")) {
			isShortMessage = true;
		}

		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType(MimeUtil.MIME_TYPE_TEXT);
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

		try {
			startActivity(intent);
		} catch (Exception e) {
			Toast.makeText(getContext(), R.string.no_activity_for_mime_type, Toast.LENGTH_LONG).show();
			logger.error("Exception", e);
		}
	}

	public void onLogoClicked() {
		if (this.listView != null) {
			// this stops the fling
			this.listView.smoothScrollBy(0, 0);
			this.listView.setSelection(0);
		}
	}

	/* selector dialog callbacks */

	@Override
	public void onClick(String tag, int which, Object data) {
		if (data == null) {
			return;
		}

		ContactModel contactModel = (ContactModel) data;

		switch (which) {
			case SELECTOR_TAG_CHAT:
				openConversationForIdentity(null, contactModel.getIdentity());
				break;
			case SELECTOR_TAG_SHOW_CONTACT:
				openContact(null, contactModel.getIdentity());
				break;
			case SELECTOR_TAG_REPORT_SPAM:
				TextWithCheckboxDialog sdialog = TextWithCheckboxDialog.newInstance(requireContext().getString(R.string.spam_report_dialog_title, NameUtil.getDisplayNameOrNickname(contactModel, true)), R.string.spam_report_dialog_explain,
					R.string.spam_report_dialog_block_checkbox, R.string.spam_report_short, R.string.cancel);
				sdialog.setData(contactModel);
				sdialog.setTargetFragment(this, 0);
				sdialog.show(getParentFragmentManager(), DIALOG_TAG_REPORT_SPAM);
				break;
			case SELECTOR_TAG_BLOCK:
				serviceManager.getBlackListService().toggle(getActivity(), contactModel);
				break;
			case SELECTOR_TAG_DELETE:
				deleteContacts(Set.of(contactModel));
				break;
		}
	}

	@Override
	public void onCancel(String tag) {}

	@Override
	public void onNo(String tag) {}

	/* callback from TextWithCheckboxDialog */
	@Override
	public void onYes(String tag, Object data, boolean checked) {
		switch(tag) {
			case DIALOG_TAG_REALLY_DELETE_CONTACTS:
				reallyDeleteContacts((Set<ContactModel>) data, checked);
				break;
			case DIALOG_TAG_REPORT_SPAM:
				ContactModel contactModel = (ContactModel) data;

				contactService.reportSpam(contactModel,
					unused -> {
						if (isAdded()) {
							Toast.makeText(getContext(), R.string.spam_successfully_reported, Toast.LENGTH_LONG).show();
						}

						if (checked) {
							ThreemaApplication.requireServiceManager().getBlackListService().add(contactModel.getIdentity());
							ThreemaApplication.requireServiceManager().getExcludedSyncIdentitiesService().add(contactModel.getIdentity());

							try {
								new EmptyChatAsyncTask(
									contactService.createReceiver(contactModel),
									ThreemaApplication.requireServiceManager().getMessageService(),
									ThreemaApplication.requireServiceManager().getConversationService(),
									null,
									true,
									() -> {
										ListenerManager.conversationListeners.handle(ConversationListener::onModifiedAll);
										ListenerManager.contactListeners.handle(listener -> listener.onModified(contactModel));
									}).execute();
							} catch (Exception e) {
								logger.error("Unable to empty chat", e);
							}
						} else {
							ListenerManager.contactListeners.handle(listener -> listener.onModified(contactModel));
						}
					},
					message -> {
						if (isAdded()) {
							Toast.makeText(getContext(), requireContext().getString(R.string.spam_error_reporting, message), Toast.LENGTH_LONG).show();
						}
					}
				);
				break;
			default:
				break;
		}
	}

	/**
	 * Callbacks from GenericAlertDialog
	 */
	@Override
	public void onYes(String tag, Object data) {
		switch(tag) {
			case DIALOG_TAG_REALLY_DELETE_CONTACTS:
				reallyDeleteContacts((Set<ContactModel>) data, false);
				break;
			default:
				break;
		}
	}

	@Override
	public void onNo(String tag, Object data) {	}
}
