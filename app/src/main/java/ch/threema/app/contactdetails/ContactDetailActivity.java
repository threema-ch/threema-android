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

package ch.threema.app.contactdetails;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.koin.android.compat.ViewModelCompat;
import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.GroupDetailActivity;
import ch.threema.app.camera.QRScannerActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.mediagallery.MediaGalleryActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.activities.VerificationLevelActivity;
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
import ch.threema.app.home.HomeActivity;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.tasks.ForwardSecurityStateLogTask;
import ch.threema.app.ui.AvatarEditView;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.ui.TooltipPopup;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.app.qrcodes.ContactUrlUtil;
import ch.threema.app.qrcodes.ContactUrlResult;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.app.webviews.WorkExplainActivity;
import ch.threema.base.ThreemaException;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.models.GroupIdentity;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import kotlin.Lazy;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.common.LazyKt.lazy;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static org.koin.core.parameter.ParametersHolderKt.parametersOf;

public class ContactDetailActivity extends ThreemaToolbarActivity
    implements LifecycleOwner,
    GenericAlertDialog.DialogClickListener,
    ContactEditDialog.ContactEditDialogClickListener {
    private static final Logger logger = getThreemaLogger("ContactDetailActivity");

    private static final String DIALOG_TAG_EDIT = "cedit";
    private static final String DIALOG_TAG_DELETE_CONTACT = "deleteContact";
    private static final String DIALOG_TAG_EXCLUDE_CONTACT = "excludeContact";
    private static final String DIALOG_TAG_ADD_CONTACT = "dac";
    private static final String DIALOG_TAG_CONFIRM_BLOCK = "block";

    private static final int PERMISSION_REQUEST_CAMERA = 2;

    private static final String RUN_ON_ACTIVE_RELOAD = "reload";
    private static final String RUN_ON_ACTIVE_RELOAD_GROUP = "reload_group";

    private static final int REQUEST_CODE_CONTACT_EDITOR = 39255;
    private static final int REQUEST_CODE_QR_SCANNER = 26657;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    @NonNull
    private final Lazy<AndroidContactUtil> androidContactUtil = KoinJavaComponent.inject(AndroidContactUtil.class);

    @NonNull
    private final Lazy<BackgroundExecutor> backgroundExecutor = lazy(BackgroundExecutor::new);

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

        ContactModelData fetchedData = viewModel.contactModelData.getValue();
        if (fetchedData != null) {
            contactDetailRecyclerView.setAdapter(setupAdapter(fetchedData));
        }
    }

    private final ResumePauseHandler.RunIfActive runIfActiveUpdate = new ResumePauseHandler.RunIfActive() {
        @Override
        public void runOnUiThread() {
            groupList = dependencies.getGroupService().getGroupsByIdentity(identity);
            refreshAdapter();
        }
    };
    private final ResumePauseHandler.RunIfActive runIfActiveGroupUpdate = new ResumePauseHandler.RunIfActive() {
        @Override
        public void runOnUiThread() {
            groupList = dependencies.getGroupService().getGroupsByIdentity(identity);
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
        public void onCreate(@NonNull GroupIdentity groupIdentity) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }

        @Override
        public void onRename(@NonNull GroupIdentity groupIdentity) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }

        @Override
        public void onUpdatePhoto(@NonNull GroupIdentity groupIdentity) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }

        @Override
        public void onRemove(long groupDbId) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }

        @Override
        public void onNewMember(@NonNull GroupIdentity groupIdentity, String identityNew) {
            if (identityNew.equals(identity)) {
                resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
            }
        }

        @Override
        public void onMemberLeave(@NonNull GroupIdentity groupIdentity, @NonNull String identityLeft) {
            if (identityLeft.equals(identity)) {
                resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
            }
        }

        @Override
        public void onMemberKicked(@NonNull GroupIdentity groupIdentity, String identityKicked) {
            if (identityKicked.equals(identity)) {
                resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
            }
        }

        @Override
        public void onUpdate(@NonNull GroupIdentity groupIdentity) {
            //ignore
        }

        @Override
        public void onLeave(@NonNull GroupIdentity groupIdentity) {
            resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
        }
    };

    @Override
    public int getLayoutResource() {
        return R.layout.activity_contact_detail;
    }

    @Override
    protected void handleDeviceInsets() {
        final @NonNull LinearLayout collapsingBottomContainer = findViewById(R.id.collapsing_bottom_container);
        final @NonNull FloatingActionButton floatingActionButton = findViewById(R.id.floating);
        final @NonNull RecyclerView contactGroupList = findViewById(R.id.contact_group_list);

        // As the CollapsingToolbarLayout will consume the window insets internally, we have to apply the window insets to every our child views
        // with one inset listener from the root layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_content), (view, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            final @Px int spacingTwoGridUnits = getResources().getDimensionPixelSize(R.dimen.grid_unit_x2);
            final @Px int spacingFiveGridUnits = getResources().getDimensionPixelSize(R.dimen.grid_unit_x5);
            final @Px int spacingSixGridUnits = getResources().getDimensionPixelSize(R.dimen.grid_unit_x6);

            // Apply the window insets to our MaterialToolbar
            getToolbar().setPadding(insets.left, 0, insets.right, 0);

            // Apply only the horizontal insets to the collapsing name container
            ViewExtensionsKt.setMargin(
                collapsingBottomContainer,
                insets.left + spacingTwoGridUnits,
                0,
                insets.right + spacingTwoGridUnits,
                spacingSixGridUnits
            );

            // Apply only the horizontal insets to the floating action button
            ViewExtensionsKt.setMargin(
                floatingActionButton,
                insets.left + spacingTwoGridUnits,
                0,
                insets.right + spacingTwoGridUnits,
                -spacingFiveGridUnits
            );

            // Apply horizontal and bottom padding to the the listview
            contactGroupList.setPadding(
                insets.left,
                spacingTwoGridUnits,
                insets.right,
                insets.bottom + spacingTwoGridUnits
            );

            // Do not consume the insets, as the CollapsingToolbarLayout uses them too
            return windowInsets;
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!isSessionScopeReady()) {
            finish();
            return;
        }

        this.identity = this.getIntent().getStringExtra(AppConstants.INTENT_DATA_CONTACT);
        if (this.identity == null || this.identity.isEmpty()) {
            logger.error("no identity");
            this.finish();
            return;
        }
        if (dependencies.getUserService().isMe(identity)) {
            this.finish();
            return;
        }

        this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this);

        // Look up contact data
        contact = dependencies.getContactService().getByIdentity(identity);
        contactModel = dependencies.getContactModelRepository().getByIdentity(identity);
        ContactModelData contactModelDataSnapshot = contactModel != null ? contactModel.getData() : null;
        if (this.contact == null || contactModel == null || contactModelDataSnapshot == null) {
            Toast.makeText(this, R.string.contact_not_found, Toast.LENGTH_LONG).show();
            this.finish();
            return;
        }
        this.groupList = dependencies.getGroupService().getGroupsByIdentity(this.identity);

        // Look up viewmodel
        this.viewModel = ViewModelCompat.getViewModel(
            this,
            ContactDetailViewModel.class,
            null,
            null,
            () -> parametersOf(identity)
        );

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final @NonNull AppBarLayout appBarLayout = findViewById(R.id.appbar);
        final @NonNull CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        final @NonNull ViewGroup collapsingBottomContainer = findViewById(R.id.collapsing_bottom_container);
        collapsingToolbar.setTitle(" ");
        @ColorInt int scrimColor = dependencies.getContactService().getAvatarColor(identity);
        collapsingToolbar.setContentScrimColor(scrimColor);
        collapsingToolbar.setStatusBarScrimColor(scrimColor);

        appBarLayout.addOnOffsetChangedListener((view, verticalOffset) -> {
            float expandedPercent = 1f;
            final float offsetPixels = Math.abs(verticalOffset);
            final float offsetPixelsWhenCollapsed = (float) view.getTotalScrollRange();
            if (offsetPixelsWhenCollapsed > 0) {
                expandedPercent = 1 - (offsetPixels / offsetPixelsWhenCollapsed);
            }

            // Fade out this container while collapsing
            collapsingBottomContainer.setAlpha(expandedPercent);

            // Show the contacts display name as a toolbar title only of we are fully collapsed
            @NonNull String toolbarTitle = " ";
            if (viewModel != null && expandedPercent == 0.0f) {
                final @Nullable ContactModelData currentContactModelData = viewModel.contactModelData.getValue();
                if (currentContactModelData != null) {
                    toolbarTitle = currentContactModelData.getDisplayName();
                }
            }
            collapsingToolbar.setTitle(toolbarTitle);
        });

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
        this.avatarEditView.setContactIdentity(contactModel.getIdentity());

        this.isReadonly = getIntent().getBooleanExtra(AppConstants.INTENT_DATA_CONTACT_READONLY, false);

        // Hide profile picture release settings if restricted by MDM
        if (ConfigUtils.isWorkRestricted()) {
            Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_send_profile_picture));
            if (value != null) {
                isDisabledProfilePicReleaseSettings = value;
            }
        }

        // Subscribe to viewmodel changes
        this.viewModel.contactModelData.observe(this, this::onContactModelDataUpdate);

        // Set up contact detail recycler view
        this.contactDetailRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set description for badge
        this.workIcon.setContentDescription(
            getString(ConfigUtils.isWorkBuild() ? R.string.private_contact : R.string.threema_work_contact)
        );

        // Note: This logic should probably be changed to be more reactive, instead of using
        //       the contact model data snapshot here.
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
                if (!ConfigUtils.isWorkBuild() && dependencies.getContactService().showBadge(contactModelDataSnapshot)) {
                    if (!dependencies.getPreferenceService().getIsWorkHintTooltipShown()) {
                        showWorkTooltip();
                    }
                }
            }
        }

        dependencies.getTaskManager().schedule(new ForwardSecurityStateLogTask(
            dependencies.getContactService(), contact
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
        ViewUtil.show(workIcon, dependencies.getContactService().showBadge(contactModelData));

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
                    startActivity(WorkExplainActivity.createIntent(ContactDetailActivity.this));
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
                intent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, (long) groupModel.getId());

                startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_GROUP_DETAIL);
            }

            @Override
            public void onVerificationInfoClick(View v) {
                startActivity(VerificationLevelActivity.createIntent(ContactDetailActivity.this));
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
        if (contactModel != null) {
            if (!androidContactUtil.getValue().openContactEditor(this, contactModel, REQUEST_CODE_CONTACT_EDITOR)) {
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
        backgroundExecutor.getValue().execute(
            new DialogMarkContactAsDeletedBackgroundTask(
                getSupportFragmentManager(),
                new WeakReference<>(this),
                Set.of(identity),
                dependencies.getContactModelRepository(),
                new DeleteContactServices(
                    dependencies.getUserService(),
                    dependencies.getContactService(),
                    dependencies.getConversationService(),
                    dependencies.getRingtoneService(),
                    dependencies.getConversationCategoryService(),
                    dependencies.getProfilePictureRecipientsService(),
                    dependencies.getWallpaperService(),
                    dependencies.getFileService(),
                    dependencies.getExcludedSyncIdentitiesService(),
                    dependencies.getDhSessionStore(),
                    dependencies.getNotificationService(),
                    dependencies.getDatabaseService()
                ),
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

        ContactModelData contactModelData = viewModel.contactModelData.getValue();
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
        galleryMenuItem.setVisible(!dependencies.getConversationCategoryService().isPrivateChat(ContactUtil.getUniqueIdString(identity)));

        return super.onPrepareOptionsMenu(menu);
    }

    @UiThread
    private void updateVoipCallMenuItem(final Boolean newState) {
        if (callItem != null) {
            if (
                ContactUtil.canReceiveVoipMessages(contact, dependencies.getBlockedIdentitiesService())
                    && ConfigUtils.isCallsEnabled()) {
                logger.debug("updateVoipMenu newState {}", newState);

                callItem.setVisible(newState != null ? newState : dependencies.getVoipStateService().getCallState().isIdle());
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
                intent.putExtra(AppConstants.INTENT_DATA_CONTACT, identity);
                intent.putExtra(AppConstants.INTENT_DATA_EDITFOCUS, Boolean.TRUE);
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
            if (dependencies.getBlockedIdentitiesService().isBlocked(this.identity)) {
                unblockContact();
            } else {
                GenericAlertDialog.newInstance(R.string.block_contact, R.string.really_block_contact, R.string.yes, R.string.no).show(getSupportFragmentManager(), DIALOG_TAG_CONFIRM_BLOCK);
            }
        } else if (id == R.id.action_share_contact) {
            ShareUtil.shareContact(this, contact);
        } else if (id == R.id.menu_gallery) {
            if (!dependencies.getConversationCategoryService().isPrivateChat(ContactUtil.getUniqueIdString(identity))) {
                Intent mediaGalleryIntent = new Intent(this, MediaGalleryActivity.class);
                mediaGalleryIntent.putExtra(AppConstants.INTENT_DATA_CONTACT, identity);
                startActivity(mediaGalleryIntent);
            }
        } else if (id == R.id.action_add_profilepic_recipient) {
            if (!dependencies.getProfilePictureRecipientsService().has(this.identity)) {
                dependencies.getProfilePictureRecipientsService().add(this.identity);
            } else {
                dependencies.getProfilePictureRecipientsService().remove(this.identity);
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
        dependencies.getTaskCreator().scheduleProfilePictureSendTaskAsync(this.identity);
    }

    private void blockContact() {
        dependencies.getBlockedIdentitiesService().blockIdentity(identity, this);
    }

    private void unblockContact() {
        dependencies.getBlockedIdentitiesService().unblockIdentity(identity, null);
    }

    private void updateBlockMenu() {
        if (this.blockMenuItem != null) {
            if (dependencies.getBlockedIdentitiesService().isBlocked(this.identity)) {
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

            switch (dependencies.getPreferenceService().getProfilePicRelease()) {
                case PreferenceService.PROFILEPIC_RELEASE_EVERYONE:
                    this.profilePicItem.setVisible(false);
                    this.profilePicSendItem.setVisible(!ContactUtil.isEchoEchoOrGatewayContact(contact));
                    break;
                case PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST:
                    if (!ContactUtil.isEchoEchoOrGatewayContact(contact)) {
                        if (dependencies.getProfilePictureRecipientsService().has(this.identity)) {
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
        var intent = QRScannerActivity.createIntent(this);
        startActivityForResult(intent, REQUEST_CODE_QR_SCANNER);
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
                this.groupList = dependencies.getGroupService().getGroupsByIdentity(this.identity);
                this.refreshAdapter();
                break;
            case REQUEST_CODE_QR_SCANNER:
                if (resultCode == Activity.RESULT_OK) {
                    var qrCodeText = QRScannerActivity.extractResult(intent);
                    if (qrCodeText != null) {
                        ContactUrlUtil contactUrlUtil = KoinJavaComponent.get(ContactUrlUtil.class);
                        var result = contactUrlUtil.parse(qrCodeText);
                        if (result != null) {
                            applyQRCodeResult(result);
                            break;
                        }
                    }
                    SimpleStringAlertDialog.newInstance(R.string.scan_id, R.string.invalid_threema_qr_code).show(getSupportFragmentManager(), "");
                }
                break;
            case REQUEST_CODE_CONTACT_EDITOR:
                try {
                    androidContactUtil.getValue().updateNameByAndroidContact(contactModel, null, this);
                    androidContactUtil.getValue().updateAvatarByAndroidContact(contactModel, this);
                    this.avatarEditView.setContactIdentity(contactModel.getIdentity());
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
        //second question, if the contact is a synced contact
        var excludedSyncIdentitiesService = dependencies.getExcludedSyncIdentitiesService();
        if (contactModel.isLinkedToAndroidContact() && !excludedSyncIdentitiesService.isExcluded(contactModel.getIdentity())) {

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
        dependencies.getContactService().setAcquaintanceLevel(contactModel.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        onCreateLocal();
    }

    private void applyQRCodeResult(@NonNull ContactUrlResult contactUrlResult) {
        if (contactUrlResult.isExpired(Instant.now())) {
            SimpleStringAlertDialog.newInstance(R.string.title_adduser, getString(R.string.expired_barcode)).show(getSupportFragmentManager(), "expiredId");
            return;
        }

        if (!contactUrlResult.getIdentity().equals(identity)) {
            SimpleStringAlertDialog.newInstance(
                R.string.scan_id_mismatch_title,
                getString(R.string.scan_id_mismatch_message)).show(getSupportFragmentManager(), "scanId");
            return;
        }

        AddOrUpdateContactBackgroundTask<String> task = new AddOrUpdateContactBackgroundTask<>(
            identity,
            ContactModel.AcquaintanceLevel.DIRECT,
            dependencies.getContactService().getMe().getIdentity(),
            dependencies.getApiConnector(),
            dependencies.getContactModelRepository(),
            AddContactRestrictionPolicy.CHECK,
            this,
            contactUrlResult.getPublicKey()
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

        backgroundExecutor.getValue().execute(task);
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
