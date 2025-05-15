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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

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

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.GroupDetailAdapter;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.GroupDescEditDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.dialogs.ShowOnceDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.emojis.EmojiEditText;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.groupflows.GroupChanges;
import ch.threema.app.groupflows.GroupCreateProperties;
import ch.threema.app.groupflows.GroupDisbandIntent;
import ch.threema.app.groupflows.GroupLeaveIntent;
import ch.threema.app.groupflows.ProfilePicture;
import ch.threema.app.grouplinks.GroupLinkOverviewActivity;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.protocol.ProfilePictureChange;
import ch.threema.app.protocol.RemoveProfilePicture;
import ch.threema.app.protocol.SetProfilePicture;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.services.group.GroupInviteService;
import ch.threema.app.ui.AvatarEditView;
import ch.threema.app.ui.GroupDetailViewModel;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.GroupCallUtilKt;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.groupcall.GroupCallDescription;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupIdentity;
import ch.threema.data.models.GroupModelData;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import kotlinx.coroutines.Deferred;

import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.COLLAPSED;
import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.NONE;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

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
    private static final String DIALOG_TAG_RESYNC_GROUP = "resyncGroup";
    private static final String DIALOG_TAG_DELETE_GROUP = "delG";
    private static final String DIALOG_TAG_CLONE_GROUP = "cg";
    private static final String DIALOG_TAG_CHANGE_GROUP_DESC = "cgDesc";
    private static final String DIALOG_TAG_CLONE_GROUP_CONFIRM = "cgc";
    public static final String DIALOG_SHOW_ONCE_RESET_LINK_INFO = "resetGroupLink";
    private static final String RUN_ON_ACTIVE_RELOAD = "reload";

    private static final int SELECTOR_OPTION_CONTACT_DETAIL = 0;
    private static final int SELECTOR_OPTION_CHAT = 1;
    private static final int SELECTOR_OPTION_CALL = 2;
    private static final int SELECTOR_OPTION_REMOVE = 3;

    private GroupInviteService groupInviteService;
    private DeviceService deviceService;
    private BlockedIdentitiesService blockedIdentitiesService;
    private GroupCallManager groupCallManager;

    private GroupModel groupModel;
    private GroupDetailViewModel groupDetailViewModel;
    private GroupDetailAdapter groupDetailAdapter;

    private EmojiEditText groupNameEditText;
    private ResumePauseHandler resumePauseHandler;
    private AvatarEditView avatarEditView;
    private ExtendedFloatingActionButton floatingActionButton;

    private String myIdentity;
    private int operationMode;
    private int groupId;
    private boolean hasMemberChanges = false, hasAvatarChanges = false;

    private final ResumePauseHandler.RunIfActive runIfActiveUpdate = new ResumePauseHandler.RunIfActive() {
        @Override
        public void runOnUiThread() {
            groupDetailViewModel.setGroupName(groupModel.getName());

            groupDetailViewModel.setGroupIdentities(groupService.getGroupMemberIdentities(groupModel));
            sortGroupMembers();
        }
    };

    private final AvatarEditView.AvatarEditListener avatarEditViewListener = new AvatarEditView.AvatarEditListener() {
        @Override
        public void onAvatarSet(File avatarFile1) {
            groupDetailViewModel.setAvatarFile(avatarFile1);
            groupDetailViewModel.setIsAvatarRemoved(false);
            hasAvatarChanges = true;
            updateFloatingActionButtonAndMenu();
        }

        @Override
        public void onAvatarRemoved() {
            groupDetailViewModel.setAvatarFile(null);
            groupDetailViewModel.setIsAvatarRemoved(true);
            avatarEditView.setDefaultAvatar(null, groupModel);
            hasAvatarChanges = true;
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
        public void onSortingChanged() {
        }

        @Override
        public void onNameFormatChanged() {
        }

        @Override
        public void onAvatarSettingChanged() {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }

        @Override
        public void onInactiveContactsSettingChanged() {
        }

        @Override
        public void onNotificationSettingChanged(String uid) {
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

        @Override
        public void onUpdate(@NonNull GroupIdentity groupIdentity) {
            //ignore
        }

        @Override
        public void onLeave(@NonNull GroupIdentity groupIdentity) {
            // ignore
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        this.myIdentity = userService.getIdentity();

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            finish();
            return;
        }

        ConfigUtils.configureTransparentStatusBar(this);

        this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this);
        this.groupDetailViewModel = new ViewModelProvider(this).get(GroupDetailViewModel.class);

        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        this.avatarEditView = findViewById(R.id.avatar_edit_view);
        CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        this.floatingActionButton = findViewById(R.id.floating);
        RecyclerView groupDetailRecyclerView = findViewById(R.id.group_members_list);
        collapsingToolbar.setTitle(" ");
        this.groupNameEditText = findViewById(R.id.group_title);

        // services
        try {
            this.deviceService = serviceManager.getDeviceService();
            this.blockedIdentitiesService = serviceManager.getBlockedIdentitiesService();
            this.groupInviteService = serviceManager.getGroupInviteService();
            this.groupCallManager = serviceManager.getGroupCallManager();
        } catch (ThreemaException e) {
            logger.error("Exception, could not get required services", e);
            finish();
            return;
        }

        if (this.deviceService == null || this.blockedIdentitiesService == null) {
            finish();
            return;
        }

        groupId = getIntent().getIntExtra(ThreemaApplication.INTENT_DATA_GROUP_DATABASE_ID, 0);
        if (this.groupId == 0) {
            finish();
        }
        this.groupModel = groupService.getById(this.groupId);

        observeNewGroupModel();

        if (savedInstanceState == null) {
            // new instance
            this.groupDetailViewModel.setGroupContacts(this.contactService.getByIdentities(groupService.getGroupMemberIdentities(this.groupModel)));
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

        ((AppBarLayout) findViewById(R.id.appbar)).addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                if (verticalOffset == 0) {
                    if (!floatingActionButton.isExtended()) {
                        floatingActionButton.extend();
                    }
                } else if (floatingActionButton.isExtended()) {
                    floatingActionButton.shrink();
                }
            }
        });

        this.sortGroupMembers();
        setTitle();
        setHasMemberChanges(false);

        if (groupService.isGroupCreator(groupModel) && groupService.isGroupMember(groupModel)) {
            operationMode = MODE_EDIT;
            actionBar.setHomeButtonEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);

            floatingActionButton.setOnClickListener(v -> {
                logger.info("FAB (save group settings) clicked");
                saveGroupSettings();
            });
            groupNameEditText.setMaxByteSize(GroupModel.GROUP_NAME_MAX_LENGTH_BYTES);
            groupNameEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
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
            if (!groupService.isGroupMember(groupModel)) {
                // Get the paint flags and add the strike through flag
                int paintFlags = groupNameEditText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG;
                groupNameEditText.setPaintFlags(paintFlags);
            }
        }

        groupDetailRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        try {
            setupAdapter();
        } catch (MasterKeyLockedException | FileSystemNotPresentException e) {
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

        @ColorInt int color = groupService.getAvatarColor(groupModel);
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

    private void setupAdapter() throws MasterKeyLockedException, FileSystemNotPresentException {
        Runnable onCloneGroupRunnable = null;
        if (groupService.isOrphanedGroup(groupModel) && groupService.countMembersWithoutUser(groupModel) > 0) {
            onCloneGroupRunnable = this::showCloneDialog;
        }

        this.groupDetailAdapter = new GroupDetailAdapter(
            this,
            this.groupModel,
            groupDetailViewModel,
            serviceManager,
            onCloneGroupRunnable
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
            intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ActivityOptionsCompat options = ActivityOptionsCompat.makeScaleUpAnimation(view, 0, 0, view.getWidth(), view.getHeight());
            ActivityCompat.startActivityForResult(this, intent, ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL, options.toBundle());
        }
    }

    private void sortGroupMembers() {
        final boolean isSortingFirstName = preferenceService.isContactListSortingFirstName();
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
            setHasMemberChanges(true);
        }
    }

    private void setHasMemberChanges(boolean hasChanges) {
        this.hasMemberChanges = hasChanges;
        updateFloatingActionButtonAndMenu();
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
        MenuItem groupLinkMenu = menu.findItem(R.id.menu_group_links_manage);
        MenuItem groupCallMenu = menu.findItem(R.id.menu_group_call);

        if (AppRestrictionUtil.isCreateGroupDisabled(this)) {
            cloneMenu.setVisible(false);
        }

        if (groupModel != null) {
            GroupCallDescription call = groupCallManager.getCurrentChosenCall(groupModel);
            groupCallMenu.setVisible(GroupCallUtilKt.qualifiesForGroupCalls(groupService, groupModel) && !hasChanges() && call == null);

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

            // The group link menu is only available for the creator and if enabled in configuration
            groupLinkMenu.setVisible(isCreator && isMember && ConfigUtils.supportsGroupLinks());

            mediaGalleryMenu.setVisible(
                conversationCategoryService != null &&
                    !conversationCategoryService.isPrivateChat(GroupUtil.getUniqueIdString(this.groupModel))
            );

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
        } else if (itemId == R.id.menu_group_links_manage) {
            Intent groupLinkOverviewIntent = new Intent(this, GroupLinkOverviewActivity.class);
            groupLinkOverviewIntent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_DATABASE_ID, groupId);
            startActivityForResult(groupLinkOverviewIntent, ThreemaActivity.ACTIVITY_ID_MANAGE_GROUP_LINKS);
        } else if (itemId == R.id.action_send_message) {
            if (groupModel != null) {
                Intent intent = new Intent(this, ComposeMessageActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_DATABASE_ID, groupId);
                intent.putExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, Boolean.TRUE);
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
            boolean isGroupCreator = groupService.isGroupCreator(groupModel);
            boolean isGroupMember = groupService.isGroupMember(groupModel);
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
            if (groupId > 0 && conversationCategoryService != null &&
                !conversationCategoryService.isPrivateChat(GroupUtil.getUniqueIdString(this.groupModel))
            ) {
                Intent mediaGalleryIntent = new Intent(this, MediaGalleryActivity.class);
                mediaGalleryIntent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_DATABASE_ID, groupId);
                startActivity(mediaGalleryIntent);
            }
        } else if (itemId == R.id.menu_group_call) {
            GroupCallUtilKt.initiateCall(this, groupModel);
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
        if (!groupService.isGroupMember(groupModel)) {
            removeGroupAndQuit();
        } else if (groupService.isGroupCreator(groupModel)) {
            disbandOrDeleteGroupAndQuit(GroupDisbandIntent.DISBAND_AND_REMOVE);
        } else {
            leaveOrDeleteGroupAndQuit(GroupLeaveIntent.LEAVE_AND_REMOVE);
        }
    }

    private void leaveOrDeleteGroupAndQuit(GroupLeaveIntent intent) {
        ch.threema.data.models.GroupModel newGroupModel = getNewGroupModel();
        if (newGroupModel == null) {
            return;
        }

        try {
            Deferred<Boolean> result = serviceManager.getGroupFlowDispatcher().runLeaveGroupFlow(
                getSupportFragmentManager(),
                intent,
                newGroupModel
            );

            result.invokeOnCompletion(throwable -> {
                if (result.getCompleted() != Boolean.TRUE) {
                    logger.error("Could not leave group", throwable);
                    return null;
                }

                navigateHome();
                return null;
            });
        } catch (ThreemaException e) {
            logger.error("Could not leave group", e);
        }
    }

    private void disbandOrDeleteGroupAndQuit(GroupDisbandIntent intent) {
        ch.threema.data.models.GroupModel newGroupModel = getNewGroupModel();
        if (newGroupModel == null) {
            return;
        }

        try {
            Deferred<Boolean> result = serviceManager.getGroupFlowDispatcher().runDisbandGroupFlow(
                getSupportFragmentManager(),
                intent,
                newGroupModel
            );

            result.invokeOnCompletion(throwable -> {
                if (result.getCompleted() != Boolean.TRUE) {
                    logger.error("Could not disband or delete group", throwable);
                }

                finish();

                return null;
            });
        } catch (ThreemaException e) {
            logger.error("Could not leave group", e);
        }
    }

    private void removeGroupAndQuit() {
        ch.threema.data.models.GroupModel newGroupModel = getNewGroupModel();
        if (newGroupModel == null) {
            return;
        }

        try {
            Deferred<Boolean> result = serviceManager.getGroupFlowDispatcher().runRemoveGroupFlow(
                getSupportFragmentManager(),
                newGroupModel
            );

            result.invokeOnCompletion(throwable -> {
                if (throwable != null) {
                    logger.error("Could not remove group", throwable);
                } else {
                    try {
                        if (result.getCompleted() != Boolean.TRUE) {
                            logger.error("Failed to remove group");
                        }
                    } catch (Exception e) {
                        logger.error("Could not check group removal result", e);
                    }
                }

                finish();

                return null;
            });
        } catch (ThreemaException e) {
            logger.error("Could not remove group", e);
        }
    }

    @Nullable
    private ch.threema.data.models.GroupModel getNewGroupModel() {
        ch.threema.data.models.GroupModel newGroupModel = groupModelRepository.getByCreatorIdentityAndId(
            groupModel.getCreatorIdentity(),
            groupModel.getApiGroupId()
        );

        if (newGroupModel == null) {
            logger.error("New group model is null");
        }

        return newGroupModel;
    }

    private void cloneGroup(final String newGroupName) {
        GroupFlowDispatcher groupFlowDispatcher;
        try {
            groupFlowDispatcher = serviceManager.getGroupFlowDispatcher();
        } catch (ThreemaException e) {
            logger.error("Could not get group flow dispatcher", e);
            return;
        }

        Deferred<ch.threema.data.models.GroupModel> groupAddResult =
            groupFlowDispatcher.runCreateGroupFlow(
                getSupportFragmentManager(),
                this,
                new GroupCreateProperties(
                    newGroupName,
                    new ProfilePicture(groupService.getAvatar(groupModel, true, false)),
                    Set.of(groupService.getGroupMemberIdentities(groupModel))
                )
            );

        // TODO(ANDR-3631): Improve result type handling including handling of the throwable
        groupAddResult.invokeOnCompletion(throwable -> {
            ch.threema.data.models.GroupModel groupModel = groupAddResult.getCompleted();
            if (groupModel != null) {
                Intent intent = new Intent(GroupDetailActivity.this, ComposeMessageActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_DATABASE_ID, groupModel.getDatabaseId());
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(GroupDetailActivity.this, getString(R.string.error_creating_group) + ": " + getString(R.string.internet_connection_required), Toast.LENGTH_LONG).show();
            }
            return null;
        });
    }

    private void showConversation(String identity) {
        Intent intent = new Intent(this, ComposeMessageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
        startActivity(intent);
    }

    private void addNewMembers() {
        Intent intent = new Intent(GroupDetailActivity.this, GroupAddActivity.class);
        IntentDataUtil.append(groupModel, intent);
        IntentDataUtil.append(groupDetailViewModel.getGroupContacts(), intent);
        startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_GROUP_ADD);
    }

    private void syncGroup() {
        ch.threema.data.models.GroupModel newGroupModel =
            groupModelRepository.getByCreatorIdentityAndId(groupModel.getCreatorIdentity(),
                groupModel.getApiGroupId());
        if (newGroupModel == null) {
            logger.error("New group model is null");
            return;
        }

        try {
            GroupFlowDispatcher groupFlowDispatcher = serviceManager.getGroupFlowDispatcher();

            GenericProgressDialog.newInstance(R.string.resync_group, R.string.please_wait)
                .show(getSupportFragmentManager(), DIALOG_TAG_RESYNC_GROUP);

            Deferred<Boolean> result = groupFlowDispatcher.runGroupResyncFlow(newGroupModel);

            result.invokeOnCompletion(throwable -> {
                try {
                    if (result.getCompleted() == Boolean.TRUE) {
                        RuntimeUtil.runOnUiThread(() -> Toast.makeText(
                            GroupDetailActivity.this,
                            R.string.group_was_synchronized,
                            Toast.LENGTH_SHORT
                        ).show());
                    } else {
                        logger.error("Could not synchronize group", throwable);
                        RuntimeUtil.runOnUiThread(() -> Toast.makeText(GroupDetailActivity.this
                            , R.string.error, Toast.LENGTH_SHORT).show());
                    }
                } finally {
                    DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_RESYNC_GROUP, true);
                }
                return null;
            });
        } catch (ThreemaException exception) {
            logger.error("Error while manual group resync", exception);
        }
    }

    private void saveGroupSettings() {
        ch.threema.data.models.GroupModel newGroupModel = groupModelRepository.getByGroupIdentity(
            new GroupIdentity(
                groupModel.getCreatorIdentity(),
                groupModel.getApiGroupId().toLong()
            )
        );
        if (newGroupModel == null) {
            logger.error("Group model does not exist");
            return;
        }

        GroupModelData groupModelData = newGroupModel.getData().getValue();
        if (groupModelData == null) {
            logger.warn("Group model data is null");
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

        GroupFlowDispatcher groupFlowDispatcher;
        try {
            groupFlowDispatcher = serviceManager.getGroupFlowDispatcher();
        } catch (ThreemaException exception) {
            logger.error("Could not get group flow dispatcher");
            return;
        }

        Deferred<Boolean> updateResult = groupFlowDispatcher.runUpdateGroupFlow(
            getSupportFragmentManager(),
            groupChanges,
            newGroupModel
        );

        updateResult.invokeOnCompletion(throwable -> {
            Boolean result = updateResult.getCompleted();
            if (Boolean.TRUE.equals(result)) {
                finish();
            } else {
                SimpleStringAlertDialog.newInstance(R.string.updating_group, getString(R.string.error_creating_group) + ": " + getString(R.string.internet_connection_required)).show(getSupportFragmentManager(), "er");
            }
            return null;
        });
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
        if (!hasAvatarChanges) {
            return null;
        }

        Bitmap avatar = groupDetailViewModel.getAvatarFile() != null ? BitmapFactory.decodeFile(groupDetailViewModel.getAvatarFile().getPath()) : null;
        byte[] newAvatarBytes = null;
        if (avatar != null) {
            newAvatarBytes = BitmapUtil.bitmapToByteArray(avatar, Bitmap.CompressFormat.PNG, 100);
        }

        byte[] oldAvatarBytes;
        try {
            oldAvatarBytes = fileService.getGroupAvatarBytes(groupModel);
        } catch (Exception e) {
            logger.error("Could not get group avatar", e);
            oldAvatarBytes = null;
        }

        if (Arrays.equals(newAvatarBytes, oldAvatarBytes)) {
            return null;
        } else if (newAvatarBytes != null) {
            return new SetProfilePicture(newAvatarBytes, null);
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
                0)
            .show(getSupportFragmentManager(), DIALOG_TAG_CLONE_GROUP);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ThreemaActivity.ACTIVITY_ID_GROUP_ADD) {
                // some users were added
                groupDetailViewModel.addGroupContacts(IntentDataUtil.getContactIdentities(data));
                sortGroupMembers();
                setHasMemberChanges(true);
            } else if (this.groupService.isGroupCreator(this.groupModel) && requestCode == ThreemaActivity.ACTIVITY_ID_MANAGE_GROUP_LINKS) {
                // make sure we reset the default link switch if the default link was deleted
                groupDetailAdapter.notifyDataSetChanged();
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
        return hasMemberChanges || hasGroupNameChanges() || hasAvatarChanges;
    }

    private boolean hasGroupNameChanges() {
        Editable groupNameEditable = groupNameEditText.getText();
        String editedGroupNameText = groupNameEditable != null ? groupNameEditable.toString() : "";

        String currentGroupName = groupModel.getName() != null ? groupModel.getName() : "";

        return !editedGroupNameText.equals(currentGroupName);
    }

    private void updateFloatingActionButtonAndMenu() {
        if (this.groupService == null ||
            this.groupDetailAdapter == null) {
            logger.debug("Required instances not available");
            return;
        }

        if (this.floatingActionButton == null) {
            return;
        }

        if (this.groupService.isGroupCreator(this.groupModel) && hasChanges()) {
            this.floatingActionButton.show(new ExtendedFloatingActionButton.OnChangedCallback() {
                @Override
                public void onShown(ExtendedFloatingActionButton extendedFab) {
                    super.onShown(extendedFab);
                    adjustEditTextLocation(true);
                }
            });
        } else {
            this.floatingActionButton.hide(new ExtendedFloatingActionButton.OnChangedCallback() {
                @Override
                public void onHidden(ExtendedFloatingActionButton extendedFab) {
                    super.onHidden(extendedFab);
                    adjustEditTextLocation(false);
                }
            });
        }
        invalidateOptionsMenu();
    }

    synchronized private void adjustEditTextLocation(boolean show) {
        floatingActionButton.post(() -> {
            // check if FAB overlaps the group name
            if (show) {
                int[] editTextLocation = new int[2];
                int[] fabLocation = new int[2];

                groupNameEditText.getLocationInWindow(editTextLocation);
                floatingActionButton.getLocationInWindow(fabLocation);

                Rect editTextRect = new Rect(editTextLocation[0], editTextLocation[1],
                    editTextLocation[0] + groupNameEditText.getMeasuredWidth(), editTextLocation[1] + groupNameEditText.getMeasuredHeight());
                Rect fabRect = new Rect(fabLocation[0], fabLocation[1],
                    fabLocation[0] + floatingActionButton.getMeasuredWidth(), fabLocation[1] + floatingActionButton.getMeasuredHeight());

                if (editTextRect.intersect(fabRect)) {
                    // place above fab
                    groupNameEditText.setTranslationY((float) fabRect.top - editTextRect.bottom - getResources().getDimensionPixelSize(R.dimen.floating_button_margin));
                }
            } else {
                groupNameEditText.setTranslationY(0F);
            }
        });
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

            if (ContactUtil.canReceiveVoipMessages(contactModel, blockedIdentitiesService)
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
    public void onResetLinkClick() {
        logger.info("Reset link button clicked");
        RuntimeUtil.runOnUiThread(() -> ShowOnceDialog.newInstance(R.string.reset_default_group_link_title, R.string.reset_default_group_link_desc).show(getSupportFragmentManager(), DIALOG_SHOW_ONCE_RESET_LINK_INFO));
    }

    @Override
    public void onShareLinkClick() {
        logger.info("Share link button clicked");
        // option only enabled if there is a default link
        groupInviteService.shareGroupLink(this, groupInviteService.getDefaultGroupInvite(groupModel).get());
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
                groupModelRepository.getByLocalGroupDbId(this.groupId);

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
