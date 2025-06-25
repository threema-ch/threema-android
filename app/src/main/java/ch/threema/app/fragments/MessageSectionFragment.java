/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.contactdetails.ContactDetailActivity;
import ch.threema.app.activities.DistributionListAddActivity;
import ch.threema.app.activities.RecipientListBaseActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.adapters.MessageListAdapter;
import ch.threema.app.adapters.MessageListAdapterItem;
import ch.threema.app.adapters.MessageListViewHolder;
import ch.threema.app.archive.ArchiveActivity;
import ch.threema.app.asynctasks.DeleteDistributionListAsyncTask;
import ch.threema.app.asynctasks.EmptyOrDeleteConversationsAsyncTask;
import ch.threema.app.backuprestore.BackupChatService;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.dialogs.CancelableGenericProgressDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.dialogs.loadingtimeout.LoadingWithTimeoutDialogXml;
import ch.threema.app.groupflows.GroupDisbandIntent;
import ch.threema.app.groupflows.GroupFlowResult;
import ch.threema.app.groupflows.GroupLeaveIntent;
import ch.threema.app.listeners.ChatListener;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.preference.SettingsActivity;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.MessageService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.HiddenChatUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.app.voip.activities.GroupCallActivity;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.CoroutinesExtensionKt;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupIdentity;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTag;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import kotlin.Unit;
import kotlinx.coroutines.Deferred;

import static android.view.MenuItem.SHOW_AS_ACTION_ALWAYS;
import static android.view.MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW;
import static android.view.MenuItem.SHOW_AS_ACTION_NEVER;
import static ch.threema.app.AppConstants.MAX_PW_LENGTH_BACKUP;
import static ch.threema.app.AppConstants.MIN_PW_LENGTH_BACKUP;
import static ch.threema.app.groupflows.GroupFlowResultKt.GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS;
import static ch.threema.app.managers.ListenerManager.conversationListeners;

/**
 * This is one of the tabs in the home screen. It shows the current conversations.
 */
public class MessageSectionFragment extends MainFragment
    implements
    PasswordEntryDialog.PasswordEntryDialogClickListener,
    GenericAlertDialog.DialogClickListener,
    CancelableGenericProgressDialog.ProgressDialogClickListener,
    MessageListAdapter.ItemClickListener,
    SelectorDialog.SelectorDialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("MessageSectionFragment");

    private static final int PERMISSION_REQUEST_SHARE_THREAD = 1;
    private static final int ID_RETURN_FROM_SECURITY_SETTINGS = 33211;
    private static final int TEMP_MESSAGES_FILE_DELETE_WAIT_TIME = 2 * 60 * 1000;

    private static final String DIALOG_TAG_PREPARING_MESSAGES = "progressMsgs";
    private static final String DIALOG_TAG_SHARE_CHAT = "shareChat";
    private static final String DIALOG_TAG_REALLY_HIDE_THREAD = "lockC";
    private static final String DIALOG_TAG_HIDE_THREAD_EXPLAIN = "hideEx";
    private static final String DIALOG_TAG_SELECT_DELETE_ACTION = "sel";
    private static final String DIALOG_TAG_REALLY_LEAVE_GROUP = "rlg";
    private static final String DIALOG_TAG_REALLY_DISSOLVE_GROUP = "reallyDissolveGroup";
    private static final String DIALOG_TAG_REALLY_DELETE_MY_GROUP = "rdmg";
    private static final String DIALOG_TAG_REALLY_DELETE_GROUP = "rdgcc";
    private static final String DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST = "rddl";
    private static final String DIALOG_TAG_REALLY_EMPTY_CHAT = "remc";
    private static final String DIALOG_TAG_REALLY_DELETE_CHAT = "rdec";

    private static final int ID_PRIVATE_TO_PUBLIC = 8111;

    private static final int TAG_EMPTY_CHAT = 1;
    private static final int TAG_DELETE_DISTRIBUTION_LIST = 2;
    private static final int TAG_LEAVE_GROUP = 3;
    private static final int TAG_DISSOLVE_GROUP = 4;
    private static final int TAG_DELETE_MY_GROUP = 5;
    private static final int TAG_DELETE_GROUP = 6;
    private static final int TAG_SET_PRIVATE = 7;
    private static final int TAG_UNSET_PRIVATE = 8;
    private static final int TAG_SHARE = 9;
    private static final int TAG_DELETE_LEFT_GROUP = 10;
    private static final int TAG_EDIT_GROUP = 11;
    private static final int TAG_MARK_READ = 12;
    private static final int TAG_MARK_UNREAD = 13;
    private static final int TAG_DELETE_CHAT = 14;
    private static final int TAG_ARCHIVE_CHAT = 15;

    private static final String BUNDLE_FILTER_QUERY = "filterQuery";
    private static String highlightUid;

    private ServiceManager serviceManager;
    private ConversationService conversationService;
    private ContactService contactService;
    private GroupService groupService;
    private GroupModelRepository groupModelRepository;
    private GroupFlowDispatcher groupFlowDispatcher;
    private GroupCallManager groupCallManager;
    private MessageService messageService;
    private DistributionListService distributionListService;
    private BackupChatService backupChatService;
    @Nullable
    private ConversationCategoryService conversationCategoryService;
    private ConversationTagService conversationTagService;
    private RingtoneService ringtoneService;
    private FileService fileService;
    private PreferenceService preferenceService;
    private LockAppService lockAppService;

    private Activity activity;
    private File tempMessagesFile;
    @Nullable
    private MessageListAdapter messageListAdapter;
    private EmptyRecyclerView recyclerView;
    private View loadingView;
    private SearchView searchView;
    private WeakReference<MenuItem> searchMenuItemRef, toggleHiddenMenuItemRef;
    private ResumePauseHandler resumePauseHandler;
    private int currentFullSyncs = 0;
    private String filterQuery;
    private int cornerRadius;
    private final Map<ConversationModel, MessageListAdapterItem> messageListAdapterItemCache = new HashMap<>();

    private @Nullable String myIdentity;

    private ArchiveSnackbar archiveSnackbar;

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
            if (messageListAdapter != null && recyclerView != null) {
                List<ConversationModel> changedPositions = Collections.singletonList(conversationModel);

                // If the first item of the recycler view is visible, then scroll up
                Integer scrollToPosition = null;
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManager
                    && ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition() == 0) {
                    // By passing a large integer we simulate a "moving up" change that triggers scrolling up
                    scrollToPosition = Integer.MAX_VALUE;
                }
                updateList(scrollToPosition, changedPositions, null);
            }
        }

        @Override
        public void onModified(final ConversationModel modifiedConversationModel, final @Nullable Integer oldPosition) {
            logger.debug("on modified conversation. old position = {}", oldPosition);
            if (messageListAdapter == null || recyclerView == null) {
                return;
            }
            synchronized (messageListAdapterItemCache) {
                messageListAdapterItemCache.remove(modifiedConversationModel);
            }

            // Scroll if position changed (to top)
            List<ConversationModel> changedPositions = new ArrayList<>();
            changedPositions.add(modifiedConversationModel);
            updateList(oldPosition, changedPositions, null);
        }

        @Override
        public void onRemoved(final ConversationModel conversationModel) {
            if (messageListAdapter != null) {
                updateList();
            }
        }

        @Override
        public void onModifiedAll() {
            logger.debug("on modified all");
            if (messageListAdapter != null && recyclerView != null) {
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

    private final GroupListener groupListener = new GroupListener() {
        @Override
        public void onNewMember(@NonNull GroupIdentity groupIdentity, String identityNew) {
            // If this user is added to an existing group
            if (groupService != null && myIdentity != null && myIdentity.equals(identityNew)) {
                GroupModel groupModel = groupService.getByGroupIdentity(groupIdentity);
                if (groupModel != null) {
                    fireReceiverUpdate(groupService.createReceiver(groupModel));
                }
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
        public void onModified(final @NonNull String identity) {
            this.handleChange();
        }

        @Override
        public void onAvatarChanged(final @NonNull String identity) {
            this.handleChange();
        }

        public void handleChange() {
            if (currentFullSyncs <= 0) {
                refreshListEvent();
            }
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
            this.groupCallManager,
            this.conversationService,
            this.distributionListService,
            this.fileService,
            this.backupChatService,
            this.conversationCategoryService,
            this.ringtoneService,
            this.preferenceService,
            this.lockAppService
        );
    }

    protected void instantiate() {
        this.serviceManager = ThreemaApplication.getServiceManager();

        if (this.serviceManager != null) {
            try {
                this.contactService = this.serviceManager.getContactService();
                this.groupService = this.serviceManager.getGroupService();
                this.groupCallManager = this.serviceManager.getGroupCallManager();
                this.groupModelRepository = this.serviceManager.getModelRepositories().getGroups();
                this.groupFlowDispatcher = this.serviceManager.getGroupFlowDispatcher();
                this.messageService = this.serviceManager.getMessageService();
                this.conversationService = this.serviceManager.getConversationService();
                this.distributionListService = this.serviceManager.getDistributionListService();
                this.fileService = this.serviceManager.getFileService();
                this.backupChatService = this.serviceManager.getBackupChatService();
                this.conversationCategoryService = this.serviceManager.getConversationCategoryService();
                this.ringtoneService = this.serviceManager.getRingtoneService();
                this.preferenceService = this.serviceManager.getPreferenceService();
                this.conversationTagService = this.serviceManager.getConversationTagService();
                this.lockAppService = this.serviceManager.getLockAppService();
                myIdentity = serviceManager.getUserService().getIdentity();
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

        logger.info("onAttach");

        this.activity = activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logger.info("onCreate");

        setRetainInstance(true);
        setHasOptionsMenu(true);

        setupListeners();

        this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this.activity);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        logger.info("onViewCreated");

        try {
            //show loading first
            ViewUtil.show(loadingView, true);

            updateList(null, null, new Runnable() {
                @Override
                public void run() {
                    //hide loading
                    ViewUtil.show(loadingView, false);
                }
            }, true);
        } catch (Exception e) {
            LogUtil.exception(e, getActivity());
        }

        if (savedInstanceState != null && TestUtil.isEmptyOrNull(filterQuery)) {
            filterQuery = savedInstanceState.getString(BUNDLE_FILTER_QUERY);
        }

        if (messageListAdapter != null) {
            messageListAdapter.setFilterQuery(filterQuery);
        }
    }

    @Override
    public void onDestroyView() {
        logger.info("onDestroyView");

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

                    if (activity != null && this.isAdded()) {
                        searchMenuItem = menu.findItem(R.id.menu_search_messages);
                        this.searchView = (SearchView) searchMenuItem.getActionView();

                        if (this.searchView != null) {
                            if (!TestUtil.isEmptyOrNull(filterQuery)) {
                                // restore filter
                                MenuItemCompat.expandActionView(searchMenuItem);
                                searchView.setQuery(filterQuery, false);
                                searchView.clearFocus();
                            }
                            this.searchView.setQueryHint(getString(R.string.hint_filter_list));
                            this.searchView.setOnQueryTextListener(queryTextListener);
                        }
                    }
                }

                this.searchMenuItemRef = new WeakReference<>(searchMenuItem);

                toggleHiddenMenuItemRef = new WeakReference<>(menu.findItem(R.id.menu_toggle_private_chats));
                if (toggleHiddenMenuItemRef.get() != null) {
                    if (isAdded()) {
                        toggleHiddenMenuItemRef.get().setOnMenuItemClickListener(item -> {
                            if (preferenceService.isPrivateChatsHidden()) {
                                logger.info("Requesting to show private chats");
                                requestUnhideChats();
                            } else {
                                logger.info("Requesting to hide private chats");
                                preferenceService.setPrivateChatsHidden(true);
                                updateList(null, null, new Thread(this::firePrivateReceiverUpdate));
                            }
                            return true;
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
            if (messageListAdapter != null) {
                messageListAdapter.setFilterQuery(query);
                updateList(0, null, null);
            }
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            return true;
        }
    };

    private void showConversation(ConversationModel conversationModel, View v) {
        conversationTagService.removeTagAndNotify(conversationModel, ConversationTag.MARKED_AS_UNREAD, TriggerSource.LOCAL);
        conversationModel.setUnreadCount(0);

        // Close keyboard if search view is expanded
        if (searchView != null && !searchView.isIconified()) {
            EditTextUtil.hideSoftKeyboard(searchView);
        }

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
            activity.startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
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
                    updateList(0, null, new Thread(() -> firePrivateReceiverUpdate()));
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
                        removePrivateMark(selectedConversation);
                    }
                }
                // fallthrough
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void removePrivateMark(@NonNull ConversationModel conversationModel) {
        MessageReceiver<?> receiver = conversationModel.messageReceiver;
        if (receiver == null) {
            logger.warn("Cannot remove private mark as the receiver is null");
            return;
        }

        if (conversationCategoryService == null) {
            logger.error("Conversation category service is null: cannot remove private mark");
            return;
        }

        if (!conversationCategoryService.removePrivateMark(receiver)) {
            logger.warn("Private mark couldn't be removed from conversation");
            return;
        }

        if (getView() != null) {
            Snackbar.make(getView(), R.string.chat_visible, Snackbar.LENGTH_SHORT).show();
        }

        this.fireReceiverUpdate(receiver);
        if (messageListAdapter != null) {
            messageListAdapter.clearSelections();
        }
    }

    private void markAsPrivate(ConversationModel conversationModel) {
        MessageReceiver<?> receiver = conversationModel.messageReceiver;
        if (receiver == null) {
            logger.warn("Cannot mark chat as private as the receiver is null");
            return;
        }

        if (conversationCategoryService == null) {
            logger.error("Conversation category service is null: cannot mark chat as private");
            return;
        }

        if (conversationCategoryService.isPrivateChat(receiver.getUniqueIdString())) {
            if (ConfigUtils.hasProtection(preferenceService)) {
                // persist selection
                selectedConversation = conversationModel;
                HiddenChatUtil.launchLockCheckDialog(null, this, preferenceService, ID_PRIVATE_TO_PUBLIC);
            } else {
                removePrivateMark(conversationModel);
            }
        } else {
            if (ConfigUtils.hasProtection(preferenceService)) {
                logger.info("Showing dialog for confirming making a chat private");
                GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.hide_chat,
                    R.string.really_hide_chat_message,
                    R.string.ok,
                    R.string.cancel);

                dialog.setTargetFragment(this, 0);
                dialog.setData(conversationModel);
                dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_HIDE_THREAD);
            } else {
                logger.info("Showing dialog to explain private chats");
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
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                if (resumePauseHandler != null) {
                    resumePauseHandler.onPause();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                if (conversationModel == null) {
                    return false;
                }
                MessageReceiver<?> messageReceiver = conversationModel.messageReceiver;
                if (messageReceiver == null) {
                    logger.warn("The chat cannot be marked as private as the receiver is null");
                    return false;
                }

                if (conversationCategoryService == null) {
                    logger.error("Conversation category service is null in background task: cannot mark chat as private");
                    return false;
                }

                if (!conversationCategoryService.markAsPrivate(messageReceiver)) {
                    logger.warn("Conversation hasn't been marked as private");
                    return false;
                }

                fireReceiverUpdate(conversationModel.messageReceiver);
                return true;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    if (messageListAdapter == null) {
                        return;
                    }
                    messageListAdapter.clearSelections();
                    if (getView() != null) {
                        Snackbar.make(getView(), R.string.chat_hidden, Snackbar.LENGTH_SHORT).show();
                    }
                    if (resumePauseHandler != null) {
                        resumePauseHandler.onResume();
                    }
                    updateHiddenMenuVisibility();
                    if (ConfigUtils.hasProtection(preferenceService) && preferenceService.isPrivateChatsHidden()) {
                        updateList(null, null, new Thread(() -> firePrivateReceiverUpdate()));
                    }
                } else {
                    Toast.makeText(ThreemaApplication.getAppContext(), R.string.an_error_occurred, Toast.LENGTH_SHORT).show();
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
                tempMessagesFile = FileUtil.getUniqueFile(fileService.getTempPath().getPath(), "threema-chat.zip");
                FileUtil.deleteFileOrWarn(tempMessagesFile, "tempMessagesFile", logger);

                if (backupChatService.backupChatToZip(conversationModel, tempMessagesFile, password, includeMedia)) {

                    if (tempMessagesFile != null && tempMessagesFile.exists() && tempMessagesFile.length() > 0) {
                        final Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType(MimeUtil.MIME_TYPE_ZIP);
                        intent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.share_subject, getString(R.string.app_name)));
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
            R.string.backup_data_media,
            PasswordEntryDialog.ForgotHintType.NONE);
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

            ViewExtensionsKt.applyDeviceInsetsAsPadding(
                recyclerView,
                new InsetSides(false, true, isMultiPaneEnabled(activity), true),
                new SpacingValues(null, null, R.dimen.grid_unit_x10, null)
            );

            this.cornerRadius = getResources().getDimensionPixelSize(R.dimen.messagelist_card_corner_radius);

            final ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT) {
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
                        return makeMovementFlags(0, 0);
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
                    if (messageListAdapter == null) {
                        return;
                    }

                    // swipe has ended successfully

                    // required to clear swipe layout
                    messageListAdapter.notifyDataSetChanged();

                    final MessageListViewHolder holder = (MessageListViewHolder) viewHolder;
                    MessageListAdapterItem messageListAdapterItem = holder.getMessageListAdapterItem();
                    ConversationModel conversationModel = messageListAdapterItem != null ? messageListAdapterItem.getConversationModel() : null;
                    if (conversationModel == null) {
                        logger.error("Conversation model is null");
                        return;
                    }
                    final int oldPosition = conversationModel.getPosition();

                    if (direction == ItemTouchHelper.RIGHT) {
                        logger.info("Chat swiped right for pinning");
                        conversationTagService.toggle(conversationModel, ConversationTag.PINNED, true, TriggerSource.LOCAL);
                        conversationModel.isPinTagged = !conversationModel.isPinTagged;

                        ArrayList<ConversationModel> conversationModels = new ArrayList<>();
                        conversationModels.add(conversationModel);

                        updateList(
                            null,
                            conversationModels,
                            () -> conversationListeners.handle((ConversationListener listener) -> listener.onModified(conversationModel, oldPosition))
                        );
                    } else if (direction == ItemTouchHelper.LEFT) {
                        logger.info("Chat swiped right for archiving");
                        archiveChat(conversationModel);
                    }
                }

                @Override
                public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                    View itemView = viewHolder.itemView;

                    if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                        Paint paint = new Paint();

                        if (dX > 0) {
                            MessageListViewHolder holder = (MessageListViewHolder) viewHolder;

                            MessageListAdapterItem messageListAdapterItem = holder.getMessageListAdapterItem();
                            ConversationModel conversationModel = messageListAdapterItem != null ? messageListAdapterItem.getConversationModel() : null;

                            VectorDrawableCompat icon = conversationTagService.isTaggedWith(conversationModel, ConversationTag.PINNED)
                                ? unpinIconDrawable
                                : pinIconDrawable;
                            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());

                            String label = conversationTagService.isTaggedWith(conversationModel, ConversationTag.PINNED) ? getString(R.string.unpin) : getString(R.string.pin);

                            paint.setColor(getResources().getColor(R.color.messagelist_pinned_color));
                            canvas.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX + cornerRadius, (float) itemView.getBottom(), paint);
                            canvas.save();
                            canvas.translate(
                                (float) itemView.getLeft() + getResources().getDimension(R.dimen.swipe_icon_inset),
                                (float) itemView.getTop() + ((float) itemView.getBottom() - (float) itemView.getTop() - icon.getIntrinsicHeight()) / 2);
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
                                (float) itemView.getTop() + ((float) itemView.getBottom() - (float) itemView.getTop() - icon.getIntrinsicHeight()) / 2);
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
            this.floatingButtonView.setOnClickListener(this::onFABClicked);

            ViewExtensionsKt.applyDeviceInsetsAsMargin(
                floatingButtonView,
                new InsetSides(false, true, isMultiPaneEnabled(activity), true),
                SpacingValues.all(R.dimen.floating_button_margin)
            );

            // add text view if contact list is empty
            EmptyView emptyView = new EmptyView(activity);
            emptyView.setup(R.string.no_recent_conversations);
            ((ViewGroup) recyclerView.getParent()).addView(emptyView);
            recyclerView.setNumHeadersAndFooters(-1);
            recyclerView.setEmptyView(emptyView);
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);

                    if (linearLayoutManager.findFirstVisibleItemPosition() == 0) {
                        floatingButtonView.extend();
                    } else {
                        floatingButtonView.shrink();
                    }
                }
            });
/* TODO(ANDR-2505) this solution does not currently work on Chromebooks.
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && getActivity() != null && getActivity().isInMultiWindowMode()) {
				recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
					private final int TOUCH_SAFE_AREA_PX = 5;

					// ignore touches at the very left and right edge of the screen to prevent interference with UI gestures
					@Override
					public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
						int width = getResources().getDisplayMetrics().widthPixels;
						int touchX = (int) e.getRawX();

						return touchX < TOUCH_SAFE_AREA_PX || touchX > width - TOUCH_SAFE_AREA_PX;
					}

					@Override
					public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
					}

					@Override
					public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
					}
				});
			}
*/
            //instantiate fragment
            //
            if (!this.requiredInstances()) {
                logger.error("could not instantiate required objects");
            }
        }
        return fragmentView;
    }

    private void archiveChat(ConversationModel conversationModel) {
        conversationService.archive(conversationModel, TriggerSource.LOCAL);
        archiveSnackbar = new ArchiveSnackbar(archiveSnackbar, conversationModel);
    }

    private void onFABClicked(View v) {
        logger.info("FAB clicked, opening new chat screen");
        // stop list fling to avoid crashes due to concurrent access to conversation data
        recyclerView.stopScroll();
        Intent intent = new Intent(getContext(), RecipientListBaseActivity.class);
        intent.putExtra(AppConstants.INTENT_DATA_HIDE_RECENTS, true);
        intent.putExtra(RecipientListBaseActivity.INTENT_DATA_MULTISELECT, false);
        intent.putExtra(RecipientListBaseActivity.INTENT_DATA_MULTISELECT_FOR_COMPOSE, true);
        getActivity().startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
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
        logger.info("Conversation clicked");
        new Thread(() -> showConversation(model, view)).start();
    }

    @Override
    public void onAvatarClick(View view, int position, ConversationModel model) {
        Intent intent = null;
        if (model.isContactConversation()) {
            logger.info("Contact avatar clicked");
            intent = new Intent(getActivity(), ContactDetailActivity.class);
            intent.putExtra(AppConstants.INTENT_DATA_CONTACT, model.getContact().getIdentity());
        } else if (model.isGroupConversation()) {
            logger.info("Group avatar clicked");
            openGroupDetails(model);
        } else if (model.isDistributionListConversation()) {
            logger.info("Distribution list avatar clicked");
            intent = new Intent(getActivity(), DistributionListAddActivity.class);
            intent.putExtra(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID, model.getDistributionList().getId());
        }
        if (intent != null) {
            activity.startActivity(intent);
        }
    }

    @Override
    public void onFooterClick(View view) {
        logger.info("Footer clicked, showing archive");
        Intent intent = new Intent(getActivity(), ArchiveActivity.class);
        intent.putExtra(AppConstants.INTENT_DATA_ARCHIVE_FILTER, filterQuery);
        getActivity().startActivity(intent);
    }

    @Override
    public void onJoinGroupCallClick(ConversationModel conversationModel) {
        logger.info("Join group call clicked");
        GroupModel group = conversationModel.getGroup();
        if (group != null) {
            startActivity(GroupCallActivity.getJoinCallIntent(requireActivity(), group.getId()));
        }
    }

    private void openGroupDetails(ConversationModel model) {
        GroupModel groupModel = model.getGroup();
        if (groupModel == null) {
            return;
        }
        Intent intent = groupService.getGroupDetailIntent(groupModel, activity);
        activity.startActivity(intent);
    }

    @Override
    public boolean onItemLongClick(View view, int position, ConversationModel conversationModel) {
        if (!isMultiPaneEnabled(activity) && messageListAdapter != null) {
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
        logger.info("*** onPause");

        if (this.resumePauseHandler != null) {
            this.resumePauseHandler.onPause();
        }
    }

    @Override
    public void onResume() {
        logger.info("*** onResume");

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

        if (messageListAdapter != null) {
            messageListAdapter.updateDateView();
        }

        super.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        logger.info("saveInstance");

        if (!TestUtil.isEmptyOrNull(filterQuery)) {
            outState.putString(BUNDLE_FILTER_QUERY, filterQuery);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onYes(String tag, String text, boolean isChecked, Object data) {
        logger.info("Chat sharing confirmed");
        shareChat((ConversationModel) data, text, isChecked);
    }

    private void showSelector() {
        ArrayList<SelectorDialogItem> labels = new ArrayList<>();
        ArrayList<Integer> tags = new ArrayList<>();

        if (messageListAdapter == null || messageListAdapter.getCheckedItemCount() != 1) {
            return;
        }

        ConversationModel conversationModel = messageListAdapter.getCheckedItems().get(0);
        if (conversationModel == null) {
            return;
        }

        MessageReceiver receiver;
        try {
            receiver = conversationModel.messageReceiver;
        } catch (Exception e) {
            logger.error("Could not get receiver of conversation model", e);
            return;
        }

        if (receiver == null) {
            logger.warn("No receiver in conversation model for showing selector");
            return;
        }

        if (conversationCategoryService == null) {
            logger.error("Conversation category service is null: cannot show selector");
            return;
        }

        boolean isPrivate = conversationCategoryService.isPrivateChat(receiver.getUniqueIdString());

        if (conversationModel.hasUnreadMessage() || conversationTagService.isTaggedWith(conversationModel, ConversationTag.MARKED_AS_UNREAD)) {
            labels.add(new SelectorDialogItem(getString(R.string.mark_read), R.drawable.ic_outline_visibility));
            tags.add(TAG_MARK_READ);
        } else {
            labels.add(new SelectorDialogItem(getString(R.string.mark_unread), R.drawable.ic_outline_visibility_off));
            tags.add(TAG_MARK_UNREAD);
        }

        if (isPrivate) {
            labels.add(new SelectorDialogItem(getString(R.string.unset_private), R.drawable.ic_outline_shield_24));
            tags.add(TAG_UNSET_PRIVATE);
        } else {
            labels.add(new SelectorDialogItem(getString(R.string.set_private), R.drawable.ic_privacy_outline));
            tags.add(TAG_SET_PRIVATE);
        }

        if (!isPrivate && !AppRestrictionUtil.isExportDisabled(getActivity())) {
            labels.add(new SelectorDialogItem(getString(R.string.share_chat), R.drawable.ic_share_outline));
            tags.add(TAG_SHARE);
        }

        labels.add(new SelectorDialogItem(getString(R.string.archive_chat), R.drawable.ic_archive_outline));
        tags.add(TAG_ARCHIVE_CHAT);

        if (conversationModel.messageCount > 0) {
            labels.add(new SelectorDialogItem(getString(R.string.empty_chat_title), R.drawable.ic_outline_delete_sweep));
            tags.add(TAG_EMPTY_CHAT);
        }
        if (conversationModel.isContactConversation()) {
            labels.add(new SelectorDialogItem(getString(R.string.delete_chat_title), R.drawable.ic_delete_outline));
            tags.add(TAG_DELETE_CHAT);
        }

        if (conversationModel.isDistributionListConversation()) {
            // distribution lists
            labels.add(new SelectorDialogItem(getString(R.string.really_delete_distribution_list), R.drawable.ic_delete_outline));
            tags.add(TAG_DELETE_DISTRIBUTION_LIST);
        } else if (conversationModel.isGroupConversation()) {
            // group chats
            ch.threema.data.models.GroupModel groupModel = conversationModel.getGroupModel();
            if (groupModel == null) {
                logger.error("Cannot access the group from the conversation model");
                return;
            }
            GroupModelData data = groupModel.getData().getValue();
            if (data == null) {
                logger.warn("Group model data is null");
                return;
            }
            boolean isCreator = data.groupIdentity.getCreatorIdentity().equals(myIdentity);
            boolean isMember = data.isMember();
            boolean hasOtherMembers = !data.otherMembers.isEmpty();
            // Check also if the user is a group member, because orphaned groups should not be
            // editable.
            if (isCreator && isMember) {
                labels.add(new SelectorDialogItem(getString(R.string.group_edit_title), R.drawable.ic_pencil_outline));
                tags.add(TAG_EDIT_GROUP);
            }
            // Members (except the creator) can leave the group
            if (!isCreator && isMember) {
                labels.add(new SelectorDialogItem(getString(R.string.action_leave_group), R.drawable.ic_outline_directions_run));
                tags.add(TAG_LEAVE_GROUP);
            }
            if (isCreator && isMember && hasOtherMembers) {
                labels.add(new SelectorDialogItem(getString(R.string.action_dissolve_group), R.drawable.ic_outline_directions_run));
                tags.add(TAG_DISSOLVE_GROUP);
            }
            labels.add(new SelectorDialogItem(getString(R.string.action_delete_group), R.drawable.ic_delete_outline));
            if (isMember) {
                if (isCreator) {
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

        if (messageListAdapter != null) {
            messageListAdapter.clearSelections();
        }

        final ConversationModel conversationModel = (ConversationModel) data;

        switch (which) {
            case TAG_ARCHIVE_CHAT:
                logger.info("Archive chat clicked");
                archiveChat(conversationModel);
                break;
            case TAG_EMPTY_CHAT:
                logger.info("Empty chat clicked, showing dialog");
                dialog = GenericAlertDialog.newInstance(
                    R.string.empty_chat_title,
                    R.string.empty_chat_confirm,
                    R.string.ok,
                    R.string.cancel);
                dialog.setData(conversationModel);
                dialog.setTargetFragment(this, 0);
                dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_EMPTY_CHAT);
                break;
            case TAG_DELETE_CHAT:
                logger.info("Delete chat clicked, showing dialog");
                dialog = GenericAlertDialog.newInstance(
                    R.string.delete_chat_title,
                    R.string.delete_chat_confirm,
                    R.string.ok,
                    R.string.cancel);
                dialog.setData(conversationModel);
                dialog.setTargetFragment(this, 0);
                dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_CHAT);
                break;
            case TAG_DELETE_DISTRIBUTION_LIST:
                logger.info("Delete distribution list clicked, showing dialog");
                dialog = GenericAlertDialog.newInstance(
                    R.string.really_delete_distribution_list,
                    R.string.really_delete_distribution_list_message,
                    R.string.ok,
                    R.string.cancel);
                dialog.setTargetFragment(this, 0);
                dialog.setData(conversationModel.getDistributionList());
                dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST);
                break;
            case TAG_EDIT_GROUP:
                logger.info("Edit group clicked, opening details screen");
                openGroupDetails(conversationModel);
                break;
            case TAG_LEAVE_GROUP:
                logger.info("Leave group clicked, showing dialog");
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_leave_group,
                    R.string.really_leave_group_message,
                    R.string.ok,
                    R.string.cancel);
                dialog.setTargetFragment(this, 0);
                dialog.setData(conversationModel.getGroup());
                dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_LEAVE_GROUP);
                break;
            case TAG_DISSOLVE_GROUP:
                logger.info("Dissolve group clicked, showing dialog");
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_dissolve_group,
                    R.string.really_dissolve_group,
                    R.string.ok,
                    R.string.cancel
                );
                dialog.setTargetFragment(this, 0);
                dialog.setData(conversationModel.getGroup());
                dialog.show(getParentFragmentManager(), DIALOG_TAG_REALLY_DISSOLVE_GROUP);
                break;
            case TAG_DELETE_MY_GROUP:
                logger.info("Delete my group clicked");
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_dissolve_and_delete_group,
                    R.string.delete_my_group_message,
                    R.string.ok,
                    R.string.cancel
                );
                dialog.setTargetFragment(this, 0);
                dialog.setData(conversationModel.getGroup());
                dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_MY_GROUP);
                break;
            case TAG_DELETE_GROUP:
                logger.info("Delete group clicked");
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_delete_group,
                    R.string.delete_group_message,
                    R.string.ok,
                    R.string.cancel
                );
                dialog.setTargetFragment(this, 0);
                dialog.setData(conversationModel.getGroup());
                dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_GROUP);
                break;
            case TAG_DELETE_LEFT_GROUP:
                logger.info("Leave group clicked");
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_delete_group,
                    R.string.delete_left_group_message,
                    R.string.ok,
                    R.string.cancel);
                dialog.setTargetFragment(this, 0);
                dialog.setData(conversationModel.getGroup());
                dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE_GROUP);
                break;
            case TAG_SET_PRIVATE:
            case TAG_UNSET_PRIVATE:
                logger.info("(un)private clicked");
                markAsPrivate(conversationModel);
                break;
            case TAG_SHARE:
                logger.info("Share clicked");
                if (ConfigUtils.requestWriteStoragePermissions(activity, this, PERMISSION_REQUEST_SHARE_THREAD)) {
                    prepareShareChat(conversationModel);
                }
                break;
            case TAG_MARK_READ:
                logger.info("Mark read clicked");
                conversationTagService.removeTagAndNotify(conversationModel, ConversationTag.MARKED_AS_UNREAD, TriggerSource.LOCAL);
                conversationModel.isUnreadTagged = false;
                conversationModel.setUnreadCount(0);
                new Thread(() -> messageService.markConversationAsRead(
                    conversationModel.messageReceiver,
                    serviceManager.getNotificationService())
                ).start();
                break;
            case TAG_MARK_UNREAD:
                logger.info("Mark unread clicked");
                conversationTagService.addTagAndNotify(conversationModel, ConversationTag.MARKED_AS_UNREAD, TriggerSource.LOCAL);
                conversationModel.isUnreadTagged = true;
                break;
        }
    }

    @Override
    public void onCancel(String tag) {
        if (messageListAdapter != null) {
            messageListAdapter.clearSelections();
        }
    }

    @Override
    public void onNo(String tag) {
        if (messageListAdapter != null && DIALOG_TAG_SELECT_DELETE_ACTION.equals(tag)) {
            messageListAdapter.clearSelections();
        }
    }

    @Override
    public void onYes(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_REALLY_HIDE_THREAD:
                logger.info("Make chat private confirmed");
                reallyHideChat((ConversationModel) data);
                break;
            case DIALOG_TAG_HIDE_THREAD_EXPLAIN:
                selectedConversation = (ConversationModel) data;
                Intent intent = new Intent(activity, SettingsActivity.class);
                intent.putExtra(SettingsActivity.EXTRA_SHOW_SECURITY_FRAGMENT, true);
                startActivityForResult(intent, ID_RETURN_FROM_SECURITY_SETTINGS);
                break;
            case DIALOG_TAG_REALLY_LEAVE_GROUP:
                logger.info("Leave group confirmed");
                leaveGroup(GroupLeaveIntent.LEAVE, getNewGroupModel((GroupModel) data));
                break;
            case DIALOG_TAG_REALLY_DISSOLVE_GROUP:
                logger.info("Dissolve group confirmed");
                disbandGroup(GroupDisbandIntent.DISBAND, getNewGroupModel((GroupModel) data));
                break;
            case DIALOG_TAG_REALLY_DELETE_MY_GROUP:
            case DIALOG_TAG_REALLY_DELETE_GROUP:
                logger.info("Delete group confirmed");
                removeGroup(getNewGroupModel((GroupModel) data));
                break;
            case DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST:
                logger.info("Deletion of distribution list confirmed");
                new DeleteDistributionListAsyncTask((DistributionListModel) data, distributionListService, this, null).execute();
                break;
            case DIALOG_TAG_REALLY_EMPTY_CHAT:
            case DIALOG_TAG_REALLY_DELETE_CHAT:
                if (myIdentity == null) {
                    logger.error("Cannot empty or remove chat when identity is null");
                    return;
                }

                final ConversationModel conversationModel = (ConversationModel) data;

                final EmptyOrDeleteConversationsAsyncTask.Mode mode = tag.equals(DIALOG_TAG_REALLY_DELETE_CHAT)
                    ? EmptyOrDeleteConversationsAsyncTask.Mode.DELETE
                    : EmptyOrDeleteConversationsAsyncTask.Mode.EMPTY;
                MessageReceiver<?> receiver = conversationModel.messageReceiver;
                if (receiver != null) {
                    logger.info("{} chat with receiver {} (type={}).", mode, receiver.getUniqueIdString(), receiver.getType());
                } else {
                    logger.warn("Cannot {} chat, receiver is null", mode);
                }
                new EmptyOrDeleteConversationsAsyncTask(
                    mode,
                    new MessageReceiver[]{receiver},
                    conversationService,
                    distributionListService,
                    groupModelRepository,
                    groupFlowDispatcher,
                    myIdentity,
                    getFragmentManager(),
                    null,
                    null
                ).execute();
                break;
            default:
                break;
        }
    }

    private void leaveGroup(
        @NonNull GroupLeaveIntent intent,
        @Nullable ch.threema.data.models.GroupModel groupModel
    ) {
        if (groupModel == null) {
            logger.error("Cannot leave group: group model is null");
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_leaving_group_internal)
                .show(getParentFragmentManager());
            return;
        }

        final @NonNull GroupFlowDispatcher groupFlowDispatcher;
        try {
            groupFlowDispatcher = serviceManager.getGroupFlowDispatcher();
        } catch (ThreemaException exception) {
            logger.error("Failed to leave group", exception);
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_leaving_group_internal)
                .show(getParentFragmentManager());
            return;
        }

        final @NonNull LoadingWithTimeoutDialogXml loadingDialog = LoadingWithTimeoutDialogXml.newInstance(
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            R.string.leaving_group
        );
        loadingDialog.show(getParentFragmentManager());

        Deferred<GroupFlowResult> leaveGroupFlowResultDeferred = groupFlowDispatcher
            .runLeaveGroupFlow(intent, groupModel);

        CoroutinesExtensionKt.onCompleted(
            leaveGroupFlowResultDeferred,
            exception -> {
                logger.error("leave-group-flow was completed exceptionally", exception);
                onLeaveGroupFailed(GroupFlowResult.Failure.Other.INSTANCE, loadingDialog);
                return Unit.INSTANCE;
            },
            result -> {
                if (result instanceof GroupFlowResult.Success) {
                    RuntimeUtil.runOnUiThread(loadingDialog::dismiss);
                } else if (result instanceof GroupFlowResult.Failure) {
                    onLeaveGroupFailed((GroupFlowResult.Failure) result, loadingDialog);
                }
                return Unit.INSTANCE;
            }
        );
    }

    @AnyThread
    private void onLeaveGroupFailed(
        @NonNull GroupFlowResult.Failure failureResult,
        @NonNull LoadingWithTimeoutDialogXml loadingDialog
    ) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            final @StringRes int errorMessageRes;
            if (failureResult instanceof GroupFlowResult.Failure.Network) {
                errorMessageRes = R.string.error_leaving_group_network;
            } else {
                errorMessageRes = R.string.error_leaving_group_internal;
            }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getParentFragmentManager());
        });
    }

    private void disbandGroup(
        @NonNull GroupDisbandIntent intent,
        @Nullable ch.threema.data.models.GroupModel groupModel
    ) {
        if (groupModel == null) {
            logger.error("Cannot disband group: group model is null");
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_disbanding_group_internal)
                .show(getParentFragmentManager());
            return;
        }

        final @NonNull GroupFlowDispatcher groupFlowDispatcher;
        try {
            groupFlowDispatcher = serviceManager.getGroupFlowDispatcher();
        } catch (ThreemaException exception) {
            logger.error("Failed to disband group", exception);
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_disbanding_group_internal)
                .show(getParentFragmentManager());
            return;
        }

        Deferred<GroupFlowResult> disbandGroupFlowResultDeferred = groupFlowDispatcher
            .runDisbandGroupFlow(intent, groupModel);

        final @NonNull LoadingWithTimeoutDialogXml loadingDialog = LoadingWithTimeoutDialogXml.newInstance(
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            R.string.disbanding_group
        );
        loadingDialog.show(getParentFragmentManager());

        CoroutinesExtensionKt.onCompleted(
            disbandGroupFlowResultDeferred,
            exception -> {
                logger.error("disband-group-flow was completed exceptionally", exception);
                onDisbandGroupFailed(GroupFlowResult.Failure.Other.INSTANCE, loadingDialog);
                return Unit.INSTANCE;
            },
            result -> {
                if (result instanceof GroupFlowResult.Success) {
                    RuntimeUtil.runOnUiThread(loadingDialog::dismiss);
                } else if (result instanceof GroupFlowResult.Failure) {
                    onDisbandGroupFailed((GroupFlowResult.Failure) result, loadingDialog);
                }
                return Unit.INSTANCE;
            }
        );
    }

    @AnyThread
    private void onDisbandGroupFailed(
        @NonNull GroupFlowResult.Failure failureResult,
        @NonNull LoadingWithTimeoutDialogXml loadingDialog
    ) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            final @StringRes int errorMessageRes;
            if (failureResult instanceof GroupFlowResult.Failure.Network) {
                errorMessageRes = R.string.error_disbanding_group_network;
            } else {
                errorMessageRes = R.string.error_disbanding_group_internal;
            }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getParentFragmentManager());
        });
    }

    private void removeGroup(@Nullable ch.threema.data.models.GroupModel groupModel) {
        if (groupModel == null) {
            // Group already removed
            return;
        }

        GroupModelData data = groupModel.getData().getValue();
        if (data == null) {
            // Group already removed
            return;
        }

        if (data.isMember()) {
            // Disband or leave if the user is still part of the group.
            if (data.groupIdentity.getCreatorIdentity().equals(myIdentity)) {
                disbandGroup(GroupDisbandIntent.DISBAND_AND_REMOVE, groupModel);
            } else {
                leaveGroup(GroupLeaveIntent.LEAVE_AND_REMOVE, groupModel);
            }
        } else {
            // Just remove the group
            runGroupRemoveFlow(groupModel);
        }
    }

    /**
     * Note that this must only be run for groups that are already left or disbanded.
     */
    private void runGroupRemoveFlow(
        @NonNull ch.threema.data.models.GroupModel groupModel
    ) {

        final @NonNull GroupFlowDispatcher groupFlowDispatcher;
        try {
            groupFlowDispatcher = serviceManager.getGroupFlowDispatcher();
        } catch (ThreemaException exception) {
            logger.error("Failed to remove group", exception);
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_removing_group_internal)
                .show(getParentFragmentManager());
            return;
        }

        final @NonNull LoadingWithTimeoutDialogXml loadingDialog = LoadingWithTimeoutDialogXml.newInstance(
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            R.string.removing_group
        );
        loadingDialog.show(getParentFragmentManager());

        Deferred<GroupFlowResult> removeGroupFlowResultDeferred = groupFlowDispatcher
            .runRemoveGroupFlow(groupModel);

        CoroutinesExtensionKt.onCompleted(
            removeGroupFlowResultDeferred,
            exception -> {
                logger.error("remove-group-flow was completed exceptionally", exception);
                onRemoveGroupFailed(GroupFlowResult.Failure.Other.INSTANCE, loadingDialog);
                return Unit.INSTANCE;
            },
            result -> {
                if (result instanceof GroupFlowResult.Success) {
                    RuntimeUtil.runOnUiThread(loadingDialog::dismiss);
                } else if (result instanceof GroupFlowResult.Failure) {
                    onRemoveGroupFailed((GroupFlowResult.Failure) result, loadingDialog);
                }
                return Unit.INSTANCE;
            }
        );
    }

    @AnyThread
    private void onRemoveGroupFailed(
        @NonNull GroupFlowResult.Failure failureResult,
        @NonNull LoadingWithTimeoutDialogXml loadingDialog
    ) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            final @StringRes int errorMessageRes;
            if (failureResult instanceof GroupFlowResult.Failure.Network) {
                errorMessageRes = R.string.error_removing_group_network;
            } else {
                errorMessageRes = R.string.error_removing_group_internal;
            }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getParentFragmentManager());
        });
    }

    @Nullable
    private ch.threema.data.models.GroupModel getNewGroupModel(@Nullable GroupModel groupModel) {
        if (groupModel == null) {
            logger.error("Provided group model is null");
            return null;
        }

        ch.threema.data.models.GroupModel newGroupModel = groupModelRepository.getByCreatorIdentityAndId(
            groupModel.getCreatorIdentity(),
            groupModel.getApiGroupId()
        );

        if (newGroupModel == null) {
            logger.error("New group model is null");
        }

        return newGroupModel;
    }

    @Override
    @Deprecated
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_SHARE_THREAD:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    prepareShareChat(selectedConversation);
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    ConfigUtils.showPermissionRationale(getContext(), getView(), R.string.permission_storage_required);
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
        ListenerManager.groupListeners.add(this.groupListener);
    }

    private void removeListeners() {
        logger.debug("*** remove listeners");

        conversationListeners.remove(this.conversationListener);
        ListenerManager.contactListeners.remove(this.contactListener);
        ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
        ListenerManager.synchronizeContactsListeners.remove(this.synchronizeContactsListener);
        ListenerManager.chatListener.remove(this.chatListener);
        ListenerManager.groupListeners.remove(this.groupListener);
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

        Thread updateListThread = new Thread(() -> {
            List<ConversationModel> conversationModels;

            conversationModels = conversationService.getAll(false, new ConversationService.Filter() {

                @Override
                public boolean noHiddenChats() {
                    return preferenceService.isPrivateChatsHidden();
                }

                @Override
                public String filterQuery() {
                    return filterQuery;
                }
            });

            RuntimeUtil.runOnUiThread(() -> {
                synchronized (messageListAdapterLock) {
                    if ((messageListAdapter == null || recreate) && conversationCategoryService != null) {
                        messageListAdapter = new MessageListAdapter(
                            MessageSectionFragment.this.activity,
                            contactService,
                            groupService,
                            distributionListService,
                            conversationService,
                            ringtoneService,
                            conversationCategoryService,
                            preferenceService,
                            groupCallManager,
                            highlightUid,
                            MessageSectionFragment.this,
                            messageListAdapterItemCache,
                            Glide.with(ThreemaApplication.getAppContext())
                        );

                        recyclerView.setAdapter(messageListAdapter);
                    }

                    try {
                        messageListAdapter.setData(conversationModels, changedPositions);
                    } catch (IndexOutOfBoundsException e) {
                        logger.debug("Failed to set adapter data", e);
                    }
                    // make sure footer is refreshed
                    messageListAdapter.refreshFooter();

                    if (recyclerView != null && scrollToPosition != null) {
                        if (changedPositions != null && changedPositions.size() == 1) {
                            ConversationModel changedModel = changedPositions.get(0);

                            if (changedModel != null && scrollToPosition > changedModel.getPosition() && conversationModels.contains(changedModel)) {
                                recyclerView.scrollToPosition(changedModel.getPosition());
                            }
                        }
                    }
                }

                if (runAfterSetData != null) {
                    runAfterSetData.run();
                }
            });

            synchronized (messageListAdapterItemCache) {
                for (ConversationModel conversationModel : conversationModels) {
                    if (!messageListAdapterItemCache.containsKey(conversationModel)) {
                        if (conversationCategoryService == null) {
                            logger.error("Conversation category service is null while updating cache");
                            break;
                        }
                        messageListAdapterItemCache.put(
                            conversationModel,
                            new MessageListAdapterItem(
                                conversationModel,
                                contactService,
                                ringtoneService,
                                conversationCategoryService
                            )
                        );
                    }
                }
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
            if (conversationCategoryService == null) {
                logger.error("Conversation category service is null: cannot update hidden menu visibility");
                return;
            }
            toggleHiddenMenuItemRef.get().setVisible(
                conversationCategoryService.hasPrivateChats() &&
                    ConfigUtils.hasProtection(preferenceService)
            );
        }
    }

    private boolean isMultiPaneEnabled(Activity activity) {
        if (activity != null) {
            return ConfigUtils.isTabletLayout() && activity instanceof ComposeMessageActivity;
        }
        return false;
    }

    private void fireReceiverUpdate(final MessageReceiver receiver) {
        if (receiver instanceof GroupMessageReceiver) {
            GroupModel groupModel = ((GroupMessageReceiver) receiver).getGroup();
            GroupIdentity groupIdentity = new GroupIdentity(
                groupModel.getCreatorIdentity(),
                groupModel.getApiGroupId().toLong()
            );
            ListenerManager.groupListeners.handle(listener ->
                listener.onUpdate(groupIdentity)
            );
        } else if (receiver instanceof ContactMessageReceiver) {
            ListenerManager.contactListeners.handle(listener ->
                listener.onModified(((ContactMessageReceiver) receiver).getContact().getIdentity())
            );
        } else if (receiver instanceof DistributionListMessageReceiver) {
            ListenerManager.distributionListListeners.handle(listener ->
                listener.onModify(((DistributionListMessageReceiver) receiver).getDistributionList())
            );
        }
    }

    @WorkerThread
    private void firePrivateReceiverUpdate() {
        if (conversationCategoryService == null) {
            logger.error("Conversation category service is null: cannot fire private receiver update");
            return;
        }

        //fire a update for every secret receiver (to update webclient data)
        for (ConversationModel c : Functional.filter(
            this.conversationService.getAll(false, null),
            (IPredicateNonNull<ConversationModel>)
                conversationModel -> conversationCategoryService.isPrivateChat(conversationModel.messageReceiver.getUniqueIdString())
        )) {
            if (c != null) {
                this.fireReceiverUpdate(c.messageReceiver);
            }
        }
    }

    public void onLogoClicked() {
        if (this.recyclerView != null) {
            logger.info("Logo clicked, scrolling to top");
            this.recyclerView.stopScroll();
            this.recyclerView.scrollToPosition(0);
        }
    }

    /**
     * Keeps track of the last archive chats. This class is used for the undo action.
     */
    private class ArchiveSnackbar {
        private final Snackbar snackbar;
        private final List<ConversationModel> conversationModels;

        /**
         * Creates an updated archive snackbar, dismisses the old snackbar (if available), and shows
         * the updated snackbar.
         *
         * @param archiveSnackbar      the currently shown archive snackbar (if available)
         * @param archivedConversation the conversation that just has been archived
         */
        ArchiveSnackbar(@Nullable ArchiveSnackbar archiveSnackbar, ConversationModel archivedConversation) {
            this.conversationModels = new ArrayList<>();
            this.conversationModels.add(archivedConversation);

            if (archiveSnackbar != null) {
                this.conversationModels.addAll(archiveSnackbar.conversationModels);
                archiveSnackbar.dismiss();
            }

            if (getView() != null) {
                int amountArchived = this.conversationModels.size();
                String snackText = ConfigUtils.getSafeQuantityString(getContext(), R.plurals.message_archived, amountArchived, amountArchived, this.conversationModels.size());
                this.snackbar = Snackbar.make(getView(), snackText, 7 * (int) DateUtils.SECOND_IN_MILLIS);
                this.snackbar.setAction(R.string.undo, v -> conversationService.unarchive(conversationModels, TriggerSource.LOCAL));
                this.snackbar.addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar snackbar, int event) {
                        super.onDismissed(snackbar, event);
                        if (MessageSectionFragment.this.archiveSnackbar == ArchiveSnackbar.this) {
                            MessageSectionFragment.this.archiveSnackbar = null;
                        }
                    }
                });
                this.snackbar.show();
            } else {
                this.snackbar = null;
            }
        }

        void dismiss() {
            if (this.snackbar != null) {
                this.snackbar.dismiss();
            }
        }

    }
}
