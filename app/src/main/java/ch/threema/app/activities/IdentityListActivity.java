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

package ch.threema.app.activities;

import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.IdentityListAdapter;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.IdListService;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.localcrypto.MasterKeyLockedException;

abstract public class IdentityListActivity extends ThreemaToolbarActivity implements TextEntryDialog.TextEntryDialogClickListener {
    private static final String BUNDLE_RECYCLER_LAYOUT = "recycler";
    private static final String BUNDLE_SELECTED_ITEM = "item";

    private IdentityListAdapter adapter;
    private ActionMode actionMode = null;
    private Bundle savedInstanceState;
    private EmptyRecyclerView recyclerView;
    private ContactService contactService;

    abstract protected IdListService getIdentityListService();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            contactService = ThreemaApplication.requireServiceManager().getContactService();
        } catch (MasterKeyLockedException | FileSystemNotPresentException e) {
            finish();
        }

        this.savedInstanceState = savedInstanceState;

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(this.getTitleText());

        recyclerView = this.findViewById(R.id.recycler);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));

        ExtendedFloatingActionButton floatingActionButton = findViewById(R.id.floating);
        floatingActionButton.show();
        floatingActionButton.setOnClickListener(v -> startExclude());

        // add text view if contact list is empty
        EmptyView emptyView = new EmptyView(this);
        emptyView.setup(this.getBlankListText());
        ((ViewGroup) recyclerView.getParent()).addView(emptyView);
        recyclerView.setEmptyView(emptyView);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
                super.onScrolled(recyclerView, dx, dy);

                if (dy >0) {
                    // Scroll Down
                    if (floatingActionButton.isShown()) {
                        floatingActionButton.shrink();
                    }
                }
                else if (dy <0) {
                    // Scroll Up
                    if (floatingActionButton.isShown()) {
                        floatingActionButton.extend();
                    }
                }
            }
        });

        this.updateListAdapter();

    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_recycler_toolbar;
    }

    protected abstract String getBlankListText();
    protected abstract String getTitleText();

    private void updateListAdapter() {
        if(this.getIdentityListService() == null) {
            //do nothing;
            return;
        }

        List<IdentityListAdapter.Entity> identityList = Arrays.stream(this.getIdentityListService().getAll())
            .map(IdentityListAdapter.Entity::new)
            .collect(Collectors.toList());

        if (adapter == null) {
            adapter = new IdentityListAdapter(this);
            adapter.setOnItemClickListener(entity -> {
                if (entity.equals(adapter.getSelected())) {
                    if (actionMode != null) {
                        actionMode.finish();
                    }
                } else {
                    adapter.setSelected(entity);
                    if (actionMode == null) {
                        actionMode = startSupportActionMode(new IdentityListAction());
                    }
                }
            });
            recyclerView.setAdapter(adapter);
        }
        adapter.setData(identityList);

        // restore after rotate
        if(savedInstanceState != null) {
            Parcelable savedRecyclerLayoutState = savedInstanceState.getParcelable(BUNDLE_RECYCLER_LAYOUT);
            recyclerView.getLayoutManager().onRestoreInstanceState(savedRecyclerLayoutState);

            String selectedIdentity = savedInstanceState.getString(BUNDLE_SELECTED_ITEM);
            if (selectedIdentity != null) {
                Iterator<IdentityListAdapter.Entity> iterator = identityList.iterator();
                while (iterator.hasNext()) {
                    IdentityListAdapter.Entity entity = iterator.next();
                    if (selectedIdentity.equals(entity.getText())) {
                        adapter.setSelected(entity);
                        this.actionMode = startSupportActionMode(new IdentityListAction());
                        break;
                    }
                    iterator.remove();
                }
            }
            savedInstanceState = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    private void startExclude() {
        if (actionMode != null) {
            actionMode.finish();
        }

        DialogFragment dialogFragment = TextEntryDialog.newInstance(
            R.string.title_enter_id,
            R.string.enter_id_hint,
            R.string.ok,
            R.string.cancel,
            "",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            TextEntryDialog.INPUT_FILTER_TYPE_IDENTITY);
        dialogFragment.show(getSupportFragmentManager(), "excludeDialog");
    }

    private void excludeIdentity(String identity) {
        if(this.getIdentityListService() == null) {
            return;
        }

        //add identity to list!
        this.getIdentityListService()
            .add(identity);

        fireOnModifiedContact(identity);

        this.updateListAdapter();
    }

    private void removeIdentity(String identity) {
        if(this.getIdentityListService() == null) {
            return;
        }

        //remove identity from list!
        this.getIdentityListService().remove(identity);

        fireOnModifiedContact(identity);

        this.updateListAdapter();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (recyclerView != null && adapter != null) {
            outState.putParcelable(BUNDLE_RECYCLER_LAYOUT, recyclerView.getLayoutManager().onSaveInstanceState());
            if (adapter.getSelected() != null) {
                outState.putString(BUNDLE_SELECTED_ITEM, adapter.getSelected().getText());
            }
        }
    }

    public class IdentityListAction implements ActionMode.Callback {

        public IdentityListAction() {
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.action_identity_list, menu);
            return true;
        }


        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.menu_identity_remove) {
                IdentityListAdapter.Entity selectedEntity = adapter.getSelected();
                if (selectedEntity != null) {
                    removeIdentity(selectedEntity.getText());
                }
                mode.finish();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;

            adapter.clearSelection();
        }
    }

    private void fireOnModifiedContact(final String identity) {
        if (contactService != null) {
            ListenerManager.contactListeners.handle(listener -> listener.onModified(identity));
        }
    }

    @Override
    public void onYes(@NonNull String tag, final @NonNull String text) {
        if (text.length() == ProtocolDefines.IDENTITY_LEN) {
            excludeIdentity(text);
        }
    }

    @Override
    public void onNo(String tag) {}
}
