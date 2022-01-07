/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2022 Threema GmbH
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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.FitWindowsFrameLayout;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.PopupMenuWrapper;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.EnterSerialActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.UnlockMasterKeyActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.CheckableFrameLayout;
import ch.threema.app.ui.CheckableView;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.MediaGridItemDecoration;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.localcrypto.MasterKey;
import me.zhanghai.android.fastscroll.FastScroller;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

import static ch.threema.app.ThreemaApplication.MAX_BLOB_SIZE;
import static ch.threema.app.mediaattacher.MediaFilterQuery.FILTER_MEDIA_BUCKET;
import static ch.threema.app.mediaattacher.MediaFilterQuery.FILTER_MEDIA_SELECTED;
import static ch.threema.app.mediaattacher.MediaFilterQuery.FILTER_MEDIA_TYPE;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_DRAGGING;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;

abstract public class MediaSelectionBaseActivity extends ThreemaActivity implements View.OnClickListener,
														MediaAttachAdapter.ItemClickListener {
	// Logging
	private static final Logger logger = LoggerFactory.getLogger(MediaSelectionBaseActivity.class);

	// Threema services
	protected ServiceManager serviceManager;
	protected PreferenceService preferenceService;
	protected GroupService groupService;

	public static final String KEY_BOTTOM_SHEET_STATE = "bottom_sheet_state";
	public static final String KEY_PREVIEW_MODE = "preview_mode";
	private static final String KEY_PREVIEW_ITEM_POSITION = "preview_item";

	protected static final int PERMISSION_REQUEST_ATTACH_FROM_GALLERY = 4;
	protected static final int PERMISSION_REQUEST_ATTACH_FILE = 5;
	protected static final int PERMISSION_REQUEST_ATTACH_STORAGE = 7;

	protected static final int REQUEST_CODE_ATTACH_FROM_GALLERY = 2454;

	protected CoordinatorLayout rootView, gridContainer, pagerContainer;
	protected AppBarLayout appBarLayout;
	protected MaterialToolbar toolbar;
	protected EmptyRecyclerView mediaAttachRecyclerView;
	protected FastScroller fastScroller;
	protected GridLayoutManager gridLayoutManager;
	protected ConstraintLayout bottomSheetLayout, previewBottomSheetLayout;
	protected ImageView dragHandle;
	protected FrameLayout controlPanel, dateView;
	protected LinearLayout menuTitleFrame;
	protected TextView dateTextView, menuTitle, previewFilenameTextView, previewDateTextView;
	protected DisplayMetrics displayMetrics;
	protected MenuItem selectFromGalleryItem;
	protected PopupMenu bucketFilterMenu;
	protected ViewPager2 previewPager;
	private CheckableView checkBox;
	protected MaterialButton permissionButton;
	protected TextView enablePermissionText;

	protected MediaAttachViewModel mediaAttachViewModel;

	protected MediaAttachAdapter mediaAttachAdapter;
	protected ImagePreviewPagerAdapter imagePreviewPagerAdapter;

	protected int peekHeightNumElements = 1;
	private @ColorInt int savedStatusBarColor = 0;

	private boolean isDragging = false;
	private boolean bottomSheetScroll = false;
	private boolean isPreviewMode = false;
	private boolean expandedForFirstTime = true;

	BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior, previewBottomSheetBehavior;

	// Locks
	private final Object filterMenuLock = new Object();
	private final Object previewLock = new Object();

	/* start lifecycle methods */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ConfigUtils.configureActivityTheme(this);
		checkMasterKey();
		initServices();
		// set font size according to user preferences
		getTheme().applyStyle(preferenceService.getFontStyle(), true);
		initActivity(savedInstanceState);
	}

	@Override
	protected void onDestroy() {
		logger.debug("*** onDestroy");
		super.onDestroy();
	}

	@UiThread
	protected void handleSavedInstanceState(Bundle savedInstanceState){
		if (savedInstanceState != null) {
			onItemChecked(mediaAttachViewModel.getSelectedMediaItemsHashMap().size());
			int bottomSheetStyleState = savedInstanceState.getInt(KEY_BOTTOM_SHEET_STATE);
			if (bottomSheetStyleState != 0) {
				updateUI(bottomSheetStyleState);
			}
			boolean previewModeState = savedInstanceState.getBoolean(KEY_PREVIEW_MODE);
			int previewItemPosition = savedInstanceState.getInt(KEY_PREVIEW_ITEM_POSITION);

			if (previewModeState) {
				startPreviewMode(previewItemPosition, 50);
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(KEY_BOTTOM_SHEET_STATE, bottomSheetBehavior.getState());
		outState.putBoolean(KEY_PREVIEW_MODE, isPreviewMode);
		outState.putInt(KEY_PREVIEW_ITEM_POSITION, previewPager.getCurrentItem());
	}

	/* end lifecycle methods */

	/* start setup methods */
	protected void initActivity(Bundle savedInstanceState) {
		// The display metrics are used to query the size of the device display
		this.displayMetrics = ThreemaApplication.getAppContext().getResources().getDisplayMetrics();

		// The view model handles data associated with this view
		this.mediaAttachViewModel = new ViewModelProvider(this).get(MediaAttachViewModel.class);

		// Initialize UI
		this.setLayout();
		this.setDropdownMenu();
		this.setListeners();

		this.toolbar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.menu_select_from_gallery) {
				attachImageFromGallery();
				return true;
			}
			return false;
		});
		this.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				collapseBottomSheet();
			}
		});
	}

	protected void initServices() {
		this.serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				this.preferenceService = serviceManager.getPreferenceService();
				this.groupService = serviceManager.getGroupService();
			} catch (Exception e) {
				logger.error("Exception", e);
				finish();
			}
		}
	}

	protected void setLayout() {
		setContentView(R.layout.activity_media_attach);
		this.rootView = findViewById(R.id.coordinator);
		this.appBarLayout = findViewById(R.id.appbar_layout);
		this.toolbar = findViewById(R.id.toolbar);
		this.selectFromGalleryItem = this.toolbar.getMenu().findItem(R.id.menu_select_from_gallery);
		this.menuTitleFrame = findViewById(R.id.toolbar_title);
		this.menuTitle = findViewById(R.id.toolbar_title_textview);
		this.bottomSheetLayout = findViewById(R.id.bottom_sheet);
		this.previewBottomSheetLayout = findViewById(R.id.preview_bottom_sheet);
		this.mediaAttachRecyclerView = findViewById(R.id.media_grid_recycler);
		this.dragHandle = findViewById(R.id.drag_handle);
		this.controlPanel = findViewById(R.id.control_panel);
		this.dateView = findViewById(R.id.date_separator_container);
		this.dateTextView = findViewById(R.id.text_view);
		this.gridContainer = findViewById(R.id.grid_container);
		this.previewPager = findViewById(R.id.pager);
		this.pagerContainer = findViewById(R.id.pager_container);
		this.checkBox = findViewById(R.id.check_box);
		this.previewFilenameTextView = findViewById(R.id.filename_view);
		this.previewDateTextView = findViewById(R.id.date_view);

		this.permissionButton = findViewById(R.id.permission_button);
		this.enablePermissionText = findViewById(R.id.permission_text);

		this.bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
		this.previewBottomSheetBehavior = BottomSheetBehavior.from(previewBottomSheetLayout);

		MaterialToolbar previewToolbar = findViewById(R.id.preview_toolbar);
		previewToolbar.setNavigationOnClickListener(v -> onBackPressed());

		if (!ConfigUtils.isPermissionGranted(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
			findViewById(R.id.permission_container).setVisibility(View.VISIBLE);
			this.permissionButton.setOnClickListener(v -> ConfigUtils.requestStoragePermissions(MediaSelectionBaseActivity.this, null, PERMISSION_REQUEST_ATTACH_STORAGE));
		} else {
			findViewById(R.id.permission_container).setVisibility(View.GONE);
		}

		this.checkBox.setOnClickListener(v -> {
			checkBox.toggle();
			MediaAttachItem mediaItem = imagePreviewPagerAdapter.getItem(previewPager.getCurrentItem());
			if (checkBox.isChecked()) {
				mediaAttachViewModel.addSelectedMediaItem(mediaItem.getId(), mediaItem);
			} else {
				mediaAttachViewModel.removeSelectedMediaItem(mediaItem.getId());
			}
		});

		this.previewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				updatePreviewInfo(position);
			}
		});

		// fill background with transparent black to see chat behind drawer
		FitWindowsFrameLayout contentFrameLayout = (FitWindowsFrameLayout) ((ViewGroup) rootView.getParent()).getParent();
		contentFrameLayout.setOnClickListener(v -> finish());

		// set status bar color
		getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(this, R.attr.attach_status_bar_color_collapsed));

		// horizontal layout fill screen 2/3 with media selection layout
		if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && !isInSplitScreenMode()) {
			CoordinatorLayout bottomSheetContainer = findViewById(R.id.bottom_sheet_container);
			CoordinatorLayout.LayoutParams bottomSheetContainerParams = (CoordinatorLayout.LayoutParams) bottomSheetContainer.getLayoutParams();
			FrameLayout.LayoutParams attacherLayoutParams = (FrameLayout.LayoutParams) rootView.getLayoutParams();

			attacherLayoutParams.width = displayMetrics.widthPixels * 2 / 3;
			attacherLayoutParams.gravity = Gravity.CENTER;
			bottomSheetContainerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
			bottomSheetContainerParams.gravity = Gravity.CENTER;
			bottomSheetContainerParams.insetEdge = Gravity.CENTER;

			bottomSheetContainer.setLayoutParams(bottomSheetContainerParams);
			rootView.setLayoutParams(attacherLayoutParams);

			contentFrameLayout.setOnClickListener(v -> finish());

			this.gridLayoutManager = new GridLayoutManager(this, 4);
			this.mediaAttachRecyclerView.setLayoutManager(gridLayoutManager);

			this.peekHeightNumElements = 1;
		} else {
			this.gridLayoutManager = new GridLayoutManager(this, 3);
			this.mediaAttachRecyclerView.setLayoutManager(gridLayoutManager);

			this.peekHeightNumElements = isInSplitScreenMode() ? 1 : 2;
		}

		// Set initial peek height
		this.updatePeekHeight();

		// Listen for layout changes
		this.mediaAttachAdapter = new MediaAttachAdapter(this, this);
		this.imagePreviewPagerAdapter = new ImagePreviewPagerAdapter(this);
		this.previewPager.setOffscreenPageLimit(1);
		this.mediaAttachRecyclerView.addItemDecoration(new MediaGridItemDecoration(getResources().getDimensionPixelSize(R.dimen.grid_spacing)));
		this.mediaAttachRecyclerView.setAdapter(mediaAttachAdapter);

		ConfigUtils.addIconsToOverflowMenu(this, this.toolbar.getMenu());

		this.rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

				// adjust height of bottom sheet container to snap smack below toolbar
				CoordinatorLayout bottomSheetContainer = findViewById(R.id.bottom_sheet_container);
				int topMargin = toolbar.getHeight() - getResources().getDimensionPixelSize(R.dimen.drag_handle_height) -
					(getResources().getDimensionPixelSize(R.dimen.drag_handle_topbottom_margin) * 2);

				CoordinatorLayout.LayoutParams bottomSheetContainerLayoutParams = (CoordinatorLayout.LayoutParams) bottomSheetContainer.getLayoutParams();
				bottomSheetContainerLayoutParams.setMargins(0, topMargin, 0, 0);
				bottomSheetContainer.setLayoutParams(bottomSheetContainerLayoutParams);
			}
		});
	}

	protected void setDropdownMenu() {
		this.bucketFilterMenu = new PopupMenuWrapper(this, menuTitle);

		if (mediaAttachViewModel.getLastQuery() == null) {
			menuTitle.setText(R.string.attach_gallery);
		} else {
			menuTitle.setText(mediaAttachViewModel.getLastQuery());
		}

		MenuItem topMenuItem = bucketFilterMenu.getMenu().add(Menu.NONE, 0, 0, R.string.attach_gallery).setOnMenuItemClickListener(menuItem -> {
			setAllResultsGrid();
			return true;
		});
		topMenuItem.setIcon(R.drawable.ic_collections);
		ConfigUtils.themeMenuItem(topMenuItem, ConfigUtils.getColorFromAttribute(this, R.attr.textColorSecondary));

		// Fetch all media, add a unique menu item for each media storage bucket and media type group.
		registerOnAllDataFetchedListener(new Observer<List<MediaAttachItem>>() {
			@Override
			public void onChanged(List<MediaAttachItem> mediaAttachItems) {
				synchronized (filterMenuLock) {
					// We need data!
					if (mediaAttachItems == null || mediaAttachItems.isEmpty()) {
						return;
					}

					// If menu is already filled, do nothing
					final Menu menu = bucketFilterMenu.getMenu();
					if (menu.size() > 1) {
						logger.warn("Filter menu already contained {} entries, clearing all except first", menu.size());
						for (int i = 1; i < menu.size(); i++) {
							menu.removeItem(i);
						}
					}

					// Extract buckets and media types
					final List<String> buckets = new ArrayList<>();
					final TreeMap<String, Integer> mediaTypes = new TreeMap<>();

					for (MediaAttachItem mediaItem : mediaAttachItems) {
						String bucket = mediaItem.getBucketName();
						if (!TextUtils.isEmpty(bucket) && !buckets.contains(bucket)) {
							buckets.add(mediaItem.getBucketName());
						}

						int type = mediaItem.getType();
						if (!mediaTypes.containsValue(type)) {
							String mediaTypeName = MediaSelectionBaseActivity.this.getMimeTypeTitle(type);
							mediaTypes.put(mediaTypeName, type);
						}
					}

					Collections.sort(buckets);

					// Fill menu first media types sorted then folders/buckets sorted
					for (Map.Entry<String, Integer> mediaType : mediaTypes.entrySet()) {
						MenuItem item = menu.add(mediaType.getKey()).setOnMenuItemClickListener(menuItem -> {
							MediaSelectionBaseActivity.this.filterMediaByMimeType(menuItem.toString());
							return true;
						});

						switch (mediaType.getValue()) {
							case MediaItem.TYPE_IMAGE:
								item.setIcon(R.drawable.ic_image_outline);
								break;
							case MediaItem.TYPE_VIDEO:
								item.setIcon(R.drawable.ic_movie_outline);
								break;
							case MediaItem.TYPE_GIF:
								item.setIcon(R.drawable.ic_gif_24dp);
								break;
						}
						ConfigUtils.themeMenuItem(item, ConfigUtils.getColorFromAttribute(MediaSelectionBaseActivity.this, R.attr.textColorSecondary));
					}

					for (String bucket : buckets) {
						if (!TextUtils.isEmpty(bucket)) {
							MenuItem item = menu.add(bucket).setOnMenuItemClickListener(menuItem -> {
								MediaSelectionBaseActivity.this.filterMediaByBucket(menuItem.toString());
								return true;
							});
							item.setIcon(R.drawable.ic_outline_folder_24);
							ConfigUtils.themeMenuItem(item, ConfigUtils.getColorFromAttribute(MediaSelectionBaseActivity.this, R.attr.textColorSecondary));
						}
					}

					// Enable menu and fade in dropdown icon to indicate it is ready
					menuTitleFrame.setOnClickListener(view -> bucketFilterMenu.show());
					ImageView dropDownIcon = findViewById(R.id.dropdown_icon);
					dropDownIcon.setVisibility(View.VISIBLE);
					Animation anim = AnimationUtils.loadAnimation(MediaSelectionBaseActivity.this, R.anim.medium_fade_in);
					dropDownIcon.setAnimation(anim);
					anim.start();
					// removes listener after menu was set to avoid rebuilding it multiple times
					mediaAttachViewModel.getAllMedia().removeObserver(this);
				}

				// reset last recent filter if activity was destroyed by the system due to memory pressure etc.
				String savedQuery = mediaAttachViewModel.getLastQuery();
				Integer savedQueryType = mediaAttachViewModel.getLastQueryType();
				if (savedQueryType != null) {
					switch (savedQueryType) {
						case FILTER_MEDIA_TYPE:
							MediaSelectionBaseActivity.this.filterMediaByMimeType(savedQuery);
							break;
						case FILTER_MEDIA_BUCKET:
							MediaSelectionBaseActivity.this.filterMediaByBucket(savedQuery);
							break;
						case FILTER_MEDIA_SELECTED:
							MediaSelectionBaseActivity.this.filterMediaBySelectedItems();
							break;
						default:
							menuTitle.setText(R.string.filter_by_album);
							break;
					}
				}
			}
		});
	}

	private void updatePreviewInfo(int position) {
		MediaAttachItem mediaItem = imagePreviewPagerAdapter.getItem(position);
		checkBox.setChecked(mediaAttachViewModel.getSelectedMediaItemsHashMap().containsKey(mediaItem.getId()));

		previewFilenameTextView.setText(String.format("%s/%s", mediaItem.getBucketName(), mediaItem.getDisplayName()));
		long taken = mediaItem.getDateTaken();
		//multiply because of format takes millis
		long modified = mediaItem.getDateModified() * 1000;
		long added = mediaItem.getDateAdded() * 1000;
		if (taken != 0) {
			previewDateTextView.setText(String.format(getString(R.string.media_date_taken), LocaleUtil.formatTimeStampString(this, taken, false)));
		} else if (added != 0) {
			previewDateTextView.setText(String.format(getString(R.string.media_date_added), LocaleUtil.formatTimeStampString(this, added, false)));
		} else if (modified != 0) {
			previewDateTextView.setText(String.format(getString(R.string.media_date_modified), LocaleUtil.formatTimeStampString(this, modified, false)));
		} else {
			previewDateTextView.setText(getString(R.string.media_date_unknown));
		}

		previewBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
	}

	/**
	 * If the media grid is enabled and all necessary permissions are granted,
	 * initialize and show it.
	 */
	@UiThread
	protected void setInitialMediaGrid() {
		if (shouldShowMediaGrid()) {
			// Observe the LiveData for current selection, passing in this activity as the LifecycleOwner and Observer.
			mediaAttachViewModel.getCurrentMedia().observe(this, currentlyShowingItems -> {
				mediaAttachAdapter.setMediaItems(currentlyShowingItems);
				imagePreviewPagerAdapter.setMediaItems(currentlyShowingItems);
				// Data loaded, we can now properly calculate the peek height and set/reset UI to expanded state
				updatePeekHeight();
			});
		}
	}

	protected void registerOnAllDataFetchedListener(Observer<List<MediaAttachItem>> listener) {
		mediaAttachViewModel.getAllMedia().observe(this, listener);
	}

	/**
	 * Check if the media attacher's selectable media grid can be shown
	 * @return true if option has been enabled by user and permissions are available
	 */
	protected boolean shouldShowMediaGrid() {
		return preferenceService.isShowImageAttachPreviewsEnabled() &&
			ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}

	protected void setListeners() {
		this.appBarLayout.setOnClickListener(this);

		Context context = MediaSelectionBaseActivity.this;

		bottomSheetBehavior.setExpandedOffset(50);
		bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
			@Override
			public void onStateChanged(@NonNull View bottomSheet, int newState) {
				// on expanding the attach gallery drawer the first time all available media should be loaded and shown
				if (newState == STATE_EXPANDED && (context instanceof MediaAttachActivity) && expandedForFirstTime) {
					mediaAttachViewModel.fetchAllMediaFromRepository(true);
					expandedForFirstTime = false;
				}
				updateUI(newState);
			}

			@Override
			public void onSlide(@NonNull View bottomSheet, float slideOffset) {
				// we don't care about sliding events
			}
		});

		mediaAttachRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				setFirstVisibleItemDate();

				// make sure only bottom sheet or recylcerview is scrolling at a same time
				if (bottomSheetScroll && bottomSheetBehavior.getState() == STATE_EXPANDED) {
					bottomSheetScroll = false;
					bottomSheetBehavior.setDraggable(false);
				}
				else if (!bottomSheetScroll && !recyclerView.canScrollVertically(-1)) {
					bottomSheetScroll = true;
					bottomSheetBehavior.setDraggable(true);
				}
			}
		});

		registerOnAllDataFetchedListener(new Observer<List<MediaAttachItem>>() {
			@Override
			public void onChanged(List<MediaAttachItem> mediaAttachItems) {
				// only show thumbscroller after all media is loaded to avoid a jumping thumbscroller
				if (!mediaAttachItems.isEmpty()) {
					if (fastScroller == null) {
						Drawable thumbDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_thumbscroller, context.getTheme());
						MediaSelectionBaseActivity.this.fastScroller = new FastScrollerBuilder(MediaSelectionBaseActivity.this.mediaAttachRecyclerView)
							.setThumbDrawable(Objects.requireNonNull(thumbDrawable))
							.setTrackDrawable(Objects.requireNonNull(AppCompatResources.getDrawable(context, R.drawable.fastscroll_track_media)))
							.setPadding(0, 0, 0, 0)
							.build();

					}
					// remove listener after thumbscroller was added
					mediaAttachViewModel.getAllMedia().removeObserver(this);
				}
			}
		});
	}
	/* end setup methods */

	@Override
	public void onItemLongClick(View view, int position, MediaAttachItem mediaAttachItem) {
		view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

		startPreviewMode(position, 0);
	}

	@Override
	public void onBackPressed() {
		logger.debug("*** onBackPressed");
		synchronized (previewLock) {
			if (pagerContainer.getVisibility() == View.VISIBLE) {
				if (isPreviewMode) {
					gridContainer.setVisibility(View.VISIBLE);
					pagerContainer.setVisibility(View.GONE);
					previewPager.setAdapter(null);
					mediaAttachAdapter.notifyDataSetChanged();
					onItemChecked(mediaAttachViewModel.getSelectedMediaItemsHashMap().size());

					getWindow().setStatusBarColor(savedStatusBarColor);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
						getWindow().setNavigationBarColor(ConfigUtils.getColorFromAttribute(this, R.attr.attach_status_bar_color_expanded));
					}
					if (ConfigUtils.getAppTheme(this) != ConfigUtils.THEME_DARK) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
							getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
						}
					}
					isPreviewMode = false;
				}
			} else {
				super.onBackPressed();
			}
		}
	}

	private void startPreviewMode(int position, int delay) {
		logger.debug("*** startPreviewMode");
		synchronized (previewLock) {
			if (!isPreviewMode) {
				pagerContainer.setVisibility(View.VISIBLE);
				previewPager.setAdapter(imagePreviewPagerAdapter);
				gridContainer.setVisibility(View.GONE);

				logger.debug("*** setStatusBarColor");

				toolbar.postDelayed(new Runnable() {
					@Override
					public void run() {
						savedStatusBarColor = getWindow().getStatusBarColor();
						getWindow().setStatusBarColor(getResources().getColor(R.color.gallery_background));
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							getWindow().setNavigationBarColor(getResources().getColor(R.color.gallery_background));
						}
						if (ConfigUtils.getAppTheme(MediaSelectionBaseActivity.this) != ConfigUtils.THEME_DARK) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
								getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
							}
						}
					}
				}, delay);

				previewPager.post(new Runnable() {
					@Override
					public void run() {
						previewPager.setCurrentItem(position, false);
					}
				});
				isPreviewMode = true;
			}
		}
	}

	public void setAllResultsGrid() {
		mediaAttachViewModel.setAllMedia();
		menuTitle.setText(getResources().getString(R.string.attach_gallery));
		mediaAttachViewModel.clearLastQuery();
	}

	public void filterMediaByBucket(@NonNull String mediaBucket) {
		mediaAttachViewModel.setMediaByBucket(mediaBucket);
		menuTitle.setText(mediaBucket);
		mediaAttachViewModel.setlastQuery(FILTER_MEDIA_BUCKET, mediaBucket);
	}

	public void filterMediaByMimeType(@NonNull String mimeTypeTitle) {
		int mimeTypeIndex = 0;

		if (mimeTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_pictures))) {
			mimeTypeIndex = MediaItem.TYPE_IMAGE;
		}
		else if (mimeTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_videos))) {
			mimeTypeIndex = MediaItem.TYPE_VIDEO;
		}
		else if (mimeTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_gifs))) {
			mimeTypeIndex = MediaItem.TYPE_GIF;
		}

		if (mimeTypeIndex != 0) {
			mediaAttachViewModel.setMediaByType(mimeTypeIndex);
		}
		menuTitle.setText(mimeTypeTitle);
		mediaAttachViewModel.setlastQuery(FILTER_MEDIA_TYPE, mimeTypeTitle);
	}

	public void filterMediaBySelectedItems() {
		mediaAttachViewModel.setSelectedMedia();
		menuTitle.setText(R.string.selected_media);
		mediaAttachViewModel.setlastQuery(FILTER_MEDIA_SELECTED, null);
	}

	public String getMimeTypeTitle(int mimeType) {
		switch (mimeType){
			case (MediaItem.TYPE_IMAGE):
				return getResources().getString(R.string.media_gallery_pictures);
			case (MediaItem.TYPE_VIDEO):
				return getResources().getString(R.string.media_gallery_videos);
			case (MediaItem.TYPE_GIF):
				return getResources().getString(R.string.media_gallery_gifs);
			default:
				return null;
		}
	}

	public void updateUI(int state){
		Animation animation;

		if (bottomSheetBehavior.getState() != state) {
			bottomSheetBehavior.setState(state);
		}

		switch (state) {
			case STATE_HIDDEN:
				finish();
				break;
			case STATE_EXPANDED:
				dateView.setVisibility(View.VISIBLE);
				dragHandle.setVisibility(View.INVISIBLE);
				setFirstVisibleItemDate();

				bucketFilterMenu.getMenu().setGroupVisible(Menu.NONE, true);
				menuTitleFrame.setClickable(true);

				animation = toolbar.getAnimation();
				if (animation != null) {
					animation.cancel();
				}

				toolbar.setAlpha(0f);
				toolbar.setVisibility(View.VISIBLE);
				toolbar.animate()
					.alpha(1f)
					.setDuration(100)
					.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						toolbar.setVisibility(View.VISIBLE);
					}
				});
				toolbar.postDelayed(
					() -> getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(this, R.attr.attach_status_bar_color_expanded)),
					50
				);
				// show/hide control panel in attach mode depending on whether we have selected items
				if (MediaSelectionBaseActivity.this instanceof MediaAttachActivity) {
					if (mediaAttachViewModel.getSelectedMediaItemsHashMap().isEmpty() && controlPanel.getTranslationY() == 0) {
						controlPanel.animate().translationY(controlPanel.getHeight());
					} else { // show
						controlPanel.animate().translationY(0);
					}
				}

				isDragging = false;

				break;
			case STATE_DRAGGING:
				if (!isDragging) {
					isDragging = true;

					dateView.setVisibility(View.GONE);
					dragHandle.setVisibility(View.VISIBLE);

					animation = toolbar.getAnimation();
					if (animation != null) {
						animation.cancel();
					}
					toolbar.setAlpha(1f);
					toolbar.animate()
						.alpha(0f)
						.setDuration(100)
						.setListener(new AnimatorListenerAdapter() {
							@Override
							public void onAnimationEnd(Animator animation) {
								toolbar.setVisibility(View.GONE);
							}
						});
					toolbar.postDelayed(() -> {
						getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(this, R.attr.attach_status_bar_color_collapsed));
					}, 50);
				}
				break;
			case STATE_COLLAPSED:
				bottomSheetBehavior.setDraggable(true);
				bottomSheetScroll = true;
				dateView.setVisibility(View.GONE);
				bucketFilterMenu.getMenu().setGroupVisible(Menu.NONE, false);
				menuTitleFrame.setClickable(false);
				// only default slide up control panel if in attach mode, as there are no control button options otherwise
				if (MediaSelectionBaseActivity.this instanceof MediaAttachActivity) {
					controlPanel.animate().translationY(0);
				}
				isDragging = false;
			default:
				break;
		}
	}

	/**
	 * Adjust the peek height of the bottom sheet to fit in the {@link #peekHeightNumElements}
	 * number of items vertically.
	 */
	protected synchronized void updatePeekHeight() {
		logger.debug("*** updatePeekHeight");

		if (shouldShowMediaGrid()) {
			final int numElements = this.peekHeightNumElements;
			logger.debug("Update peek height ({} elements)", numElements);
			int numItems = mediaAttachRecyclerView.getLayoutManager().getItemCount();

			bottomSheetLayout.setVisibility(View.VISIBLE);

			// Fetch some pixel dimensions we need for calculations below
			final Resources resources = this.getResources();
			final int controlPanelHeight = resources.getDimensionPixelSize(R.dimen.control_panel_height);
			final int controlPanelShadowHeight = resources.getDimensionPixelSize(R.dimen.media_attach_control_panel_shadow_size);
			final int dragHandleHeight = resources.getDimensionPixelSize(R.dimen.drag_handle_height);
			final int dragHandleTopBottomMargin = resources.getDimensionPixelSize(R.dimen.drag_handle_topbottom_margin);

			// We have a bit of a chicken-and-egg problem: Before the peek height is not set,
			// the grid items are not loaded. But without the grid items being loaded, we cannot
			// calculate the proper height.
			// To avoid this, we initially set a peek height that's just enough to show the top
			// "grab handle", which will trigger the loading of the elements. Once the elements
			// are loaded, we can update the peek height.
			int peekHeight = controlPanelHeight
				- controlPanelShadowHeight
				+ dragHandleHeight
				+ dragHandleTopBottomMargin * 2;
			boolean peekHeightKnown;
			if (numItems > 0 && mediaAttachRecyclerView.getChildAt(0) != null) {
				// Child views are already here, we can calculate the total height
				peekHeight += mediaAttachRecyclerView.getChildAt(0).getHeight() * numElements;
				peekHeight += MediaSelectionBaseActivity.this.getResources().getDimensionPixelSize(R.dimen.grid_spacing) * numElements;
				peekHeightKnown = true;
			} else {
				// Child views aren't initialized yet
				peekHeightKnown = false;
				logger.debug("Peek height could not yet be determined, no items found");
			}

			if (bottomSheetBehavior != null) {
				bottomSheetBehavior.setPeekHeight(peekHeight);
			}

			// Recalculate the peek height when the layout changes the next time
			if (!peekHeightKnown) {
				rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						logger.debug("onGlobalLayoutListener");

						// Run only once
						rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

						// Update peek height again
						MediaSelectionBaseActivity.this.updatePeekHeight();
					}
				});
			}
		} else {
			bottomSheetBehavior.setPeekHeight(0);
			bottomSheetLayout.setVisibility(View.GONE);
		}
	}

	protected void setFirstVisibleItemDate(){
		int firstVisible = gridLayoutManager.findFirstVisibleItemPosition();
		if (firstVisible >= 0){
			MediaAttachItem item = mediaAttachAdapter.getMediaItems().get(firstVisible);
			dateView.post(() -> {
				dateTextView.setMaxLines(1);
				dateTextView.setText(LocaleUtil.formatDateRelative(MediaSelectionBaseActivity.this, item.getDateModified() * 1000));
			});
			dateView.setVisibility(View.VISIBLE);
		} else {
			mediaAttachRecyclerView.clearEmptyView();

			EmptyView emptyView = new EmptyView(this, 0);
			emptyView.setup(R.string.no_media_found_global);
			((ViewGroup) mediaAttachRecyclerView.getParent()).addView(emptyView);
			mediaAttachRecyclerView.setEmptyView(emptyView);

			dateView.setVisibility(View.GONE);
		}
	}

	@SuppressLint("NonConstantResourceId")
	@Override
	public void onClick(View v) {
		int id = v.getId();
		switch (id) {
			case R.id.collapsing_toolbar:
				finish();
				break;
			// finish when clicking transparent area showing the chat behind the attacher
			case R.id.cancel:
				if (mediaAttachAdapter != null) {
					for (int childCount = mediaAttachRecyclerView.getChildCount(), i = 0; i < childCount; ++i) {
						final RecyclerView.ViewHolder holder = mediaAttachRecyclerView.getChildViewHolder(mediaAttachRecyclerView.getChildAt(i));
						if (mediaAttachViewModel.getSelectedMediaItemsHashMap().containsKey(((MediaAttachAdapter.MediaGalleryHolder) holder).itemId)) {
							final CheckableFrameLayout checkableFrameLayout = ((MediaAttachAdapter.MediaGalleryHolder) holder).contentView;
							checkableFrameLayout.setChecked(false);
						}

					}
					mediaAttachViewModel.clearSelection();
					onItemChecked(0);
				}
				break;
			case R.id.select_counter_button:
				if (mediaAttachAdapter != null) {
					filterMediaBySelectedItems();
				}
				break;
		}
	}

	public abstract void onItemChecked(int count);

	protected void showPermissionRationale(int stringResource) {
		ConfigUtils.showPermissionRationale(this, rootView, stringResource);
	}

	public void checkMasterKey() {
		MasterKey masterKey = ThreemaApplication.getMasterKey();
		if (masterKey != null && masterKey.isLocked()) {
			startActivityForResult(new Intent(this, UnlockMasterKeyActivity.class), ThreemaActivity.ACTIVITY_ID_UNLOCK_MASTER_KEY);
		} else {
			if (ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid()) {
				startActivity(new Intent(this, EnterSerialActivity.class));
				finish();
			}
		}
	}

	/**
	 * Return true if split screen / multi window mode is enabled.
	 */
	protected boolean isInSplitScreenMode() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return isInMultiWindowMode();
		} else {
			return false;
		}
	}

	protected void attachImageFromGallery() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			FileUtil.selectFile(this, null, new String[]{MimeUtil.MIME_TYPE_IMAGE, MimeUtil.MIME_TYPE_VIDEO}, REQUEST_CODE_ATTACH_FROM_GALLERY, true, MAX_BLOB_SIZE, null);
		} else if (ConfigUtils.requestStoragePermissions(this, null, PERMISSION_REQUEST_ATTACH_FROM_GALLERY)) {
			FileUtil.selectFromGallery(this, null, REQUEST_CODE_ATTACH_FROM_GALLERY, true);
		}
	}

	protected void expandBottomSheet() {
		if (MediaSelectionBaseActivity.this instanceof MediaAttachActivity && expandedForFirstTime) {
			mediaAttachViewModel.fetchAllMediaFromRepository(true);
		}
		updateUI(BottomSheetBehavior.STATE_EXPANDED);
		expandedForFirstTime = false;
	}

	protected void collapseBottomSheet() {
		Animation animation = toolbar.getAnimation();
		if (animation != null) {
			animation.cancel();
		}

		dragHandle.setVisibility(View.VISIBLE);
		toolbar.setVisibility(View.GONE);
		toolbar.post(() -> {
			getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(this, R.attr.attach_status_bar_color_collapsed));
		});

		updateUI(STATE_COLLAPSED);
	}
}
