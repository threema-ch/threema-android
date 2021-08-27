/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.archive;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.appbar.MaterialToolbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.asynctasks.DeleteConversationsAsyncTask;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.MessageListener;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.ThreemaSearchView;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;

import static ch.threema.app.managers.ListenerManager.conversationListeners;
import static ch.threema.app.managers.ListenerManager.messageListeners;

public class ArchiveActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener, SearchView.OnQueryTextListener {
	private static final Logger logger = LoggerFactory.getLogger(ArchiveActivity.class);
	private static final String DIALOG_TAG_REALLY_DELETE_CHATS = "delc";

	private ArchiveAdapter archiveAdapter;
	private ArchiveViewModel viewModel;
	private ActionMode actionMode = null;
	private ConversationService conversationService;
	private GroupService groupService;
	private EmptyRecyclerView recyclerView;

	public int getLayoutResource() {
		return R.layout.activity_archive;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		conversationListeners.add(this.conversationListener);
		messageListeners.add(this.messageListener);
	}

	@Override
	protected void onDestroy() {
		conversationListeners.remove(this.conversationListener);
		messageListeners.remove(this.messageListener);

		super.onDestroy();
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		if (!super.initActivity(savedInstanceState)) {
			return false;
		}

		try {
			conversationService = serviceManager.getConversationService();
			groupService = serviceManager.getGroupService();
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			return false;
		}

		MaterialToolbar toolbar = findViewById(R.id.material_toolbar);
		toolbar.setNavigationOnClickListener(view -> finish());
		toolbar.setTitle(R.string.archived_chats);

		String filterQuery = getIntent().getStringExtra(ThreemaApplication.INTENT_DATA_ARCHIVE_FILTER);

		MenuItem filterMenu = toolbar.getMenu().findItem(R.id.menu_filter_archive);
		ThreemaSearchView searchView = (ThreemaSearchView) filterMenu.getActionView();

		if (searchView != null) {
			searchView.setQueryHint(getString(R.string.hint_filter_list));
			if (!TestUtil.empty(filterQuery)) {
				filterMenu.expandActionView();
				searchView.setQuery(filterQuery, false);
			}
			searchView.post(() -> searchView.setOnQueryTextListener(ArchiveActivity.this));
		} else {
			filterMenu.setVisible(false);
		}

		archiveAdapter = new ArchiveAdapter(this);
		archiveAdapter.setOnClickItemListener(new ArchiveAdapter.OnClickItemListener() {
			@Override
			public void onClick(ConversationModel conversationModel, View view, int position) {
				if (actionMode != null) {
					archiveAdapter.toggleChecked(position);
					if (archiveAdapter.getCheckedItemsCount() > 0) {
						if (actionMode != null) {
							actionMode.invalidate();
						}
					} else {
						actionMode.finish();
					}
				} else {
					showConversation(conversationModel, view);
				}
			}

			@Override
			public boolean onLongClick(ConversationModel conversationModel, View itemView, int position) {
				if (actionMode != null) {
					actionMode.finish();
				}
				archiveAdapter.toggleChecked(position);
				if (archiveAdapter.getCheckedItemsCount() > 0) {
					actionMode = startSupportActionMode(new ArchiveAction());
				}
				return true;
			}
		});

		recyclerView = this.findViewById(R.id.recycler);
		recyclerView.setHasFixedSize(true);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.setItemAnimator(new DefaultItemAnimator());

		EmptyView emptyView = new EmptyView(this, ConfigUtils.getActionBarSize(this));
		emptyView.setup(R.string.no_archived_chats);
		((ViewGroup) recyclerView.getParent().getParent()).addView(emptyView);
		recyclerView.setEmptyView(emptyView);
		recyclerView.setAdapter(archiveAdapter);

		// Get the ViewModel
		viewModel = new ViewModelProvider(this).get(ArchiveViewModel.class);

		// Create the observer which updates the UI
		final Observer<List<ConversationModel>> conversationsObserver = new Observer<List<ConversationModel>>() {
			@Override
			public void onChanged(List<ConversationModel> newConversations) {
				// Update the UI
				archiveAdapter.setConversationModels(newConversations);
				if (actionMode != null) {
					actionMode.invalidate();
				}
			}
		};

		// Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
		viewModel.getConversationModels().observe(this, conversationsObserver);
		if (!TestUtil.empty(filterQuery)) {
			viewModel.filter(filterQuery);
		} else {
			viewModel.onDataChanged();
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				this.finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		return false;
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		viewModel.filter(newText);
		return true;
	}

	public class ArchiveAction implements ActionMode.Callback {
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			mode.getMenuInflater().inflate(R.menu.action_archive, menu);

			ConfigUtils.themeMenu(menu, ConfigUtils.getColorFromAttribute(ArchiveActivity.this, R.attr.colorAccent));

			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			final int checked = archiveAdapter.getCheckedItemsCount();
			if (checked > 0) {
				mode.setTitle(Integer.toString(checked));
				return true;
			}
			return false;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
				case R.id.menu_delete:
					delete(archiveAdapter.getCheckedItems());
					return true;
				case R.id.menu_unarchive:
					unarchive(archiveAdapter.getCheckedItems());
					return true;
				case R.id.menu_select_all:
					archiveAdapter.selectAll();
					return true;
				default:
					return false;
			}
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			archiveAdapter.clearCheckedItems();
			actionMode = null;
		}
	}

	@Override
	public void onBackPressed() {
		if (actionMode != null) {
			actionMode.finish();
		} else {
			super.onBackPressed();
		}
	}

	private void showConversation(ConversationModel conversationModel, View v) {
		Intent intent = IntentDataUtil.getShowConversationIntent(conversationModel, this);

		if (intent == null) {
			return;
		}
		AnimationUtil.startActivityForResult(this, ConfigUtils.isTabletLayout() ? null : v, intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
	}

	private void unarchive(List<ConversationModel> checkedItems) {
		conversationService.unarchive(checkedItems);
		viewModel.onDataChanged();
		if (actionMode != null) {
			actionMode.finish();
		}
	}

	@SuppressLint("StringFormatInvalid")
	private void delete(List<ConversationModel> checkedItems) {
		String confirmText = String.format(getString(R.string.really_delete_thread_message), checkedItems.size());

		if (checkedItems.size() == 1 && checkedItems.get(0).isGroupConversation()) {
			if (groupService.isGroupMember(checkedItems.get(0).getGroup())) {
				if (groupService.isGroupOwner(checkedItems.get(0).getGroup())) {
					confirmText = getString(R.string.delete_my_group_message);
				} else {
					confirmText = getString(R.string.delete_group_message);
				}
			}
		}

		GenericAlertDialog dialog = GenericAlertDialog.newInstance(
			R.string.really_delete_thread,
			confirmText,
			R.string.ok,
			R.string.cancel);
		dialog.setData(checkedItems);
		dialog.show(getSupportFragmentManager(), DIALOG_TAG_REALLY_DELETE_CHATS);
	}

	private void reallyDelete(final List<ConversationModel> checkedItems) {
		synchronized (checkedItems) {
			new DeleteConversationsAsyncTask(getSupportFragmentManager(), checkedItems, findViewById(R.id.parent_layout), new Runnable() {
				@Override
				public void run() {
					synchronized (checkedItems) {
						checkedItems.clear();
						if (actionMode != null) {
							actionMode.finish();
						}
						viewModel.onDataChanged();
					}
				}
			}).execute();
		}
	}

	@Override
	public void onYes(String tag, Object data) {
		reallyDelete((List<ConversationModel>)data);
	}

	@Override
	public void onNo(String tag, Object data) {

	}

	private final ConversationListener conversationListener = new ConversationListener() {
		@Override
		public void onNew(final ConversationModel conversationModel) {
			// unarchive calls onNew()
			if (archiveAdapter != null && recyclerView != null && viewModel != null) {
				List<ConversationModel> conversationModels = viewModel.getConversationModels().getValue();
				if (conversationModels != null && !conversationModels.contains(conversationModel)) {
					int currentCount = archiveAdapter.getItemCount();
					if (conversationModels.size() != currentCount) {
						// adapter and repository disagree about count: refresh.
						viewModel.onDataChanged();
					}
				}
			}
		}

		@Override
		public void onModified(final ConversationModel modifiedConversationModel, final Integer oldPosition) {
			if (archiveAdapter != null && recyclerView != null && viewModel != null) {
				List<ConversationModel> conversationModels = viewModel.getConversationModels().getValue();
				if (conversationModels != null && conversationModels.contains(modifiedConversationModel)) {
					viewModel.onDataChanged();
				}
			}
		}

		@Override
		public void onRemoved(final ConversationModel conversationModel) {
			if (archiveAdapter != null && recyclerView != null && viewModel != null) {
				List<ConversationModel> conversationModels = viewModel.getConversationModels().getValue();
				if (conversationModels != null && conversationModels.contains(conversationModel)) {
					viewModel.onDataChanged();
				}
			}
		}

		@Override
		public void onModifiedAll() {
			if (archiveAdapter != null && recyclerView != null && viewModel != null) {
				viewModel.onDataChanged();
			}
		}
	};

	private final MessageListener messageListener = new MessageListener() {
		@Override
		public void onNew(AbstractMessageModel newMessage) {
			if (!newMessage.isOutbox() && !newMessage.isStatusMessage() && !newMessage.isRead()) {
				viewModel.onDataChanged();
			}
		}

		@Override
		public void onModified(List<AbstractMessageModel> modifiedMessageModel) { }

		@Override
		public void onRemoved(AbstractMessageModel removedMessageModel) {}

		@Override
		public void onProgressChanged(AbstractMessageModel messageModel, int newProgress) {}
	};

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		ConfigUtils.adjustToolbar(this, getToolbar());
	}
}
