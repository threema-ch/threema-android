/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.ViewModelProvider;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.CropImageActivity;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

import static android.app.Activity.RESULT_OK;
import static ch.threema.app.dialogs.ContactEditDialog.CONTACT_AVATAR_HEIGHT_PX;
import static ch.threema.app.dialogs.ContactEditDialog.CONTACT_AVATAR_WIDTH_PX;

public class AvatarEditView extends FrameLayout implements DefaultLifecycleObserver, View.OnClickListener, View.OnLongClickListener {
	private static final Logger logger = LoggerFactory.getLogger(AvatarEditView.class);
	private static final int REQUEST_CODE_FILE_SELECTOR_ID = 43320;
	private static final int REQUEST_CODE_CAMERA_PERMISSION = 43321;
	private static final int REQUEST_CODE_CAMERA = 43322;
	private static final int REQUEST_CODE_CROP = 43323;
	private ContactService contactService;
	private GroupService groupService;
	private FileService fileService;
	private PreferenceService preferenceService;
	private ImageView avatarImage, avatarEditOverlay;
	private WeakReference<AvatarEditListener> listenerRef = new WeakReference<>(null);
	private boolean hires, isEditable, isMyProfilePicture;

	// the hosting fragment
	private WeakReference<Fragment> fragmentRef = new WeakReference<>(null);
	private WeakReference<AppCompatActivity> activityRef = new WeakReference<>(null);

	// the VieModel containing all data for this view
	public AvatarEditViewModel avatarData;

	// the type of avatar
	@Retention(RetentionPolicy.SOURCE)
	@IntDef({AVATAR_TYPE_CONTACT, AVATAR_TYPE_GROUP})
	public @interface AvatarTypeDef {}
	public static final int AVATAR_TYPE_CONTACT = 0;
	public static final int AVATAR_TYPE_GROUP = 1;

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
		avatarData = new ViewModelProvider(getActivity()).get(AvatarEditViewModel.class);

		try {
			contactService = ThreemaApplication.getServiceManager().getContactService();
			groupService = ThreemaApplication.getServiceManager().getGroupService();
			fileService = ThreemaApplication.getServiceManager().getFileService();
			preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
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

	/**
	 * Load saved avatar for the specified model - do not call this if changes are to be deferred
	 */
	@SuppressLint("StaticFieldLeak")
	@UiThread
	public void loadAvatarForModel(ContactModel contactModel, GroupModel groupModel) {
		new AsyncTask<Void, Void, Bitmap>() {
			@Override
			protected Bitmap doInBackground(Void... params) {
				if (contactModel != null) {
					return contactService.getAvatar(avatarData.getContactModel(), hires);
				} else if (groupModel != null) {
					Bitmap groupAvatar = groupService.getAvatar(groupModel, hires);
					if (groupAvatar == null) {
						groupAvatar = groupService.getDefaultAvatar(groupModel, hires);
					}
					return groupAvatar;
				} else {
					return groupService.getDefaultAvatar(groupModel, hires);
				}
				//				return null;
			}

			@Override
			protected void onPostExecute(Bitmap bitmap) {
				if (avatarImage != null) {
					setAvatarBitmap(bitmap);
				}

				boolean editable = isAvatarEditable();
				avatarImage.setClickable(editable);
				avatarImage.setFocusable(editable);
				avatarEditOverlay.setVisibility(editable ? View.VISIBLE : View.GONE);
			}
		}.execute();
	}

	@Override
	protected void onDetachedFromWindow() {
//		ListenerManager.profileListeners.remove(this.profileListener);
		super.onDetachedFromWindow();
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
//		ListenerManager.profileListeners.add(this.profileListener);
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
	public void onClick(View v) {
		if (!isAvatarEditable()) {
			return;
		}

		MenuBuilder menuBuilder = new MenuBuilder(getContext());
		new MenuInflater(getContext()).inflate(R.menu.view_avatar_edit, menuBuilder);

		ConfigUtils.themeMenu(menuBuilder, ConfigUtils.getColorFromAttribute(getContext(), R.attr.textColorSecondary));

		if (!hasAvatar()) {
			menuBuilder.removeItem(R.id.menu_remove_picture);
		}
		menuBuilder.setCallback(new MenuBuilder.Callback() {
			@Override
			public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
				switch (item.getItemId()) {
					case R.id.menu_take_photo:
						if (ConfigUtils.requestCameraPermissions(getActivity(), getFragment(), REQUEST_CODE_CAMERA_PERMISSION)) {
							openCamera();
						}
						break;
					case R.id.menu_select_from_gallery:
						FileUtil.selectFile(getActivity(), getFragment(), new String[]{MimeUtil.MIME_TYPE_IMAGE}, REQUEST_CODE_FILE_SELECTOR_ID, false, 0, null);
						break;
					case R.id.menu_remove_picture:
						removeAvatar();
						break;
					default:
						return false;
				}
				return true;
			}

			@Override
			public void onMenuModeChange(MenuBuilder menu) { }
		});

		Context wrapper = new ContextThemeWrapper(getContext(), ConfigUtils.getAppTheme(getContext()) == ConfigUtils.THEME_DARK ? R.style.AppBaseTheme_Dark : R.style.AppBaseTheme);
		MenuPopupHelper optionsMenu = new MenuPopupHelper(wrapper, menuBuilder, avatarEditOverlay);
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
				ImagePopup detailPopup = new ImagePopup(getContext(), parent, R.layout.popup_image_nomargin);
				detailPopup.show(AvatarEditView.this, bitmap, null);
			}
		}.execute();

		return false;
	}

	@UiThread
	@SuppressLint("StaticFieldLeak")
	private void removeAvatar() {
		loadDefaultAvatar(avatarData.getContactModel(), avatarData.getGroupModel());

		if (listenerRef.get() != null) {
			listenerRef.get().onAvatarRemoved();
		} else {
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... voids) {
					if (avatarData.getContactModel() != null) {
						contactService.removeAvatar(avatarData.getContactModel());
						fileService.removeContactPhoto(avatarData.getContactModel());
					} else if (avatarData.getGroupModel() != null) {
						saveGroupAvatar(null, true);
					}
					return null;
				}

				@Override
				protected void onPostExecute(Void aVoid) {
					loadAvatarForModel(avatarData.getContactModel(), avatarData.getGroupModel());
				}
			}.execute();
		}
	}

	private void loadDefaultAvatar(ContactModel contactModel, GroupModel groupModel) {
		if (contactModel != null) {
			setAvatarBitmap(contactService.getDefaultAvatar(avatarData.getContactModel(), hires));
		} else if (groupModel != null) {
			setAvatarBitmap(groupService.getDefaultAvatar(avatarData.getGroupModel(), hires));
		}
	}

	/**
	 * Save avatar bitmap data to group model
	 * @param avatar
	 * @param removeAvatar
	 */
	@WorkerThread
	public void saveGroupAvatar(Bitmap avatar, boolean removeAvatar) {
		if (avatar != null || removeAvatar) {
			try {
				groupService.updateGroup(
					avatarData.getGroupModel(),
					null,
					null,
					avatar,
					removeAvatar
				);
			} catch (Exception x) {
				logger.error("Exception", x);
			}
		}
	}

	private void openCamera() {
		try {
			avatarData.setCameraFile(fileService.createTempFile(".camera", ".jpg", !ConfigUtils.useContentUris()));
			FileUtil.getCameraFile(getActivity(), getFragment(), avatarData.getCameraFile(), REQUEST_CODE_CAMERA, fileService, true);
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	private void doCrop(File srcFile, int orientation) {
		try {
			avatarData.setCroppedFile(fileService.createTempFile(".avatar", ".jpg"));
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		Intent intent = new Intent(getActivity(), CropImageActivity.class);
		intent.setData(Uri.fromFile(srcFile));
		intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(avatarData.getCroppedFile()));
		intent.putExtra(CropImageActivity.EXTRA_MAX_X, CONTACT_AVATAR_WIDTH_PX);
		intent.putExtra(CropImageActivity.EXTRA_MAX_Y, CONTACT_AVATAR_HEIGHT_PX);
		intent.putExtra(CropImageActivity.EXTRA_ASPECT_X, 1);
		intent.putExtra(CropImageActivity.EXTRA_ASPECT_Y, 1);
		intent.putExtra(CropImageActivity.EXTRA_OVAL, true);
		intent.putExtra(ThreemaApplication.EXTRA_ORIENTATION, orientation);
		if (getFragment() != null) {
			getFragment().startActivityForResult(intent, REQUEST_CODE_CROP);
		} else {
			getActivity().startActivityForResult(intent, REQUEST_CODE_CROP);
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
							avatarData.setCameraFile(fileService.createTempFile(".camera", ".jpg", !ConfigUtils.useContentUris()));
							try (InputStream is = getActivity().getContentResolver().openInputStream(intent.getData());
							     FileOutputStream fos = new FileOutputStream(avatarData.getCameraFile())) {
								if (is != null) {
									IOUtils.copy(is, fos);
								} else {
									throw new Exception("Unable to open input stream");
								}
							}
							doCrop(avatarData.getCameraFile(), 0);
						} catch (Exception e) {
							logger.error("Exception", e);
						}
					}
					break;
				case REQUEST_CODE_CROP:
					Bitmap bitmap = null;
					if (avatarData.getCroppedFile() != null && avatarData.getCroppedFile().exists() && avatarData.getCroppedFile().length() > 0) {
						bitmap = BitmapUtil.safeGetBitmapFromUri(getActivity(), Uri.fromFile(avatarData.getCroppedFile()), CONTACT_AVATAR_HEIGHT_PX, true);
						if (bitmap != null) {
							if (listenerRef.get() != null) {
								listenerRef.get().onAvatarSet(avatarData.getCroppedFile());
							} else {
								if (this.avatarData.getContactModel() != null) {
									try {
										contactService.setAvatar(this.avatarData.getContactModel(), avatarData.getCroppedFile());
										loadAvatarForModel(this.avatarData.getContactModel(), null);
									} catch (Exception e) {
										logger.error("Exception", e);
									}
								} else if (avatarData.getGroupModel() != null) {
									new AsyncTask<Bitmap, Void, Void>() {
										@Override
										protected Void doInBackground(Bitmap... bitmaps) {
											saveGroupAvatar(bitmaps[0], false);
											return null;
										}

										@Override
										protected void onPostExecute(Void aVoid) {
											loadAvatarForModel(null, avatarData.getGroupModel());
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
					int cameraRotation = 0;
					doCrop(avatarData.getCameraFile(), cameraRotation);
					break;
			}
		}
	}

	@WorkerThread
	private @Nullable Bitmap getCurrentAvatarBitmap(boolean hires) {
		if (this.avatarData.getContactModel() != null) {
			return contactService.getAvatar(this.avatarData.getContactModel(), hires);
		} else if (this.avatarData.getGroupModel() != null) {
			return groupService.getAvatar(this.avatarData.getGroupModel(), hires);
		}
		return null;
	}

	@UiThread
	private void setAvatarBitmap(@Nullable Bitmap bitmap) {
		if (bitmap != null) {
			if (hires) {
				if (isMyProfilePicture) {
					this.avatarImage.setImageDrawable(AvatarConverterUtil.convertToRound(getResources(), bitmap));
				} else {
					this.avatarImage.setImageBitmap(bitmap);
				}
			} else {
				this.avatarImage.setImageDrawable(AvatarConverterUtil.convertToRound(getResources(), bitmap));
			}
			if (ColorUtil.getInstance().calculateBrightness(bitmap, 2) > 100) {
				this.avatarImage.setColorFilter(getResources().getColor(R.color.material_grey_300), PorterDuff.Mode.DARKEN);
			} else {
				this.avatarImage.clearColorFilter();
			}
			this.avatarImage.invalidate();
		} else {
			if (this.avatarData.getGroupModel() == null && this.avatarData.getContactModel() == null) {
				this.avatarImage.setColorFilter(ConfigUtils.getColorFromAttribute(getContext(), R.attr.textColorSecondary), PorterDuff.Mode.SRC_IN);
			}
		}
	}

	/**
	 * Check if an avatar is currently set for this entity
	 * @return true if an avatar is set, false if currently no avatar is set
	 */
	private boolean hasAvatar() {
		if (this.avatarData.getContactModel() != null) {
			return fileService.hasContactAvatarFile(this.avatarData.getContactModel()) || fileService.hasContactPhotoFile(this.avatarData.getContactModel());
		} else if (this.avatarData.getGroupModel() != null) {
			return fileService.hasGroupAvatarFile(this.avatarData.getGroupModel());
		}
		return false;
	}

	/**
	 * Check if the avatar of this entity can be edited
	 * @return true if user can set an avatar
	 */
	private boolean isAvatarEditable() {
		if (this.avatarData.getContactModel() != null) {
			return isEditable && ContactUtil.canHaveCustomAvatar(this.avatarData.getContactModel()) && !(preferenceService.getProfilePicReceive() && fileService.hasContactPhotoFile(this.avatarData.getContactModel()));
		} else if (this.avatarData.getGroupModel() != null) {
			return isEditable && groupService.isGroupOwner(this.avatarData.getGroupModel());
		}

		// we have neither a group model nor a contact model => user is creating a new group
		if (this.avatarData.getContactModel() == null && this.avatarData.getGroupModel() == null) {
			return isEditable;
		}

		return false;
	}

	/**** Getters and Setters *****/

	public void setFragment(@NonNull Fragment fragment) {
		this.fragmentRef = new WeakReference<>(fragment);
	}

	public @Nullable Fragment getFragment() {
		return this.fragmentRef.get();
	}

	public void setContactModel(@NonNull ContactModel contactModel) {
		this.avatarData.setContactModel(contactModel);
		loadAvatarForModel(contactModel, null);
	}

	/**
	 * Set GroupModel that represents this avatar
	 * @param groupModel GroupModel
	 */
	public void setGroupModel(@NonNull GroupModel groupModel) {
		this.avatarData.setGroupModel(groupModel);
		loadAvatarForModel(null, groupModel);
	}

	public void setAvatarFile(File avatarFile) {
		if (avatarFile != null && avatarFile.exists() && avatarFile.length() > 0) {
			this.avatarData.setCroppedFile(avatarFile);
			Bitmap bitmap = BitmapUtil.safeGetBitmapFromUri(getActivity(), Uri.fromFile(avatarData.getCroppedFile()), CONTACT_AVATAR_HEIGHT_PX, hires);
			if (bitmap != null) {
				setAvatarBitmap(bitmap);
			}
		}
	}

	/**
	 * Set whether the avatar is editable (i.e. is clickable and gets an overlaid photo button or not)
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
	 * @param listener AvatarEditListener that wants to know about changes
	 */
	public void setListener(AvatarEditListener listener) {
		this.listenerRef = new WeakReference<>(listener);
	}

	public void setHires(boolean hires) {
		this.hires = hires;
		this.avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			this.avatarEditOverlay.setForeground(getContext().getDrawable(R.drawable.selector_avatar));
			this.avatarImage.setForeground(null);
		}
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
		loadDefaultAvatar(contactModel, groupModel);
	}

	/**
	 * Set avatar representing a contact or group that is yet to be created and thus has no color defined
	 * @param avatarType Type of avatar
	 */
	public void setUndefinedAvatar(@AvatarTypeDef int avatarType) {
		if (avatarType == AVATAR_TYPE_CONTACT) {
			setAvatarBitmap(contactService.getNeutralAvatar(hires));
		} else {
			setAvatarBitmap(groupService.getNeutralAvatar(hires));
		}
	}

	public interface AvatarEditListener {
		void onAvatarSet(File avatarFile);
		void onAvatarRemoved();
	}
}
