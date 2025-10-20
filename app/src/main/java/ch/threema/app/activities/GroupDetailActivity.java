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

package ch.threema.app.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.koin.android.compat.ViewModelCompat;
import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.GroupDetailAdapter;
import ch.threema.app.contactdetails.ContactDetailActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GroupDescEditDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.dialogs.loadingtimeout.LoadingWithTimeoutDialogXml;
import ch.threema.app.emojis.EmojiEditText;
import ch.threema.app.groupflows.GroupFlowResult;
import ch.threema.app.groupflows.GroupChanges;
import ch.threema.app.groupflows.GroupCreateProperties;
import ch.threema.app.groupflows.GroupDisbandIntent;
import ch.threema.app.groupflows.GroupLeaveIntent;
import ch.threema.app.home.HomeActivity;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.mediagallery.MediaGalleryActivity;
import ch.threema.app.profilepicture.CheckedProfilePicture;
import ch.threema.app.protocol.ProfilePictureChange;
import ch.threema.app.protocol.RemoveProfilePicture;
import ch.threema.app.protocol.SetProfilePicture;
import ch.threema.app.ui.AvatarEditView;
import ch.threema.app.ui.GroupDetailViewModel;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.GroupCallUtil;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.groupcall.GroupCallDescription;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.utils.CoroutinesExtensionKt;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupIdentity;
import ch.threema.data.models.GroupModelData;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import kotlin.Unit;
import kotlinx.coroutines.Deferred;

import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.COLLAPSED;
import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.NONE;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.app.groupflows.GroupFlowResultKt.GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS;

public class GroupDetailActivity extends GroupEditActivity implements SelectorDialog.SelectorDialogClickListener,
    GenericAlertDialog.DialogClickListener,
    TextEntryDialog.TextEntryDialogClickListener,
    GroupDetailAdapter.OnGroupDetailsClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupDetailActivity");
    // static values
    private final int MODE_EDIT = 1;
    private final int MODE_READONLY = 2;

    private static final String DIALOG_TAG_LEAVE_GROUP = "leaveGroup";
    private static final String DIALOG_TAG_DISSOLVE_GROUP = "dissolveGroup";
    private static final String DIALOG_TAG_QUIT = "quit";
    private static final String DIALOG_TAG_CHOOSE_ACTION = "chooseAction";
    private static final String DIALOG_TAG_DELETE_GROUP = "delG";
    private static final String DIALOG_TAG_CLONE_GROUP = "cg";
    private static final String DIALOG_TAG_CHANGE_GROUP_DESC = "cgDesc";
    private static final String DIALOG_TAG_CLONE_GROUP_CONFIRM = "cgc";
    private static final String RUN_ON_ACTIVE_RELOAD = "reload";

    private static final int SELECTOR_OPTION_CONTACT_DETAIL = 0;
    private static final int SELECTOR_OPTION_CHAT = 1;
    private static final int SELECTOR_OPTION_CALL = 2;
    private static final int SELECTOR_OPTION_REMOVE = 3;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private GroupModel groupModel;
    private GroupDetailViewModel groupDetailViewModel;
    private GroupDetailAdapter groupDetailAdapter;

    private EmojiEditText groupNameEditText;
    private ResumePauseHandler resumePauseHandler;
    private AvatarEditView avatarEditView;
    private ExtendedFloatingActionButton floatingActionButton;

    private String myIdentity;
    private int operationMode;
    private long groupId;

    private final ResumePauseHandler.RunIfActive runIfActiveUpdate = new ResumePauseHandler.RunIfActive() {
        @Override
        public void runOnUiThread() {
            groupDetailViewModel.setGroupName(groupModel.getName());

            groupDetailViewModel.setGroupIdentities(dependencies.getGroupService().getGroupMemberIdentities(groupModel));
            sortGroupMembers();
        }
    };

    private final AvatarEditView.AvatarEditListener avatarEditViewListener = new AvatarEditView.AvatarEditListener() {
        @Override
        public void onAvatarSet(File avatarFile1) {
            groupDetailViewModel.setAvatarFile(avatarFile1);
            groupDetailViewModel.setIsAvatarRemoved(false);
            updateFloatingActionButtonAndMenu();
        }

        @Override
        public void onAvatarRemoved() {
            groupDetailViewModel.setAvatarFile(null);
            groupDetailViewModel.setIsAvatarRemoved(true);
            avatarEditView.setDefaultAvatar(null, groupModel);
            updateFloatingActionButtonAndMenu();
        }
    };

    private static class SelectorInfo {
        public View view;
        public ContactModel contactModel;
        public ArrayList<Integer> optionsMap;
    }

    private final ContactSettingsListener contactSettingsListener = new ContactSettingsListener() {
        @Override
        public void onAvatarSettingChanged() {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }
    };

    private final ContactListener contactListener = new ContactListener() {
        @Override
        public void onModified(final @NonNull String identity) {
            if (this.shouldHandleChange(identity)) {
                resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
            }
        }

        @Override
        public void onAvatarChanged(final @NonNull String identity) {
            if (this.shouldHandleChange(identity)) {
                this.onModified(identity);
            }
        }

        private boolean shouldHandleChange(@NonNull String identity) {
            return groupDetailViewModel.containsModel(identity);
        }
    };

    private final GroupListener groupListener = new GroupListener() {
        @Override
        public void onCreate(@NonNull GroupIdentity groupIdentity) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }

        @Override
        public void onRename(@NonNull GroupIdentity groupIdentity) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }

        @Override
        public void onUpdatePhoto(@NonNull GroupIdentity groupIdentity) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }

        @Override
        public void onRemove(long groupDbId) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }

        @Override
        public void onNewMember(@NonNull GroupIdentity groupIdentity, String identityNew) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }

        @Override
        public void onMemberLeave(@NonNull GroupIdentity groupIdentity, @NonNull String identityLeft) {
            if (identityLeft.equals(myIdentity)) {
                finish();
            } else {
                resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
            }
        }

        @Override
        public void onMemberKicked(@NonNull GroupIdentity groupIdentity, String identityKicked) {
            if (identityKicked.equals(myIdentity)) {
                finish();
            } else {
                resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!dependencies.isAvailable()) {
            finish();
            return;
        }

        this.myIdentity = dependencies.getUserService().getIdentity();

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            finish();
            return;
        }

        final @NonNull CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        this.groupNameEditText = findViewById(R.id.group_title);
        final @Nullable ImageView avatarEditView = findViewById(R.id.avatar_edit);

        if (getAppBarLayout() != null) {
            getAppBarLayout().addOnOffsetChangedListener((view, verticalOffset) -> {
                float expandedPercent = 1f;
                final float offsetPixels = Math.abs(verticalOffset);
                final float offsetPixelsWhenCollapsed = (float) view.getTotalScrollRange();
                if (offsetPixelsWhenCollapsed > 0) {
                    expandedPercent = 1 - (offsetPixels / offsetPixelsWhenCollapsed);
                }

                // Fade out contents while collapsing
                groupNameEditText.setAlpha(expandedPercent);
                if (avatarEditView != null && avatarEditView.getVisibility() == View.VISIBLE) {
                    avatarEditView.setAlpha(expandedPercent);
                }

                // Show the group name as a toolbar title only of we are fully collapsed
                @NonNull String toolbarTitle = " ";
                if (groupDetailViewModel != null && expandedPercent == 0.0f) {
                    toolbarTitle = groupDetailViewModel.getGroupName();
                }
                collapsingToolbar.setTitle(toolbarTitle);
            });
        }

        this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this);
        this.groupDetailViewModel = ViewModelCompat.getViewModel(this, GroupDetailViewModel.class);

        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        this.avatarEditView = findViewById(R.id.avatar_edit_view);

        this.floatingActionButton = findViewById(R.id.floating);
        RecyclerView groupDetailRecyclerView = findViewById(R.id.group_members_list);
        collapsingToolbar.setTitle(" ");

        groupId = getIntent().getLongExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, 0L);
        if (this.groupId == 0) {
            finish();
        }
        this.groupModel = dependencies.getGroupService().getById(groupId);

        observeNewGroupModel();

        if (savedInstanceState == null) {
            // new instance
            this.groupDetailViewModel.setGroupContacts(dependencies.getContactService().getByIdentities(dependencies.getGroupService().getGroupMemberIdentities(this.groupModel)));
            this.groupDetailViewModel.setGroupName(this.groupModel.getName());
            String groupDesc = this.groupModel.getGroupDesc();
            if (groupDesc == null || groupDesc.isEmpty()) {
                this.groupDetailViewModel.setGroupDesc(null);
                this.groupDetailViewModel.setGroupDescState(NONE);
            } else {
                this.groupDetailViewModel.setGroupDesc(groupDesc);
                this.groupDetailViewModel.setGroupDescState(COLLAPSED);
            }
            this.groupDetailViewModel.setGroupDescTimestamp(this.groupModel.getGroupDescTimestamp());
        }

        this.avatarEditView.setHires(true);
        if (groupDetailViewModel.getIsAvatarRemoved()) {
            this.avatarEditView.setDefaultAvatar(null, groupModel);
        } else {
            if (groupDetailViewModel.getAvatarFile() != null) {
                this.avatarEditView.setAvatarFile(groupDetailViewModel.getAvatarFile());
            } else {
                this.avatarEditView.loadAvatarForModel(null, groupModel);
            }
        }
        this.avatarEditView.setListener(this.avatarEditViewListener);

        ((AppBarLayout) findViewById(R.id.appbar)).addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (verticalOffset == 0) {
                if (!floatingActionButton.isExtended()) {
                    floatingActionButton.extend();
                }
            } else if (floatingActionButton.isExtended()) {
                floatingActionButton.shrink();
            }
        });

        this.sortGroupMembers();
        setTitle();
        updateFloatingActionButtonAndMenu();

        if (dependencies.getGroupService().isGroupCreator(groupModel) && dependencies.getGroupService().isGroupMember(groupModel)) {
            operationMode = MODE_EDIT;
            actionBar.setHomeButtonEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);

            floatingActionButton.setOnClickListener(v -> {
                logger.info("FAB (save group settings) clicked");
                saveGroupSettings();
            });
            groupNameEditText.setMaxByteSize(GroupModel.GROUP_NAME_MAX_LENGTH_BYTES);
            groupNameEditText.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(@NonNull Editable editable) {
                    updateFloatingActionButtonAndMenu();
                }
            });
        } else {
            operationMode = MODE_READONLY;
            actionBar.setHomeButtonEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);

            groupNameEditText.setFocusable(false);
            groupNameEditText.setClickable(false);
            groupNameEditText.setFocusableInTouchMode(false);
            groupNameEditText.setBackground(null);
            groupNameEditText.setPadding(0, 0, 0, 0);

            floatingActionButton.setVisibility(View.GONE);

            // If the user is not a member of the group, then display the group name with strike
            // through style
            if (!dependencies.getGroupService().isGroupMember(groupModel)) {
                // Get the paint flags and add the strike through flag
                int paintFlags = groupNameEditText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG;
                groupNameEditText.setPaintFlags(paintFlags);
            }
        }

        groupDetailRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        try {
            setupAdapter();
        } catch (MasterKeyLockedException e) {
            logger.error("Could not setup group detail adapter", e);
            finish();
            return;
        }

        Fragment dialogFragment = getSupportFragmentManager().findFragmentByTag(DIALOG_TAG_CHANGE_GROUP_DESC);
        if (dialogFragment instanceof GroupDescEditDialog) {
            GroupDescEditDialog dialog = (GroupDescEditDialog) dialogFragment;
            dialog.setCallback(newGroupDesc -> {
                hideKeyboard(); // is used for older devices
                onGroupDescChange(newGroupDesc);
            });
        }


        groupDetailRecyclerView.setAdapter(this.groupDetailAdapter);

        final Observer<List<ContactModel>> groupMemberObserver = groupMembers -> {
            // Update the UI
            groupDetailAdapter.setContactModels(groupMembers);
        };

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        groupDetailViewModel.getGroupMembers().observe(this, groupMemberObserver);
        groupDetailViewModel.onDataChanged();

        @ColorInt int color = dependencies.getGroupService().getAvatarColor(groupModel);
        collapsingToolbar.setContentScrimColor(color);
        collapsingToolbar.setStatusBarScrimColor(color);

        updateFloatingActionButtonAndMenu();

        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }

        ListenerManager.contactSettingsListeners.add(this.contactSettingsListener);
        ListenerManager.groupListeners.add(this.groupListener);
        ListenerManager.contactListeners.add(this.contactListener);
    }

    @Override
    public void handleDeviceInsets(){
        // As the CollapsingToolbarLayout will consume the window insets internally, we have to apply the window insets to every our child views
        // with one inset listener from the root layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_content), (view, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            // Apply the window insets to our MaterialToolbar
            ViewExtensionsKt.setMargin(getToolbar(), insets.left, 0, insets.right, 0);

            // Apply horizontal insets to the group name edittext
            final int ownMarginHorizontal = getResources().getDimensionPixelSize(R.dimen.grid_unit_x2);
            final int ownMarginBottom = getResources().getDimensionPixelSize(R.dimen.grid_unit_x4);
            ViewExtensionsKt.setMargin(
                findViewById(R.id.group_title),
                insets.left + ownMarginHorizontal,
                0,
                insets.right + ownMarginHorizontal,
                ownMarginBottom
            );

            // Apply horizontal and bottom insets to the listview
            findViewById(R.id.group_members_list).setPadding(
                insets.left,
                0,
                insets.right,
                insets.bottom
            );

            // Place the floating action button
            final int ownMargin = getResources().getDimensionPixelSize(R.dimen.grid_unit_x2);
            ViewExtensionsKt.setMargin(
                findViewById(R.id.floating),
                insets.left + ownMargin,
                0,
                insets.right + ownMargin,
                insets.bottom + ownMargin
            );

            return windowInsets;
        });
    }

    private void setupAdapter() throws MasterKeyLockedException {
        this.groupDetailAdapter = new GroupDetailAdapter(
            this,
            this.groupModel,
            groupDetailViewModel,
            dependencies.getServiceManager()
        );

        this.groupDetailAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                updateFloatingActionButtonAndMenu();
            }
        });
        this.groupDetailAdapter.setOnClickListener(this);
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_group_detail;
    }

    private void setTitle() {
        this.groupNameEditText.setText(groupDetailViewModel.getGroupName());
    }

    private void launchContactDetail(View view, String identity) {
        if (!this.myIdentity.equals(identity)) {
            Intent intent = new Intent(GroupDetailActivity.this, ContactDetailActivity.class);
            intent.putExtra(AppConstants.INTENT_DATA_CONTACT, identity);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ActivityOptionsCompat options = ActivityOptionsCompat.makeScaleUpAnimation(view, 0, 0, view.getWidth(), view.getHeight());
            ActivityCompat.startActivityForResult(this, intent, ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL, options.toBundle());
        }
    }

    private void sortGroupMembers() {
        final boolean isSortingFirstName = dependencies.getPreferenceService().isContactListSortingFirstName();
        List<ContactModel> contactModels = groupDetailViewModel.getGroupContacts();
        Collections.sort(contactModels, new Comparator<ContactModel>() {
            @Override
            public int compare(ContactModel model1, ContactModel model2) {
                return ContactUtil.getSafeNameString(model1, isSortingFirstName).compareTo(
                    ContactUtil.getSafeNameString(model2, isSortingFirstName)
                );
            }
        });

        if (contactModels.size() > 1 && groupModel.getCreatorIdentity() != null) {
            for (ContactModel currentMember : contactModels) {
                if (groupModel.getCreatorIdentity().equals(currentMember.getIdentity())) {
                    contactModels.remove(currentMember);
                    contactModels.add(0, currentMember);
                    break;
                }
            }
        }

        groupDetailViewModel.setGroupContacts(contactModels);
    }

    private void removeMemberFromGroup(final ContactModel contactModel) {
        if (contactModel != null) {
            this.groupDetailViewModel.removeGroupContact(contactModel);
            updateFloatingActionButtonAndMenu();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.resumePauseHandler != null) {
            this.resumePauseHandler.onPause();
        }
    }

    @Override
    public void onResume() {
        if (this.resumePauseHandler != null) {
            this.resumePauseHandler.onResume();
        }
        super.onResume();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem groupSyncMenu = menu.findItem(R.id.menu_resync);
        MenuItem leaveGroupMenu = menu.findItem(R.id.menu_leave_group);
        MenuItem dissolveGroupMenu = menu.findItem(R.id.menu_dissolve_group);
        MenuItem deleteGroupMenu = menu.findItem(R.id.menu_delete_group);
        MenuItem cloneMenu = menu.findItem(R.id.menu_clone_group);
        MenuItem mediaGalleryMenu = menu.findItem(R.id.menu_gallery);
        MenuItem groupCallMenu = menu.findItem(R.id.menu_group_call);

        if (AppRestrictionUtil.isCreateGroupDisabled(this)) {
            cloneMenu.setVisible(false);
        }

        if (groupModel != null) {
            var groupService = dependencies.getGroupService();
            GroupCallDescription call = dependencies.getGroupCallManager().getCurrentChosenCall(groupModel);
            groupCallMenu.setVisible(GroupCallUtil.qualifiesForGroupCalls(groupService, groupModel) && !hasChanges() && call == null);

            boolean isMember = groupService.isGroupMember(groupModel);
            boolean isCreator = groupService.isGroupCreator(groupModel);
            boolean hasOtherMembers = groupService.countMembersWithoutUser(groupModel) > 0;

            // The clone menu only makes sense if at least one other member is present
            cloneMenu.setVisible(hasOtherMembers);

            // The leave option is only available for members
            leaveGroupMenu.setVisible(isMember && !isCreator);

            // The dissolve option is only available for the creator (if it is not yet dissolved)
            dissolveGroupMenu.setVisible(isMember && isCreator && hasOtherMembers);

            // The delete option is always available
            deleteGroupMenu.setVisible(true);

            // The group sync option is only available for the creator when other members are in the group
            groupSyncMenu.setVisible(isCreator && isMember && hasOtherMembers);

            mediaGalleryMenu.setVisible(!dependencies.getConversationCategoryService().isPrivateChat(GroupUtil.getUniqueIdString(this.groupModel)));

            menu.findItem(R.id.action_send_message).setVisible(!hasChanges());
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_group_detail, menu);

        try {
            MenuBuilder menuBuilder = (MenuBuilder) menu;
            menuBuilder.setOptionalIconsVisible(true);
        } catch (Exception ignored) {
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_send_message) {
            if (groupModel != null) {
                Intent intent = new Intent(this, ComposeMessageActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, groupId);
                intent.putExtra(AppConstants.INTENT_DATA_EDITFOCUS, Boolean.TRUE);
                startActivity(intent);
                finish();
            }
        } else if (itemId == R.id.menu_resync) {
            this.syncGroup();
        } else if (itemId == R.id.menu_leave_group) {
            GenericAlertDialog.newInstance(
                R.string.action_leave_group,
                R.string.really_leave_group_message,
                R.string.ok,
                R.string.cancel
            ).show(getSupportFragmentManager(), DIALOG_TAG_LEAVE_GROUP);
        } else if (itemId == R.id.menu_dissolve_group) {
            GenericAlertDialog.newInstance(
                R.string.action_dissolve_group,
                getString(R.string.really_dissolve_group),
                R.string.ok,
                R.string.cancel
            ).show(getSupportFragmentManager(), DIALOG_TAG_DISSOLVE_GROUP);
        } else if (itemId == R.id.menu_delete_group) {
            @StringRes int title;
            @StringRes int description;
            boolean isGroupCreator = dependencies.getGroupService().isGroupCreator(groupModel);
            boolean isGroupMember = dependencies.getGroupService().isGroupMember(groupModel);
            if (isGroupCreator && isGroupMember) {
                // Group creator and still member
                title = R.string.action_dissolve_and_delete_group;
                description = R.string.delete_my_group_message;
            } else if (isGroupMember) {
                // Just a member
                title = R.string.action_leave_and_delete_group;
                description = R.string.delete_group_message;
            } else {
                // Not even a member anymore
                title = R.string.action_delete_group;
                description = R.string.delete_left_group_message;
            }

            GenericAlertDialog.newInstance(
                    title,
                    description,
                    R.string.ok,
                    R.string.cancel)
                .show(getSupportFragmentManager(), DIALOG_TAG_DELETE_GROUP);
        } else if (itemId == R.id.menu_clone_group) {
            GenericAlertDialog.newInstance(
                    R.string.action_clone_group,
                    R.string.clone_group_message,
                    R.string.yes,
                    R.string.no)
                .show(getSupportFragmentManager(), DIALOG_TAG_CLONE_GROUP_CONFIRM);
        } else if (itemId == R.id.menu_gallery) {
            if (groupId > 0 && !dependencies.getConversationCategoryService().isPrivateChat(GroupUtil.getUniqueIdString(this.groupModel))) {
                Intent mediaGalleryIntent = new Intent(this, MediaGalleryActivity.class);
                mediaGalleryIntent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, groupId);
                startActivity(mediaGalleryIntent);
            }
        } else if (itemId == R.id.menu_group_call) {
            GroupCallUtil.initiateCall(this, groupModel);
        }
        return super.onOptionsItemSelected(item);
    }

    private void leaveGroupAndQuit() {
        leaveOrDeleteGroupAndQuit(GroupLeaveIntent.LEAVE);
    }

    private void dissolveGroupAndQuit() {
        disbandOrDeleteGroupAndQuit(GroupDisbandIntent.DISBAND);
    }

    private void deleteGroupAndQuit() {
        if (!dependencies.getGroupService().isGroupMember(groupModel)) {
            removeGroupAndQuit();
        } else if (dependencies.getGroupService().isGroupCreator(groupModel)) {
            disbandOrDeleteGroupAndQuit(GroupDisbandIntent.DISBAND_AND_REMOVE);
        } else {
            leaveOrDeleteGroupAndQuit(GroupLeaveIntent.LEAVE_AND_REMOVE);
        }
    }

    private void leaveOrDeleteGroupAndQuit(GroupLeaveIntent intent) {
        ch.threema.data.models.GroupModel newGroupModel = getNewGroupModel();
        if (newGroupModel == null) {
            logger.error("Could not leave or delete group: group model missing");
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_leaving_group_internal)
                .show(getSupportFragmentManager());
            return;
        }

        final @NonNull LoadingWithTimeoutDialogXml loadingDialog = LoadingWithTimeoutDialogXml.newInstance(
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            R.string.leaving_group
        );
        loadingDialog.show(getSupportFragmentManager());

        Deferred<GroupFlowResult> leaveGroupFlowResultDeferred = dependencies.getGroupFlowDispatcher()
            .runLeaveGroupFlow(intent, newGroupModel);

        CoroutinesExtensionKt.onCompleted(
            leaveGroupFlowResultDeferred,
            exception -> {
                logger.error("leave-group-flow was completed exceptionally", exception);
                onLeaveGroupFailed(GroupFlowResult.Failure.Other.INSTANCE, loadingDialog);
                return Unit.INSTANCE;
            },
            result -> {
                if (result instanceof GroupFlowResult.Success) {
                    onLeaveGroupSuccess(loadingDialog);
                } else if (result instanceof GroupFlowResult.Failure) {
                    onLeaveGroupFailed((GroupFlowResult.Failure) result, loadingDialog);
                }
                return Unit.INSTANCE;
            }
        );
    }

    @AnyThread
    private void onLeaveGroupSuccess(@NonNull LoadingWithTimeoutDialogXml loadingDialog) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            navigateHome();
        });
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
                .show(getSupportFragmentManager());
        });
    }

    private void disbandOrDeleteGroupAndQuit(GroupDisbandIntent intent) {
        ch.threema.data.models.GroupModel newGroupModel = getNewGroupModel();
        if (newGroupModel == null) {
            logger.error("Could not disband or delete group: group model missing");
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_disbanding_group_internal)
                .show(getSupportFragmentManager());
            return;
        }

        final @NonNull LoadingWithTimeoutDialogXml loadingDialog = LoadingWithTimeoutDialogXml.newInstance(
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            R.string.disbanding_group
        );
        loadingDialog.show(getSupportFragmentManager());

        Deferred<GroupFlowResult> disbandGroupFlowResultDeferred = dependencies.getGroupFlowDispatcher()
            .runDisbandGroupFlow(intent, newGroupModel);

        CoroutinesExtensionKt.onCompleted(
            disbandGroupFlowResultDeferred,
            exception -> {
                logger.error("disband-group-flow was completed exceptionally", exception);
                onDisbandGroupFailed(GroupFlowResult.Failure.Other.INSTANCE, loadingDialog);
                return Unit.INSTANCE;
            },
            result -> {
                if (result instanceof GroupFlowResult.Success) {
                    RuntimeUtil.runOnUiThread(this::finish);
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
                .show(getSupportFragmentManager());
        });
    }

    private void removeGroupAndQuit() {
        ch.threema.data.models.GroupModel newGroupModel = getNewGroupModel();
        if (newGroupModel == null) {
            logger.error("Cannot remove group: group model is null");
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_removing_group_internal)
                .show(getSupportFragmentManager());
            return;
        }

        final @NonNull LoadingWithTimeoutDialogXml loadingDialog = LoadingWithTimeoutDialogXml.newInstance(
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            R.string.removing_group
        );
        loadingDialog.show(getSupportFragmentManager());

        Deferred<GroupFlowResult> removeGroupFlowResultDeferred = dependencies.getGroupFlowDispatcher()
            .runRemoveGroupFlow(newGroupModel);

        CoroutinesExtensionKt.onCompleted(
            removeGroupFlowResultDeferred,
            exception -> {
                logger.error("remove-group-flow was completed exceptionally", exception);
                RuntimeUtil.runOnUiThread(this::finish);
                return Unit.INSTANCE;
            },
            groupFlowResult -> {
                if (groupFlowResult instanceof GroupFlowResult.Success) {
                    RuntimeUtil.runOnUiThread(this::finish);
                } else if (groupFlowResult instanceof GroupFlowResult.Failure) {
                    onRemoveGroupFailed(
                        (GroupFlowResult.Failure) groupFlowResult,
                        loadingDialog
                    );
                }
                return Unit.INSTANCE;
            }
        );
    }

    @AnyThread
    private void onRemoveGroupFailed(
        @NonNull GroupFlowResult.Failure removeGroupFlowResultFailure,
        @NonNull LoadingWithTimeoutDialogXml loadingDialog
    ) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            final @StringRes int errorMessageRes;
            if (removeGroupFlowResultFailure instanceof GroupFlowResult.Failure.Network) {
                errorMessageRes = R.string.error_removing_group_network;
            } else {
                errorMessageRes = R.string.error_removing_group_internal;
            }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getSupportFragmentManager());
        });
    }

    @Nullable
    private ch.threema.data.models.GroupModel getNewGroupModel() {
        ch.threema.data.models.GroupModel newGroupModel = dependencies.getGroupModelRepository().getByCreatorIdentityAndId(
            groupModel.getCreatorIdentity(),
            groupModel.getApiGroupId()
        );

        if (newGroupModel == null) {
            logger.error("New group model is null");
        }

        return newGroupModel;
    }

    private void cloneGroup(final String newGroupName) {
        final @NonNull LoadingWithTimeoutDialogXml loadingDialog = LoadingWithTimeoutDialogXml.newInstance(
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            R.string.creating_group
        );
        loadingDialog.show(getSupportFragmentManager(), null);

        Deferred<GroupFlowResult> cloneGroupFlowResultDeferred = dependencies.getGroupFlowDispatcher().runCreateGroupFlow(
            this,
            new GroupCreateProperties(
                newGroupName,
                CheckedProfilePicture.getOrConvertFromBitmap(dependencies.getGroupService().getAvatar(groupModel, true, false)),
                Set.of(dependencies.getGroupService().getGroupMemberIdentities(groupModel))
            )
        );

        CoroutinesExtensionKt.onCompleted(
            cloneGroupFlowResultDeferred,
            exception -> {
                logger.error("The create-group-flow failed exceptionally", exception);
                onGroupCloneFailed(
                    GroupFlowResult.Failure.Other.INSTANCE,
                    loadingDialog
                );
                return Unit.INSTANCE;
            },
            groupFlowResult -> {
                if (groupFlowResult instanceof GroupFlowResult.Success) {
                    onGroupClonedSuccessfully(
                        ((GroupFlowResult.Success) groupFlowResult).getGroupModel(),
                        loadingDialog
                    );
                } else if (groupFlowResult instanceof GroupFlowResult.Failure) {
                    onGroupCloneFailed(
                        (GroupFlowResult.Failure) groupFlowResult,
                        loadingDialog
                    );
                }
                return Unit.INSTANCE;
            }
        );
    }

    @AnyThread
    private void onGroupClonedSuccessfully(
        @NonNull ch.threema.data.models.GroupModel groupModel,
        @NonNull LoadingWithTimeoutDialogXml loadingDialog
    ) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            Intent intent = new Intent(GroupDetailActivity.this, ComposeMessageActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, groupModel.getDatabaseId());
            startActivity(intent);
            finish();
        });
    }

    @AnyThread
    private void onGroupCloneFailed(
        @NonNull GroupFlowResult.Failure createGroupFlowResultFailure,
        @NonNull LoadingWithTimeoutDialogXml loadingDialog
    ) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            final @StringRes int errorMessageRes;
            if (createGroupFlowResultFailure instanceof GroupFlowResult.Failure.Network) {
                errorMessageRes = R.string.error_cloning_group_network;
            } else {
                errorMessageRes = R.string.error_cloning_group_internal;
            }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getSupportFragmentManager());
        });
    }

    private void showConversation(String identity) {
        Intent intent = new Intent(this, ComposeMessageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(AppConstants.INTENT_DATA_CONTACT, identity);
        startActivity(intent);
    }

    private void addNewMembers() {
        Intent intent = new Intent(GroupDetailActivity.this, GroupAddActivity.class);
        IntentDataUtil.append(groupModel, intent);
        IntentDataUtil.append(groupDetailViewModel.getGroupContacts(), intent);
        startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_GROUP_ADD);
    }

    private void syncGroup() {
        ch.threema.data.models.GroupModel newGroupModel = dependencies.getGroupModelRepository().getByCreatorIdentityAndId(
            groupModel.getCreatorIdentity(),
            groupModel.getApiGroupId()
        );
        if (newGroupModel == null) {
            logger.error("Failed to resync group: New group model is null");
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_resyncing_group_internal)
                .show(getSupportFragmentManager());
            return;
        }

        Deferred<GroupFlowResult> resyncGroupFlowResultDeferred = dependencies.getGroupFlowDispatcher()
            .runGroupResyncFlow(newGroupModel);

        final @NonNull LoadingWithTimeoutDialogXml loadingDialog = LoadingWithTimeoutDialogXml.newInstance(
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            R.string.resyncing_group
        );
        loadingDialog.show(getSupportFragmentManager());

        CoroutinesExtensionKt.onCompleted(
            resyncGroupFlowResultDeferred,
            exception -> {
                logger.error("resync-group-flow was completed exceptionally", exception);
                onResyncGroupFailed(GroupFlowResult.Failure.Other.INSTANCE, loadingDialog);
                return Unit.INSTANCE;
            },
            result -> {
                if (result instanceof GroupFlowResult.Success) {
                    RuntimeUtil.runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        Toast.makeText(GroupDetailActivity.this, R.string.group_was_synchronized, Toast.LENGTH_SHORT).show();
                    });
                } else if (result instanceof GroupFlowResult.Failure) {
                    onResyncGroupFailed((GroupFlowResult.Failure) result, loadingDialog);
                }
                return Unit.INSTANCE;
            }
        );
    }

    @AnyThread
    private void onResyncGroupFailed(
        @NonNull GroupFlowResult.Failure failureResult,
        @NonNull LoadingWithTimeoutDialogXml loadingDialog
    ) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            final @StringRes int errorMessageRes;
            if (failureResult instanceof GroupFlowResult.Failure.Network) {
                errorMessageRes = R.string.error_resyncing_group_network;
            } else {
                errorMessageRes = R.string.error_resyncing_group_internal;
            }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getSupportFragmentManager());
        });
    }

    private void saveGroupSettings() {
        ch.threema.data.models.GroupModel newGroupModel = dependencies.getGroupModelRepository().getByGroupIdentity(
            new GroupIdentity(
                groupModel.getCreatorIdentity(),
                groupModel.getApiGroupId().toLong()
            )
        );
        if (newGroupModel == null) {
            logger.error("Group model does not exist");
            showGroupUpdateErrorInternal();
            return;
        }

        GroupModelData groupModelData = newGroupModel.getData();
        if (groupModelData == null) {
            logger.warn("Group model data is null");
            showGroupUpdateErrorInternal();
            return;
        }

        if (groupNameEditText.getText() != null) {
            this.groupDetailViewModel.setGroupName(groupNameEditText.getText().toString());
        } else {
            this.groupDetailViewModel.setGroupName("");
        }

        Set<String> updatedIdentities = new HashSet<>();
        Collections.addAll(updatedIdentities, groupDetailViewModel.getGroupIdentities());
        updatedIdentities.remove(myIdentity);

        GroupChanges groupChanges = new GroupChanges(
            getGroupNameChange(),
            getProfilePictureChange(newGroupModel),
            updatedIdentities,
            groupModelData
        );

        final @NonNull LoadingWithTimeoutDialogXml loadingDialog = LoadingWithTimeoutDialogXml.newInstance(
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            R.string.updating_group
        );
        loadingDialog.show(getSupportFragmentManager());

        Deferred<GroupFlowResult> updateResultDeferred = dependencies.getGroupFlowDispatcher().runUpdateGroupFlow(groupChanges, newGroupModel);

        CoroutinesExtensionKt.onCompleted(
            updateResultDeferred,
            exception -> {
                logger.error("The update-group-flow failed exceptionally", exception);
                onGroupUpdateFailed(
                    GroupFlowResult.Failure.Other.INSTANCE,
                    loadingDialog
                );
                return Unit.INSTANCE;
            },
            groupFlowResult -> {
                if (groupFlowResult instanceof GroupFlowResult.Success) {
                    onGroupUpdateSucceeded(loadingDialog);
                } else if (groupFlowResult instanceof GroupFlowResult.Failure) {
                    onGroupUpdateFailed(
                        (GroupFlowResult.Failure) groupFlowResult,
                        loadingDialog
                    );
                }
                return Unit.INSTANCE;
            }
        );
    }

    @AnyThread
    private void onGroupUpdateSucceeded(@NonNull LoadingWithTimeoutDialogXml loadingDialog) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            finish();
        });
    }

    @AnyThread
    private void onGroupUpdateFailed(
        @NonNull GroupFlowResult.Failure resultFailed,
        @NonNull LoadingWithTimeoutDialogXml loadingDialog
    ) {
        RuntimeUtil.runOnUiThread(() -> {
            loadingDialog.dismiss();
            final @StringRes int errorMessageRes;
            if (resultFailed instanceof GroupFlowResult.Failure.Network) {
                errorMessageRes = R.string.error_updating_group_network;
            } else {
                errorMessageRes = R.string.error_updating_group_internal;
            }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getSupportFragmentManager());
        });
    }

    @UiThread
    private void showGroupUpdateErrorInternal() {
        SimpleStringAlertDialog
            .newInstance(R.string.error, R.string.error_updating_group_internal)
            .show(getSupportFragmentManager());
    }

    @Nullable
    private String getGroupNameChange() {
        @NonNull String newGroupName = groupDetailViewModel.getGroupName();
        @NonNull String oldGroupName = groupModel.getName() != null ?
            groupModel.getName() : "";

        if (!newGroupName.equals(oldGroupName)) {
            return newGroupName;
        } else {
            return null;
        }
    }

    @Nullable
    private ProfilePictureChange getProfilePictureChange(ch.threema.data.models.GroupModel groupModel) {
        if (!groupDetailViewModel.hasAvatarChanges()) {
            return null;
        }

        CheckedProfilePicture profilePicture = CheckedProfilePicture.getOrConvertFromFile(groupDetailViewModel.getAvatarFile());
        byte[] newAvatarBytes = profilePicture != null ? profilePicture.getProfilePictureBytes() : null;

        byte[] oldAvatarBytes;
        try {
            oldAvatarBytes = dependencies.getFileService().getGroupAvatarBytes(groupModel);
        } catch (Exception e) {
            logger.error("Could not get group avatar", e);
            oldAvatarBytes = null;
        }

        if (Arrays.equals(newAvatarBytes, oldAvatarBytes)) {
            return null;
        } else if (newAvatarBytes != null) {
            return new SetProfilePicture(profilePicture, null);
        } else {
            return RemoveProfilePicture.INSTANCE;
        }
    }

    private void showCloneDialog() {
        TextEntryDialog.newInstance(
            R.string.action_clone_group,
            R.string.name,
            R.string.ok,
            R.string.cancel,
            groupModel.getName(),
            0,
            0
        ).show(getSupportFragmentManager(), DIALOG_TAG_CLONE_GROUP);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ThreemaActivity.ACTIVITY_ID_GROUP_ADD) {
                // some users were added
                groupDetailViewModel.addGroupContacts(IntentDataUtil.getContactIdentities(data));
                sortGroupMembers();
            } else {
                if (this.avatarEditView != null) {
                    this.avatarEditView.onActivityResult(requestCode, resultCode, data);
                }
                super.onActivityResult(requestCode, resultCode, data);
            }
        }

        if (requestCode == ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL) {
            // contacts may have been edited
            sortGroupMembers();
        }
    }

    @Override
    public void onDestroy() {
        ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
        ListenerManager.groupListeners.remove(this.groupListener);
        ListenerManager.contactListeners.remove(this.contactListener);

        if (this.resumePauseHandler != null) {
            this.resumePauseHandler.onDestroy(this);
        }

        super.onDestroy();
    }

    @Override
    public void onClick(String tag, int which, Object object) {
        SelectorInfo selectorInfo = (SelectorInfo) object;

        // click on selector
        if (selectorInfo.contactModel != null) {
            switch (selectorInfo.optionsMap.get(which)) {
                case SELECTOR_OPTION_CONTACT_DETAIL:
                    logger.info("Contact details button clicked");
                    launchContactDetail(selectorInfo.view, selectorInfo.contactModel.getIdentity());
                    break;
                case SELECTOR_OPTION_CHAT:
                    logger.info("Chat button clicked");
                    showConversation(selectorInfo.contactModel.getIdentity());
                    finish();
                    break;
                case SELECTOR_OPTION_REMOVE:
                    logger.info("Kick user button clicked");
                    removeMemberFromGroup(selectorInfo.contactModel);
                    break;
                case SELECTOR_OPTION_CALL:
                    logger.info("Call button clicked");
                    VoipUtil.initiateCall(this, selectorInfo.contactModel, false, null);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onCancel(String tag) {
    }

    @Override
    public void onYes(@NonNull String tag, @NonNull String text) {
        // text entry dialog
        switch (tag) {
            case DIALOG_TAG_CLONE_GROUP:
                logger.info("Clone group dialog confirmed");
                cloneGroup(text);
                break;
            default:
                break;
        }
    }

    public void onGroupDescChange(String newGroupDesc) {
        if (newGroupDesc.equals(groupModel.getGroupDesc())) {
            return;
        }
        groupDetailViewModel.setGroupDescTimestamp(new Date());


        // delete group description
        if (newGroupDesc.isEmpty()) {
            removeGroupDescription();
            return;
        }

        // create or update description
        groupDetailViewModel.setGroupDesc(newGroupDesc);
        if (groupDetailViewModel.getGroupDescState() == NONE) {
            groupDetailViewModel.setGroupDescState(COLLAPSED);
        }
        groupDetailAdapter.updateGroupDescriptionLayout();
    }


    @Override
    public void onNo(String tag) {
        // do nothing
    }

    @Override
    public void onYes(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_LEAVE_GROUP:
                logger.info("Leave group dialog confirmed");
                leaveGroupAndQuit();
                break;
            case DIALOG_TAG_DISSOLVE_GROUP:
                logger.info("Dissolve group dialog confirmed");
                dissolveGroupAndQuit();
                break;
            case DIALOG_TAG_DELETE_GROUP:
                logger.info("Delete group dialog confirmed");
                deleteGroupAndQuit();
                break;
            case DIALOG_TAG_QUIT:
                logger.info("Save group changes dialog confirmed");
                saveGroupSettings();
                break;
            case DIALOG_TAG_CLONE_GROUP_CONFIRM:
                logger.info("Clone group info dialog confirmed");
                showCloneDialog();
                break;
            default:
                break;
        }
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    public void handleOnBackPressed() {
        if (this.operationMode == MODE_EDIT && hasChanges()) {
            logger.info("Showing warning about unsaved changes");
            GenericAlertDialog.newInstance(
                    R.string.leave,
                    R.string.save_group_changes,
                    R.string.yes,
                    R.string.no,
                    R.string.cancel,
                    0)
                .show(getSupportFragmentManager(), DIALOG_TAG_QUIT);
        } else {
            finish();
        }
    }

    @Override
    public void onNo(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_QUIT:
                finish();
                break;
            default:
                break;
        }
    }

    private boolean hasChanges() {
        return groupDetailViewModel.hasMemberChanges() || hasGroupNameChanges() || groupDetailViewModel.hasAvatarChanges();
    }

    private boolean hasGroupNameChanges() {
        Editable groupNameEditable = groupNameEditText.getText();
        String editedGroupNameText = groupNameEditable != null ? groupNameEditable.toString() : "";

        String currentGroupName = groupModel.getName() != null ? groupModel.getName() : "";

        return !editedGroupNameText.equals(currentGroupName);
    }

    private void updateFloatingActionButtonAndMenu() {
        if (this.groupDetailAdapter == null) {
            logger.debug("Required instances not available");
            return;
        }

        if (this.floatingActionButton == null) {
            return;
        }

        if (dependencies.getGroupService().isGroupCreator(this.groupModel) && hasChanges()) {
            this.floatingActionButton.show();
        } else {
            this.floatingActionButton.hide();
        }
        invalidateOptionsMenu();
    }

    private void navigateHome() {
        Intent intent = new Intent(GroupDetailActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        ActivityCompat.finishAffinity(GroupDetailActivity.this);
        overridePendingTransition(0, 0);
    }

    /* callbacks from MyAvatarView */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (this.avatarEditView != null) {
            this.avatarEditView.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onGroupMemberClick(View v, @NonNull ContactModel contactModel) {
        logger.info("Group member clicked");
        String identity = contactModel.getIdentity();
        String shortName = NameUtil.getShortName(contactModel);

        ArrayList<SelectorDialogItem> items = new ArrayList<>();
        ArrayList<Integer> optionsMap = new ArrayList<>();

        items.add(new SelectorDialogItem(getString(R.string.show_contact), R.drawable.ic_outline_visibility));
        optionsMap.add(SELECTOR_OPTION_CONTACT_DETAIL);

        if (!TestUtil.compare(myIdentity, identity)) {
            items.add(new SelectorDialogItem(String.format(getString(R.string.chat_with), shortName), R.drawable.ic_chat_bubble));
            optionsMap.add(SELECTOR_OPTION_CHAT);

            if (ContactUtil.canReceiveVoipMessages(contactModel, dependencies.getBlockedIdentitiesService())
                && ConfigUtils.isCallsEnabled()
            ) {
                items.add(new SelectorDialogItem(String.format(getString(R.string.call_with), shortName), R.drawable.ic_phone_locked_outline));
                optionsMap.add(SELECTOR_OPTION_CALL);
            }

            if (operationMode == MODE_EDIT) {
                if (groupModel != null && !TestUtil.compare(groupModel.getCreatorIdentity(), identity)) {
                    items.add(new SelectorDialogItem(String.format(getString(R.string.kick_user_from_group), shortName), R.drawable.ic_person_remove_outline));
                    optionsMap.add(SELECTOR_OPTION_REMOVE);
                }
            }
            SelectorDialog selectorDialog = SelectorDialog.newInstance(null, items, null);
            SelectorInfo selectorInfo = new SelectorInfo();
            selectorInfo.contactModel = contactModel;
            selectorInfo.view = v;
            selectorInfo.optionsMap = optionsMap;
            selectorDialog.setData(selectorInfo);
            try {
                selectorDialog.show(getSupportFragmentManager(), DIALOG_TAG_CHOOSE_ACTION);
            } catch (IllegalStateException e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    public void onGroupDescriptionEditClick() {
        logger.info("Edit description button clicked");
        showGroupDescEditDialog();
    }

    @Override
    public void onAddMembersClick(View v) {
        logger.info("Add member button clicked");
        addNewMembers();
    }

    // hide keyboard on older devices after ok clicked when group description changed
    public void hideKeyboard() {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    public void showGroupDescEditDialog() {
        String groupDescText = groupDetailViewModel.getGroupDesc();
        GroupDescEditDialog descriptionDialog = GroupDescEditDialog.newGroupDescriptionInstance(R.string.change_group_description, groupDescText, newGroupDesc -> {
            hideKeyboard(); // is used for older devices
            onGroupDescChange(newGroupDesc);
        });
        descriptionDialog.show(getSupportFragmentManager(), DIALOG_TAG_CHANGE_GROUP_DESC);
    }

    private void removeGroupDescription() {
        groupDetailViewModel.setGroupDesc(null);
        groupDetailViewModel.setGroupDescState(NONE);
        groupDetailAdapter.updateGroupDescriptionLayout();
    }

    private void observeNewGroupModel() {
        LiveData<GroupModelData> groupModelDataLiveData = groupDetailViewModel.group;
        if (groupModelDataLiveData == null) {
            ch.threema.data.models.GroupModel newGroupModel =
                dependencies.getGroupModelRepository().getByLocalGroupDbId(this.groupId);

            if (newGroupModel == null) {
                logger.error("Group model is null");
                finish();
                return;
            }

            groupDetailViewModel.setGroup(newGroupModel);
            groupModelDataLiveData = groupDetailViewModel.group;
            if (groupModelDataLiveData == null) {
                logger.error("Live data is null");
                finish();
                return;
            }
        }

        groupModelDataLiveData.observe(
            this,
            this::onGroupModelDataUpdate
        );
    }

    /**
     * Updates the view with the new provided data. If the data is null, the activity is finished.
     * Note that currently only the group name is updated.
     */
    private void onGroupModelDataUpdate(@Nullable GroupModelData data) {
        logger.debug("New group model data observed: {}", data);
        if (data == null) {
            finish();
            return;
        }

        // Set new group name
        groupDetailViewModel.setGroupName(data.name);
        setTitle();
    }

}
