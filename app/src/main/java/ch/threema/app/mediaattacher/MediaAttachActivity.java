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

package ch.threema.app.mediaattacher;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.FitWindowsFrameLayout;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.actions.LocationMessageSendAction;
import ch.threema.app.actions.SendAction;
import ch.threema.app.activities.SendMediaActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ballot.BallotWizardActivity;
import ch.threema.app.camera.CameraUtil;
import ch.threema.app.dialogs.ExpandableTextEntryDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.listeners.QRCodeScanListener;
import ch.threema.app.locationpicker.LocationPickerActivity;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.app.utils.RuntimeUtil;

import static ch.threema.app.ThreemaApplication.MAX_BLOB_SIZE;
import static ch.threema.app.utils.IntentDataUtil.INTENT_DATA_LOCATION_NAME;

public class MediaAttachActivity extends MediaSelectionBaseActivity implements View.OnClickListener,
									MediaAttachAdapter.ItemClickListener,
									ExpandableTextEntryDialog.ExpandableTextEntryDialogClickListener,
									GenericAlertDialog.DialogClickListener {

	private static final Logger logger = LoggerFactory.getLogger(MediaAttachActivity.class);

	private static final int CONTACT_PICKER_INTENT = 33002;
	private static final int LOCATION_PICKER_INTENT = 33003;

	private static final int PERMISSION_REQUEST_LOCATION = 1;
	private static final int PERMISSION_REQUEST_ATTACH_CONTACT = 2;
	private static final int PERMISSION_REQUEST_QR_READER = 3;
	private static final int PERMISSION_REQUEST_ATTACH_FROM_GALLERY = 4;
	private static final int PERMISSION_REQUEST_ATTACH_FROM_EXTERNAL_CAMERA = 6;

	protected static final int REQUEST_CODE_ATTACH_FROM_GALLERY = 2454;

	public static final String CONFIRM_TAG_REALLY_SEND_FILE = "reallySendFile";
	public static final String DIALOG_TAG_PREPARE_SEND_FILE = "prepSF";

	private ConstraintLayout sendPanel;
	private LinearLayout attachPanel;
	private ControlPanelButton attachGalleryButton, attachLocationButton, attachQRButton, attachBallotButton, attachContactButton, attachFileButton, sendButton, editButton, cancelButton, attachFromExternalCameraButton;
	private Button selectCounterButton;
	private ImageView moreArrowView;
	private HorizontalScrollView scrollView;

	private MessageReceiver messageReceiver;
	private MessageService messageService;

	/* start setup methods */
	@Override
	protected void initActivity(Bundle savedInstanceState) {
		super.initActivity(savedInstanceState);

		this.handleIntent();

		this.setControlPanelLayout();
		this.setupControlPanelListeners();
		this.setInitialMediaGrid();

		this.handleSavedInstanceState(savedInstanceState);

		this.toolbar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.menu_select_from_gallery) {
				if (ConfigUtils.requestStoragePermissions(MediaAttachActivity.this, null, PERMISSION_REQUEST_ATTACH_FROM_GALLERY)) {
					attachImageFromGallery();
				}
				return true;
			}
			return false;
		});

		this.scrollView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

				// we shortly display an arrow hinting users at more options that are available when scrolling
				// of course, we do this only when some buttons are obscured
				View child = (View) scrollView.getChildAt(0);
				if (child != null) {
					int childWidth = (child).getWidth();
					if (scrollView.getWidth() < (childWidth + scrollView.getPaddingLeft() + scrollView.getPaddingRight())) {
						if (moreArrowView != null) {
							moreArrowView.setVisibility(View.VISIBLE);
							moreArrowView.animate()
								.alpha(0f)
								.setStartDelay(1500)
								.setDuration(500)
								.setListener(new AnimatorListenerAdapter() {
									@Override
									public void onAnimationEnd(Animator animation) {
										moreArrowView.setVisibility(View.GONE);
									}
								});
						}
					}
				}
			}
		});
	}

	@Override
	protected void initServices() {
		super.initServices();
		try {
			this.messageService = serviceManager.getMessageService();
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	public void handleIntent() {
		Intent intent = this.getIntent();
		this.messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(this, intent);

		if (this.messageReceiver == null) {
			logger.error("invalid receiver");
			finish();
		}
	}

	public void setControlPanelLayout() {
		ViewStub stub = findViewById(R.id.stub);
		stub.setLayoutResource(R.layout.media_attach_control_panel);
		stub.inflate();

		this.controlPanel = findViewById(R.id.control_panel);
		this.sendPanel = findViewById(R.id.send_panel);
		this.attachPanel = findViewById(R.id.attach_options_container);
		this.scrollView = findViewById(R.id.attach_panel);

		// Horizontal buttons in the panel
		this.attachGalleryButton = attachPanel.findViewById(R.id.attach_gallery);
		this.attachLocationButton = attachPanel.findViewById(R.id.attach_location);
		this.attachFileButton = attachPanel.findViewById(R.id.attach_file);
		this.attachQRButton = attachPanel.findViewById(R.id.attach_qr_code);
		this.attachBallotButton = attachPanel.findViewById(R.id.attach_poll);
		this.attachContactButton = attachPanel.findViewById(R.id.attach_contact);
		this.attachFromExternalCameraButton = attachPanel.findViewById(R.id.attach_system_camera);

		// Send/edit/cancel buttons
		this.sendButton = sendPanel.findViewById(R.id.send);
		this.editButton = sendPanel.findViewById(R.id.edit);
		this.cancelButton = sendPanel.findViewById(R.id.cancel);
		this.selectCounterButton = sendPanel.findViewById(R.id.select_counter_button);

		// Reset click listeners
		this.controlPanel.setOnClickListener(null);
		this.sendPanel.setOnClickListener(null);

		// additional decoration
		this.moreArrowView = findViewById(R.id.more_arrow);
		this.moreArrowView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (scrollView != null) {
					scrollView.smoothScrollTo(65535, 0);
				}
			}
		});

		// If the media grid is shown, we don't need the gallery button
		if (shouldShowMediaGrid()) {
			this.attachGalleryButton.setVisibility(View.GONE);
		}

		if (messageReceiver instanceof DistributionListMessageReceiver) {
			this.attachBallotButton.setVisibility(View.GONE);
		}

		if (attachFromExternalCameraButton != null && !CameraUtil.isInternalCameraSupported()) {
			this.attachFromExternalCameraButton.setVisibility(View.GONE);
		}
	}

	private void setupControlPanelListeners() {
		attachGalleryButton.setOnClickListener(this);
		attachLocationButton.setOnClickListener(this);
		attachFileButton.setOnClickListener(this);
		attachQRButton.setOnClickListener(this);
		attachBallotButton.setOnClickListener(this);
		attachContactButton.setOnClickListener(this);
		sendButton.setOnClickListener(this);
		editButton.setOnClickListener(this);
		cancelButton.setOnClickListener(this);
		selectCounterButton.setOnClickListener(this);
		attachFromExternalCameraButton.setOnClickListener(this);
	}
	/* end setup methods */

	/* start section action methods */
	@Override
	public void onItemChecked(int count) {
		int gridPaddingLeftRight = getResources().getDimensionPixelSize(R.dimen.grid_spacing);

		if (this.selectFromGalleryItem != null) {
			this.selectFromGalleryItem.setVisible(count == 0);
		}

		if (count > 0) {
			if (moreArrowView != null) {
				moreArrowView.setVisibility(View.GONE);
			}

			if (sendPanel.getVisibility() == View.GONE) {
				attachPanel.setVisibility(View.GONE);
				sendPanel.setVisibility(View.VISIBLE);
				// only slide up when previously hidden otherwise animate switch between panels
				if (controlPanel.getTranslationY() != 0) {
					controlPanel.animate().translationY(0).withEndAction(() -> bottomSheetLayout.setPadding(
						gridPaddingLeftRight,
						0,
						gridPaddingLeftRight,
						controlPanel.getHeight() - getResources().getDimensionPixelSize(R.dimen.media_attach_control_panel_shadow_size)));
				} else {
					AnimationUtil.bubbleAnimate(sendButton, 50);
					AnimationUtil.bubbleAnimate(selectCounterButton, 50);
					AnimationUtil.bubbleAnimate(editButton, 75);
					AnimationUtil.bubbleAnimate(cancelButton, 100);
					bottomSheetLayout.setPadding(
						gridPaddingLeftRight,
						0,
						gridPaddingLeftRight,
						controlPanel.getHeight() - getResources().getDimensionPixelSize(R.dimen.media_attach_control_panel_shadow_size)
					);
				}
			}

			if (count > SendMediaActivity.MAX_SELECTABLE_IMAGES) {
				editButton.setAlpha(0.2f);
				editButton.setClickable(false);
			} else {
				editButton.setAlpha(1.0f);
				editButton.setClickable(true);
				editButton.setLabelText(R.string.edit);
			}
			selectCounterButton.setText(String.format(LocaleUtil.getCurrentLocale(this), "%d", count));

		} else if (BottomSheetBehavior.from(bottomSheetLayout).getState() == BottomSheetBehavior.STATE_EXPANDED) {
			controlPanel.animate().translationY(
				(float) controlPanel.getHeight() - getResources().getDimensionPixelSize(R.dimen.media_attach_control_panel_shadow_size)
			).withEndAction(() -> {
				sendPanel.setVisibility(View.GONE);
				attachPanel.setVisibility(View.VISIBLE);
			});
			//animate padding change to avoid flicker
			ValueAnimator animator = ValueAnimator.ofInt(bottomSheetLayout.getPaddingBottom(), 0);
			animator.addUpdateListener(valueAnimator -> bottomSheetLayout.setPadding(
				gridPaddingLeftRight,
				0,
				gridPaddingLeftRight,
				(Integer) valueAnimator.getAnimatedValue()));
			animator.setDuration(300);
			animator.start();
		} else {
			sendPanel.setVisibility(View.GONE);
			attachPanel.setVisibility(View.VISIBLE);
			bottomSheetLayout.setPadding(
				gridPaddingLeftRight,
				0,
				gridPaddingLeftRight,
				0);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				if (attachGalleryButton.getVisibility() == View.VISIBLE) {
					AnimationUtil.bubbleAnimate(attachGalleryButton, 25);
				}
				AnimationUtil.bubbleAnimate(attachFileButton, 25);
				AnimationUtil.bubbleAnimate(attachLocationButton, 50);
				if (attachBallotButton.getVisibility() == View.VISIBLE) {
					AnimationUtil.bubbleAnimate(attachBallotButton, 50);
				}
				AnimationUtil.bubbleAnimate(attachContactButton, 75);
				AnimationUtil.bubbleAnimate(attachQRButton, 75);
				AnimationUtil.bubbleAnimate(attachFromExternalCameraButton, 100);
			}
		}
	}

	@SuppressLint("NonConstantResourceId")
	@Override
	public void onClick(View v) {
		super.onClick(v);
		int id = v.getId();
		switch (id) {
			case R.id.attach_location:
				if (ConfigUtils.requestLocationPermissions(this, null, PERMISSION_REQUEST_LOCATION)) {
					if (!ConfigUtils.hasNoMapboxSupport()) {
						launchPlacePicker();
					} else {
						Toast.makeText(this, "Feature not available due to firmware error", Toast.LENGTH_LONG).show();
					}
				}
				break;
			case R.id.attach_file:
				if (ConfigUtils.requestStoragePermissions(this, null, PERMISSION_REQUEST_ATTACH_FILE)) {
					attachFile();
				}
				break;
			case R.id.attach_poll:
				createBallot();
				break;
			case R.id.attach_qr_code:
				if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_QR_READER)) {
					attachQR();
				}
				break;
			case R.id.attach_contact:
				if (ConfigUtils.requestContactPermissions(this, null, PERMISSION_REQUEST_ATTACH_CONTACT)) {
					attachContact();
				}
				break;
			case R.id.edit:
				if (mediaAttachAdapter != null) {
					onEdit(mediaAttachViewModel.getSelectedMediaUris());
					finish();
				}
				break;
			case R.id.send:
				if (mediaAttachAdapter != null) {
					v.setAlpha(0.3f);
					v.setClickable(false);
					onSend(mediaAttachViewModel.getSelectedMediaUris());
					finish();
				}
				break;
			case R.id.attach_gallery:
				if (ConfigUtils.requestStoragePermissions(this, null, PERMISSION_REQUEST_ATTACH_FROM_GALLERY)) {
					attachImageFromGallery();
				}
				break;
			case R.id.attach_system_camera:
				if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_ATTACH_FROM_EXTERNAL_CAMERA)) {
					attachFromExternalCamera();
				}
			default:
				break;
		}
	}

	/* end section action methods */

	/* start section callback methods */
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			switch (requestCode) {
				case PERMISSION_REQUEST_LOCATION:
					launchPlacePicker();
					break;
				case PERMISSION_REQUEST_ATTACH_CONTACT:
					attachContact();
					break;
				case PERMISSION_REQUEST_ATTACH_FILE:
					attachFile();
					break;
				case PERMISSION_REQUEST_QR_READER:
					attachQR();
					break;
				case PERMISSION_REQUEST_ATTACH_FROM_GALLERY:
					attachImageFromGallery();
					break;
				case PERMISSION_REQUEST_ATTACH_FROM_EXTERNAL_CAMERA:
					attachFromExternalCamera();
					break;
			}
		} else {
			switch (requestCode) {
				case PERMISSION_REQUEST_LOCATION:
					if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
						super.showPermissionRationale(R.string.permission_location_required);
					}
					break;
				case PERMISSION_REQUEST_ATTACH_CONTACT:
					if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
						super.showPermissionRationale(R.string.permission_contacts_required);
					}
					break;
				case PERMISSION_REQUEST_QR_READER:
					if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
						super.showPermissionRationale(R.string.permission_camera_qr_required);
					}
					break;
				case PERMISSION_REQUEST_ATTACH_FROM_GALLERY:
				case PERMISSION_REQUEST_ATTACH_FILE:
					if (!ActivityCompat.shouldShowRequestPermissionRationale(MediaAttachActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
						showPermissionRationale(R.string.permission_storage_required);
					}
					break;
				case PERMISSION_REQUEST_ATTACH_FROM_EXTERNAL_CAMERA:
					if (!ActivityCompat.shouldShowRequestPermissionRationale(MediaAttachActivity.this, Manifest.permission.CAMERA)) {
						showPermissionRationale(R.string.permission_camera_photo_required);
					}
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, final Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if (resultCode == Activity.RESULT_OK) {
			final String scanResult = QRScannerUtil.getInstance().parseActivityResult(this, requestCode, resultCode, intent);
			if (scanResult != null && scanResult.length() > 0) {
				ListenerManager.qrCodeScanListener.handle(new ListenerManager.HandleListener<QRCodeScanListener>() {
					@Override
					public void handle(QRCodeScanListener listener) {
						listener.onScanCompleted(scanResult);
					}
				});
				finish();
			}

			switch (requestCode) {
				case LOCATION_PICKER_INTENT:
					Location location = IntentDataUtil.getLocation(intent);
					String poiName = intent.getStringExtra(INTENT_DATA_LOCATION_NAME);
					sendLocationMessage(location, poiName);
					break;
				case CONTACT_PICKER_INTENT:
					sendContact(intent.getData());
					finish();
					break;
				case REQUEST_CODE_ATTACH_FROM_GALLERY:
					onEdit(FileUtil.getUrisFromResult(intent, getContentResolver()));
					break;
				case ThreemaActivity.ACTIVITY_ID_CREATE_BALLOT:
					// fallthrough
				case ThreemaActivity.ACTIVITY_ID_SEND_MEDIA:
					finish();
					break;
				case ThreemaActivity.ACTIVITY_ID_PICK_FILE:
					prepareSendFileMessage(FileUtil.getUrisFromResult(intent, getContentResolver()));
					break;
			}
		}
	}

	// expandable alert dialog listeners
	@Override
	public void onYes(String tag, Object data, String text) {
		if (DIALOG_TAG_PREPARE_SEND_FILE.equals(tag)) {
			ArrayList<Uri> uriList = (ArrayList<Uri>) data;
			ArrayList<String> captions = new ArrayList<>(uriList.size());

			int i = 0;
			while (i < uriList.size()) {
				captions.add(text);
				i++;
			}

			sendFileMessage(uriList, captions);
		}
	}

	@Override
	public void onNo(String tag) {
		FitWindowsFrameLayout contentFrameLayout = (FitWindowsFrameLayout) ((ViewGroup) rootView.getParent()).getParent();
		contentFrameLayout.setVisibility(View.VISIBLE);
	}

	//Generic Alert Dialog Listeners
	@Override
	public void onYes(String tag, Object data) {
		if (CONFIRM_TAG_REALLY_SEND_FILE.equals(tag)) {
			preferenceService.setFileSendInfoShown(true);
			FileUtil.selectFile(this, null, new String[]{MimeUtil.MIME_TYPE_ANY}, ThreemaActivity.ACTIVITY_ID_PICK_FILE, true, MAX_BLOB_SIZE, null);
		}
	}

	@Override
	public void onNo(String tag, Object data) { }

	/* end section callback methods */

	/* start section attachment/sending methods */

	@UiThread
	public void onEdit(final ArrayList<Uri> uriList) {
		ArrayList<MediaItem> mediaItems = new ArrayList<>(uriList.size());
		for (Uri uri: uriList) {
			String mimeType = FileUtil.getMimeTypeFromUri(this, uri);
			if (MimeUtil.isVideoFile(mimeType) || MimeUtil.isImageFile(mimeType)) {
				MediaItem mediaItem = new MediaItem(uri, mimeType, null);
				mediaItem.setFilename(FileUtil.getFilenameFromUri(getContentResolver(), mediaItem));
				mediaItems.add(mediaItem);
			}
		}
		if (mediaItems.size() > 0) {
			Intent intent = IntentDataUtil.addMessageReceiversToIntent(new Intent(this, SendMediaActivity.class), new MessageReceiver[]{this.messageReceiver});
			intent.putExtra(SendMediaActivity.EXTRA_MEDIA_ITEMS, mediaItems);
			intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, messageReceiver.getDisplayName());
			AnimationUtil.startActivityForResult(this, null, intent, ThreemaActivity.ACTIVITY_ID_SEND_MEDIA);
		} else {
			Toast.makeText(MediaAttachActivity.this, R.string.only_images_or_videos, Toast.LENGTH_LONG).show();
		}
	}

	@UiThread
	public void onSend(final ArrayList<Uri> list) {
		List<MediaItem> mediaItems = new ArrayList<>();
		if (!validateSendingPermission()) {
			return;
		}

		for (Uri uri : list) {
			MediaItem mediaItem = new MediaItem(uri, FileUtil.getMimeTypeFromUri(this, uri), null);
			mediaItem.setFilename(FileUtil.getFilenameFromUri(getContentResolver(), mediaItem));
			mediaItems.add(mediaItem);
		}

		if (mediaItems.size() > 0) {
			messageService.sendMediaAsync(mediaItems, Collections.singletonList(messageReceiver));
			finish();
		}
	}

	private void attachFile() {
		if (preferenceService != null && !preferenceService.getFileSendInfoShown()) {
			GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.send_as_files,
				R.string.send_as_files_warning,
				R.string.ok,
				R.string.cancel);
			dialog.show(getSupportFragmentManager(), CONFIRM_TAG_REALLY_SEND_FILE);
		} else {
			FileUtil.selectFile(this, null, new String[]{MimeUtil.MIME_TYPE_ANY}, ThreemaActivity.ACTIVITY_ID_PICK_FILE, true, MAX_BLOB_SIZE, null);
		}
	}

	private void attachImageFromGallery() {
		try {
			Intent getContentIntent = new Intent();
			getContentIntent.setType(MimeUtil.MIME_TYPE_VIDEO);
			getContentIntent.setAction(Intent.ACTION_GET_CONTENT);
			getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
			getContentIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, MAX_BLOB_SIZE);
			Intent pickIntent = new Intent(Intent.ACTION_PICK);
			pickIntent.setType(MimeUtil.MIME_TYPE_IMAGE);
			Intent chooserIntent = Intent.createChooser(pickIntent, getString(R.string.select_from_gallery));
			chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{getContentIntent});

			startActivityForResult(chooserIntent, REQUEST_CODE_ATTACH_FROM_GALLERY);
		} catch (Exception e) {
			logger.debug("Exception", e);
			Toast.makeText(this, R.string.no_activity_for_mime_type, Toast.LENGTH_SHORT).show();
		}
	}

	private void attachFromExternalCamera() {
		Intent intent = IntentDataUtil.addMessageReceiversToIntent(new Intent(this, SendMediaActivity.class), new MessageReceiver[]{this.messageReceiver});
		intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, messageReceiver.getDisplayName());
		intent.putExtra(ThreemaApplication.INTENT_DATA_PICK_FROM_CAMERA, true);
		intent.putExtra(SendMediaActivity.EXTRA_USE_EXTERNAL_CAMERA, true);
		AnimationUtil.startActivityForResult(this, null, intent, ThreemaActivity.ACTIVITY_ID_SEND_MEDIA);
	}

	private void createBallot() {
		Intent intent = new Intent(this, BallotWizardActivity.class);
		IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);
		AnimationUtil.startActivityForResult(this, null, intent, ThreemaActivity.ACTIVITY_ID_CREATE_BALLOT);
	}

	private void launchPlacePicker() {
		Intent intent = new Intent(this, LocationPickerActivity.class);
		AnimationUtil.startActivityForResult(this, null, intent, LOCATION_PICKER_INTENT);
	}

	private void attachContact() {
		try {
			Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
			AnimationUtil.startActivityForResult(this, null, intent, CONTACT_PICKER_INTENT);
		} catch (ActivityNotFoundException e) {
			SingleToast.getInstance().showShortText(getString(R.string.no_activity_for_mime_type));
		}
	}

	private void attachQR() {
		QRScannerUtil.getInstance().initiateScan(this, true, null);
	}

	private void prepareSendFileMessage(final ArrayList<Uri> uriList) {
		FitWindowsFrameLayout contentFrameLayout = (FitWindowsFrameLayout) ((ViewGroup) rootView.getParent()).getParent();
		contentFrameLayout.setVisibility(View.GONE);
		ExpandableTextEntryDialog alertDialog = ExpandableTextEntryDialog.newInstance(getString(R.string.send_as_files), R.string.add_caption_hint, R.string.send, R.string.cancel, true);
		alertDialog.setData(uriList);
		alertDialog.show(getSupportFragmentManager(), DIALOG_TAG_PREPARE_SEND_FILE);
	}

	private void sendLocationMessage(final Location location, final String poiName) {
		if (!validateSendingPermission()) {
			return;
		}

		new Thread(() -> LocationMessageSendAction.getInstance()
			.sendLocationMessage(
				new MessageReceiver[]{messageReceiver},
				location,
				poiName,
				new SendAction.ActionHandler() {
					@Override
					public void onError(String errorMessage) { }

					@Override
					public void onWarning(String warning, boolean continueAction) { }

					@Override
					public void onProgress(int progress, int total) { }

					@Override
					public void onCompleted() {
						finish();
					}
				})).start();
	}

	private void sendContact(Uri contactUri) {
		if (!validateSendingPermission()) {
			return;
		}

		Cursor cursor = this.getContentResolver()
			.query(contactUri, null, null, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			String lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
			cursor.close();
			Uri vcardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey);
			sendFileMessage(new ArrayList<>(Collections.singletonList(vcardUri)),null);
		} else {
			Toast.makeText(this, R.string.contact_not_found, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Send file messages of any type
	 * @param uriList
	 * @param captions
	 */
	private void sendFileMessage(final ArrayList<Uri> uriList, final ArrayList<String> captions) {
		if (!validateSendingPermission()) {
			return;
		}

		List<MediaItem> mediaItems = new ArrayList<>(uriList.size());
		for(int i = 0; i < uriList.size(); i++) {
			MediaItem mediaItem = new MediaItem(uriList.get(i), MediaItem.TYPE_FILE);
			if (captions != null) {
				mediaItem.setCaption(captions.get(i));
			}
			mediaItems.add(mediaItem);
		}
		messageService.sendMediaAsync(mediaItems, Collections.singletonList(messageReceiver));
		finish();
	}

	private boolean validateSendingPermission() {
		return this.messageReceiver != null
			&& this.messageReceiver.validateSendingPermission(errorResId -> RuntimeUtil.runOnUiThread(() -> SingleToast.getInstance().showLongText(getString(errorResId))));
	}
	/* end section sending/attachment methods */
}
