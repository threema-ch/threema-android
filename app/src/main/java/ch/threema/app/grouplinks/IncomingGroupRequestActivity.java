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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.slf4j.Logger;

import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.ViewModelFactory;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.GroupId;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;

public class IncomingGroupRequestActivity extends ThreemaToolbarActivity implements
    IncomingGroupJoinRequestDialog.IncomingGroupJoinRequestDialogClickListener,
    GenericAlertDialog.DialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("IncomingGroupRequestActivity");

    private static final String DIALOG_TAG_REALLY_DELETE_REQUEST = "deleteRequest";
    private static final String DIALOG_TAG_RESPOND = "respond";

    private IncomingGroupRequestViewModel viewModel;
    private IncomingGroupRequestAdapter requestsAdapter;
    private ActionMode actionMode = null;

    private final IncomingGroupJoinRequestListener groupJoinRequestListener = new IncomingGroupJoinRequestListener() {
        @Override
        public void onReceived(IncomingGroupJoinRequestModel incomingGroupJoinRequestModel, GroupModel groupModel) {
            RuntimeUtil.runOnUiThread(() -> viewModel.onDataChanged());
        }

        @Override
        public void onRespond() {
            RuntimeUtil.runOnUiThread(() -> viewModel.onDataChanged());
        }
    };


    @Override
    public int getLayoutResource() {
        return R.layout.activity_base_recycler_list;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logger.debug("onCreate()");
        super.onCreate(savedInstanceState);
        initActivity(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ListenerManager.incomingGroupJoinRequestListener.add(this.groupJoinRequestListener);
    }

    @Override
    protected void onDestroy() {
        ListenerManager.incomingGroupJoinRequestListener.remove(this.groupJoinRequestListener);
        super.onDestroy();
    }

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        Intent intent = getIntent();
        GroupId groupId = (GroupId) intent.getSerializableExtra(ThreemaApplication.INTENT_DATA_GROUP_API);
        if (groupId == null) {
            logger.error("No group received to display group request for");
            finish();
        }

        this.viewModel = new ViewModelProvider(this,
            new ViewModelFactory(groupId))
            .get(IncomingGroupRequestViewModel.class);

        try {
            this.requestsAdapter = new IncomingGroupRequestAdapter(this, viewModel);
        } catch (ThreemaException e) {
            logger.error("Exception", e);
            finish();
            return false;
        }

        initLayout();
        initListeners();
        return true;
    }

    private void initLayout() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getString(R.string.all_open_group_requests));
        }

        EmptyView emptyView = new EmptyView(this, ConfigUtils.getActionBarSize(this));
        emptyView.setup(getString(R.string.no_incoming_group_requests));

        EmptyRecyclerView recyclerView = this.findViewById(R.id.recycler);
        recyclerView.setHasFixedSize(true);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        ((ViewGroup) recyclerView.getParent().getParent()).addView(emptyView);
        recyclerView.setEmptyView(emptyView);
        recyclerView.setAdapter(this.requestsAdapter);
    }

    private void initListeners() {
        this.requestsAdapter.setOnClickItemListener(new IncomingGroupRequestAdapter.OnClickItemListener() {
            @Override
            public void onClick(IncomingGroupJoinRequestModel groupJoinRequestModel, View view, int position) {
                if (actionMode != null) {
                    viewModel.toggleChecked(position);
                    if (viewModel.getCheckedItemsCount() > 0) {
                        actionMode.invalidate();
                    } else {
                        actionMode.finish();
                    }
                } else if (groupJoinRequestModel.getResponseStatus() == IncomingGroupJoinRequestModel.ResponseStatus.OPEN) {
                    IncomingGroupJoinRequestDialog.newInstance(groupJoinRequestModel.getId())
                        .setCallback(IncomingGroupRequestActivity.this) // only required here, but not in the open requests chip view
                        .show(getSupportFragmentManager(), DIALOG_TAG_RESPOND);
                }
            }

            @Override
            public boolean onLongClick(IncomingGroupJoinRequestModel groupJoinRequestModel, View itemView, int position) {
                if (actionMode != null) {
                    actionMode.finish();
                }
                viewModel.toggleChecked(position);
                if (viewModel.getCheckedItemsCount() > 0) {
                    actionMode = startSupportActionMode(new IncomingGroupRequestActivity.RequestsActions());
                }
                return true;
            }
        });

        final Observer<List<IncomingGroupJoinRequestModel>> groupRequestsObserver = newGroupRequest ->
            requestsAdapter.setRequestModels(newGroupRequest);

        viewModel.getRequests().observe(this, groupRequestsObserver);
        viewModel.onDataChanged();
    }

    private void delete(List<IncomingGroupJoinRequestModel> checkedItems) {
        final int amountOfRequests = checkedItems.size();
        final String confirmText = ConfigUtils.getSafeQuantityString(this, R.plurals.really_delete_incoming_request, amountOfRequests, amountOfRequests);
        String reallyDeleteGroupRequestTitle = getString(amountOfRequests > 1 ? R.string.really_delete_group_request_title_plural : R.string.really_delete_group_request_title_singular);

        GenericAlertDialog dialog = GenericAlertDialog.newInstance(
            reallyDeleteGroupRequestTitle,
            confirmText,
            R.string.ok,
            R.string.cancel);
        dialog.setData(checkedItems);
        dialog.show(getSupportFragmentManager(), DIALOG_TAG_REALLY_DELETE_REQUEST);
    }

    private void reallyDelete(final List<IncomingGroupJoinRequestModel> checkedItems) {
        this.viewModel.deleteIncomingGroupJoinRequests(checkedItems);
        actionMode.finish();
    }

    @Override
    public void onAccept(String message) {
        viewModel.onDataChanged();
    }

    @Override
    public void onReject() {
        viewModel.onDataChanged();
    }

    @Override
    public void onYes(String tag, Object data) {
        if (tag.equals(DIALOG_TAG_REALLY_DELETE_REQUEST)) {
            reallyDelete((List<IncomingGroupJoinRequestModel>) data);
        }
    }

    @Override
    public void onNo(String tag, Object data) {
        // fall through, delete action aborted
    }

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
            if (item.getItemId() == R.id.menu_select_all) {
                if (viewModel.selectAll()) {
                    mode.setTitle(Integer.toString(viewModel.getCheckedItemsCount()));
                } else {
                    actionMode.finish();
                }
                return true;
            } else if (item.getItemId() == R.id.menu_delete) {
                delete(viewModel.getCheckedItems());
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            viewModel.clearCheckedItems();
            actionMode = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
