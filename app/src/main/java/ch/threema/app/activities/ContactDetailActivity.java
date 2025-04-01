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

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.ContactDetailAdapter;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.AddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.AlreadyVerified;
import ch.threema.app.asynctasks.AndroidContactLinkPolicy;
import ch.threema.app.asynctasks.ContactModified;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.asynctasks.ContactSyncPolicy;
import ch.threema.app.asynctasks.DeleteContactServices;
import ch.threema.app.asynctasks.DialogMarkContactAsDeletedBackgroundTask;
import ch.threema.app.asynctasks.Failed;
import ch.threema.app.asynctasks.LocalPublicKeyMismatch;
import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.QRCodeService;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.tasks.ForwardSecurityStateLogTask;
import ch.threema.app.ui.AvatarEditView;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.ui.TooltipPopup;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.LazyProperty;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.data.repositories.ModelRepositories;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

import static ch.threema.app.utils.QRScannerUtil.REQUEST_CODE_QR_SCANNER;

public class ContactDetailActivity extends ThreemaToolbarActivity
    implements LifecycleOwner,
    GenericAlertDialog.DialogClickListener,
    ContactEditDialog.ContactEditDialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ContactDetailActivity");

    private static final String DIALOG_TAG_EDIT = "cedit";
    private static final String DIALOG_TAG_DELETE_CONTACT = "deleteContact";
    private static final String DIALOG_TAG_EXCLUDE_CONTACT = "excludeContact";
    private static final String DIALOG_TAG_ADD_CONTACT = "dac";
    private static final String DIALOG_TAG_CONFIRM_BLOCK = "block";

    private static final int PERMISSION_REQUEST_CAMERA = 2;

    private static final String RUN_ON_ACTIVE_RELOAD = "reload";
    private static final String RUN_ON_ACTIVE_RELOAD_GROUP = "reload_group";

    private static final int REQUEST_CODE_CONTACT_EDITOR = 39255;

    // Services
    private ContactService contactService;
    private ContactModelRepository contactModelRepository;
    private GroupService groupService;
    private BlockedIdentitiesService blockedIdentitiesService;
    private IdListService profilePicRecipientsService;
    private DeadlineListService hiddenChatsListService;
    private VoipStateService voipStateService;
    private DeleteContactServices deleteContactServices;

    private final @NonNull LazyProperty<BackgroundExecutor> backgroundExecutor = new LazyProperty<>(BackgroundExecutor::new);

    // Data and state holders
    private String identity;
    @Deprecated
    private ContactModel contact;
    private ch.threema.data.models.ContactModel contactModel;
    private @Nullable ContactDetailViewModel viewModel; // Initially null, until initialized
    private List<GroupModel> groupList;
    private boolean isReadonly;
    private boolean isDisabledProfilePicReleaseSettings = false;

    // Views
    private MenuItem blockMenuItem = null, profilePicItem = null, profilePicSendItem = null, callItem = null;
    private ResumePauseHandler resumePauseHandler;
    private RecyclerView contactDetailRecyclerView;
    private AvatarEditView avatarEditView;
    private FloatingActionButton floatingActionButton;
    private TextView contactTitle;
    private View workIcon;

    private void refreshAdapter() {
        if (viewModel == null) {
            logger.error("View model is null. Cannot refresh adapter.");
            return;
        }

        ContactModelData fetchedData = viewModel.getContact().getValue();
        if (fetchedData != null) {
            contactDetailRecyclerView.setAdapter(setupAdapter(fetchedData));
        }
    }

    private final ResumePauseHandler.RunIfActive runIfActiveUpdate = new ResumePauseHandler.RunIfActive() {
        @Override
        public void runOnUiThread() {
            groupList = groupService.getGroupsByIdentity(identity);
            refreshAdapter();
        }
    };
    private final ResumePauseHandler.RunIfActive runIfActiveGroupUpdate = new ResumePauseHandler.RunIfActive() {
        @Override
        public void runOnUiThread() {
            groupList = groupService.getGroupsByIdentity(identity);
            refreshAdapter();
        }
    };

    private final ContactSettingsListener contactSettingsListener = new ContactSettingsListener() {
        @Override
        public void onSortingChanged() {
        }

        @Override
        public void onNameFormatChanged() {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }

        @Override
        public void onAvatarSettingChanged() {
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
            if (!this.shouldHandleChange(identity)) {
                return;
            }
            RuntimeUtil.runOnUiThread(() -> updateBlockMenu());
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }

        @Override
        public void onAvatarChanged(final @NonNull String identity) {
            if (!this.shouldHandleChange(identity)) {
                return;
            }
            RuntimeUtil.runOnUiThread(() -> updateProfilepicMenu());
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
        }

        /** @noinspection BooleanMethodIsAlwaysInverted*/
        public boolean shouldHandleChange(@NonNull String identity) {
            return identity.equals(ContactDetailActivity.this.identity);
        }
    };

    private final GroupListener groupListener = new GroupListener() {
        @Override
        public void onCreate(GroupModel newGroupModel) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }

        @Override
        public void onRename(GroupModel groupModel) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }

        @Override
        public void onUpdatePhoto(GroupModel groupModel) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }

        @Override
        public void onRemove(GroupModel groupModel) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }

        @Override
        public void onNewMember(GroupModel group, String newIdentity) {
            if (newIdentity.equals(identity)) {
                resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
            }
        }

        @Override
        public void onMemberLeave(GroupModel group, String leftIdentity) {
            if (leftIdentity.equals(identity)) {
                resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
            }
        }

        @Override
        public void onMemberKicked(GroupModel group, String kickedIdentity) {
            if (kickedIdentity.equals(identity)) {
                resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
            }
        }

        @Override
        public void onUpdate(GroupModel groupModel) {
            //ignore
        }

        @Override
        public void onLeave(GroupModel groupModel) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }
    };

    @Override
    public int getLayoutResource() {
        return R.layout.activity_contact_detail;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.identity = this.getIntent().getStringExtra(ThreemaApplication.INTENT_DATA_CONTACT);
        if (this.identity == null || this.identity.length() == 0) {
            logger.error("no identity");
            this.finish();
            return;
        }
        if (this.identity.equals(getMyIdentity())) {
            this.finish();
            return;
        }

        ConfigUtils.configureTransparentStatusBar(this);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this);

        // Set up services
        final ModelRepositories modelRepositories;
        try {
            this.contactService = serviceManager.getContactService();
            modelRepositories = serviceManager.getModelRepositories();
            contactModelRepository = modelRepositories.getContacts();
            this.blockedIdentitiesService = serviceManager.getBlockedIdentitiesService();
            this.profilePicRecipientsService = serviceManager.getProfilePicRecipientsService();
            this.groupService = serviceManager.getGroupService();
            this.hiddenChatsListService = serviceManager.getHiddenChatsListService();
            this.voipStateService = serviceManager.getVoipStateService();
            this.deleteContactServices = new DeleteContactServices(
                serviceManager.getUserService(),
                contactService,
                serviceManager.getConversationService(),
                serviceManager.getRingtoneService(),
                serviceManager.getMutedChatsListService(),
                hiddenChatsListService,
                profilePicRecipientsService,
                serviceManager.getWallpaperService(),
                serviceManager.getFileService(),
                serviceManager.getExcludedSyncIdentitiesService(),
                serviceManager.getDHSessionStore(),
                serviceManager.getNotificationService(),
                serviceManager.getDatabaseServiceNew()
            );
        } catch (Exception e) {
            LogUtil.exception(e, this);
            this.finish();
            return;
        }

        // Look up contact data
        this.contact = this.contactService.getByIdentity(this.identity);
        contactModel = modelRepositories.getContacts().getByIdentity(this.identity);
        if (this.contact == null || contactModel == null) {
            Toast.makeText(this, R.string.contact_not_found, Toast.LENGTH_LONG).show();
            this.finish();
            return;
        }
        this.groupList = this.groupService.getGroupsByIdentity(this.identity);

        // Look up viewmodel
        this.viewModel = new ViewModelProvider(
            this,
            ContactDetailViewModel.Companion.getFactory()
        ).get(ContactDetailViewModel.class);

        // Set up toolbar
        final CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        if (collapsingToolbar == null) {
            logger.debug("Collapsing Toolbar not available");
            finish();
            return;
        }
        collapsingToolbar.setTitle(" ");
        @ColorInt int scrimColor = contactService.getAvatarColor(contact);
        collapsingToolbar.setContentScrimColor(scrimColor);
        collapsingToolbar.setStatusBarScrimColor(scrimColor);

        // Look up view references
        this.contactTitle = findViewById(R.id.contact_title);
        this.workIcon = findViewById(R.id.work_icon);
        this.avatarEditView = findViewById(R.id.avatar_edit_view);
        this.contactDetailRecyclerView = findViewById(R.id.contact_group_list);
        if (this.contactDetailRecyclerView == null) {
            logger.error("list not available");
            this.finish();
            return;
        }

        // Configure avatar view
        this.avatarEditView.setHires(true);
        this.avatarEditView.setContactModel(contact);

        this.isReadonly = getIntent().getBooleanExtra(ThreemaApplication.INTENT_DATA_CONTACT_READONLY, false);

        // Hide profile picture release settings if restricted by MDM
        if (ConfigUtils.isWorkRestricted()) {
            Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_send_profile_picture));
            if (value != null) {
                isDisabledProfilePicReleaseSettings = value;
            }
        }

        // Subscribe to viewmodel changes
        this.viewModel.getContact().observe(this, this::onContactModelDataUpdate);

        // Set up contact detail recycler view
        this.contactDetailRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set description for badge
        this.workIcon.setContentDescription(
            getString(ConfigUtils.isWorkBuild() ? R.string.private_contact : R.string.threema_work_contact)
        );

        // Get current contact model (only used for further initialization)
        //
        // Note: This logic should probably be changed to be more reactive, instead of using
        //       the contact model data snapshot here.
        final ContactModelData contactModelDataSnapshot = contactModel.getData().getValue();
        if (contactModelDataSnapshot.acquaintanceLevel == ContactModel.AcquaintanceLevel.GROUP) {
            GenericAlertDialog.newInstance(
                R.string.menu_add_contact,
                String.format(getString(R.string.contact_add_confirm), NameUtil.getDisplayNameOrNickname(contact, true)),
                R.string.yes,
                R.string.no
            ).show(getSupportFragmentManager(), DIALOG_TAG_ADD_CONTACT);
        } else {
            onCreateLocal();

            if (savedInstanceState == null) {
                if (!ConfigUtils.isWorkBuild() && contactService.showBadge(contactModelDataSnapshot)) {
                    if (!preferenceService.getIsWorkHintTooltipShown()) {
                        showWorkTooltip();
                    }
                }
            }
        }

        serviceManager.getTaskManager().schedule(new ForwardSecurityStateLogTask(
            contactService, contact
        ));
    }

    private void onCreateLocal() {
        ListenerManager.contactListeners.add(this.contactListener);
        ListenerManager.contactSettingsListeners.add(this.contactSettingsListener);
        ListenerManager.groupListeners.add(this.groupListener);

        this.floatingActionButton = findViewById(R.id.floating);
        this.floatingActionButton.setOnClickListener(v -> openContactEditor());

        if (Objects.requireNonNull(viewModel).showEditFAB()) {
            floatingActionButton.setContentDescription(getString(R.string.edit));
            floatingActionButton.setImageResource(R.drawable.ic_outline_contacts_app_24);
        }

        if (getToolbar().getNavigationIcon() != null) {
            getToolbar().getNavigationIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
    }

    /**
     * Update the UI whenever the contact model data changes.
     */
    @UiThread
    private void onContactModelDataUpdate(@Nullable ContactModelData contactModelData) {
        logger.debug("Contact data updated");

        if (contactModelData == null) {
            // The contact has been deleted. Therefore we finish this activity.
            Toast.makeText(this, R.string.contact_deleted, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Update name
        this.contactTitle.setText(contactModelData.getDisplayName());

        // Show or hide badge for work/private contacts
        ViewUtil.show(workIcon, contactService.showBadge(contactModelData));

        // Update adapter
        this.refreshAdapter();
    }

    private void showWorkTooltip() {
        workIcon.postDelayed(() -> {
            int[] location = new int[2];
            workIcon.getLocationOnScreen(location);
            location[0] += workIcon.getWidth() / 2;
            location[1] += workIcon.getHeight();

            final TooltipPopup workTooltipPopup = new TooltipPopup(this, R.string.preferences__tooltip_work_hint_shown, this, R.drawable.ic_badge_work_24dp);
            workTooltipPopup.setListener(new TooltipPopup.TooltipPopupListener() {
                @Override
                public void onClicked(@NonNull TooltipPopup tooltipPopup) {
                    startActivity(new Intent(ContactDetailActivity.this, WorkExplainActivity.class));
                }
            });
            workTooltipPopup.show(this, workIcon, null, getString(R.string.tooltip_work_hint), TooltipPopup.Alignment.BELOW_ANCHOR_ARROW_LEFT, location, 0);

            final AppBarLayout appBarLayout = findViewById(R.id.appbar);
            if (appBarLayout != null) {
                appBarLayout.addOnOffsetChangedListener(new AppBarLayout.BaseOnOffsetChangedListener<>() {
                    @Override
                    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                        workTooltipPopup.dismiss(false);
                        appBarLayout.removeOnOffsetChangedListener(this);
                    }
                });
            }

            new Handler().postDelayed(
                () -> RuntimeUtil.runOnUiThread(
                    () -> workTooltipPopup.dismiss(false)
                ),
                4000
            );
        }, 1000);
    }

    @UiThread
    @Nullable
    private ContactDetailAdapter setupAdapter(@NonNull ContactModelData contactModelData) {
        if (viewModel == null) {
            logger.error("View model is null");
            return null;
        }

        final ContactDetailAdapter contactDetailAdapter = new ContactDetailAdapter(
            this,
            this.groupList,
            viewModel.getContactModel(),
            contactModelData,
            Glide.with(this)
        );

        contactDetailAdapter.setOnClickListener(new ContactDetailAdapter.OnClickListener() {
            @Override
            public void onItemClick(View v, GroupModel groupModel) {

                Intent intent = new Intent(ContactDetailActivity.this, GroupDetailActivity.class);
                intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupModel.getId());

                startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_GROUP_DETAIL);
            }

            @Override
            public void onVerificationInfoClick(View v) {
                Intent intent = new Intent(ContactDetailActivity.this, VerificationLevelActivity.class);
                startActivity(intent);
            }
        });

        return contactDetailAdapter;
    }

    @Override
    protected void onDestroy() {
        if (floatingActionButton != null) {
            floatingActionButton.hide();
        }

        ListenerManager.contactListeners.remove(this.contactListener);
        ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
        ListenerManager.groupListeners.remove(this.groupListener);

        if (this.resumePauseHandler != null) {
            this.resumePauseHandler.onDestroy(this);
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (this.resumePauseHandler != null) {
            this.resumePauseHandler.onPause();
        }
    }

    private void openContactEditor() {
        if (contact != null) {
            if (!AndroidContactUtil.getInstance().openContactEditor(this, contact, REQUEST_CODE_CONTACT_EDITOR)) {
                editName();
            }
        }
    }

    @Override
    public void onResume() {
        if (this.resumePauseHandler != null) {
            this.resumePauseHandler.onResume();
        }
        super.onResume();
    }

    private void removeContact() {
        GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
            R.string.delete_contact_action,
            R.string.really_delete_contact,
            R.string.ok,
            R.string.cancel);
        dialogFragment.setData(contact);
        dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_DELETE_CONTACT);
    }

    private void removeContactConfirmed(final boolean addToExcludeList) {
        backgroundExecutor.get().execute(
            new DialogMarkContactAsDeletedBackgroundTask(
                getSupportFragmentManager(),
                new WeakReference<>(this),
                Set.of(identity),
                contactModelRepository,
                deleteContactServices,
                addToExcludeList ? ContactSyncPolicy.EXCLUDE : ContactSyncPolicy.INCLUDE,
                AndroidContactLinkPolicy.REMOVE_LINK
            ) {
                @Override
                protected void onFinished() {
                    // TODO(ANDR-3051): Do not leave contact detail activity if contact could not be
                    //  deleted.
                    finishAndGoHome();
                }
            }
        );
    }

    private void editName() {
        if (viewModel == null) {
            logger.error("View model is null");
            return;
        }

        ContactModelData contactModelData = viewModel.getContact().getValue();
        if (contactModelData == null) {
            logger.error("Contact model data is null");
            return;
        }

        ContactEditDialog contactEditDialog = ContactEditDialog.newInstance(contactModelData);
        contactEditDialog.show(getSupportFragmentManager(), DIALOG_TAG_EDIT);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (isFinishing()) {
            return false;
        }

        getMenuInflater().inflate(R.menu.activity_contact_detail, menu);
        try {
            MenuBuilder menuBuilder = (MenuBuilder) menu;
            menuBuilder.setOptionalIconsVisible(true);
        } catch (Exception ignored) {
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return false;
        }

        // display verification level in action bar
        if (contact != null && contact.verificationLevel != VerificationLevel.FULLY_VERIFIED) {
            MenuItem menuItem = menu.findItem(R.id.action_scan_id);
            menuItem.setVisible(true);
        }
        if (isReadonly) {
            menu.findItem(R.id.action_send_message).setVisible(false);
        }
        this.blockMenuItem = menu.findItem(R.id.action_block_contact);
        updateBlockMenu();

        this.profilePicSendItem = menu.findItem(R.id.action_send_profilepic);
        this.profilePicItem = menu.findItem(R.id.action_add_profilepic_recipient);
        updateProfilepicMenu();

        this.callItem = menu.findItem(R.id.menu_threema_call);

        updateVoipCallMenuItem(null);

        MenuItem galleryMenuItem = menu.findItem(R.id.menu_gallery);
        if (hiddenChatsListService.has(ContactUtil.getUniqueIdString(identity))) {
            galleryMenuItem.setVisible(false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @UiThread
    private void updateVoipCallMenuItem(final Boolean newState) {
        if (callItem != null) {
            if (
                ContactUtil.canReceiveVoipMessages(contact, blockedIdentitiesService)
                    && ConfigUtils.isCallsEnabled()) {
                logger.debug("updateVoipMenu newState {}", newState);

                callItem.setVisible(newState != null ? newState : voipStateService.getCallState().isIdle());
            } else {
                callItem.setVisible(false);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_send_message) {
            if (identity != null) {
                Intent intent = new Intent(this, ComposeMessageActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
                intent.putExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, Boolean.TRUE);
                startActivity(intent);
                finish();
            }
        } else if (id == R.id.action_remove_contact) {
            removeContact();
        } else if (id == R.id.action_scan_id) {
            if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_CAMERA)) {
                scanQR();
            }
        } else if (id == R.id.menu_threema_call) {
            VoipUtil.initiateCall(this, contact, false, null);
        } else if (id == R.id.action_block_contact) {
            if (this.blockedIdentitiesService != null && this.blockedIdentitiesService.isBlocked(this.identity)) {
                unblockContact();
            } else {
                GenericAlertDialog.newInstance(R.string.block_contact, R.string.really_block_contact, R.string.yes, R.string.no).show(getSupportFragmentManager(), DIALOG_TAG_CONFIRM_BLOCK);
            }
        } else if (id == R.id.action_share_contact) {
            ShareUtil.shareContact(this, contact);
        } else if (id == R.id.menu_gallery) {
            if (!hiddenChatsListService.has(ContactUtil.getUniqueIdString(identity))) {
                Intent mediaGalleryIntent = new Intent(this, MediaGalleryActivity.class);
                mediaGalleryIntent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
                startActivity(mediaGalleryIntent);
            }
        } else if (id == R.id.action_add_profilepic_recipient) {
            if (!profilePicRecipientsService.has(this.identity)) {
                profilePicRecipientsService.add(this.identity);
            } else {
                profilePicRecipientsService.remove(this.identity);
            }
            updateProfilepicMenu();
        } else if (id == R.id.action_send_profilepic) {
            sendProfilePic();
        } else {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void sendProfilePic() {
        serviceManager.getTaskCreator().scheduleProfilePictureSendTaskAsync(this.identity);
    }

    private void blockContact() {
        if (this.blockedIdentitiesService != null) {
            this.blockedIdentitiesService.blockIdentity(identity, this);
        } else {
            logger.error("Could not block contact as the service is null");
        }
    }

    private void unblockContact() {
        if (this.blockedIdentitiesService != null) {
            this.blockedIdentitiesService.unblockIdentity(identity, null);
        } else {
            logger.error("Could not unblock contact as the service is null");
        }
    }

    private void updateBlockMenu() {
        if (this.blockMenuItem != null) {
            if (blockedIdentitiesService != null && blockedIdentitiesService.isBlocked(this.identity)) {
                blockMenuItem.setTitle(R.string.unblock_contact);
            } else {
                blockMenuItem.setTitle(R.string.block_contact);
            }
        }
    }

    private void updateProfilepicMenu() {
        if (this.profilePicItem != null && this.profilePicSendItem != null) {

            if (isDisabledProfilePicReleaseSettings) {
                this.profilePicItem.setVisible(false);
                this.profilePicSendItem.setVisible(false);
                return;
            }

            switch (preferenceService.getProfilePicRelease()) {
                case PreferenceService.PROFILEPIC_RELEASE_EVERYONE:
                    this.profilePicItem.setVisible(false);
                    this.profilePicSendItem.setVisible(!ContactUtil.isEchoEchoOrGatewayContact(contact));
                    break;
                case PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST:
                    if (!ContactUtil.isEchoEchoOrGatewayContact(contact)) {
                        if (profilePicRecipientsService != null && profilePicRecipientsService.has(this.identity)) {
                            profilePicItem.setTitle(R.string.menu_send_profilpic_off);
                            profilePicItem.setIcon(R.drawable.ic_person_remove_outline);
                            profilePicSendItem.setVisible(true);
                        } else {
                            profilePicItem.setTitle(R.string.menu_send_profilpic);
                            profilePicItem.setIcon(R.drawable.ic_person_add_outline);
                            profilePicSendItem.setVisible(false);
                        }
                        this.profilePicItem.setVisible(true);
                    } else {
                        this.profilePicSendItem.setVisible(false);
                        this.profilePicItem.setVisible(false);
                    }
                    break;
                case PreferenceService.PROFILEPIC_RELEASE_NOBODY:
                    this.profilePicItem.setVisible(false);
                    this.profilePicSendItem.setVisible(false);
                    break;
                default:
                    break;
            }
        }
    }

    private void scanQR() {
        QRScannerUtil.getInstance().initiateScan(this, null, QRCodeServiceImpl.QR_TYPE_ID);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        try {
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(DIALOG_TAG_EDIT);
            if (fragment != null && fragment.isAdded()) {
                fragment.onActivityResult(requestCode, resultCode, intent);
            }
        } catch (Exception e) {
            logger.error("Could not set up fragment", e);
        }

        switch (requestCode) {
            case ACTIVITY_ID_GROUP_DETAIL:
                // contacts may have been edited
                this.groupList = this.groupService.getGroupsByIdentity(this.identity);
                this.refreshAdapter();
                break;
            case REQUEST_CODE_QR_SCANNER:
                QRCodeService.QRCodeContentResult qrRes =
                    QRScannerUtil.getInstance().parseActivityResult(this, requestCode, resultCode, intent,
                        this.serviceManager.getQRCodeService());

                if (qrRes != null) {
                    applyQRCodeResult(qrRes);
                }
                break;
            case REQUEST_CODE_CONTACT_EDITOR:
                try {
                    AndroidContactUtil.getInstance().updateNameByAndroidContact(contactModel);
                    AndroidContactUtil.getInstance().updateAvatarByAndroidContact(contactModel);
                    this.avatarEditView.setContactModel(contact);
                } catch (ThreemaException | SecurityException e) {
                    logger.info("Unable to update contact name or avatar after returning from ContactEditor");
                }
                break;
            default:
                if (this.avatarEditView != null) {
                    this.avatarEditView.onActivityResult(requestCode, resultCode, intent);
                }
                break;
        }
    }

    void deleteContact(ContactModel contactModel) {
        final IdListService excludeFromSyncListService = this.serviceManager.getExcludedSyncIdentitiesService();

        //second question, if the contact is a synced contact
        if (contactModel.isLinkedToAndroidContact()
            && !excludeFromSyncListService.has(contactModel.getIdentity())) {

            GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
                R.string.delete_contact_action,
                R.string.want_to_add_to_exclude_list,
                R.string.yes,
                R.string.no);
            dialogFragment.setData(contact);
            dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_EXCLUDE_CONTACT);
        } else {
            removeContactConfirmed(false);
        }
    }

    void unhideContact(ContactModel contactModel) {
        contactService.setAcquaintanceLevel(contactModel.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        onCreateLocal();
    }

    private void applyQRCodeResult(@NonNull QRCodeService.QRCodeContentResult qrRes) {
        if (qrRes.getExpirationDate() != null && qrRes.getExpirationDate().before(new Date())) {
            SimpleStringAlertDialog.newInstance(R.string.title_adduser, getString(R.string.expired_barcode)).show(getSupportFragmentManager(), "expiredId");
            return;
        }

        if (!TestUtil.compare(identity, qrRes.getIdentity())) {
            SimpleStringAlertDialog.newInstance(
                R.string.scan_id_mismatch_title,
                getString(R.string.scan_id_mismatch_message)).show(getSupportFragmentManager(), "scanId");
            return;
        }

        AddOrUpdateContactBackgroundTask<String> task = new AddOrUpdateContactBackgroundTask<>(
            identity,
            ContactModel.AcquaintanceLevel.DIRECT,
            contactService.getMe().getIdentity(),
            serviceManager.getAPIConnector(),
            contactModelRepository,
            AddContactRestrictionPolicy.CHECK,
            this,
            qrRes.getPublicKey()
        ) {
            @Override
            public String onContactAdded(@NonNull ContactResult result) {
                if (result instanceof AlreadyVerified) {
                    return getString(R.string.scan_duplicate);
                } else if (result instanceof ContactModified) {
                    if (((ContactModified) result).getVerificationLevelChanged()) {
                        return getString(R.string.scan_successful);
                    } else if (((ContactModified) result).getAcquaintanceLevelChanged()) {
                        logger.warn("Acquaintance level has changed instead of verification level");
                    }
                } else if (result instanceof LocalPublicKeyMismatch) {
                    return getString(R.string.id_mismatch);
                } else if (result instanceof Failed) {
                    return ((Failed) result).getMessage();
                }
                return null;
            }

            @Override
            public void onFinished(@Nullable String result) {
                if (result != null) {
                    SimpleStringAlertDialog.newInstance(R.string.id_scanned, result).show(getSupportFragmentManager(), "scanId");
                }
            }
        };

        backgroundExecutor.get().execute(task);
    }

    @Override
    public void onYes(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_DELETE_CONTACT:
                ContactModel contactModel = (ContactModel) data;
                deleteContact(contactModel);
                break;
            case DIALOG_TAG_EXCLUDE_CONTACT:
                removeContactConfirmed(true);
                break;
            case DIALOG_TAG_ADD_CONTACT:
                unhideContact(this.contact);
                break;
            case DIALOG_TAG_CONFIRM_BLOCK:
                blockContact();
                break;
            default:
                break;
        }
    }

    @Override
    public void onNo(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_EXCLUDE_CONTACT:
                removeContactConfirmed(false);
                break;
            case DIALOG_TAG_ADD_CONTACT:
                finish();
                break;
            default:
                break;
        }
    }

    /**
     * Called when the edit dialog is confirmed.
     */
    @Override
    public void onYes(String tag, @NonNull String firstName, @NonNull String lastName, @Nullable File ignored) {
        final ContactDetailViewModel viewModel = Objects.requireNonNull(this.viewModel);
        viewModel.updateContactName(firstName, lastName);
    }

    @Override
    public void onNo(String tag) {
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        @NonNull String[] permissions,
        @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanQR();
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                ConfigUtils.showPermissionRationale(
                    this,
                    findViewById(R.id.main_content),
                    R.string.permission_camera_qr_required
                );
            }
        }
    }

    private void finishAndGoHome() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        navigateUpTo(new Intent(this, HomeActivity.class));
    }
}
