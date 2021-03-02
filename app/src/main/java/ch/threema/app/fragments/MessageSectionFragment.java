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
import android.app.KeyguardManager;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.ContactDetailActivity;
import ch.threema.app.activities.DistributionListAddActivity;
import ch.threema.app.activities.GroupDetailActivity;
import ch.threema.app.activities.RecipientListBaseActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.adapters.MessageListAdapter;
import ch.threema.app.archive.ArchiveActivity;
import ch.threema.app.asynctasks.DeleteDistributionListAsyncTask;
import ch.threema.app.asynctasks.DeleteGroupAsyncTask;
import ch.threema.app.asynctasks.DeleteMyGroupAsyncTask;
import ch.threema.app.asynctasks.EmptyChatAsyncTask;
import ch.threema.app.asynctasks.LeaveGroupAsyncTask;
import ch.threema.app.backuprestore.BackupChatService;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.dialogs.CancelableGenericProgressDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.listeners.ChatListener;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.preference.SettingsActivity;
import ch.threema.app.preference.SettingsSecurityFragment;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.ConversationTagServiceImpl;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.HiddenChatUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.base.ThreemaException;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.TagModel;

import static android.view.MenuItem.SHOW_AS_ACTION_ALWAYS;
import static android.view.MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW;
import static android.view.MenuItem.SHOW_AS_ACTION_NEVER;
import static ch.threema.app.ThreemaApplication.MAX_PW_LENGTH_BACKUP;
import static ch.threema.app.ThreemaApplication.MIN_PW_LENGTH_BACKUP;
import static ch.threema.app.managers.ListenerManager.conversationListeners;

public class MessageSectionFragment extends MainFragment
		implements
			PasswordEntryDialog.PasswordEntryDialogClickListener,
			GenericAlertDialog.DialogClickListener,
			CancelableGenericProgressDialog.ProgressDialogClickListener,
			MessageListAdapter.ItemClickListener,
			SelectorDialog.SelectorDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(MessageSectionFragment.class);

	private static final int PERMISSION_REQUEST_SHARE_THREAD = 1;
	private static final int ID_RETURN_FROM_SECURITY_SETTINGS = 33211;
	private static final int TEMP_MESSAGES_FILE_DELETE_WAIT_TIME = 2 * 60 * 1000;

	private static final String DIALOG_TAG_PREPARING_MESSAGES = "progressMsgs";
	private static final String DIALOG_TAG_SHARE_CHAT = "shareChat";
	private static final String DIALOG_TAG_REALLY_HIDE_THREAD = "lockC";
	private static final String DIALOG_TAG_HIDE_THREAD_EXPLAIN = "hideEx";
	private static final String DIALOG_TAG_SELECT_DELETE_ACTION = "sel";
	private static final String DIALOG_TAG_REALLY_LEAVE_GROUP = "rlg" ;
	private static final String DIALOG_TAG_REALLY_DELETE_MY_GROUP = "rdmg" ;
	private static final String DIALOG_TAG_REALLY_DELETE_GROUP = "rdgcc";
	private static final String DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST = "rddl";
	private static final String DIALOG_TAG_REALLY_EMPTY_CHAT = "rdec";

	private static final int ID_PRIVATE_TO_PUBLIC = 8111;

	private static final int TAG_EMPTY_CHAT = 1;
	private static final int TAG_DELETE_DISTRIBUTION_LIST = 2;
	private static final int TAG_LEAVE_GROUP = 3;
	private static final int TAG_DELETE_MY_GROUP = 4;
	private static final int TAG_DELETE_GROUP = 5;
	private static final int TAG_SET_PRIVATE = 7;
	private static final int TAG_UNSET_PRIVATE = 8;
	private static final int TAG_SHARE = 9;
	private static final int TAG_DELETE_LEFT_GROUP = 10;
	private static final int TAG_EDIT_GROUP = 11;
	private static final int TAG_MARK_READ = 12;

	private static final String BUNDLE_FILTER_QUERY = "filterQuery";
	private static String highlightUid;

	private ServiceManager serviceManager;
	private ConversationService conversationService;
	private ContactService contactService;
	private GroupService groupService;
	private MessageService messageService;
	private DistributionListService distributionListService;
	private BackupChatService backupChatService;
	private DeadlineListService mutedChatsListService, mentionOnlyChatsListService, hiddenChatsListService;
	private ConversationTagService conversationTagService;
	private RingtoneService ringtoneService;
	private FileService fileService;
	private PreferenceService preferenceService;
	private LockAppService lockAppService;

	private Activity activity;
	private File tempMessagesFile;
	private MessageListAdapter messageListAdapter;
	private EmptyRecyclerView recyclerView;
	private View loadingView;
	private SearchView searchView;
	private WeakReference<MenuItem> searchMenuItemRef, toggleHiddenMenuItemRef;
	private ResumePauseHandler resumePauseHandler;
	private int currentFullSyncs = 0;
	private String filterQuery;
	private int cornerRadius;

	private int archiveCount = 0;
	private Snackbar archiveSnackbar;

	private ConversationModel selectedConversation;
	private ExtendedFloatingActionButton floatingButtonView;

	private final Object messageListAdapterLock = new Object();

	private final SynchronizeContactsListener synchronizeContactsListener = new SynchronizeContactsListener() {
		@Override
		public void onStarted(SynchronizeContactsRoutine startedRoutine) {
			if (startedRoutine.fullSync()) {
				currentFullSyncs++;
			}
		}

		@Override
		public void onFinished(SynchronizeContactsRoutine finishedRoutine) {
			if (finishedRoutine.fullSync()) {
				currentFullSyncs--;

				logger.debug("synchronizeContactsListener.onFinished");
				refreshListEvent();
			}
		}

		@Override
		public void onError(SynchronizeContactsRoutine finishedRoutine) {
			if (finishedRoutine.fullSync()) {
				currentFullSyncs--;
				logger.debug("synchronizeContactsListener.onError");
				refreshListEvent();
			}
		}
	};

	private final ConversationListener conversationListener = new ConversationListener() {
		@Override
		public void onNew(final ConversationModel conversationModel) {
			logger.debug("on new conversation");
			if(messageListAdapter != null && recyclerView != null) {
				updateList(0, null, null);
			}
		}

		@Override
		public void onModified(final ConversationModel modifiedConversationModel, final Integer oldPosition) {
			logger.debug("on modified conversation");
			if(messageListAdapter != null && recyclerView != null) {
				//scroll if position changed (to top)
				List<ConversationModel> l = new ArrayList<>();
				l.add(modifiedConversationModel);

				updateList(
						oldPosition,
						l,
						null);
			}
		}

		@Override
		public void onRemoved(final ConversationModel conversationModel) {
			if (isMultiPaneEnabled(activity)) {
				activity.finish();
			} else {
				if(messageListAdapter != null) {
					updateList();
				}
			}
		}

		@Override
		public void onModifiedAll() {
			logger.debug("on modified all");
			if(messageListAdapter != null && recyclerView != null) {
				updateList(0, null, new Runnable() {
					@Override
					public void run() {
						RuntimeUtil.runOnUiThread(new Runnable() {
							@Override
							public void run() {
									messageListAdapter.notifyDataSetChanged();
								}
						});
					}
				});
			}
		}
	};

	private final ChatListener chatListener = new ChatListener() {
		@Override
		public void onChatOpened(String conversationUid) {
			highlightUid = conversationUid;

			if (isMultiPaneEnabled(activity) && messageListAdapter != null) {
				messageListAdapter.setHighlightItem(conversationUid);
				messageListAdapter.notifyDataSetChanged();
			}
		}
	};

	private final ContactSettingsListener contactSettingsListener = new ContactSettingsListener() {
		@Override
		public void onSortingChanged() {
			//ignore
		}

		@Override
		public void onNameFormatChanged() {
			logger.debug("contactSettingsListener.onNameFormatChanged");
			refreshListEvent();
		}

		@Override
		public void onAvatarSettingChanged() {
			logger.debug("contactSettingsListener.onAvatarSettingChanged");
			refreshListEvent();
		}

		@Override
		public void onInactiveContactsSettingChanged() {

		}

		@Override
		public void onNotificationSettingChanged(String uid) {
			logger.debug("contactSettingsListener.onNotificationSettingChanged");
			refreshListEvent();
		}
	};

	private final ContactListener contactListener = new ContactListener() {
		@Override
		public void onModified(ContactModel modifiedContactModel) {
			logger.debug("contactListener.onModified [" + modifiedContactModel + "]");
			refreshListEvent();
		}

		@Override
		public void onAvatarChanged(ContactModel contactModel) {
			this.onModified(contactModel); // TODO: Is this required?
		}

		@Override
		public void onNew(ContactModel createdContactModel) {
			//ignore
		}

		@Override
		public void onRemoved(ContactModel removedContactModel) {
			//ignore
		}

		@Override
		public boolean handle(String identity) {
			return currentFullSyncs <= 0;
		}
	};

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
				this.groupService,
				this.conversationService,
				this.distributionListService,
				this.fileService,
				this.backupChatService,
				this.mutedChatsListService,
				this.hiddenChatsListService,
				this.ringtoneService,
				this.preferenceService,
				this.lockAppService);
	}

	protected void instantiate() {
		this.serviceManager = ThreemaApplication.getServiceManager();

		if (this.serviceManager != null) {
			try {
				this.contactService = this.serviceManager.getContactService();
				this.groupService = this.serviceManager.getGroupService();
				this.messageService = this.serviceManager.getMessageService();
				this.conversationService = this.serviceManager.getConversationService();
				this.distributionListService = this.serviceManager.getDistributionListService();
				this.fileService = this.serviceManager.getFileService();
				this.backupChatService = this.serviceManager.getBackupChatService();
				this.mutedChatsListService = this.serviceManager.getMutedChatsListService();
				this.mentionOnlyChatsListService = this.serviceManager.getMentionOnlyChatsListService();
				this.hiddenChatsListService = this.serviceManager.getHiddenChatsListService();
				this.ringtoneService = this.serviceManager.getRingtoneService();
				this.preferenceService = this.serviceManager.getPreferenceService();
				this.conversationTagService = this.serviceManager.getConversationTagService();
				this.lockAppService = this.serviceManager.getLockAppService();
			} catch (MasterKeyLockedException e) {
				logger.debug("Master Key locked!");
			} catch (ThreemaException e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		logger.debug("onAttach");

		this.activity = activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		logger.debug("onCreate");

		setRetainInstance(true);
		setHasOptionsMenu(true);

		setupListeners();

		this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this.activity);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		logger.debug("onViewCreated");

		try {
			//show loading first
			ViewUtil.show(loadingView, true);

			updateList(null,null, new Runnable() {
				@Override
				public void run() {
					//hide loading
					ViewUtil.show(loadingView, false);
				}
			}, true);
		} catch (Exception e) {
			LogUtil.exception(e, getActivity());
		}

		if (savedInstanceState != null && TestUtil.empty(filterQuery)) {
			filterQuery = savedInstanceState.getString(BUNDLE_FILTER_QUERY);
		}
	}

	@Override
	public void onDestroyView() {
		searchView = null;

		if (searchMenuItemRef != null && searchMenuItemRef.get() != null) {
			searchMenuItemRef.clear();
		}
		messageListAdapter = null;

		super.onDestroyView();
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		super.onPrepareOptionsMenu(menu);

		// move search item to popup if the lock item is visible
		if (this.searchMenuItemRef != null) {
			if (lockAppService != null && lockAppService.isLockingEnabled()) {
				this.searchMenuItemRef.get().setShowAsAction(SHOW_AS_ACTION_NEVER | SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
			} else {
				this.searchMenuItemRef.get().setShowAsAction(SHOW_AS_ACTION_ALWAYS | SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		logger.debug("onCreateOptionsMenu");

		if (activity != null) {
			if (!isMultiPaneEnabled(activity)) {
				MenuItem searchMenuItem = menu.findItem(R.id.menu_search_messages);

				if (searchMenuItem == null) {
					inflater.inflate(R.menu.fragment_messages, menu);

					// Associate searchable configuration with the SearchView
					if (activity != null && this.isAdded()) {
						SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);

						searchMenuItem = menu.findItem(R.id.menu_search_messages);
						this.searchView = (SearchView) searchMenuItem.getActionView();

						if (this.searchView != null && searchManager != null) {
							SearchableInfo mSearchableInfo = searchManager.getSearchableInfo(activity.getComponentName());
							if (this.searchView != null) {
								if (!TestUtil.empty(filterQuery)) {
									// restore filter
									MenuItemCompat.expandActionView(searchMenuItem);
									searchView.setQuery(filterQuery, false);
									searchView.clearFocus();
								}
								this.searchView.setSearchableInfo(mSearchableInfo);
								this.searchView.setQueryHint(getString(R.string.hint_filter_list));
								this.searchView.setOnQueryTextListener(queryTextListener);
							}
						}
					}
				}

				this.searchMenuItemRef = new WeakReference<>(searchMenuItem);

				toggleHiddenMenuItemRef = new WeakReference<>(menu.findItem(R.id.menu_toggle_private_chats));
				if (toggleHiddenMenuItemRef.get() != null) {
					if (isAdded()) {
						toggleHiddenMenuItemRef.get().setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
								@Override
								public boolean onMenuItemClick(MenuItem item) {
									if (preferenceService.isPrivateChatsHidden()) {
										requestUnhideChats();
									} else {
										preferenceService.setPrivateChatsHidden(true);
										fireSecretReceiverUpdate();
										updateList();
									}
									return true;
								}
							});
						updateHiddenMenuVisibility();
					}
				}
			}
		}
		super.onCreateOptionsMenu(menu, inflater);
	}

	private void requestUnhideChats() {
		HiddenChatUtil.launchLockCheckDialog(this, preferenceService);
	}

	final SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
		@Override
		public boolean onQueryTextChange(String query) {
			filterQuery = query;
			updateList(0, null, null);
			return true;
		}

		@Override
		public boolean onQueryTextSubmit(String query) {
			return true;
		}
	};

	private void showConversation(ConversationModel conversationModel, View v) {
		Intent intent = IntentDataUtil.getShowConversationIntent(conversationModel, activity);

		if (intent == null) {
			return;
		}

		if (isMultiPaneEnabled(activity)) {
			if (this.isAdded()) {
				intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
				activity.overridePendingTransition(0, 0);
			}
		} else {
			AnimationUtil.startActivityForResult(activity, ConfigUtils.isTabletLayout() ? null : v, intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case ThreemaActivity.ACTIVITY_ID_SHARE_CHAT:
				if (tempMessagesFile != null) {
				/* We cannot delete the file immediately as some apps (e.g. Dropbox)
				   take some time until they read the file after the intent has been completed.
				   As we can't know for sure when they're done, we simply wait for one minute before
				   we delete the temporary file. */
					new Thread() {
						final String tmpfilePath = tempMessagesFile.getAbsolutePath();

						@Override
						public void run() {
							try {
								Thread.sleep(TEMP_MESSAGES_FILE_DELETE_WAIT_TIME);
							} catch (InterruptedException e) {
								logger.error("Exception", e);
							} finally {
								FileUtil.deleteFileOrWarn(tmpfilePath, "tempMessagesFile", logger);
							}
						}
					}.start();

					tempMessagesFile = null;
				}
				break;
			case ThreemaActivity.ACTIVITY_ID_CHECK_LOCK:
				if (resultCode == Activity.RESULT_OK) {
					serviceManager.getScreenLockService().setAuthenticated(true);
					preferenceService.setPrivateChatsHidden(false);
					fireSecretReceiverUpdate();
					updateList(0, null, null);
				}
				break;
			case ID_RETURN_FROM_SECURITY_SETTINGS:
				if (ConfigUtils.hasProtection(preferenceService)) {
					reallyHideChat(selectedConversation);
				}
				break;
			case ID_PRIVATE_TO_PUBLIC:
				if (resultCode == Activity.RESULT_OK) {
					ThreemaApplication.getServiceManager().getScreenLockService().setAuthenticated(true);
					if (selectedConversation != null) {
						MessageReceiver receiver = selectedConversation.getReceiver();
						if (receiver != null) {
							doUnhideChat(receiver);
						}
					}
				}
				// fallthrough
			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private void doUnhideChat(MessageReceiver receiver) {
		if (receiver != null && hiddenChatsListService.has(receiver.getUniqueIdString())) {
			hiddenChatsListService.remove(receiver.getUniqueIdString());

			if (getView() != null) {
				Snackbar.make(getView(), R.string.chat_visible, Snackbar.LENGTH_SHORT).show();
			}

			this.fireReceiverUpdate(receiver);
			messageListAdapter.clearSelections();
		}
	}

	private void hideChat(ConversationModel conversationModel) {
		MessageReceiver receiver = conversationModel.getReceiver();

		if (hiddenChatsListService.has(receiver.getUniqueIdString())) {
			if (ConfigUtils.hasProtection(preferenceService)) {
				// persist selection
				selectedConversation = conversationModel;
				HiddenChatUtil.launchLockCheckDialog(null, this, preferenceService, ID_PRIVATE_TO_PUBLIC);
			} else {
				doUnhideChat(receiver);
			}
		} else {
			if (ConfigUtils.hasProtection(preferenceService)) {
				GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.hide_chat,
						R.string.really_hide_chat_message,
						R.string.ok,
						R.string.cancel);

				dialog.setTargetFragment(this, 0);
				dialog.setData(conversationModel);
				dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_HIDE_THREAD);
			} else {
				GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.hide_chat,
						R.string.hide_chat_message_explain,
						R.string.set_lock,
						R.string.cancel);

				dialog.setTargetFragment(this, 0);
				dialog.setData(conversationModel);
				dialog.show(getFragmentManager(), DIALOG_TAG_HIDE_THREAD_EXPLAIN);
			}
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void reallyHideChat(ConversationModel conversationModel) {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected void onPreExecute() {
				if (resumePauseHandler != null) {
					resumePauseHandler.onPause();
				}
			}

			@Override
			protected Void doInBackground(Void... params) {
				hiddenChatsListService.add(conversationModel.getReceiver().getUniqueIdString(), DeadlineListService.DEADLINE_INDEFINITE);
				fireReceiverUpdate(conversationModel.getReceiver());
				return null;
			}

			@Override
			protected void onPostExecute(Void aVoid) {
				messageListAdapter.clearSelections();
				if (getView() != null) {
					Snackbar.make(getView(), R.string.chat_hidden, Snackbar.LENGTH_SHORT).show();
				}
				if (resumePauseHandler != null) {
					resumePauseHandler.onResume();
				}
				updateHiddenMenuVisibility();
				if (ConfigUtils.hasProtection(preferenceService) && preferenceService.isPrivateChatsHidden()) {
					fireSecretReceiverUpdate();
					updateList();
				}
			}
		}.execute();
	}

	private void shareChat(final ConversationModel conversationModel, final String password, final boolean includeMedia) {
		CancelableGenericProgressDialog progressDialog = CancelableGenericProgressDialog.newInstance(R.string.preparing_messages, 0, R.string.cancel);
		progressDialog.setTargetFragment(this, 0);
		progressDialog.show(getFragmentManager(), DIALOG_TAG_PREPARING_MESSAGES);

		new Thread(new Runnable() {
			@Override
			public void run() {
				String displayName = FileUtil.sanitizeFileName(conversationModel.getReceiver().getDisplayName());
				String filename = FilenameUtils.normalizeNoEndSeparator("messages-" + displayName);
				tempMessagesFile = new File(ConfigUtils.useContentUris() ? fileService.getTempPath() : fileService.getExtTmpPath(), filename + ".zip");
				FileUtil.deleteFileOrWarn(tempMessagesFile, "tempMessagesFile", logger);

				if (backupChatService.backupChatToZip(conversationModel, tempMessagesFile, password, includeMedia, displayName)) {

					if (tempMessagesFile != null && tempMessagesFile.exists() && tempMessagesFile.length() > 0) {
						final Intent intent = new Intent(Intent.ACTION_SEND);
						intent.setType(MimeUtil.MIME_TYPE_ZIP);
						intent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.share_subject) + " " + conversationModel.getReceiver().getDisplayName());
						intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.chat_history_attached) + "\n\n" + getString(R.string.share_conversation_body));
						intent.putExtra(Intent.EXTRA_STREAM, fileService.getShareFileUri(tempMessagesFile, null));
						intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

						RuntimeUtil.runOnUiThread(() -> {
							DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_PREPARING_MESSAGES, true);
							startActivityForResult(Intent.createChooser(intent, getString(R.string.share_via)), ThreemaActivity.ACTIVITY_ID_SHARE_CHAT);
						});
					}
				} else {
					RuntimeUtil.runOnUiThread(() -> {
						DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_PREPARING_MESSAGES, true);
						SimpleStringAlertDialog.newInstance(R.string.share_via, getString(R.string.an_error_occurred)).
								show(getFragmentManager(), "diskfull");
					});
				}
			}
		}).start();
	}

	private void prepareShareChat(ConversationModel model) {
		PasswordEntryDialog dialogFragment = PasswordEntryDialog.newInstance(
				R.string.share_chat,
				R.string.enter_zip_password_body,
				R.string.password_hint,
				R.string.ok,
				R.string.cancel,
				MIN_PW_LENGTH_BACKUP,
				MAX_PW_LENGTH_BACKUP,
				R.string.backup_password_again_summary,
				0,
				R.string.backup_data_media);
		dialogFragment.setTargetFragment(this, 0);
		dialogFragment.setData(model);
		dialogFragment.show(getFragmentManager(), DIALOG_TAG_SHARE_CHAT);
	}

	private void refreshListEvent() {
		logger.debug("refreshListEvent reloadData");
		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.runOnActive("refresh_list", new ResumePauseHandler.RunIfActive() {
				@Override
				public void runOnUiThread() {
					if (messageListAdapter == null) {
						return;
					}
					messageListAdapter.notifyDataSetChanged();
				}
			});
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View fragmentView = getView();

		if (fragmentView == null) {
			fragmentView = inflater.inflate(R.layout.fragment_messages, container, false);

			final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());

			this.recyclerView = fragmentView.findViewById(R.id.list);
			this.recyclerView.setHasFixedSize(true);
			this.recyclerView.setLayoutManager(linearLayoutManager);
			this.recyclerView.setItemAnimator(new DefaultItemAnimator());

			this.cornerRadius = getResources().getDimensionPixelSize(R.dimen.messagelist_card_corner_radius);

			final ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT|ItemTouchHelper.LEFT) {
				private final VectorDrawableCompat pinIconDrawable = VectorDrawableCompat.create(getResources(), R.drawable.ic_pin, null);
				private final VectorDrawableCompat unpinIconDrawable = VectorDrawableCompat.create(getResources(), R.drawable.ic_pin_outline, null);
				private final VectorDrawableCompat archiveDrawable = VectorDrawableCompat.create(getResources(), R.drawable.ic_archive_outline, null);

				@Override
				public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
					return 0.7f;
				}


				@Override
				public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
					// disable swiping and dragging for footer views
					if (viewHolder.getItemViewType() == MessageListAdapter.TYPE_FOOTER) {
						return makeMovementFlags(0 , 0);
					}
					return super.getMovementFlags(recyclerView, viewHolder);
				}

				@Override
				public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
					return false;
				}

				@Override
				public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
					return super.getSwipeDirs(recyclerView, viewHolder);
				}

				@Override
				public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
					// swipe has ended

					// required to clear swipe layout
					messageListAdapter.notifyDataSetChanged();

					final MessageListAdapter.MessageListViewHolder holder = (MessageListAdapter.MessageListViewHolder) viewHolder;
					final int oldPosition = holder.getConversationModel().getPosition();

					if (direction == ItemTouchHelper.RIGHT) {
						TagModel pinTagModel = conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_PIN);

						conversationTagService.toggle(holder.getConversationModel(), pinTagModel, true);

						ArrayList<ConversationModel> conversationModels = new ArrayList<>();
						conversationModels.add(holder.getConversationModel());

						updateList(null, conversationModels, new Runnable() {
							@Override
							public void run() {
								ListenerManager.conversationListeners.handle((ConversationListener listener) -> {
									listener.onModified(holder.getConversationModel(), oldPosition);
								});
							}
						});
					} else if (direction == ItemTouchHelper.LEFT) {
						archiveCount++;
						conversationService.archive(holder.getConversationModel());

						String snackText = String.format(getString(R.string.message_archived), archiveCount);

						if (archiveSnackbar != null && archiveSnackbar.isShown()) {
							archiveSnackbar.dismiss();
						}

						if (getView() != null) {
							archiveSnackbar = Snackbar.make(getView(), snackText, Snackbar.LENGTH_LONG);
							archiveSnackbar.addCallback(new Snackbar.Callback() {
								@Override
								public void onDismissed(Snackbar snackbar, int event) {
									super.onDismissed(snackbar, event);
									archiveCount = 0;
								}
							});
							archiveSnackbar.show();
						}
					}
 				}

				@Override
				public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
					View itemView = viewHolder.itemView;

					if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
						Paint paint = new Paint();

						if (dX > 0) {
							MessageListAdapter.MessageListViewHolder holder = (MessageListAdapter.MessageListViewHolder) viewHolder;
							TagModel pinTagModel = conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_PIN);

							VectorDrawableCompat icon = conversationTagService.isTaggedWith(holder.getConversationModel(), pinTagModel) ? unpinIconDrawable : pinIconDrawable;
							icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());

							String label = conversationTagService.isTaggedWith(holder.getConversationModel(), pinTagModel) ? getString(R.string.unpin) : getString(R.string.pin);

							paint.setColor(getResources().getColor(R.color.messagelist_pinned_color));
							canvas.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX + cornerRadius, (float) itemView.getBottom(), paint);
							canvas.save();
							canvas.translate(
								(float) itemView.getLeft() + getResources().getDimension(R.dimen.swipe_icon_inset),
								(float) itemView.getTop() + ((float) itemView.getBottom() - (float) itemView.getTop() - icon.getIntrinsicHeight())/2);
							icon.draw(canvas);
							canvas.restore();

							Paint textPaint = new Paint();
							textPaint.setColor(Color.WHITE);
							textPaint.setTextSize(getResources().getDimension(R.dimen.swipe_text_size));

							Rect rect = new Rect();
							textPaint.getTextBounds(label, 0, label.length(), rect);

							canvas.drawText(label,
								itemView.getLeft() + getResources().getDimension(R.dimen.swipe_text_inset),
								itemView.getTop() + (itemView.getBottom() - itemView.getTop() + rect.height()) / 2,
								textPaint);
						} else if (dX < 0) {
							VectorDrawableCompat icon = archiveDrawable;
							icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
							icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);

							String label = getString(R.string.to_archive);

							paint.setColor(getResources().getColor(R.color.messagelist_archive_color));
							canvas.drawRect(dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom(), paint);
							canvas.save();
							canvas.translate(
									(float) itemView.getRight() - getResources().getDimension(R.dimen.swipe_icon_inset) - icon.getIntrinsicWidth(),
									(float) itemView.getTop() + ((float) itemView.getBottom() - (float) itemView.getTop() - icon.getIntrinsicHeight())/2);
							icon.draw(canvas);
							canvas.restore();

							Paint textPaint = new Paint();
							textPaint.setColor(Color.WHITE);
							textPaint.setTextSize(getResources().getDimension(R.dimen.swipe_text_size));

							Rect rect = new Rect();
							textPaint.getTextBounds(label, 0, label.length(), rect);
							float textStartX = itemView.getRight() - getResources().getDimension(R.dimen.swipe_text_inset) - rect.width();
							if (textStartX < 0) {
								textStartX = 0;
							}

							canvas.drawText(label,
									textStartX,
									itemView.getTop() + (itemView.getBottom() - itemView.getTop() + rect.height()) / 2,
									textPaint);
						}
					}
					super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
				}

				@Override
				public float getSwipeEscapeVelocity(float defaultValue) {
					return defaultValue * 20;
				}

				@Override
				public float getSwipeVelocityThreshold(float defaultValue) {
					return defaultValue * 5;
				}
			};
			ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
			itemTouchHelper.attachToRecyclerView(recyclerView);

			//disable change animation to avoid avatar flicker FX
			((SimpleItemAnimator) this.recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

			this.loadingView = fragmentView.findViewById(R.id.session_loading);
			ViewUtil.show(this.loadingView, true);

			this.floatingButtonView = fragmentView.findViewById(R.id.floating);
			this.floatingButtonView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onFABClicked(v);
				}
			});

			// add text view if contact list is empty
			EmptyView emptyView = new EmptyView(activity);
			emptyView.setup(R.string.no_recent_conversations);
			((ViewGroup) recyclerView.getParent()).addView(emptyView);
			recyclerView.setEmptyView(emptyView);
			recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
				@Override
				public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
					super.onScrolled(recyclerView, dx, dy);

					if (linearLayoutManager.findFirstVisibleItemPosition() == 0) {
						floatingButtonView.extend();
					} else {
						floatingButtonView.shrink();
					}
				}
			});

			//instantiate fragment
			//
			if(!this.requiredInstances()) {
				logger.error("could not instantiate required objects");
			}
		}
		return fragmentView;
	}

	private void onFABClicked(View v) {
		// stop list fling to avoid crashes due to concurrent access to conversation data
		recyclerView.stopScroll();
		Intent intent = new Intent(getContext(), RecipientListBaseActivity.class);
		intent.putExtra(ThreemaApplication.INTENT_DATA_HIDE_RECENTS, true);
		intent.putExtra(RecipientListBaseActivity.INTENT_DATA_MULTISELECT, false);
		AnimationUtil.startActivityForResult(this.getActivity(), v, intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
	}

	@Override
	public void onDestroy() {
		this.removeListeners();

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onDestroy(this);
		}

		super.onDestroy();
	}

	@Override
	public void onItemClick(View view, int position, ConversationModel model) {
		showConversation(model, view);
	}

	@Override
	public void onAvatarClick(View view, int position, ConversationModel model) {
		Intent intent = null;
		if (model.isContactConversation()) {
			intent = new Intent(getActivity(), ContactDetailActivity.class);
			intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, model.getContact().getIdentity());
		} else if (model.isGroupConversation() && groupService.isGroupMember(model.getGroup())) {
			editGroup(model, view);
		} else if (model.isDistributionListConversation()) {
			intent = new Intent(getActivity(), DistributionListAddActivity.class);
			intent.putExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, model.getDistributionList().getId());
		}
		if (intent != null) {
			AnimationUtil.startActivityForResult(activity, view, intent, 0);
		}
	}

	@Override
	public void onFooterClick(View view) {
		AnimationUtil.startActivity(getActivity(), view,  new Intent(getActivity(), ArchiveActivity.class));
	}

	private void editGroup(ConversationModel model, View view) {
		Intent intent = new Intent(getActivity(), GroupDetailActivity.class);
		intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, model.getGroup().getId());
		AnimationUtil.startActivityForResult(activity, view, intent, 0);
	}

	@Override
	public boolean onItemLongClick(View view, int position, ConversationModel conversationModel) {
		if (!isMultiPaneEnabled(activity)) {
			messageListAdapter.toggleItemChecked(conversationModel, position);
			showSelector();
			return true;
		}
		return false;
	}

	@Override
	public void onProgressbarCanceled(String tag) {
		if (this.backupChatService != null) {
			this.backupChatService.cancel();
		}
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		logger.debug("*** onHiddenChanged: " + hidden);

		if (hidden) {
			if (this.searchView != null && this.searchView.isShown() && this.searchMenuItemRef != null && this.searchMenuItemRef.get() != null) {
				this.searchMenuItemRef.get().collapseActionView();
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
	public void onPause() {
		super.onPause();
		logger.debug("*** onPause");

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onPause();
		}
	}

	@Override
	public void onResume() {
		logger.debug("*** onResume");

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onResume();
		}

		if (this.preferenceService != null) {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
					(PreferenceService.LockingMech_SYSTEM.equals(preferenceService.getLockMechanism()))) {
				KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
				if (!keyguardManager.isDeviceSecure()) {
					Toast.makeText(getActivity(), R.string.no_lockscreen_set, Toast.LENGTH_LONG).show();
					preferenceService.setLockMechanism(PreferenceService.LockingMech_NONE);
					preferenceService.setAppLockEnabled(false);
					preferenceService.setPrivateChatsHidden(false);
					updateList(0, null, null);
				}
			}
		}
		updateHiddenMenuVisibility();

		super.onResume();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		logger.info("saveInstance");

		if (!TestUtil.empty(filterQuery)) {
			outState.putString(BUNDLE_FILTER_QUERY, filterQuery);
		}

		super.onSaveInstanceState(outState);
	}

	@Override
	public void onYes(String tag, String text, boolean isChecked, Object data) {
		shareChat((ConversationModel) data, text, isChecked);
	}

	private void showSelector() {
		ArrayList<String> labels = new ArrayList<>();
		ArrayList<Integer> tags = new ArrayList<>();

		if (messageListAdapter.getCheckedItemCount() != 1) {
			return;
		}

		ConversationModel conversationModel = messageListAdapter.getCheckedItems().get(0);
		if (conversationModel == null) {
			return;
		}

		MessageReceiver receiver;
		try {
			receiver = conversationModel.getReceiver();
		} catch (Exception e) {
			logger.error("Exception", e);
			return;
		}

		if (receiver == null) {
			return;
		}

		boolean isPrivate = hiddenChatsListService.has(receiver.getUniqueIdString());

		if (conversationModel.hasUnreadMessage()) {
			labels.add(getString(R.string.mark_read));
			tags.add(TAG_MARK_READ);
		}

		if (isPrivate) {
			labels.add(getString(R.string.unset_private));
			tags.add(TAG_UNSET_PRIVATE);
		} else {
			labels.add(getString(R.string.set_private));
			tags.add(TAG_SET_PRIVATE);
		}

		if (!isPrivate && !AppRestrictionUtil.isExportDisabled(getActivity())) {
			labels.add(getString(R.string.share_chat));
			tags.add(TAG_SHARE);
		}

		if (conversationModel.getMessageCount() > 0) {
			labels.add(getString(R.string.empty_chat_title));
			tags.add(TAG_EMPTY_CHAT);
		}

		if (conversationModel.isDistributionListConversation()) {
			// distribution lists
			labels.add(getString(R.string.really_delete_distribution_list));
			tags.add(TAG_DELETE_DISTRIBUTION_LIST);
		} else if (conversationModel.isGroupConversation()) {
			// group chats
			if (groupService.isGroupOwner(conversationModel.getGroup()) &&
				groupService.isGroupMember(conversationModel.getGroup())) {
				labels.add(getString(R.string.group_edit_title));
				tags.add(TAG_EDIT_GROUP);
			}
			if (groupService.isGroupMember(conversationModel.getGroup())) {
				labels.add(getString(R.string.action_leave_group));
				tags.add(TAG_LEAVE_GROUP);
			}
			labels.add(getString(R.string.action_delete_group));
			if (groupService.isGroupMember(conversationModel.getGroup())) {
				if (groupService.isGroupOwner(conversationModel.getGroup())) {
					tags.add(TAG_DELETE_MY_GROUP);
				} else {
					tags.add(TAG_DELETE_GROUP);
				}
			} else {
				tags.add(TAG_DELETE_LEFT_GROUP);
			}
		}

		SelectorDialog selectorDialog = SelectorDialog.newInstance(receiver.getDisplayName(), labels, tags, getString(R.string.cancel));
		selectorDialog.setData(conversationModel);
		selectorDialog.setTargetFragment(this, 0);
		selectorDialog.show(getFragmentManager(), DIALOG_TAG_SELECT_DELETE_ACTION);
	}

	@SuppressLint("StringFormatInvalid")
	@Override
	public void onClick(String tag, int which, Object data) {
		GenericAlertDialog dialog;

		messageListAdapter.clearSelections();

		final ConversationModel conversationModel = (ConversationModel) data;

		switch (which) {
			case TAG_EMPTY_CHAT:
				dialog = GenericAlertDialog.newInstance(
						R.string.empty_chat_title,
						R.string.empty_chat_confirm,
						R.string.ok,
						R.string.cancel);
				dialog.setData(conversationModel);
				dialog.setTargetFragment(this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_EMPTY_CHAT);
				break;
			case TAG_DELETE_DISTRIBUTION_LIST:
				dialog = GenericAlertDialog.newInstance(
					R.string.really_delete_distribution_list,
					R.string.really_delete_distribution_list_message,
					R.string.ok,
					R.string.cancel);
				dialog.setTargetFragment(this, 0);
				dialog.setData(conversationModel.getDistributionList());
				dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST);
				break;
			case TAG_LEAVE_GROUP:
				int leaveMessageRes = groupService.isGroupOwner(conversationModel.getGroup()) ? R.string.really_leave_group_admin_message : R.string.really_leave_group_message;
				dialog = GenericAlertDialog.newInstance(
					R.string.action_leave_group,
					Html.fromHtml(getString(leaveMessageRes)),
					R.string.ok,
					R.string.cancel);
				dialog.setTargetFragment(this, 0);
				dialog.setData(conversationModel.getGroup());
				dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_LEAVE_GROUP);
				break;
			case TAG_EDIT_GROUP:
				editGroup(conversationModel, null);
				break;
			case TAG_DELETE_MY_GROUP:
				dialog = GenericAlertDialog.newInstance(
					R.string.action_delete_group,
					R.string.delete_my_group_message,
					R.string.ok,
					R.string.cancel);
				dialog.setTargetFragment(this, 0);
				dialog.setData(conversationModel.getGroup());
				dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_MY_GROUP);
				break;
			case TAG_DELETE_GROUP:
				dialog = GenericAlertDialog.newInstance(R.string.action_delete_group,
					String.format(getString(R.string.delete_group_message), 1),
					R.string.ok,
					R.string.cancel);
				dialog.setTargetFragment(this, 0);
				dialog.setData(conversationModel.getGroup());
				dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_GROUP);
				break;
			case TAG_DELETE_LEFT_GROUP:
				dialog = GenericAlertDialog.newInstance(R.string.action_delete_group,
					String.format(getString(R.string.delete_left_group_message), 1),
					R.string.ok,
					R.string.cancel);
				dialog.setTargetFragment(this, 0);
				dialog.setData(conversationModel.getGroup());
				dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_GROUP);
				break;
			case TAG_SET_PRIVATE:
			case TAG_UNSET_PRIVATE:
				hideChat(conversationModel);
				break;
			case TAG_SHARE:
				if (ConfigUtils.requestStoragePermissions(activity, this, PERMISSION_REQUEST_SHARE_THREAD)) {
					prepareShareChat(conversationModel);
				}
				break;
			case TAG_MARK_READ:
				new Thread(new Runnable() {
					@Override
					public void run() {
						messageService.markConversationAsRead(conversationModel.getReceiver(), serviceManager.getNotificationService());
					}
				}).start();
				break;
		}
	}

	@Override
	public void onCancel(String tag) {
		messageListAdapter.clearSelections();
	}

	@Override
	public void onNo(String tag) {
		if (DIALOG_TAG_SELECT_DELETE_ACTION.equals(tag)) {
			messageListAdapter.clearSelections();
		}
	}

	@Override
	public void onYes(String tag, Object data) {
		switch(tag) {
			case DIALOG_TAG_REALLY_HIDE_THREAD:
				reallyHideChat((ConversationModel) data);
				break;
			case DIALOG_TAG_HIDE_THREAD_EXPLAIN:
				selectedConversation = (ConversationModel) data;
				Intent intent = new Intent(activity, SettingsActivity.class);
				intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsSecurityFragment.class.getName());
				intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
				startActivityForResult(intent, ID_RETURN_FROM_SECURITY_SETTINGS);
				break;
			case DIALOG_TAG_REALLY_DELETE_MY_GROUP:
				new DeleteMyGroupAsyncTask((GroupModel) data, groupService, null, this, null).execute();
				break;
			case DIALOG_TAG_REALLY_LEAVE_GROUP:
				new LeaveGroupAsyncTask((GroupModel) data, groupService, null, this, null).execute();
				break;
			case DIALOG_TAG_REALLY_DELETE_GROUP:
				new DeleteGroupAsyncTask((GroupModel) data, groupService, null, this, null).execute();
				break;
			case DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST:
				new DeleteDistributionListAsyncTask((DistributionListModel) data, distributionListService, this, null).execute();
				break;
			case DIALOG_TAG_REALLY_EMPTY_CHAT:
				final ConversationModel conversationModel = (ConversationModel) data;
				new EmptyChatAsyncTask(new MessageReceiver[]{conversationModel.getReceiver()}, messageService, getFragmentManager(), false, new Runnable() {
					@Override
					public void run() {
						conversationListeners.handle(listener -> {
							conversationService.clear(conversationModel);
							listener.onModified(conversationModel, null);
						});
					}
				}).execute();
				break;
			default:
				break;
		}
	}

	@Override
	public void onNo(String tag, Object data) { }

	@Override
	public boolean onBackPressed() {
		return false;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   @NonNull String permissions[], @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_SHARE_THREAD:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					prepareShareChat(selectedConversation);
				} else if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
					ConfigUtils.showPermissionRationale(getContext(), getView(),  R.string.permission_storage_required);
				}
				break;
		}
	}

	private void setupListeners() {
		logger.debug("*** setup listeners");

		// set listeners
		conversationListeners.add(this.conversationListener);
		ListenerManager.contactListeners.add(this.contactListener);
		ListenerManager.contactSettingsListeners.add(this.contactSettingsListener);
		ListenerManager.synchronizeContactsListeners.add(this.synchronizeContactsListener);
		ListenerManager.chatListener.add(this.chatListener);
	}

	private void removeListeners() {
		logger.debug("*** remove listeners");

		conversationListeners.remove(this.conversationListener);
		ListenerManager.contactListeners.remove(this.contactListener);
		ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
		ListenerManager.synchronizeContactsListeners.remove(this.synchronizeContactsListener);
		ListenerManager.chatListener.remove(this.chatListener);
	}

	private void updateList() {
		this.updateList(null, null, null);
	}

	private void updateList(final Integer scrollToPosition, final List<ConversationModel> changedPositions, final Runnable runAfterSetData) {
		this.updateList(scrollToPosition, changedPositions, runAfterSetData, false);
	}

	@SuppressLint("StaticFieldLeak")
	private void updateList(final Integer scrollToPosition, final List<ConversationModel> changedPositions, final Runnable runAfterSetData, boolean recreate) {
		//require
		if (!this.requiredInstances()) {
			logger.error("could not instantiate required objects");
			return;
		}

		logger.debug("*** update list [" + scrollToPosition + ", " + (changedPositions != null ? changedPositions.size() : "0") + "]");

		Thread updateListThread = new Thread(new Runnable() {
			@Override
			public void run() {
				List<ConversationModel> conversationModels;

				conversationModels = conversationService.getAll(false, new ConversationService.Filter() {
					@Override
					public boolean onlyUnread() {
						return false;
					}

					@Override
					public boolean noDistributionLists() {
						return false;
					}

					@Override
					public boolean noHiddenChats() {
						return preferenceService.isPrivateChatsHidden();
					}

					@Override
					public boolean noInvalid() {
						return false;
					}

					@Override
					public String filterQuery() {
						return filterQuery;
					}
				});

				RuntimeUtil.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						synchronized (messageListAdapterLock) {
							if (messageListAdapter == null || recreate) {
								messageListAdapter = new MessageListAdapter(
									MessageSectionFragment.this.activity,
									contactService,
									groupService,
									distributionListService,
									conversationService,
									mutedChatsListService,
									mentionOnlyChatsListService,
									hiddenChatsListService,
									conversationTagService,
									ringtoneService,
									highlightUid,
									MessageSectionFragment.this);

								recyclerView.setAdapter(messageListAdapter);
							}

							if (messageListAdapter != null) {
								messageListAdapter.setData(conversationModels, changedPositions);
							}

							if (recyclerView != null && scrollToPosition != null) {
								if (changedPositions != null && changedPositions.size() == 1) {
									ConversationModel changedModel = changedPositions.get(0);

									if (changedModel != null) {
										final List<ConversationModel> copyOfModels = new ArrayList<>(conversationModels);
										for (ConversationModel model : copyOfModels) {
											if (model.equals(changedModel)) {
												if (scrollToPosition > changedModel.getPosition()) {
													recyclerView.scrollToPosition(changedModel.getPosition());
												}
												break;
											}
										}
									}
								}
							}
						}

						if (runAfterSetData != null) {
							runAfterSetData.run();
						}
					}
				});
			}
		});

		if (messageListAdapter == null) {
			// hack: run synchronously when setting up the adapter for the first time to avoid showing an empty list
			updateListThread.run();
		} else {
			updateListThread.start();
		}
	}

	private void updateHiddenMenuVisibility() {
		if (isAdded() && toggleHiddenMenuItemRef != null && toggleHiddenMenuItemRef.get() != null) {
			if (hiddenChatsListService != null) {
				toggleHiddenMenuItemRef.get().setVisible(hiddenChatsListService.getSize() > 0 &&
						ConfigUtils.hasProtection(preferenceService));
				return;
			}
			toggleHiddenMenuItemRef.get().setVisible(false);
		}
	}

	private boolean isMultiPaneEnabled(Activity activity) {
		if (activity != null) {
			return ConfigUtils.isTabletLayout() && activity instanceof ComposeMessageActivity;
		}
		return false;
	}

	private void fireReceiverUpdate(final MessageReceiver receiver) {
		if(receiver instanceof GroupMessageReceiver) {
			ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
				@Override
				public void handle(GroupListener listener) {
					listener.onUpdate(((GroupMessageReceiver) receiver).getGroup());
				}
			});
		}
		else if(receiver instanceof ContactMessageReceiver) {
			ListenerManager.contactListeners.handle(new ListenerManager.HandleListener<ContactListener>() {
				@Override
				public void handle(ContactListener listener) {
					listener.onModified(((ContactMessageReceiver) receiver).getContact());
				}
			});
		}
		//ignore distribution lists
	}

	private void fireSecretReceiverUpdate() {
		//fire a update for every secret receiver (to update webclient data)
		for(ConversationModel c: Functional.filter(this.conversationService.getAll(false, new ConversationService.Filter() {
			@Override
			public boolean onlyUnread() {
				return false;
			}

			@Override
			public boolean noDistributionLists() {
				return false;
			}

			@Override
			public boolean noHiddenChats() {
				return false;
			}

			@Override
			public boolean noInvalid() {
				return false;
			}
		}), new IPredicateNonNull<ConversationModel>() {
			@Override
			public boolean apply(ConversationModel conversationModel) {
				return conversationModel != null && hiddenChatsListService.has(conversationModel.getReceiver().getUniqueIdString());
			}
		})) {
			if (c != null) {
				this.fireReceiverUpdate(c.getReceiver());
			}
		}
	}

	public void onLogoClicked() {
		if (this.recyclerView != null) {
			this.recyclerView.stopScroll();
			this.recyclerView.scrollToPosition(0);
		}
	}
}
