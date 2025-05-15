/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.app.grouplinks;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.asynctasks.ContactAvailable;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.group.GroupInviteService;
import ch.threema.app.services.group.OutgoingGroupJoinRequestService;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LazyProperty;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteData;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.protobuf.url_payloads.GroupInvite;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.GroupInviteModel;
import ch.threema.storage.models.group.OutgoingGroupJoinRequestModel;
import java8.util.Optional;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class OutgoingGroupRequestActivity extends ThreemaToolbarActivity implements
    OutgoingGroupJoinRequestDialog.OutgoingGroupJoinRequestDialogClickListener,
    GenericAlertDialog.DialogClickListener,
    SelectorDialog.SelectorDialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("OutgoingGroupRequestActivity");

    public static final String EXTRA_QR_RESULT = "qr_group_link_result";

    private static final String DIALOG_TAG_REQUEST_JOIN_MESSAGE = "requestMessage";
    private static final String DIALOG_TAG_REALLY_DELETE_REQUEST = "deleteRequest";
    private static final String DIALOG_TAG_REQUEST_ALREADY_SENT = "alreadySent";
    private static final String DIALOG_TAG_REQUEST_CONFIRM_SEND = "confirmSend";
    private static final String DIALOG_TAG_OWN_INVITE = "ownInvite";
    private static final String DIALOG_TAG_RESEND = "resend";

    private OutgoingGroupJoinRequestService outgoingGroupJoinRequestService;
    private GroupInviteService groupInviteService;
    private UserService userService;
    private GroupService groupService;
    private ContactService contactService;
    private DatabaseServiceNew databaseService;
    private APIConnector apiConnector;
    private ContactModelRepository contactModelRepository;

    @NonNull
    private final LazyProperty<BackgroundExecutor> backgroundExecutor = new LazyProperty<>(BackgroundExecutor::new);

    private OutgoingGroupRequestViewModel viewModel;
    private GroupInviteData groupInvite;
    private OutgoingGroupJoinRequestModel resendRequestReference;
    private OutgoingGroupRequestAdapter requestsAdapter;
    private ActionMode actionMode = null;

    private final GroupJoinResponseListener groupJoinResponseListener = new GroupJoinResponseListener() {
        @Override
        public void onReceived(OutgoingGroupJoinRequestModel outgoingGroupJoinRequestModel,
                               OutgoingGroupJoinRequestModel.Status status) {
            RuntimeUtil.runOnUiThread(() -> viewModel.onDataChanged());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
        // scanQR code and init on callback onActivityResult or init directly
        if (getIntent().getStringExtra(EXTRA_QR_RESULT) != null) {
            parseQrResult(getIntent().getStringExtra(EXTRA_QR_RESULT));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ListenerManager.groupJoinResponseListener.add(this.groupJoinResponseListener);
    }

    @Override
    protected void onDestroy() {
        ListenerManager.groupJoinResponseListener.remove(this.groupJoinResponseListener);
        super.onDestroy();
    }

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        if (getIntent().hasExtra(ThreemaApplication.INTENT_DATA_GROUP_LINK)) {
            try {
                this.groupInvite = groupInviteService.decodeGroupInviteLink(
                    getIntent().getStringExtra(ThreemaApplication.INTENT_DATA_GROUP_LINK)
                );
            } catch (IOException | IllegalStateException |
                     GroupInviteToken.InvalidGroupInviteTokenException e) {
                LogUtil.error("Exception, could not decode group invite " + e, this);
            }
        }

        if (groupInvite != null) {
            handleGroupInvite();
        }

        initLayout();
        initListeners();
        return true;
    }

    @Override
    protected void initServices() {
        super.initServices();
        try {
            this.outgoingGroupJoinRequestService = serviceManager.getOutgoingGroupJoinRequestService();
            this.groupInviteService = serviceManager.getGroupInviteService();
            this.contactService = serviceManager.getContactService();
            this.userService = serviceManager.getUserService();
            this.groupService = serviceManager.getGroupService();
            this.databaseService = serviceManager.getDatabaseServiceNew();
            this.apiConnector = serviceManager.getAPIConnector();
            this.contactModelRepository = serviceManager.getModelRepositories().getContacts();
        } catch (MasterKeyLockedException | FileSystemNotPresentException e) {
            logger.error("Exception, services not available... finishing");
            finish();
        }
    }

    private void initLayout() {
        viewModel = new ViewModelProvider(OutgoingGroupRequestActivity.this).get(OutgoingGroupRequestViewModel.class);

        try {
            this.requestsAdapter = new OutgoingGroupRequestAdapter(this, viewModel);
        } catch (ThreemaException e) {
            logger.error("Exception", e);
            finish();
            return;
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.group_requests_all_title);
        }

        EmptyView emptyView = new EmptyView(this, ConfigUtils.getActionBarSize(this));
        emptyView.setup(getString(R.string.group_requests_none_outgoing));

        EmptyRecyclerView recyclerView = this.findViewById(R.id.recycler);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        ((ViewGroup) recyclerView.getParent().getParent()).addView(emptyView);
        recyclerView.setEmptyView(emptyView);
        recyclerView.setAdapter(this.requestsAdapter);
    }

    private void initListeners() {
        this.requestsAdapter.setOnClickItemListener(new OutgoingGroupRequestAdapter.OnClickItemListener() {
            @Override
            public void onClick(OutgoingGroupJoinRequestModel groupJoinRequestModel, View view, int position) {
                if (actionMode != null) {
                    viewModel.toggleChecked(position);
                    if (viewModel.getCheckedItemsCount() > 0) {
                        actionMode.invalidate();
                    } else {
                        actionMode.finish();
                    }
                } else {
                    if (groupJoinRequestModel.getStatus().equals(OutgoingGroupJoinRequestModel.Status.ACCEPTED)
                        && groupJoinRequestModel.getGroupApiId() != null) {
                        GroupModel groupModel = databaseService
                            .getGroupModelFactory()
                            .getByApiGroupIdAndCreator(groupJoinRequestModel.getGroupApiId().toString(), groupJoinRequestModel.getAdminIdentity());
                        if (groupModel == null || !groupService.isGroupMember(groupModel)) {
                            logger.warn("No group model found for accepted request... user might have been kick out in the meantime");
                            showResendSelectorDialog(groupJoinRequestModel);
                            return;
                        }
                        forwardToGroup(
                            groupModel.getId()
                        );
                        finish();
                    } else {
                        showResendSelectorDialog(groupJoinRequestModel);
                    }
                }
            }

            private void showResendSelectorDialog(OutgoingGroupJoinRequestModel groupJoinRequestModel) {
                ArrayList<SelectorDialogItem> items = new ArrayList<>();
                items.add(new SelectorDialogItem(getString(R.string.resend), R.drawable.ic_send_circle_outline));

                SelectorDialog selectorDialog = SelectorDialog.newInstance(null, items, null);
                selectorDialog.setData(groupJoinRequestModel);
                selectorDialog.show(getSupportFragmentManager(), DIALOG_TAG_RESEND);
            }

            @Override
            public boolean onLongClick(OutgoingGroupJoinRequestModel groupJoinRequestModel, View itemView, int position) {
                if (actionMode != null) {
                    actionMode.finish();
                }
                viewModel.toggleChecked(position);
                if (viewModel.getCheckedItemsCount() > 0) {
                    actionMode = startSupportActionMode(new RequestsActions());
                }
                return true;
            }
        });

        final Observer<List<OutgoingGroupJoinRequestModel>> groupRequestsObserver = newGroupRequest ->
            requestsAdapter.setRequestModels(newGroupRequest);

        viewModel.getRequests().observe(this, groupRequestsObserver);
        viewModel.onDataChanged();
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_base_recycler_list;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleGroupInvite() {
        // forward directly to group if i'm the admin and the group exists
        if (groupInvite.getAdminIdentity().equals(userService.getIdentity())) {
            Optional<GroupInviteModel> groupInviteModel = databaseService
                .getGroupInviteModelFactory()
                .getByToken(groupInvite.getToken().toString());
            GroupModel groupModel = databaseService
                .getGroupModelFactory()
                .getByApiGroupIdAndCreator(
                    groupInviteModel.get().getGroupApiId().toString(), userService.getIdentity()
                );
            if (groupModel != null) {
                forwardToGroup(groupModel.getId());
            } else {
                // show info that request was already sent with info about resend option
                GenericAlertDialog.newInstance(
                        R.string.group_request_already_sent_title,
                        HtmlCompat.fromHtml(
                            String.format(getString(R.string.group_request_already_sent), groupInvite.getGroupName()),
                            HtmlCompat.FROM_HTML_MODE_COMPACT),
                        R.string.ok,
                        0)
                    .show(getSupportFragmentManager(), DIALOG_TAG_REQUEST_ALREADY_SENT);
                return;
            }
        }
        Optional<OutgoingGroupJoinRequestModel> outgoingGroupJoinRequestModel =
            databaseService.getOutgoingGroupJoinRequestModelFactory()
                .getByInviteToken(groupInvite.getToken().toString());
        if (outgoingGroupJoinRequestModel.isPresent()) {
            // forward to group if we have an associated group model when we were already accepted
            if (outgoingGroupJoinRequestModel.get().getGroupApiId() != null) {
                GroupModel groupModel = databaseService
                    .getGroupModelFactory()
                    .getByApiGroupIdAndCreator(outgoingGroupJoinRequestModel.get().getGroupApiId().toString(), groupInvite.getAdminIdentity());
                if (groupModel != null && groupService.isGroupMember(groupModel)) {
                    forwardToGroup(groupModel.getId());
                    finish();
                    return;
                }
            }
            if (outgoingGroupJoinRequestModel.get().getStatus() == OutgoingGroupJoinRequestModel.Status.UNKNOWN) {
                // show info that request was already sent with info about resend option
                GenericAlertDialog.newInstance(
                        R.string.group_request_already_sent_title,
                        HtmlCompat.fromHtml(
                            String.format(getString(R.string.group_request_already_sent), groupInvite.getGroupName()),
                            HtmlCompat.FROM_HTML_MODE_COMPACT),
                        R.string.ok,
                        0)
                    .show(getSupportFragmentManager(), DIALOG_TAG_REQUEST_ALREADY_SENT);
                return;
            }
        }
        if (groupInvite.getConfirmationMode() == GroupInvite.ConfirmationMode.AUTOMATIC) {
            confirmSend(
                NameUtil.getDisplayName(contactService.getByIdentity(groupInvite.getAdminIdentity())),
                groupInvite.getGroupName()
            );
        } else {
            requestJoinRequestMessage(
                NameUtil.getDisplayName(
                    contactService.getByIdentity(groupInvite.getAdminIdentity())
                ),
                groupInvite.getGroupName());
        }
    }

    private void delete(List<OutgoingGroupJoinRequestModel> checkedItems) {
        Integer amountOfOutgoingRequests = checkedItems.size();
        String confirmText = String.format(ConfigUtils.getSafeQuantityString(this, R.plurals.really_delete_outgoing_request, amountOfOutgoingRequests, amountOfOutgoingRequests));
        String reallyDeleteGroupRequestTitle = getString(amountOfOutgoingRequests > 1 ? R.string.really_delete_group_request_title_plural : R.string.really_delete_group_request_title_singular);

        GenericAlertDialog dialog = GenericAlertDialog.newInstance(
            reallyDeleteGroupRequestTitle,
            confirmText,
            R.string.ok,
            R.string.cancel);
        dialog.setData(checkedItems);
        dialog.show(getSupportFragmentManager(), DIALOG_TAG_REALLY_DELETE_REQUEST);
    }

    private void reallyDelete(final List<OutgoingGroupJoinRequestModel> checkedItems) {
        this.viewModel.deleteOutgoingGroupJoinRequests(checkedItems);
        actionMode.finish();
    }

    private void parseQrResult(String payload) {
        Uri requestUri = Uri.parse(payload);
        if (requestUri == null) {
            LogUtil.error("Exception, could not parse group link", this);
            return;
        }
        String base64encodedGroupInvite = requestUri.getEncodedFragment();
        try {
            this.groupInvite = groupInviteService.decodeGroupInviteLink(base64encodedGroupInvite);
            handleGroupInvite();
        } catch (IOException | IllegalStateException |
                 GroupInviteToken.InvalidGroupInviteTokenException e) {
            LogUtil.error("Exception, could not decode group link " + e, this);
        }
    }

    private void forwardToGroup(int groupId) {
        Intent intent = new Intent(this, ComposeMessageActivity.class);
        intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_DATABASE_ID, groupId);
        startActivity(intent);
    }

    private void confirmSend(String admin, String groupName) {
        GenericAlertDialog.newInstance(R.string.group_request_send_title,
                HtmlCompat.fromHtml(String.format(
                    getString(R.string.group_request_confirm_send),
                    groupName,
                    admin
                ), HtmlCompat.FROM_HTML_MODE_COMPACT),
                R.string.send, R.string.cancel)
            .show(getSupportFragmentManager(), DIALOG_TAG_REQUEST_CONFIRM_SEND);
    }

    private void requestJoinRequestMessage(String toIdentity, String group) {
        OutgoingGroupJoinRequestDialog.newInstance(group, toIdentity)
            .show(getSupportFragmentManager(), DIALOG_TAG_REQUEST_JOIN_MESSAGE);
    }

    @Override
    public void onSend(@NonNull String message) {
        try {
            // first time sending the request
            if (this.resendRequestReference == null) {
                // first add contact and fetch public key to be able to send a request
                if (contactService.getByIdentity(groupInvite.getAdminIdentity()) == null) {
                    backgroundExecutor.get().execute(
                        new BasicAddOrUpdateContactBackgroundTask(
                            groupInvite.getAdminIdentity(),
                            ContactModel.AcquaintanceLevel.DIRECT,
                            userService.getIdentity(),
                            apiConnector,
                            contactModelRepository,
                            AddContactRestrictionPolicy.CHECK,
                            this,
                            null
                        ) {
                            @Override
                            public void onFinished(ContactResult result) {
                                if (result instanceof ContactAvailable) {
                                    if (isDestroyed()) {
                                        return;
                                    }
                                    try {
                                        outgoingGroupJoinRequestService.send(groupInvite, message);
                                    } catch (Exception e) {
                                        logger.error("Sending request after adding contact failed", e);
                                    }
                                }
                            }
                        }
                    );
                } else {
                    outgoingGroupJoinRequestService.send(
                        groupInvite,
                        message);
                }
            } else {
                outgoingGroupJoinRequestService.resendRequest(
                    resendRequestReference,
                    message);
            }
            viewModel.onDataChanged();
        } catch (Exception e) {
            LogUtil.error("Exception, sending request failed" + e, this);
        }
    }

    @Override
    public void cancel() {
        finish();
    }

    // start generic alert dialog callbacks
    @Override
    public void onYes(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_REALLY_DELETE_REQUEST:
                reallyDelete((List<OutgoingGroupJoinRequestModel>) data);
                break;
            case DIALOG_TAG_OWN_INVITE:
                finish();
                break;
            case DIALOG_TAG_REQUEST_CONFIRM_SEND:
                onSend("");
                break;
            default:
                break;

        }
    }

    @Override
    public void onNo(String tag, Object data) {
        // fall though, delete action aborted
    }
    // end generic alert dialog callbacks

    // start selector resend dialog callbacks
    @Override
    public void onClick(String tag, int which, Object data) {
        OutgoingGroupJoinRequestModel groupJoinRequestModel = (OutgoingGroupJoinRequestModel) data;
        this.resendRequestReference = groupJoinRequestModel;
        if (groupJoinRequestModel.getMessage().isEmpty()) {
            confirmSend(
                NameUtil.getDisplayName(contactService.getByIdentity(groupJoinRequestModel.getAdminIdentity())),
                groupJoinRequestModel.getGroupName()
            );
        } else {
            requestJoinRequestMessage(
                NameUtil.getDisplayName(
                    contactService.getByIdentity(groupJoinRequestModel.getAdminIdentity())
                ),
                groupJoinRequestModel.getGroupName()
            );
        }
    }

    @Override
    public void onCancel(String tag) {
        // don't bother
    }

    @Override
    public void onNo(String tag) {
        //  don't bother
    }
    // end selector dialog callbacks

    public class RequestsActions implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.action_group_request, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            final int checked = viewModel.getCheckedItemsCount();
            if (checked > 0) {
                mode.setTitle(Integer.toString(checked));
                return true;
            }
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.menu_select_all) {
                if (viewModel.selectAll()) {
                    mode.setTitle(Integer.toString(viewModel.getCheckedItemsCount()));
                } else {
                    actionMode.finish();
                }
                return true;
            } else if (id == R.id.menu_delete) {
                delete(viewModel.getCheckedItems());
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            viewModel.clearCheckedItems();
            actionMode = null;
        }
    }
}
