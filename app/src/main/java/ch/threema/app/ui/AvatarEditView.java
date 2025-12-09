/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.ui;

import static android.app.Activity.RESULT_OK;
import static ch.threema.android.BitmapExtensionsKt.calculateBrightness;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.apache.commons.io.IOUtils;
import org.koin.android.compat.ViewModelCompat;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.Set;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.CropImageActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.groupflows.GroupChanges;
import ch.threema.app.groupflows.GroupChanges.ProfilePictureChange;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ProfileListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.profilepicture.CheckedProfilePicture;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.services.GroupService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

public class AvatarEditView extends FrameLayout implements DefaultLifecycleObserver, View.OnClickListener, View.OnLongClickListener {
    private static final Logger logger = getThreemaLogger("AvatarEditView");
    private static final int REQUEST_CODE_FILE_SELECTOR_ID = 43320;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 43321;
    private static final int REQUEST_CODE_CAMERA = 43322;
    private static final int REQUEST_CODE_CROP = 43323;
    private static final String DIALOG_TAG_SAMSUNG_FIX = "samsung_fix";
    private UserService userService;
    private ContactService contactService;
    private GroupService groupService;
    private GroupModelRepository groupModelRepository;
    private GroupFlowDispatcher groupFlowDispatcher;
    private FileService fileService;
    private PreferenceService preferenceService;
    private ImageView avatarImage, avatarEditOverlay;
    private WeakReference<AvatarEditListener> listenerRef = new WeakReference<>(null);
    private boolean hires, isEditable, isMyProfilePicture;

    // the hosting fragment
    private WeakReference<Fragment> fragmentRef = new WeakReference<>(null);
    private WeakReference<AppCompatActivity> activityRef = new WeakReference<>(null);

    private AvatarEditViewModel viewModel;

    // the type of avatar
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AVATAR_TYPE_CONTACT, AVATAR_TYPE_GROUP, AVATAR_TYPE_NOTES})
    public @interface AvatarTypeDef {
    }

    public static final int AVATAR_TYPE_CONTACT = 0;
    public static final int AVATAR_TYPE_GROUP = 1;
    public static final int AVATAR_TYPE_NOTES = 2;

    public AvatarEditView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public AvatarEditView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AvatarEditView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        getActivity().getLifecycle().addObserver(this);
        viewModel = ViewModelCompat.getViewModel(getActivity(), AvatarEditViewModel.class);

        try {
            ServiceManager serviceManager = ThreemaApplication.requireServiceManager();
            contactService = serviceManager.getContactService();
            userService = serviceManager.getUserService();
            groupService = serviceManager.getGroupService();
            groupModelRepository = serviceManager.getModelRepositories().getGroups();
            groupFlowDispatcher = serviceManager.getGroupFlowDispatcher();
            fileService = serviceManager.getFileService();
            preferenceService = serviceManager.getPreferenceService();
        } catch (Exception e) {
            logger.error("Exception", e);
            return;
        }

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.view_avatar_edit, this);

        this.avatarImage = findViewById(R.id.avatar_view);
        this.avatarImage.setClickable(true);
        this.avatarImage.setFocusable(true);
        this.avatarImage.setOnClickListener(this);
        this.avatarImage.setOnLongClickListener(this);

        this.avatarEditOverlay = findViewById(R.id.avatar_edit);
        this.avatarEditOverlay.setVisibility(View.VISIBLE);

        this.isEditable = true;
    }

    private final ContactListener contactListener = new ContactListener() {
        @Override
        public void onAvatarChanged(final @NonNull String identity) {
            if (this.shouldHandleChange(identity)) {
                RuntimeUtil.runOnUiThread(() -> loadAvatarForModel(identity, null));
            }
        }

        @Override
        public void onRemoved(@NonNull final String identity) {
        }

        private boolean shouldHandleChange(String identity) {
            if (viewModel != null && viewModel.getContactIdentity() != null) {
                return TestUtil.compare(viewModel.getContactIdentity(), identity);
            }
            return false;
        }
    };

    private final ProfileListener profileListener = new ProfileListener() {
        @Override
        public void onAvatarChanged(@NonNull TriggerSource triggerSource) {
            reloadProfilePicture();
        }

        @Override
        public void onNicknameChanged(String newNickname) {
        }
    };

    private void reloadProfilePicture() {
        if (viewModel != null && userService.isMe(viewModel.getContactIdentity())) {
            RuntimeUtil.runOnUiThread(() -> loadAvatarForModel(viewModel.getContactIdentity(), null));
        }
    }

    /**
     * Load saved avatar for the specified model - do not call this if changes are to be deferred
     */
    @SuppressLint("StaticFieldLeak")
    @UiThread
    public synchronized void loadAvatarForModel(@Nullable String identity, GroupModel groupModel) {
        if (avatarImage == null) {
            return;
        }

        try {
            if (identity != null) {
                // Respect the settings for getting the profile picture.
                Bitmap bitmap = contactService.getAvatar(identity, new AvatarOptions.Builder()
                    .setHighRes(true)
                    .setReturnPolicy(AvatarOptions.DefaultAvatarPolicy.DEFAULT_FALLBACK)
                    .setDarkerBackground(isAvatarEditable())
                    .toOptions()
                );

                // If the preferences allow showing the profile pictures, then check if there is a
                // profile picture available for the contact. Otherwise, check whether there is a
                // locally saved avatar.
                boolean isCustomAvatar =
                    (preferenceService.getProfilePicReceive() && fileService.hasContactDefinedProfilePicture(identity))
                        || fileService.hasUserDefinedProfilePicture(identity);

                // If it is my profile picture then make it round
                if (isMyProfilePicture) {
                    avatarImage.setImageDrawable(AvatarConverterUtil.convertToRound(getContext().getResources(), bitmap));
                } else {
                    avatarImage.setImageBitmap(bitmap);
                }

                // If it is a custom avatar, we may need to adjust the darkness
                if (isCustomAvatar) {
                    adjustColorFilter(bitmap);
                }
            } else {
                // Display a group avatar
                Bitmap bitmap = groupService.getAvatar(groupModel, new AvatarOptions.Builder()
                    .setHighRes(true)
                    .setReturnPolicy(AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR)
                    .toOptions());
                if (bitmap != null) {
                    // A custom avatar is set and the bitmap may need to be darkened
                    avatarImage.setImageBitmap(bitmap);
                    adjustColorFilter(bitmap);
                } else {
                    // The default avatar is loaded with already darkened background
                    bitmap = groupService.getAvatar(groupModel, new AvatarOptions.Builder()
                        .setHighRes(true)
                        .setReturnPolicy(AvatarOptions.DefaultAvatarPolicy.DEFAULT_AVATAR)
                        .setDarkerBackground(isAvatarEditable())
                        .toOptions()
                    );
                    avatarImage.setImageBitmap(bitmap);
                }
            }
        } catch (RuntimeException e) {
            logger.debug("Unable to set avatar bitmap", e);
        }

        boolean editable = isAvatarEditable();
        avatarImage.setClickable(editable);
        avatarImage.setFocusable(editable);
        avatarEditOverlay.setVisibility(editable ? View.VISIBLE : View.GONE);
    }

    @Nullable
    private AppCompatActivity getActivity() {
        return getActivity(getContext());
    }

    @Nullable
    private AppCompatActivity getActivity(@NonNull Context context) {
        if (activityRef.get() == null) {
            if (context instanceof ContextWrapper) {
                if (context instanceof AppCompatActivity) {
                    activityRef = new WeakReference<>((AppCompatActivity) context);
                } else {
                    activityRef = new WeakReference<>(getActivity(((ContextWrapper) context).getBaseContext()));
                }
            }
        }
        return activityRef.get();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onClick(View view) {
        if (!isAvatarEditable()) {
            return;
        }

        MenuBuilder menuBuilder = new MenuBuilder(getContext());
        new MenuInflater(getContext()).inflate(R.menu.view_avatar_edit, menuBuilder);

        ConfigUtils.tintMenuIcons(menuBuilder, ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnSurface));

        if (!hasAvatar()) {
            menuBuilder.removeItem(R.id.menu_remove_picture);
        }
        menuBuilder.setCallback(new MenuBuilder.Callback() {
            @Override
            public boolean onMenuItemSelected(@NonNull MenuBuilder menu, @NonNull MenuItem item) {
                final int id = item.getItemId();
                if (id == R.id.menu_take_photo) {
                    if (ConfigUtils.requestCameraPermissions(getActivity(), getFragment(), REQUEST_CODE_CAMERA_PERMISSION)) {
                        openCamera();
                    }
                } else if (id == R.id.menu_select_from_gallery) {
                    FileUtil.selectFromGallery(getActivity(), getFragment(), REQUEST_CODE_FILE_SELECTOR_ID, false);
                } else if (id == R.id.menu_remove_picture) {
                    removeAvatar();
                } else {
                    return false;
                }
                return true;
            }

            @Override
            public void onMenuModeChange(@NonNull MenuBuilder menu) {
                // do nothing
            }
        });

        MenuPopupHelper optionsMenu = new MenuPopupHelper(getContext(), menuBuilder, avatarEditOverlay);
        optionsMenu.setForceShowIcon(true);
        optionsMenu.show();
    }

    @Override
    public boolean onLongClick(View v) {
        View parent = getRootView();

        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                return getCurrentAvatarBitmap(true);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                ImagePopup detailPopup = new ImagePopup(getContext(), parent);
                detailPopup.show(AvatarEditView.this, bitmap);
            }
        }.execute();

        return false;
    }

    @UiThread
    @SuppressLint("StaticFieldLeak")
    private void removeAvatar() {
        loadDefaultAvatar(viewModel.getContactIdentity(), viewModel.getGroupModel());

        if (listenerRef.get() != null) {
            listenerRef.get().onAvatarRemoved();
        } else {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    String identity = viewModel.getContactIdentity();
                    if (identity != null) {
                        if (userService.isMe(identity)) {
                            userService.removeUserProfilePicture(TriggerSource.LOCAL);
                        } else {
                            contactService.removeUserDefinedProfilePicture(
                                identity, TriggerSource.LOCAL
                            );
                        }
                    } else if (viewModel.getGroupModel() != null) {
                        saveGroupAvatar(null);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    loadAvatarForModel(viewModel.getContactIdentity(), viewModel.getGroupModel());
                }
            }.execute();
        }
    }

    private void loadDefaultAvatar(@Nullable String identity, @Nullable GroupModel groupModel) {
        if (identity != null) {
            setAvatarBitmap(contactService.getDefaultAvatar(viewModel.getContactIdentity(), true, isAvatarEditable()));
        } else if (groupModel != null) {
            setAvatarBitmap(groupService.getDefaultAvatar(viewModel.getGroupModel(), true, isAvatarEditable()));
        }
    }

    /**
     * Save avatar bitmap data to group model
     *
     * @param avatar save and send the updated group avatar. If null, it will be deleted.
     */
    @WorkerThread
    public void saveGroupAvatar(@Nullable Bitmap avatar) {
        ch.threema.data.models.GroupModel newGroupModel =
            groupModelRepository.getByCreatorIdentityAndId(
                viewModel.getGroupModel().getCreatorIdentity(),
                viewModel.getGroupModel().getApiGroupId()
            );

        if (newGroupModel == null) {
            logger.error("Group model is null");
            return;
        }

        CheckedProfilePicture profilePicture = CheckedProfilePicture.getOrConvertFromBitmap(avatar);
        if (avatar != null && profilePicture == null) {
            logger.error("The given bitmap could not be converted into a valid profile picture");
            return;
        }

        ProfilePictureChange profilePictureChange = profilePicture != null
            ? new ProfilePictureChange.Set(profilePicture)
            : ProfilePictureChange.Remove.INSTANCE;

        groupFlowDispatcher.runUpdateGroupFlow(
            new GroupChanges(
                null,
                profilePictureChange,
                Set.of(),
                Set.of()
            ),
            newGroupModel
        );
    }

    private void openCamera() {
        try {
            viewModel.setCameraFile(fileService.createTempFile(".camera", ".jpg"));
            FileUtil.getCameraFile(getActivity(), getFragment(), viewModel.getCameraFile(), REQUEST_CODE_CAMERA, fileService, true);
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    private void doCrop(File srcFile) {
        try {
            viewModel.setCroppedFile(fileService.createTempFile(".avatar", ".jpg"));
        } catch (Exception e) {
            logger.error("Exception", e);
        }

        Intent intent = CropImageActivity.createIntent(
            getContext(),
            CropImageActivity.CropImageParameters.getProfilePictureParameters(
                Uri.fromFile(srcFile),
                Uri.fromFile(viewModel.getCroppedFile())
            )
        );

        if (getFragment() != null) {
            getFragment().startActivityForResult(intent, REQUEST_CODE_CROP);
        } else if (getActivity() != null) {
            getActivity().startActivityForResult(intent, REQUEST_CODE_CROP);
        } else {
            logger.error("Cannot start image crop activity");
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                if ((getActivity() != null && !ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA)) ||
                    (getFragment() != null && !getFragment().shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))) {
                    ConfigUtils.showPermissionRationale(getContext(), null, R.string.permission_camera_photo_required);
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_FILE_SELECTOR_ID:
                    // return from image picker
                    if (intent != null && intent.getData() != null) {
                        try {
                            ContentResolver contentResolver = getContext().getContentResolver();
                            String mimeType = contentResolver.getType(intent.getData());
                            if (MimeUtil.isSupportedImageFile(mimeType)) {
                                viewModel.setCameraFile(fileService.createTempFile(".camera", ".jpg"));
                                try (InputStream is = contentResolver.openInputStream(intent.getData());
                                     FileOutputStream fos = new FileOutputStream(viewModel.getCameraFile())) {
                                    if (is != null) {
                                        IOUtils.copy(is, fos);
                                    } else {
                                        throw new Exception("Unable to open input stream");
                                    }
                                } catch (SecurityException e) {
                                    logger.error("Unable to open file selected in picker", e);
                                    startSamsungPermissionFixFlow();
                                    break;
                                }
                                doCrop(viewModel.getCameraFile());
                            } else {
                                Toast.makeText(getContext(), getContext().getString(R.string.unsupported_image_type, mimeType), Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            logger.error("Exception", e);
                        }
                    }
                    break;
                case REQUEST_CODE_CROP:
                    Bitmap bitmap = null;
                    if (viewModel.getCroppedFile() != null && viewModel.getCroppedFile().exists() && viewModel.getCroppedFile().length() > 0) {
                        bitmap = BitmapUtil.safeGetBitmapFromUri(getActivity(), Uri.fromFile(viewModel.getCroppedFile()), ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX, true, true, false);
                        if (bitmap != null) {
                            if (listenerRef.get() != null) {
                                listenerRef.get().onAvatarSet(viewModel.getCroppedFile());
                            } else {
                                String identity = viewModel.getContactIdentity();
                                if (identity != null) {
                                    try {
                                        CheckedProfilePicture profilePicture = CheckedProfilePicture.getOrConvertFromFile(viewModel.getCroppedFile());
                                        if (profilePicture == null) {
                                            logger.error("Could not get profile picture in correct format");
                                            return;
                                        }

                                        if (userService.isMe(identity)) {
                                            userService.setUserProfilePicture(profilePicture, TriggerSource.LOCAL);
                                        } else {
                                            contactService.setUserDefinedProfilePicture(
                                                identity,
                                                profilePicture.getBytes(),
                                                TriggerSource.LOCAL
                                            );
                                        }
                                        loadAvatarForModel(this.viewModel.getContactIdentity(), null);
                                    } catch (Exception e) {
                                        logger.error("Exception", e);
                                    }
                                } else if (viewModel.getGroupModel() != null) {
                                    new AsyncTask<Bitmap, Void, Void>() {
                                        @Override
                                        protected Void doInBackground(Bitmap... bitmaps) {
                                            saveGroupAvatar(bitmaps[0]);
                                            return null;
                                        }

                                        @Override
                                        protected void onPostExecute(Void aVoid) {
                                            loadAvatarForModel(null, viewModel.getGroupModel());
                                        }
                                    }.execute(bitmap);
                                }
                            }
                        }
                    }

                    if (bitmap == null) {
                        new AsyncTask<Void, Void, Bitmap>() {
                            @Override
                            protected Bitmap doInBackground(Void... voids) {
                                return getCurrentAvatarBitmap(false);
                            }

                            @Override
                            protected void onPostExecute(Bitmap bitmap) {
                                setAvatarBitmap(bitmap);
                            }
                        }.execute();
                    } else {
                        setAvatarBitmap(bitmap);
                    }
                    break;
                case REQUEST_CODE_CAMERA:
                    doCrop(viewModel.getCameraFile());
                    break;
            }
        }
    }

    /**
     * Sams*ng forgot to enable the "all files access" permission for the com.android.externalstorage content provider
     * This flow guides users to the system setting allowing them to enable the permission
     * https://issuetracker.google.com/issues/258270138
     */
    private void startSamsungPermissionFixFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            FragmentManager fragmentManager;
            if (getFragment() != null) {
                fragmentManager = getFragment().getParentFragmentManager();
            } else {
                fragmentManager = getActivity().getSupportFragmentManager();
            }
            GenericAlertDialog dialog = GenericAlertDialog.newInstance(
                R.string.workarounds,
                getContext().getString(R.string.samsung_permission_problem_explain),
                R.string.label_continue,
                0);
            dialog.setCallback((tag, data) -> continueSamsungPermissionFixFlow());
            dialog.show(fragmentManager, DIALOG_TAG_SAMSUNG_FIX);
        } else {
            LongToast.makeText(getContext(), R.string.permission_storage_required, Toast.LENGTH_LONG);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void continueSamsungPermissionFixFlow() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:com.android.externalstorage"));
        try {
            if (getFragment() != null) {
                getFragment().startActivity(intent);
            } else {
                getActivity().startActivity(intent);
            }
        } catch (Exception e) {
            logger.error("Unable to start all files accept preference");
        }
    }

    @WorkerThread
    private @Nullable Bitmap getCurrentAvatarBitmap(boolean hires) {
        if (this.viewModel.getContactIdentity() != null) {
            return contactService.getAvatar(this.viewModel.getContactIdentity(), true);
        } else if (this.viewModel.getGroupModel() != null) {
            return groupService.getAvatar(this.viewModel.getGroupModel(), true);
        }
        return null;
    }

    @UiThread
    private void setAvatarBitmap(@Nullable Bitmap bitmap) {
        if (bitmap != null) {
            try {
                if (hires) {
                    if (isMyProfilePicture) {
                        this.avatarImage.setImageDrawable(AvatarConverterUtil.convertToRound(getResources(), bitmap));
                    } else {
                        this.avatarImage.setImageBitmap(bitmap);
                    }
                } else {
                    this.avatarImage.setImageDrawable(AvatarConverterUtil.convertToRound(getResources(), bitmap));
                }
            } catch (RuntimeException e) {
                logger.error("Unable to set avatar bitmap", e);
            }
            if (calculateBrightness(bitmap) > 100) {
                this.avatarImage.setColorFilter(getResources().getColor(R.color.material_grey_300), PorterDuff.Mode.DARKEN);
            } else {
                this.avatarImage.clearColorFilter();
            }
            this.avatarImage.invalidate();
        } else {
            if (this.viewModel.getGroupModel() == null && this.viewModel.getContactIdentity() == null) {
                this.avatarImage.setColorFilter(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnSurface), PorterDuff.Mode.SRC_IN);
            }
        }
    }

    /**
     * Check if an avatar is currently set for this entity
     *
     * @return true if an avatar is set, false if currently no avatar is set
     */
    private boolean hasAvatar() {
        if (this.viewModel.getContactIdentity() != null) {
            return fileService.hasUserDefinedProfilePicture(this.viewModel.getContactIdentity())
                || fileService.hasContactDefinedProfilePicture(this.viewModel.getContactIdentity());
        } else if (this.viewModel.getGroupModel() != null) {
            return fileService.hasGroupProfilePicture(this.viewModel.getGroupModel().getId());
        }
        return false;
    }

    /**
     * Check if the avatar of this entity can be edited
     *
     * @return true if user can set an avatar
     */
    private boolean isAvatarEditable() {
        if (this.viewModel.getContactIdentity() != null) {
            ContactModel contact = contactService.getByIdentity(this.viewModel.getContactIdentity());
            return isEditable && ContactUtil.canHaveCustomAvatar(contact)
                && !(preferenceService.getProfilePicReceive()
                && fileService.hasContactDefinedProfilePicture(this.viewModel.getContactIdentity()));
        } else if (this.viewModel.getGroupModel() != null) {
            GroupModel group = viewModel.getGroupModel();
            return isEditable && groupService.isGroupCreator(group) && groupService.isGroupMember(group);
        }

        // we have neither a group model nor a contact model => user is creating a new group
        if (this.viewModel.getContactIdentity() == null && this.viewModel.getGroupModel() == null) {
            return isEditable;
        }

        return false;
    }

    private void adjustColorFilter(@Nullable Bitmap bitmap) {
        if (bitmap != null && calculateBrightness(bitmap) > 100) {
            AvatarEditView.this.avatarImage.setColorFilter(ContextCompat.getColor(getContext(), R.color.material_grey_300), PorterDuff.Mode.DARKEN);
        } else {
            AvatarEditView.this.avatarImage.clearColorFilter();
        }
    }

    /**** Getters and Setters *****/

    public void setFragment(@NonNull Fragment fragment) {
        this.fragmentRef = new WeakReference<>(fragment);
    }

    public @Nullable Fragment getFragment() {
        return this.fragmentRef.get();
    }

    public void setContactIdentity(@NonNull String identity) {
        this.viewModel.setContactIdentity(identity);
        loadAvatarForModel(identity, null);
    }

    /**
     * Set GroupModel that represents this avatar
     *
     * @param groupModel GroupModel
     */
    public void setGroupModel(@NonNull GroupModel groupModel) {
        this.viewModel.setGroupModel(groupModel);
        loadAvatarForModel(null, groupModel);
    }

    public void setAvatarFile(File avatarFile) {
        if (avatarFile != null && avatarFile.exists() && avatarFile.length() > 0) {
            this.viewModel.setCroppedFile(avatarFile);
            Bitmap bitmap = BitmapUtil.safeGetBitmapFromUri(getActivity(), Uri.fromFile(viewModel.getCroppedFile()), ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX, true, true, false);
            if (bitmap != null) {
                setAvatarBitmap(bitmap);
            }
        }
    }

    /**
     * Set whether the avatar is editable (i.e. is clickable and gets an overlaid photo button or not)
     *
     * @param avatarEditable Desired status
     */
    public void setEditable(boolean avatarEditable) {
        this.isEditable = avatarEditable;
        this.avatarEditOverlay.setVisibility(avatarEditable ? View.VISIBLE : View.GONE);
        this.avatarImage.setClickable(avatarEditable);
        this.avatarImage.setFocusable(avatarEditable);
    }

    /**
     * Sets a listener to be notified when changes have been performed by the user
     * If no listener has been set, any changes will apply immediately, otherwise it's up to the listener to update the underlying data
     *
     * @param listener AvatarEditListener that wants to know about changes
     */
    public void setListener(AvatarEditListener listener) {
        this.listenerRef = new WeakReference<>(listener);
    }

    public void setHires(boolean hires) {
        this.hires = hires;
        this.avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        this.avatarEditOverlay.setForeground(getContext().getDrawable(R.drawable.selector_avatar));
        this.avatarImage.setForeground(null);
        this.avatarImage.setClickable(false);
        this.avatarImage.setFocusable(false);
        this.avatarImage.setOnClickListener(null);
        this.avatarEditOverlay.setClickable(true);
        this.avatarEditOverlay.setFocusable(true);
        this.avatarEditOverlay.setOnClickListener(this);
    }

    public void setIsMyProfilePicture(boolean isMyProfilePicture) {
        this.isMyProfilePicture = isMyProfilePicture;
        setHires(true);
    }

    public void setDefaultAvatar(ContactModel contactModel, GroupModel groupModel) {
        String identity = contactModel != null ? contactModel.getIdentity() : null;
        loadDefaultAvatar(identity, groupModel);
    }

    /**
     * Set avatar representing a contact or group that is yet to be created and thus has no color defined
     *
     * @param avatarType Type of avatar
     */
    public void setUndefinedAvatar(@AvatarTypeDef int avatarType) {
        Bitmap avatar;
        if (avatarType == AVATAR_TYPE_CONTACT) {
            avatar = contactService.getNeutralAvatar(new AvatarOptions.Builder().setHighRes(hires).toOptions());
        } else {
            avatar = groupService.getNeutralAvatar(new AvatarOptions.Builder().setHighRes(hires).toOptions());
        }
        setAvatarBitmap(avatar);
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        ListenerManager.contactListeners.add(this.contactListener);
        ListenerManager.profileListeners.add(this.profileListener);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        ListenerManager.contactListeners.remove(this.contactListener);
        ListenerManager.profileListeners.remove(this.profileListener);
    }

    public interface AvatarEditListener {
        void onAvatarSet(File avatarFile);

        void onAvatarRemoved();
    }
}
