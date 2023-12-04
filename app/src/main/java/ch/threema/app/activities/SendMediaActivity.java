/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import static ch.threema.app.ThreemaApplication.getMessageDraft;
import static ch.threema.app.adapters.SendMediaPreviewAdapter.VIEW_TYPE_NORMAL;
import static ch.threema.app.services.PreferenceService.ImageScale_SEND_AS_FILE;
import static ch.threema.app.services.PreferenceService.VideoSize_DEFAULT;
import static ch.threema.app.services.PreferenceService.VideoSize_MEDIUM;
import static ch.threema.app.services.PreferenceService.VideoSize_ORIGINAL;
import static ch.threema.app.services.PreferenceService.VideoSize_SEND_AS_FILE;
import static ch.threema.app.services.PreferenceService.VideoSize_SMALL;
import static ch.threema.app.ui.MediaItem.TYPE_IMAGE;
import static ch.threema.app.ui.MediaItem.TYPE_IMAGE_CAM;
import static ch.threema.app.ui.MediaItem.TYPE_VIDEO;
import static ch.threema.app.ui.MediaItem.TYPE_VIDEO_CAM;
import static ch.threema.app.utils.MediaAdapterManagerKt.NOTIFY_ADAPTER;
import static ch.threema.app.utils.MediaAdapterManagerKt.NOTIFY_ALL;
import static ch.threema.app.utils.MediaAdapterManagerKt.NOTIFY_BOTH_ADAPTERS;
import static ch.threema.app.utils.MediaAdapterManagerKt.NOTIFY_PREVIEW_ADAPTER;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.SendMediaAdapter;
import ch.threema.app.adapters.SendMediaPreviewAdapter;
import ch.threema.app.camera.CameraActivity;
import ch.threema.app.camera.CameraUtil;
import ch.threema.app.dialogs.CallbackTextEntryDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.emojis.EmojiButton;
import ch.threema.app.emojis.EmojiPicker;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.mediaattacher.MediaFilterQuery;
import ch.threema.app.mediaattacher.MediaSelectionActivity;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.ComposeEditText;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.DebouncedOnMenuItemClickListener;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.SendButton;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.MediaAdapterListener;
import ch.threema.app.utils.MediaAdapterManager;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.video.VideoTimelineCache;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.localcrypto.MasterKeyLockedException;

public class SendMediaActivity extends ThreemaToolbarActivity implements
	GenericAlertDialog.DialogClickListener,
	ThreemaToolbarActivity.OnSoftKeyboardChangedListener,
	MediaAdapterListener {

	private static final Logger logger = LoggingUtil.getThreemaLogger("SendMediaActivity");

	private static final String STATE_BIGIMAGE_POS = "bigimage_pos";
	private static final String STATE_ITEMS = "items";
	private static final String STATE_TEMP_FILE = "tempFile";
	private static final String STATE_CAMERA_FILE = "cameraFile";
	private static final String STATE_VIDEO_FILE = "vidFile";

	public static final String EXTRA_MEDIA_ITEMS = "mediaitems";
	public static final String EXTRA_USE_EXTERNAL_CAMERA = "extcam";

	public static final int MAX_EDITABLE_IMAGES = 256; // Max number of images/videos that can be edited here at once

	private static final String DIALOG_TAG_QUIT_CONFIRM = "qc";
	private static final long IMAGE_ANIMATION_DURATION_MS = 180;
	private static final int PERMISSION_REQUEST_CAMERA = 100;

	private MediaAdapterManager mediaAdapterManager;
	private SendMediaAdapter sendMediaAdapter;
	private SendMediaPreviewAdapter sendMediaPreviewAdapter;
	private RecyclerView recyclerView;
	private ViewPager2 viewPager;
	private ArrayList<MessageReceiver> messageReceivers;
	private FileService fileService;
	private MessageService messageService;
	private File tempFile = null;
	private ComposeEditText captionEditText;
	private LinearLayout activityParentLayout;
	private EmojiPicker emojiPicker;
	private ImageButton cameraButton;
	private String cameraFilePath, videoFilePath;
	private boolean pickFromCamera, hasChanges = false;
	private View backgroundLayout;
	private boolean useExternalCamera;
	private MenuItem settingsItem, editFilenameItem;
	private MediaFilterQuery lastMediaFilter;
	private TextView itemCountText;

	final ItemTouchHelper.SimpleCallback dragCallback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.RIGHT|ItemTouchHelper.LEFT, 0) {
		@Override
		public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
			int oldPosition = viewHolder.getBindingAdapterPosition();
			int newPosition = target.getBindingAdapterPosition();

			logger.debug("drag item position changed from {} to {}", oldPosition, newPosition);

			mediaAdapterManager.move(oldPosition, newPosition, NOTIFY_PREVIEW_ADAPTER);

			return true;
		}

		@Override
		public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
			return makeMovementFlags(
				viewHolder.getItemViewType() == VIEW_TYPE_NORMAL ? getDragDirs(recyclerView, viewHolder) : 0,
				getSwipeDirs(recyclerView, viewHolder)
			);
		}

		@Override
		public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
			// we're not interested in swipes
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		backgroundLayout = null;

		super.onCreate(savedInstanceState);
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		if (!super.initActivity(savedInstanceState)) {
			return false;
		}

		if (preferenceService.getEmojiStyle() != PreferenceService.EmojiStyle_ANDROID) {
			ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_parent).getRootView(), (v, insets) -> {

				logger.debug("system window top " + insets.getSystemWindowInsetTop() + " bottom " + insets.getSystemWindowInsetBottom());
				logger.debug("stable insets top " + insets.getStableInsetTop() + " bottom " + insets.getStableInsetBottom());

				if (insets.getSystemWindowInsetBottom() <= insets.getStableInsetBottom()) {
					onSoftKeyboardClosed();
				} else {
					onSoftKeyboardOpened(insets.getSystemWindowInsetBottom() - insets.getStableInsetBottom());
				}
				return insets;
			});
			addOnSoftKeyboardChangedListener(this);
		}

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			finish();
			return false;
		}
		actionBar.setDisplayHomeAsUpEnabled(true);

		DeadlineListService hiddenChatsListService;
		try {
			this.fileService = ThreemaApplication.requireServiceManager().getFileService();
			this.messageService = ThreemaApplication.requireServiceManager().getMessageService();
			hiddenChatsListService = ThreemaApplication.requireServiceManager().getHiddenChatsListService();
		} catch (NullPointerException | ThreemaException e) {
			logger.error("Exception", e);
			finish();
			return false;
		}

		if (hiddenChatsListService == null) {
			logger.error("HiddenChatsListService not available.");
			finish();
			return false;
		}

		this.activityParentLayout = findViewById(R.id.activity_parent);

		Intent intent = getIntent();
		this.pickFromCamera = intent.getBooleanExtra(ThreemaApplication.INTENT_DATA_PICK_FROM_CAMERA, false);
		this.useExternalCamera = intent.getBooleanExtra(EXTRA_USE_EXTERNAL_CAMERA, false);
		this.messageReceivers = IntentDataUtil.getMessageReceiversFromIntent(intent);
		// check if we previously filtered media in MediaAttachActivity to reuse the filter when adding additional media items
		this.lastMediaFilter = IntentDataUtil.getLastMediaFilterFromIntent(intent);

		if (this.pickFromCamera && savedInstanceState == null) {
			launchCamera();
		}

		final List<MediaItem> initialItems;
		List<MediaItem> elementsFromIntent = intent.getParcelableArrayListExtra(EXTRA_MEDIA_ITEMS);
		// Don't add elements from intent if the activity has been recreated (savedInstanceState != null),
		// because the media items will be restored later from the state
		if (elementsFromIntent != null && savedInstanceState == null) {
			intent.removeExtra(EXTRA_MEDIA_ITEMS);
			initialItems = elementsFromIntent;
		} else {
			initialItems = new ArrayList<>();
		}
		setResult(RESULT_CANCELED);

		boolean allReceiverChatsAreHidden = true;
		for (MessageReceiver messageReceiver : messageReceivers) {
			messageReceiver.validateSendingPermission(errorResId -> {
				messageReceivers.remove(messageReceiver);
				Toast.makeText(getApplicationContext(), errorResId, Toast.LENGTH_LONG).show();
			});
			if (allReceiverChatsAreHidden && !hiddenChatsListService.has(messageReceiver.getUniqueIdString())) {
				allReceiverChatsAreHidden = false;
			}
		}

		if (this.messageReceivers.size() < 1) {
			finish();
			return false;
		}

		this.mediaAdapterManager = new MediaAdapterManager(this);

		this.viewPager = findViewById(R.id.view_pager);
		this.sendMediaAdapter = new SendMediaAdapter(getSupportFragmentManager(), getLifecycle(), mediaAdapterManager, this.viewPager);

		this.sendMediaPreviewAdapter = new SendMediaPreviewAdapter(
			this,
			mediaAdapterManager
		);

		if (savedInstanceState != null) {
			this.cameraFilePath = savedInstanceState.getString(STATE_CAMERA_FILE);
			this.videoFilePath = savedInstanceState.getString(STATE_VIDEO_FILE);
			Uri cropUri = savedInstanceState.getParcelable(STATE_TEMP_FILE);
			if (cropUri != null) {
				this.tempFile = new File(cropUri.getPath());
			}
			initialItems.addAll(savedInstanceState.getParcelableArrayList(STATE_ITEMS));
		}

		itemCountText = findViewById(R.id.item_count);

		this.captionEditText = findViewById(R.id.caption_edittext);
		this.captionEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				ThreemaApplication.activityUserInteract(SendMediaActivity.this);
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (s != null) {
					try {
						mediaAdapterManager.getCurrentItem().setCaption(s.toString());
					} catch (IndexOutOfBoundsException ignored) {
						// ignore this exception
					}
				}
			}
		});

		if (messageReceivers != null && messageReceivers.size() == 1 && messageReceivers.get(0) instanceof GroupMessageReceiver) {
			try {
				captionEditText.enableMentionPopup(
					this,
					serviceManager.getGroupService(),
					serviceManager.getContactService(),
					serviceManager.getUserService(),
					preferenceService,
					((GroupMessageReceiver) messageReceivers.get(0)).getGroup()
				);
				ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_parent), (v, insets) -> {
					if (insets.getSystemWindowInsetBottom() <= insets.getStableInsetBottom()) {
						captionEditText.dismissMentionPopup();
					}
					return insets;
				});
			} catch (FileSystemNotPresentException | MasterKeyLockedException e) {
				logger.error("Could not show mention popup", e);
			}
		}

		TextView recipientText = findViewById(R.id.recipient_text);

		this.cameraButton = findViewById(R.id.camera_button);
		this.cameraButton.setOnClickListener(v -> launchCamera());

		this.recyclerView = findViewById(R.id.item_list);
		this.recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));

		this.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				if (mediaAdapterManager.size() == 0) {
					return;
				}
				mediaAdapterManager.changePosition(position, NOTIFY_PREVIEW_ADAPTER);
				recyclerView.scrollToPosition(position);
				updateMenu();
			}
		});

		ItemTouchHelper itemTouchHelper = new ItemTouchHelper(dragCallback);
		itemTouchHelper.attachToRecyclerView(this.recyclerView);

		EmojiButton emojiButton = findViewById(R.id.emoji_button);

		if (preferenceService.getEmojiStyle() != PreferenceService.EmojiStyle_ANDROID) {
			emojiButton.setOnClickListener(v -> showEmojiPicker());

			this.emojiPicker = (EmojiPicker) ((ViewStub) findViewById(R.id.emoji_stub)).inflate();
			this.emojiPicker.init(ThreemaApplication.requireServiceManager().getEmojiService());
			emojiButton.attach(this.emojiPicker);
			this.emojiPicker.setEmojiKeyListener(new EmojiPicker.EmojiKeyListener() {
				@Override
				public void onBackspaceClick() {
					captionEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
				}

				@Override
				public void onEmojiClick(String emojiCodeString) {
					captionEditText.addEmoji(emojiCodeString);
				}

				@Override
				public void onShowPicker() {
					showEmojiPicker();
				}
			});

			this.captionEditText.setOnClickListener(v -> {
				if (emojiPicker != null) {
					if (emojiPicker.isShown()) {
						if (ConfigUtils.isLandscape(this) &&
							!ConfigUtils.isTabletLayout()) {
							emojiPicker.hide();
						} else {
							openSoftKeyboard(emojiPicker, captionEditText);
							runOnSoftKeyboardOpen(() -> emojiPicker.hide());
						}
					}
				}
			});
			this.captionEditText.setOnEditorActionListener(
				(v, actionId, event) -> {
					if ((actionId == EditorInfo.IME_ACTION_SEND) ||
						(event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && preferenceService.isEnterToSend())) {
						sendMedia();
						return true;
					}
					return false;
				});
			emojiButton.setColorFilter(getResources().getColor(android.R.color.white));
		} else {
			emojiButton.setVisibility(View.GONE);
			this.captionEditText.setPadding(getResources().getDimensionPixelSize(R.dimen.no_emoji_button_padding_left), this.captionEditText.getPaddingTop(), this.captionEditText.getPaddingRight(), this.captionEditText.getPaddingBottom());
		}

		String recipients = getIntent().getStringExtra(ThreemaApplication.INTENT_DATA_TEXT);
		if (!TestUtil.empty(recipients)) {
			this.captionEditText.setHint(R.string.add_caption_hint);
			this.captionEditText.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) { }

				@Override
				public void afterTextChanged(Editable s) {
					if (s == null || s.length() == 0) {
						captionEditText.setHint(R.string.add_caption_hint);
					}
				}
			});
			recipientText.setText(getString(R.string.send_to, recipients));
		} else {
			findViewById(R.id.recipient_container).setVisibility(View.GONE);
		}

		SendButton sendButton = findViewById(R.id.send_button);
		sendButton.setOnClickListener(new DebouncedOnClickListener(500) {
			@Override
			public void onDebouncedClick(View v) {
				// avoid duplicates
				v.setEnabled(false);
				AnimationUtil.zoomOutAnimate(v);
				if (emojiPicker != null && emojiPicker.isShown()) {
					emojiPicker.hide();
				}
				sendMedia();
			}
		});
		sendButton.setEnabled(true);

		this.backgroundLayout = findViewById(R.id.background_layout);

		final ViewTreeObserver observer = backgroundLayout.getViewTreeObserver();
		observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			private boolean appliedSavedInstancePosition = false;
			@Override
			public void onGlobalLayout() {
				if (savedInstanceState != null && !appliedSavedInstancePosition) {
					mediaAdapterManager.changePositionWhenItemsLoaded(savedInstanceState.getInt(STATE_BIGIMAGE_POS, 0));
					appliedSavedInstancePosition = true;
				}
				backgroundLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				initUi(backgroundLayout, initialItems);
				recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
					@Override
					public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
						int bottomHeight = SendMediaActivity.this.findViewById(R.id.bottom_panel).getHeight() + recyclerView.getHeight();
						sendMediaAdapter.setBottomElemHeight(bottomHeight);
						recyclerView.removeOnLayoutChangeListener(this);
					}
				});
			}
		});

		return true;
	}

	private void showEmojiPicker() {
		if (isSoftKeyboardOpen()) {
			runOnSoftKeyboardClose(() -> {
				if (emojiPicker != null) {
					emojiPicker.show(loadStoredSoftKeyboardHeight());
				}
			});
			captionEditText.post(() -> EditTextUtil.hideSoftKeyboard(captionEditText));
		} else {
			if (emojiPicker != null) {
				if (emojiPicker.isShown()) {
					if (ConfigUtils.isLandscape(this) &&
						!ConfigUtils.isTabletLayout()) {
						emojiPicker.hide();
					} else {
						openSoftKeyboard(emojiPicker, captionEditText);
						runOnSoftKeyboardOpen(() -> emojiPicker.hide());
					}
				} else {
					emojiPicker.show(loadStoredSoftKeyboardHeight());
				}
			}
		}
	}

	private void initUi(View backgroundLayout, List<MediaItem> mediaItems) {
		this.recyclerView.setAdapter(this.sendMediaPreviewAdapter);
		this.viewPager.setAdapter(this.sendMediaAdapter);

		// add first image
		if (mediaItems != null && !mediaItems.isEmpty()) {
			addItemsByMediaItem(mediaItems, true);
		}

		if (this.pickFromCamera) {
			if (this.backgroundLayout != null) {
				this.backgroundLayout.postDelayed(() -> backgroundLayout.setVisibility(View.VISIBLE), 500);
			}
		} else {
			this.backgroundLayout.setVisibility(View.VISIBLE);
		}
	}

	private void showSettingsDropDown(final View view, final @NonNull MediaItem mediaItem) {
		Context contextWrapper = new ContextThemeWrapper(this, R.style.Threema_PopupMenuStyle_SendMedia);
		PopupMenu popup = new PopupMenu(contextWrapper, view);

		if (mediaItem.getType() == TYPE_IMAGE) {
			setImageDropdown(popup, mediaItem);
		} else if (mediaItem.getType() == TYPE_VIDEO) {
			setVideoDropdown(popup, mediaItem);
		} else {
			return;
		}

		popup.show();
	}

	private void setImageDropdown(@NonNull PopupMenu popup, @NonNull MediaItem mediaItem) {
		popup.setOnMenuItemClickListener(item -> {
			final @PreferenceService.ImageScale int oldSetting = mediaItem.getImageScale();
			final @PreferenceService.ImageScale int newSetting = item.getOrder();
			mediaItem.setImageScale(newSetting);
			if (oldSetting != newSetting && (oldSetting == ImageScale_SEND_AS_FILE || newSetting == ImageScale_SEND_AS_FILE)) {
				mediaAdapterManager.updateSendAsFileState(NOTIFY_BOTH_ADAPTERS);
				updateMenu();
			}
			return true;
		});
		popup.inflate(R.menu.view_image_settings);

		if (mediaItem.hasChanges()) {
			popup.getMenu().removeItem(R.id.menu_send_as_file);
		}

		@PreferenceService.ImageScale int currentScale = mediaItem.getImageScale();
		if (currentScale == PreferenceService.ImageScale_DEFAULT) {
			currentScale = preferenceService.getImageScale();
		}

		popup.getMenu().getItem(currentScale).setChecked(true);
	}

	private void setVideoDropdown(@NonNull PopupMenu popup, @NonNull MediaItem mediaItem) {
		popup.setOnMenuItemClickListener(item -> {
			if (item.getItemId() ==  R.id.mute_item) {
				toggleMuteVideo();
				return true;
			}

			final @PreferenceService.VideoSize int newVideoSize = getVideoSize(item.getItemId());
			final @PreferenceService.VideoSize int oldVideoSize = mediaItem.getVideoSize();
			if (newVideoSize != VideoSize_DEFAULT && oldVideoSize != newVideoSize) {
				mediaItem.setVideoSize(newVideoSize);
				mediaItem.setRenderingType(newVideoSize == VideoSize_SEND_AS_FILE ? FileData.RENDERING_DEFAULT : FileData.RENDERING_MEDIA);
				if (oldVideoSize == VideoSize_SEND_AS_FILE || newVideoSize == VideoSize_SEND_AS_FILE) {
					mediaAdapterManager.updateSendAsFileState(NOTIFY_BOTH_ADAPTERS);
					updateMenu();
				}
			}

			return true;
		});
		popup.inflate(R.menu.view_video_settings);
		MenuCompat.setGroupDividerEnabled(popup.getMenu(), true);

		// Remove send as file option if the media item has been modified
		if (mediaItem.hasChanges()) {
			popup.getMenu().removeItem(R.id.menu_video_send_as_file);
		}

		// Set video size item checked
		@PreferenceService.VideoSize int currentSize = mediaItem.getVideoSize();
		if (currentSize == PreferenceService.VideoSize_DEFAULT) {
			currentSize = preferenceService.getVideoSize();
		}
		popup.getMenu().findItem(getMenuItemId(currentSize)).setChecked(true);

		// Update mute option
		if (mediaItem.getVideoSize() == VideoSize_SEND_AS_FILE) {
			popup.getMenu().removeItem(R.id.mute_item);
		} else {
			popup.getMenu().findItem(R.id.mute_item).setChecked(mediaItem.isMuted());
		}
	}

	private void launchCamera() {
		if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_CAMERA)) {
			reallyLaunchCamera();
		}
	}

	@SuppressLint("UnsupportedChromeOsCameraSystemFeature")
	private void reallyLaunchCamera() {
		File cameraFile = null;
		File videoFile;
		try {
			cameraFile = fileService.createTempFile(".camera", ".jpg", false);
			this.cameraFilePath = cameraFile.getCanonicalPath();

			videoFile = fileService.createTempFile(".video", ".mp4", false);
			this.videoFilePath = videoFile.getCanonicalPath();
		} catch (IOException e) {
			logger.error("Exception", e);
			finish();
		}

		final Intent cameraIntent;
		final int requestCode;
		if (CameraUtil.isInternalCameraSupported() && !useExternalCamera) {
			// use internal camera
			cameraIntent = new Intent(this, CameraActivity.class);
			cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraFilePath);
			cameraIntent.putExtra(CameraActivity.EXTRA_VIDEO_OUTPUT, videoFilePath);
			requestCode = ThreemaActivity.ACTIVITY_ID_PICK_CAMERA_INTERNAL;
		} else {
			// use external camera
			PackageManager packageManager = getPackageManager();
			if (packageManager == null || !(packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA) ||
					packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))) {
				Toast.makeText(getApplicationContext(), R.string.no_camera_installed, Toast.LENGTH_LONG).show();
				finish();
				return;
			}

			cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileService.getShareFileUri(cameraFile, null));
			cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			requestCode = ThreemaActivity.ACTIVITY_ID_PICK_CAMERA_EXTERNAL;
		}

		try {
			startActivityForResult(cameraIntent, requestCode);
		} catch (ActivityNotFoundException e) {
			logger.error("Exception", e);
			finish();
		}
	}

	@Override
	public void onPositionChanged() {
		updateMenu();

		updateCaption();

		int newPosition = mediaAdapterManager.getCurrentPosition();
		if (viewPager.getCurrentItem() != newPosition) {
			viewPager.postDelayed(() -> viewPager.setCurrentItem(newPosition, true), 50);
			mediaAdapterManager.update(newPosition, NOTIFY_ADAPTER);
		}
	}

	@Override
	public void onAddClicked() {
		Intent intent = new Intent(getApplicationContext(), MediaSelectionActivity.class);
		// pass last media filter to open the chooser with the same selection.
		if (lastMediaFilter != null) {
			IntentDataUtil.addLastMediaFilterToIntent(intent, this.lastMediaFilter);
		}
		startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_PICK_MEDIA);
	}

	@Override
	public void onAllItemsRemoved() {
		finish();
	}

	@Override
	public void onItemCountChanged(int newSize) {
		itemCountText.setText(getString(R.string.num_items_sected, Integer.toString(newSize)));
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_send_media;
	}

	@Override
	public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
		updateMenu();

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onCreateOptionsMenu(@NonNull Menu menu) {
		getToolbar().setTitle(R.string.send_media);
		getMenuInflater().inflate(R.menu.activity_send_media, menu);

		settingsItem = menu.findItem(R.id.settings);
		settingsItem.setOnMenuItemClickListener(item -> {
			new Handler().post(() -> {
				final View v = findViewById(R.id.settings);
				if (v != null) {
					showSettingsDropDown(v, mediaAdapterManager.getCurrentItem());
				}
			});
			return true;
		});

		menu.findItem(R.id.flip).setOnMenuItemClickListener(new DebouncedOnMenuItemClickListener(IMAGE_ANIMATION_DURATION_MS * 2) {
			@Override
			public boolean onDebouncedMenuItemClick(MenuItem item) {
				prepareFlip();
				return true;
			}
		});

		menu.findItem(R.id.rotate).setOnMenuItemClickListener(new DebouncedOnMenuItemClickListener(IMAGE_ANIMATION_DURATION_MS * 2) {
			@Override
			public boolean onDebouncedMenuItemClick(MenuItem item) {
				prepareRotate();
				return true;
			}
		});

		menu.findItem(R.id.crop).setOnMenuItemClickListener(item -> {
			cropImage();
			return true;
		});

		menu.findItem(R.id.edit).setOnMenuItemClickListener(item -> {
			editImage();
			return true;
		});

		editFilenameItem = menu.findItem(R.id.edit_filename);
		editFilenameItem.setOnMenuItemClickListener(item -> {
			editFilename();
			return true;
		});

		if (getToolbar().getNavigationIcon() != null) {
			getToolbar().getNavigationIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
		}

		return super.onCreateOptionsMenu(menu);
	}

	private void prepareRotate() {
		MediaItem mediaItem = mediaAdapterManager.getCurrentItem();
		int oldRotation = mediaItem.getRotation();
		int newRotation = ((oldRotation == 0 ? 360 : oldRotation) - 90) % 360;
		mediaItem.setRotation(newRotation);
		mediaAdapterManager.updateCurrent(NOTIFY_BOTH_ADAPTERS);
	}

	private void prepareFlip() {
		mediaAdapterManager.getCurrentItem().flip();
		mediaAdapterManager.updateCurrent(NOTIFY_BOTH_ADAPTERS);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			confirmQuit();
		}
		return super.onOptionsItemSelected(item);
	}

	@SuppressLint("StaticFieldLeak")
	private void addItemsByMediaItem(List<MediaItem> incomingMediaItems, boolean prepend) {
		if (incomingMediaItems.size() > 0) {
			new AsyncTask<Void, Void, List<MediaItem>>() {
				@Override
				protected List<MediaItem> doInBackground(Void... voids) {
					List<MediaItem> itemList = new ArrayList<>();

					for (MediaItem incomingMediaItem : incomingMediaItems) {
						if (incomingMediaItem.getUri() != null) {
							if (isDuplicate(mediaAdapterManager.getItems(), incomingMediaItem.getUri())) {
								continue;
							}

							BitmapUtil.ExifOrientation exifOrientation = BitmapUtil.getExifOrientation(getApplicationContext(), incomingMediaItem.getUri());
							incomingMediaItem.setExifRotation((int) exifOrientation.getRotation());
							incomingMediaItem.setExifFlip(exifOrientation.getFlip());

							if (MimeUtil.isVideoFile(incomingMediaItem.getMimeType())) {
								// do not use automatic resource management on MediaMetadataRetriever
								MediaMetadataRetriever metaDataRetriever = new MediaMetadataRetriever();
								try {
									metaDataRetriever.setDataSource(ThreemaApplication.getAppContext(), incomingMediaItem.getUri());
									incomingMediaItem.setDurationMs(Integer.parseInt(metaDataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
								} catch (Exception ignored) {
								} finally {
									try {
										metaDataRetriever.release();
									} catch (IOException e) {
										logger.debug("Failed to release MediaMetadataRetriever");
									}
								}
							}

							itemList.add(incomingMediaItem);
						}
					}
					return itemList;
				}

				@Override
				protected void onPostExecute(List<MediaItem> itemList) {
					if (mediaAdapterManager.size() + itemList.size() > MAX_EDITABLE_IMAGES) {
						Snackbar.make((View) recyclerView.getParent(), String.format(getString(R.string.max_images_reached), MAX_EDITABLE_IMAGES), BaseTransientBottomBar.LENGTH_LONG).show();
					} else {
						if (prepend) {
							mediaAdapterManager.add(itemList, 0, NOTIFY_BOTH_ADAPTERS);
							updateCaption();
						} else {
							mediaAdapterManager.add(itemList, NOTIFY_BOTH_ADAPTERS);
							mediaAdapterManager.changePosition(mediaAdapterManager.size() - 1, NOTIFY_ALL);
						}
						updateMenu();
					}
				}
			}.execute();
		}
	}

	@SuppressLint("NewApi")
	@Override
	public void onActivityResult(int requestCode, int resultCode,
								 final Intent intent) {

		if (resultCode == Activity.RESULT_OK) {
			hasChanges = true;
			switch (requestCode) {
				case CropImageActivity.REQUEST_CROP:
				case ThreemaActivity.ACTIVITY_ID_PAINT:
					mediaAdapterManager.runWhenCurrentItemAvailable(() -> {
						MediaItem mediaItem = mediaAdapterManager.getCurrentItem();
						mediaItem.setUri(Uri.fromFile(tempFile));
						mediaItem.setRotation(0);
						mediaItem.setExifRotation(0);
						mediaItem.setFlip(BitmapUtil.FLIP_NONE);
						mediaItem.setExifFlip(BitmapUtil.FLIP_NONE);
						mediaItem.setEdited(true);
						mediaAdapterManager.updateCurrent(NOTIFY_BOTH_ADAPTERS);
					});
					break;
				case ThreemaActivity.ACTIVITY_ID_PICK_CAMERA_EXTERNAL:
				case ThreemaActivity.ACTIVITY_ID_PICK_CAMERA_INTERNAL:
					if (ConfigUtils.supportsVideoCapture() && intent != null && intent.getBooleanExtra(CameraActivity.EXTRA_VIDEO_RESULT, false)) {
						// it's a video file
						if (!TestUtil.empty(this.videoFilePath)) {
							File videoFile = new File(this.videoFilePath);
							if (videoFile.exists() && videoFile.length() > 0) {
								final Uri videoUri = Uri.fromFile(videoFile);
								if (videoUri != null) {
									final int position = addItemFromCamera(TYPE_VIDEO_CAM, videoUri, null);
									mediaAdapterManager.changePosition(position, NOTIFY_ALL);
									break;
								}
							}
						}
					} else {
						if (!TestUtil.empty(this.cameraFilePath)) {
							final Uri cameraUri = Uri.fromFile(new File(this.cameraFilePath));
							if (cameraUri != null) {
								BitmapUtil.ExifOrientation exifOrientation = BitmapUtil.getExifOrientation(this, cameraUri);

								final int position = addItemFromCamera(MediaItem.TYPE_IMAGE_CAM, cameraUri, exifOrientation);
								mediaAdapterManager.changePosition(position, NOTIFY_ALL);
								break;
							}
						}
					}
					if (mediaAdapterManager.size() <= 0) {
						finish();
					}
					break;
				case ThreemaActivity.ACTIVITY_ID_PICK_MEDIA:
					ArrayList<MediaItem> mediaItemsList = intent.getParcelableArrayListExtra(EXTRA_MEDIA_ITEMS);
					if (mediaItemsList != null){
						addItemsByMediaItem(mediaItemsList, false);
					}
					// update last media filter used to add media items.
					this.lastMediaFilter = IntentDataUtil.getLastMediaFilterFromIntent(intent);
				default:
					break;
			}
		} else {
			if (mediaAdapterManager.size() <= 0) {
				finish();
			}
		}

		super.onActivityResult(requestCode, resultCode, intent);
	}

	@UiThread
	private void sendMedia() {
		if (mediaAdapterManager.size() < 1) {
			return;
		}

		messageService.sendMediaAsync(mediaAdapterManager.getItems(), messageReceivers, null);

		if (messageReceivers.size() == 1) {
			String messageDraft = getMessageDraft(messageReceivers.get(0).getUniqueIdString());
			if (!TestUtil.empty(messageDraft)) {
				for (MediaItem mediaItem : mediaAdapterManager.getItems()) {
					try {
						double similarity = new JaroWinklerSimilarity().apply(mediaItem.getCaption(), messageDraft);
						if (similarity > 0.8D) {
							ThreemaApplication.putMessageDraft(messageReceivers.get(0).getUniqueIdString(), null, null);
							break;
						}
					} catch (IllegalArgumentException ignore) {
						// one argument is probably null
					}
				}
			}
		}

		// return last media filter to chat via intermediate hop through MediaAttachActivity
		if (lastMediaFilter != null) {
			Intent lastMediaSelectionResult = IntentDataUtil.addLastMediaFilterToIntent(new Intent(), this.lastMediaFilter);
			setResult(RESULT_OK, lastMediaSelectionResult);
		} else {
			setResult(RESULT_OK);
		}
		finish();
	}

	@UiThread
	private int addItemFromCamera(int type, @NonNull Uri imageUri, BitmapUtil.ExifOrientation exifOrientation) {
		if (sendMediaPreviewAdapter == null) {
			return 0;
		}

		if (mediaAdapterManager.size() >= MAX_EDITABLE_IMAGES) {
			Snackbar.make((View) recyclerView.getParent(), String.format(getString(R.string.max_images_reached), MAX_EDITABLE_IMAGES), BaseTransientBottomBar.LENGTH_LONG).show();
		}

		MediaItem item = new MediaItem(imageUri, type);
		item.setOriginalUri(imageUri);
		if (exifOrientation != null) {
			item.setExifRotation((int) exifOrientation.getRotation());
			item.setExifFlip(exifOrientation.getFlip());
		}

		if (type == TYPE_VIDEO_CAM) {
			item.setMimeType(MimeUtil.MIME_TYPE_VIDEO_MP4);
		} else {
			item.setMimeType(MimeUtil.MIME_TYPE_IMAGE_JPG);
		}

		if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(imageUri.getScheme())) {
			item.setDeleteAfterUse(true);
		}

		mediaAdapterManager.add(item, NOTIFY_BOTH_ADAPTERS);

		return mediaAdapterManager.size() - 1;
	}

	private void cropImage() {
		Uri imageUri = mediaAdapterManager.getCurrentItem().getUri();

		try {
			tempFile = fileService.createTempFile(".crop", ".png");

			Intent intent = new Intent(this, CropImageActivity.class);
			intent.setData(imageUri);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(tempFile));
			intent.putExtra(ThreemaApplication.EXTRA_ORIENTATION, mediaAdapterManager.getCurrentItem().getRotation());
			intent.putExtra(ThreemaApplication.EXTRA_FLIP, mediaAdapterManager.getCurrentItem().getFlip());
			intent.putExtra(CropImageActivity.FORCE_DARK_THEME, true);

			startActivityForResult(intent, CropImageActivity.REQUEST_CROP);
			overridePendingTransition(R.anim.medium_fade_in, R.anim.medium_fade_out);
		} catch (IOException e) {
			logger.debug("Unable to create temp file for crop");
		}
	}

	private void editImage() {
		try {
			tempFile = fileService.createTempFile(".edit", ".png");

			Intent intent = ImagePaintActivity.getImageEditIntent(this, mediaAdapterManager.getCurrentItem(), tempFile);
			startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_PAINT);
			overridePendingTransition(0, R.anim.slow_fade_out);
		} catch (IOException e) {
			logger.debug("Unable to create temp file for crop");
		}
	}

	private void editFilename() {
		final MediaItem current = mediaAdapterManager.getCurrentItem();
		CallbackTextEntryDialog.Companion.getInstance(
			getString(R.string.edit_filename),
			current.getFilename(),
			new CallbackTextEntryDialog.OnButtonClickedCallback() {
				@Override
				public void onPositiveClicked(@NonNull String text) {
					current.setFilename(text);
					mediaAdapterManager.updateFilename(NOTIFY_ADAPTER);
				}

				@Override
				public void onNegativeClicked() {
					// Nothing to do
				}
			}).show(getSupportFragmentManager(), "edit_file_name");
	}

	private void toggleMuteVideo() {
		MediaItem item = mediaAdapterManager.getCurrentItem();
		item.setMuted(!item.isMuted());
		mediaAdapterManager.updateMuteState(NOTIFY_BOTH_ADAPTERS);
	}

	private void updateMenu() {
		if (this.cameraButton != null) {
			this.cameraButton.setVisibility(mediaAdapterManager.size() < MAX_EDITABLE_IMAGES ? View.VISIBLE : View.GONE);
		}

		Menu menu = getToolbar().getMenu();

		if (mediaAdapterManager.size() > 0) {
			MediaItem current = mediaAdapterManager.getCurrentItem();
			@MediaItem.MediaType int type = current.getType();
			boolean showImageEdit = (type == TYPE_IMAGE || type == TYPE_IMAGE_CAM) && current.getImageScale() != ImageScale_SEND_AS_FILE;
			boolean showFilenameEdit = current.sendAsFile();
			boolean showSettings = current.getType() == TYPE_IMAGE || current.getType() == TYPE_VIDEO;

			menu.setGroupVisible(R.id.image_edit_tools, showImageEdit);

			if (editFilenameItem != null) {
				editFilenameItem.setVisible(showFilenameEdit);
			}

			if (settingsItem != null) {
				settingsItem.setVisible(showSettings);
			}
		} else {
			menu.setGroupVisible(R.id.image_edit_tools, false);
		}
	}

	private void updateCaption() {
		if (mediaAdapterManager.size() == 0) {
			return;
		}

		String caption = mediaAdapterManager.getCurrentItem().getCaption();

		captionEditText.setText(null);

		if (!TestUtil.empty(caption)) {
			captionEditText.append(caption);
		}
	}

	@Override
	protected boolean enableOnBackPressedCallback() {
		return true;
	}

	@Override
	protected void handleOnBackPressed() {
		if (emojiPicker != null && emojiPicker.isShown()) {
			emojiPicker.hide();
		} else if (captionEditText.isMentionPopupShowing()) {
			captionEditText.dismissMentionPopup();
		} else {
			confirmQuit();
		}
	}

	private void confirmQuit() {
		if (hasChanges || mediaAdapterManager.hasChangedItems()) {
			GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
					R.string.discard_changes_title,
					R.string.discard_changes,
					R.string.yes,
					R.string.no);
			dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_QUIT_CONFIRM);
		} else {
			finish();
		}
	}

	private boolean isDuplicate(List<MediaItem> list, Uri uri) {
		// do not allow the same image twice
		for (int j = 0; j < list.size(); j++) {
			if (list.get(j).getUri().equals(uri) ||
				(list.get(j).getOriginalUri() != null &&
					Objects.equals(list.get(j).getOriginalUri(), uri))) {
				Snackbar.make((View) recyclerView.getParent(), getString(R.string.item_already_added), BaseTransientBottomBar.LENGTH_LONG).show();
				return true;
			}
		}
		return false;
	}

	@Override
	protected void onDestroy() {
		new Thread(() -> VideoTimelineCache.getInstance().flush()).start();

		if (preferenceService.getEmojiStyle() != PreferenceService.EmojiStyle_ANDROID) {
			removeAllListeners();
		}
		super.onDestroy();
	}

	@Override
	public void onYes(String tag, Object data) {
		finish();
	}

	@Override
	public void onNo(String tag, Object data) {}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt(STATE_BIGIMAGE_POS, this.mediaAdapterManager.getCurrentPosition());
		outState.putParcelableArrayList(STATE_ITEMS, (ArrayList<? extends Parcelable>) mediaAdapterManager.getItems());
		outState.putString(STATE_CAMERA_FILE, this.cameraFilePath);
		outState.putString(STATE_VIDEO_FILE, this.videoFilePath);
		if (this.tempFile != null) {
			outState.putParcelable(STATE_TEMP_FILE, Uri.fromFile(this.tempFile));
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PERMISSION_REQUEST_CAMERA) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				reallyLaunchCamera();
			} else {
				if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
					ConfigUtils.showPermissionRationale(this, activityParentLayout, R.string.permission_camera_photo_required);
				}
			}
		}
	}

	@Override
	public void onKeyboardShown() {
		if (emojiPicker != null && emojiPicker.isShown()) {
			emojiPicker.onKeyboardShown();
		}
	}

	@Override
	public void onKeyboardHidden() { }

	@PreferenceService.VideoSize
	private int getVideoSize(@IdRes int itemId) {
		if (itemId == R.id.menu_video_size_small) {
			return VideoSize_SMALL;
		} else if (itemId == R.id.menu_video_size_medium) {
			return VideoSize_MEDIUM;
		} else if (itemId == R.id.menu_video_size_original) {
			return VideoSize_ORIGINAL;
		} else if (itemId == R.id.menu_video_send_as_file) {
			return VideoSize_SEND_AS_FILE;
		} else {
			return VideoSize_DEFAULT;
		}
	}

	@IdRes
	private int getMenuItemId(@PreferenceService.VideoSize int videoSize) {
		switch (videoSize) {
			case VideoSize_SMALL:
				return R.id.menu_video_size_small;
			case VideoSize_MEDIUM:
				return R.id.menu_video_size_medium;
			case VideoSize_ORIGINAL:
				return R.id.menu_video_size_original;
			case VideoSize_SEND_AS_FILE:
				return R.id.menu_video_send_as_file;
			case VideoSize_DEFAULT:
			default:
				logger.error("No menu item for video size {}", videoSize);
				throw new IllegalArgumentException(String.format("No menu item for video size %d", videoSize));
		}
	}

}
