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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.FitWindowsFrameLayout;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.PopupMenuWrapper;
import androidx.appcompat.widget.SearchView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.EnterSerialActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.UnlockMasterKeyActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.mediaattacher.labeling.ImageLabelsIndexHashMap;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.CheckableFrameLayout;
import ch.threema.app.ui.GridRecyclerView;
import ch.threema.app.ui.MediaGridItemDecoration;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.OnKeyboardBackRespondingSearchView;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.localcrypto.MasterKey;

import static android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI;
import static ch.threema.app.mediaattacher.MediaAttachViewModel.FILTER_MEDIA_BUCKET;
import static ch.threema.app.mediaattacher.MediaAttachViewModel.FILTER_MEDIA_LABEL;
import static ch.threema.app.mediaattacher.MediaAttachViewModel.FILTER_MEDIA_SELECTED;
import static ch.threema.app.mediaattacher.MediaAttachViewModel.FILTER_MEDIA_TYPE;
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

	public static final String KEY_BOTTOM_SHEET_STATE = "bottom_sheet_state";
	protected static final int PERMISSION_REQUEST_ATTACH_FILE = 5;

	protected CoordinatorLayout rootView;
	protected AppBarLayout appBarLayout;
	protected MaterialToolbar toolbar;
	protected GridRecyclerView mediaAttachRecyclerView;
	protected GridLayoutManager gridLayoutManager;
	protected ConstraintLayout bottomSheetLayout;
	protected ImageView dragHandle;
	protected FrameLayout controlPanel, dateView;
	protected LinearLayout menuTitleFrame;
	protected TextView dateTextView, menuTitle;
	protected DisplayMetrics displayMetrics;
	protected SearchView searchView;
	protected MenuItem searchItem, selectFromGalleryItem;
	protected PopupMenu bucketFilterMenu;

	protected MediaAttachViewModel mediaAttachViewModel;

	protected MediaAttachAdapter mediaAttachAdapter;
	protected CursorAdapter suggestionAdapter;
	protected AutoCompleteTextView searchAutoComplete;
	protected List<String> labelSuggestions;
	protected ImageLabelsIndexHashMap labelsIndexHashMap;
	protected int peekHeightNumElements = 1;

	private boolean isDragging = false;

	// Locks
	private final Object filterMenuLock = new Object();
	private final Object firstTimeTooltipLock = new Object();

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
	public void onDestroy() {
		super.onDestroy();
	}

	@UiThread
	protected void handleSavedInstanceState(Bundle savedInstanceState){
		if (savedInstanceState != null) {
			onItemChecked(mediaAttachViewModel.getSelectedMediaItemsHashMap().size());
			int bottomSheetStyleState = savedInstanceState.getInt(KEY_BOTTOM_SHEET_STATE);
			if (bottomSheetStyleState != 0) {
				final BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
				bottomSheetBehavior.setState(bottomSheetStyleState);
				updateUI(bottomSheetStyleState);
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		final BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
		outState.putInt(KEY_BOTTOM_SHEET_STATE, bottomSheetBehavior.getState());
	}

	/* end lifecycle methods */

	/* start setup methods */
	protected void initActivity(Bundle savedInstanceState) {
		// The display metrics are used to query the size of the device display
		this.displayMetrics = ThreemaApplication.getAppContext().getResources().getDisplayMetrics();

		// The view model handles data associated with this view
		this.mediaAttachViewModel = new ViewModelProvider(MediaSelectionBaseActivity.this).get(MediaAttachViewModel.class);

		// The ImageLabelsIndexHashMap maps label indexes to readable names (e.g. "Twig" or "Boat")
		this.labelsIndexHashMap = new ImageLabelsIndexHashMap(this);

		// Initialize UI
		this.setLayout();
		this.setDropdownMenu();
		this.setListeners();
	}

	protected void initServices() {
		this.serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				this.preferenceService = serviceManager.getPreferenceService();
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
		this.searchItem = this.toolbar.getMenu().findItem(R.id.menu_search);
		this.selectFromGalleryItem = this.toolbar.getMenu().findItem(R.id.menu_select_from_gallery);
		this.searchView = (SearchView) searchItem.getActionView();
		this.searchAutoComplete = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
		this.menuTitleFrame = findViewById(R.id.toolbar_title);
		this.menuTitle = findViewById(R.id.toolbar_title_textview);
		this.bottomSheetLayout = findViewById(R.id.bottom_sheet);
		this.mediaAttachRecyclerView = findViewById(R.id.media_grid_recycler);
		this.dragHandle = findViewById(R.id.drag_handle);
		this.controlPanel = findViewById(R.id.control_panel);
		this.dateView = findViewById(R.id.date_separator_container);
		this.dateTextView = findViewById(R.id.text_view);

		this.searchView.setIconifiedByDefault(true);
		this.selectFromGalleryItem.setVisible(this instanceof MediaAttachActivity);

		// fill background with transparent black to see chat behind drawer
		FitWindowsFrameLayout contentFrameLayout = (FitWindowsFrameLayout) ((ViewGroup) rootView.getParent()).getParent();
		contentFrameLayout.setOnClickListener(v -> finish());

		// set status bar color
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(this, R.attr.attach_status_bar_color_collapsed));
		}

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
			mediaAttachRecyclerView.addItemDecoration(new MediaGridItemDecoration(getResources().getDimensionPixelSize(R.dimen.grid_spacing), 4));
			mediaAttachRecyclerView.setLayoutManager(gridLayoutManager);

			this.peekHeightNumElements = 1;
		} else {
			this.gridLayoutManager = new GridLayoutManager(this, 3);
			mediaAttachRecyclerView.addItemDecoration(new MediaGridItemDecoration(getResources().getDimensionPixelSize(R.dimen.grid_spacing), 3));
			mediaAttachRecyclerView.setLayoutManager(gridLayoutManager);

			this.peekHeightNumElements = isInSplitScreenMode() ? 1 : 2;
		}

		// Set initial peek height
		this.updatePeekHeight();

		// Listen for layout changes
		this.mediaAttachAdapter = new MediaAttachAdapter(this, this);
		this.mediaAttachRecyclerView.setAdapter(mediaAttachAdapter);

		// Wait for search labels to be ready
		if (ConfigUtils.isPlayServicesInstalled(this)) {
			this.mediaAttachViewModel.getSuggestionLabels().observe(this, labels -> {
				if (labels != null && !labels.isEmpty()) {
					this.labelSuggestions = labels;
					this.onLabelingComplete();

					// reset last recent label filter if activity was destroyed by the system due to memory pressure etc.
					String savedQuery = mediaAttachViewModel.getLastQuery();
					Integer savedQueryType = mediaAttachViewModel.getLastQueryType();
					if (savedQueryType != null && savedQueryType == FILTER_MEDIA_LABEL) {
						mediaAttachViewModel.setMediaByLabel(savedQuery);
						searchView.clearFocus();
					}
				}
			});
		}

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

		if (mediaAttachViewModel.getToolBarTitle() == null) {
			menuTitle.setText(R.string.attach_gallery);
		} else {
			menuTitle.setText(mediaAttachViewModel.getToolBarTitle());
		}

		MenuItem topMenuItem = bucketFilterMenu.getMenu().add(Menu.NONE, 0, 0, R.string.attach_gallery).setOnMenuItemClickListener(menuItem -> {
			setAllResultsGrid();
			menuTitle.setText(menuItem.toString());
			mediaAttachViewModel.setToolBarTitle(menuItem.toString());
			return true;
		});
		topMenuItem.setIcon(R.drawable.ic_collections);
		ConfigUtils.themeMenuItem(topMenuItem, ConfigUtils.getColorFromAttribute(this, R.attr.textColorSecondary));

		// Fetch all media, add a unique menu item for each media storage bucket and media type group.
		final MutableLiveData<List<MediaAttachItem>> allMediaLiveData = mediaAttachViewModel.getAllMedia();
		allMediaLiveData.observe(this, mediaAttachItems -> {
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
				final HashSet<String> buckets = new HashSet<>();
				final List<String> mediaTypes = new ArrayList<>();
				for (MediaAttachItem mediaItem : mediaAttachItems) {
					buckets.add(mediaItem.getBucketName());
					String mediaTypeName = getMimeTypeTitle(mediaItem.getType());
					if (!mediaTypes.contains(mediaTypeName)) {
						mediaTypes.add(mediaTypeName);
					}
				}

				// Fill menu
				for (int i = 0; i < mediaTypes.size(); i++) {
					String mediaTypeName = mediaTypes.get(i);
					MenuItem item = menu.add(mediaTypeName).setOnMenuItemClickListener(menuItem -> {
						filterMediaByMimeType(menuItem.toString());
						menuTitle.setText(menuItem.toString());
						mediaAttachViewModel.setToolBarTitle(menuItem.toString());
						return true;
					});

					switch(i) {
						case 0:
							item.setIcon(R.drawable.ic_image_outline);
							break;
						case 1:
							item.setIcon(R.drawable.ic_movie_outline);
							break;
						case 2:
							item.setIcon(R.drawable.ic_gif_24dp);
							break;
					}
					ConfigUtils.themeMenuItem(item, ConfigUtils.getColorFromAttribute(this, R.attr.textColorSecondary));
				}

				for (String bucket : buckets) {
					MenuItem item = menu.add(bucket).setOnMenuItemClickListener(menuItem -> {
						filterMediaByBucket(menuItem.toString());
						menuTitle.setText(menuItem.toString());
						mediaAttachViewModel.setToolBarTitle(menuItem.toString());
						return true;
					});
					item.setIcon(R.drawable.ic_outline_folder_24);
					ConfigUtils.themeMenuItem(item, ConfigUtils.getColorFromAttribute(this, R.attr.textColorSecondary));
				}

				// Enable menu
				menuTitleFrame.setOnClickListener(view -> bucketFilterMenu.show());
			}

			// reset last recent filter if activity was destroyed by the system due to memory pressure etc and we do not have to wait for suggestion labels.
			String savedQuery = mediaAttachViewModel.getLastQuery();
			Integer savedQueryType = mediaAttachViewModel.getLastQueryType();
			if (savedQueryType != null) {
				switch (savedQueryType) {
					case FILTER_MEDIA_TYPE:
						filterMediaByMimeType(savedQuery);
						break;
					case FILTER_MEDIA_BUCKET:
						filterMediaByBucket(savedQuery);
						break;
					case FILTER_MEDIA_SELECTED:
						filterMediaBySelectedItems();
						break;
					default:
						menuTitle.setText(R.string.filter_by_album);
						break;
				}
			}
		});
	}

	/**
	 * If the media grid is enabled and all necessary permissions are granted,
	 * initialize and show it.
	 */
	@UiThread
	protected void setInitialMediaGrid() {
		if (shouldShowMediaGrid()) {
			// Observe the LiveData, passing in this activity as the LifecycleOwner and Observer.
			mediaAttachViewModel.getCurrentMedia().observe(this, newMediaItems -> {
				mediaAttachAdapter.setMediaItems(newMediaItems);

				// Data loaded, we can now properly calculate the peek height
				updatePeekHeight();
			});
		}
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

		this.searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
			@Override
			public boolean onMenuItemActionExpand(MenuItem item) {
				menuTitleFrame.setVisibility(View.GONE);
				return true;
			}

			@Override
			public boolean onMenuItemActionCollapse(MenuItem item) {
				menuTitleFrame.setVisibility(View.VISIBLE);
				if (item.isEnabled()) {
					menuTitle.setText(R.string.attach_gallery);
					mediaAttachViewModel.setAllMedia();
				} else {
					item.setEnabled(true);
				}
				return true;
			}
		});

		this.searchView.setOnQueryTextListener(new OnKeyboardBackRespondingSearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				mediaAttachViewModel.setMediaByLabel(query);
				searchView.clearFocus();
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				if (labelSuggestions != null && !TextUtils.isEmpty(newText)){
					populateAdapter(newText);
				}
				return false;
			}
		});

		View searchViewCloseButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
		if (searchViewCloseButton != null) {
			searchViewCloseButton.setOnClickListener(v -> {
				mediaAttachViewModel.setAllMedia();
				searchView.setQuery("", false);
				searchView.requestFocus();
			});
		}

		BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
		bottomSheetBehavior.setExpandedOffset(50);
		bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
			@Override
			public void onStateChanged(@NonNull View bottomSheet, int newState) {
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
				final BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);

				if (controlPanel.getTranslationY() == 0 && mediaAttachViewModel.getSelectedMediaItemsHashMap().isEmpty() && bottomSheetBehavior.getState() == STATE_EXPANDED) {
					controlPanel.animate().translationY(controlPanel.getHeight());
				}
			}
		});
	}
	/* end setup methods */

	@Override
	public void onItemLongClick(View view, int position, MediaAttachItem mediaAttachItem) {
		Intent intent = new Intent(this, MediaPreviewActivity.class);
		intent.putExtra(MediaPreviewActivity.EXTRA_PARCELABLE_MEDIA_ITEM, mediaAttachItem);
		AnimationUtil.startActivity(this, view, intent);
	}

	@UiThread
	public void onLabelingComplete() {
		logger.debug("Labeling is complete, show search view");

		if (searchView != null) {
			// Show search icon
			searchItem.setVisible(true);
			searchView.setQueryHint(getString(R.string.image_label_query_hint));

			// Don't expand the search text field
			final EditText editText = searchView.findViewById(R.id.search_src_text);
			editText.setImeOptions(IME_FLAG_NO_EXTRACT_UI);

			// Create and set a CursorAdapter for the recommendation dropdown list
			MediaSelectionBaseActivity.this.suggestionAdapter = new CursorAdapter(
				MediaSelectionBaseActivity.this,
				new MatrixCursor(new String[]{ BaseColumns._ID, "labelName" }), false
			) {
				@Override
				public View newView(Context context, Cursor cursor, ViewGroup parent) {
					return LayoutInflater.from(MediaSelectionBaseActivity.this).inflate(android.R.layout.simple_list_item_1, parent, false);
				}

				@Override
				public void bindView(View view, Context context, Cursor cursor) {
					String label = cursor.getString(cursor.getColumnIndexOrThrow("labelName"));
					TextView textView = view.findViewById(android.R.id.text1);
					textView.setText(label);
					view.setOnClickListener(view1 -> {
						searchView.setQuery(label, true);
						mediaAttachViewModel.setlastQuery(FILTER_MEDIA_LABEL, label);
					});
				}
			};
			searchView.setSuggestionsAdapter(suggestionAdapter);
			MediaSelectionBaseActivity.this.searchAutoComplete.setThreshold(1);
		}
	}

	protected void resetLabelSearch() {
		searchView.setQuery("", false);
		searchView.setIconified(true);
	}

	public void setAllResultsGrid() {
		mediaAttachViewModel.setAllMedia();
		mediaAttachViewModel.clearLastQuery();
		resetLabelSearch();
	}

	public void filterMediaByBucket(String mediaBucket) {
		mediaAttachViewModel.setMediaByBucket(mediaBucket);
		mediaAttachViewModel.setlastQuery(FILTER_MEDIA_BUCKET, mediaBucket);
		resetLabelSearch();
	}

	public void filterMediaByMimeType(String mimeTypeTitle) {
		int mimeTypeIndex = 0;
		if (mimeTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_pictures))) {
			mimeTypeIndex = MediaItem.TYPE_IMAGE;
		}
		else if (mimeTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_videos))) {
			mimeTypeIndex = MediaItem.TYPE_VIDEO;
		} else if (mimeTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_gifs))) {
			mimeTypeIndex = MediaItem.TYPE_GIF;
		}
		if (mimeTypeIndex != 0) {
			mediaAttachViewModel.setMediaByType(mimeTypeIndex);
		}
		mediaAttachViewModel.setlastQuery(FILTER_MEDIA_TYPE, mimeTypeTitle);
		resetLabelSearch();
	}

	public void filterMediaBySelectedItems() {
		mediaAttachViewModel.setSelectedMedia();
		searchItem.setEnabled(false);
		searchItem.collapseActionView();
		mediaAttachViewModel.setlastQuery(FILTER_MEDIA_SELECTED, null);
		resetLabelSearch();
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

	private void populateAdapter(@NonNull String query) {
		final MatrixCursor c = new MatrixCursor(new String[]{ BaseColumns._ID, "labelName" });
		int index = 0;
		if (!labelSuggestions.isEmpty()){
			for (String label : labelSuggestions) {
				if (label != null && label.toLowerCase().startsWith(query.toLowerCase())){
					c.addRow(new Object[] {index, label});
				}
				index++;
			}

			if (c.getCount() == 0){
				SingleToast.getInstance().showShortText(getString(R.string.no_labels_info));
			}

			suggestionAdapter.changeCursor(c);
			// avoid too long drop down suggestion list that might disappear behind keyboard
			if (c.getCount() > 6){
				searchAutoComplete.setDropDownHeight(displayMetrics.heightPixels/3);
			} else {
				searchAutoComplete.setDropDownHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
			}
		} else {
			SingleToast.getInstance().showShortText(getString(R.string.no_labels_info));
		}
	}

	public void updateUI(int state){
		Animation animation;
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
				searchView.findViewById(R.id.search_button).setClickable(true);

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
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					toolbar.postDelayed(
						() -> getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(this, R.attr.attach_status_bar_color_expanded)),
						50
					);
				}

				if (mediaAttachViewModel.getSelectedMediaItemsHashMap().isEmpty()) {
					controlPanel.animate().translationY(controlPanel.getHeight());
				} else {
					controlPanel.animate().translationY(0);
				}

				// Maybe show "new feature" tooltip
				toolbar.postDelayed(() -> maybeShowFirstTimeToolTip(), 1500);

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
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
							getWindow().setStatusBarColor(ConfigUtils.getColorFromAttribute(this, R.attr.attach_status_bar_color_collapsed));
						}
					}, 50);
				}
				break;
			case STATE_COLLAPSED:
				dateView.setVisibility(View.GONE);
				bucketFilterMenu.getMenu().setGroupVisible(Menu.NONE, false);
				menuTitleFrame.setClickable(false);
				searchView.findViewById(R.id.search_button).setClickable(false);
				controlPanel.animate().translationY(0);
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
		final BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);

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
			bottomSheetBehavior.setPeekHeight(peekHeight);

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
			dateView.post(() -> dateTextView.setText(LocaleUtil.formatDateRelative(MediaSelectionBaseActivity.this, item.getDateModified() * 1000)));
		} else {
			dateView.post(() -> {
				dateTextView.setText(R.string.no_media_found_global);
			});
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
					searchView.onActionViewCollapsed();
					searchView.clearFocus();
					menuTitle.setText(R.string.selected_media);
					mediaAttachViewModel.setToolBarTitle(getResources().getString(R.string.selected_media));
				}
				break;
		}
	}

	public abstract void onItemChecked(int count);

	protected void showPermissionRationale(int stringResource) {
		ConfigUtils.showPermissionRationale(this, rootView, stringResource);
	}

	/**
	 * Show the "first time" tool tip (but only if it hasn't been shown before
	 * and if the bottom sheet is fully expanded).
	 */
	@UiThread
	protected void maybeShowFirstTimeToolTip() {
		// This code is synchronized so that we don't show the tooltip multiple times
		synchronized (this.firstTimeTooltipLock) {
			// Check preconditions
			if (preferenceService.getIsImageLabelingTooltipShown()) {
				// Only shown if it hasn't already been shown
				return;
			}
			if (this.searchView == null || !this.searchItem.isVisible()) {
				// Only show if the search icon is visible
				return;
			}
			final BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
			if (bottomSheetBehavior.getState() != STATE_EXPANDED) {
				// Only show if the bottom sheet is fully expanded
				return;
			}

			// Show tooltip
			try {
				final String title = getString(R.string.image_labeling_new);
				final String description = getString(R.string.tooltip_image_labeling);
				final int accentColor = ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK ? R.color.accent_dark : R.color.accent_light;

				TapTargetView.showFor(this,
					TapTarget.forToolbarMenuItem(this.toolbar, R.id.menu_search, title, description)
						.outerCircleColor(accentColor)      // Specify a color for the outer circle
						.outerCircleAlpha(0.96f)            // Specify the alpha amount for the outer circle
						.targetCircleColor(android.R.color.white)   // Specify a color for the target circle
						.titleTextSize(24)                  // Specify the size (in sp) of the title text
						.titleTextColor(android.R.color.white)      // Specify the color of the title text
						.descriptionTextSize(18)            // Specify the size (in sp) of the description text
						.descriptionTextColor(android.R.color.white)  // Specify the color of the description text
						.textColor(android.R.color.white)            // Specify a color for both the title and description text
						.textTypeface(Typeface.SANS_SERIF)  // Specify a typeface for the text
						.dimColor(android.R.color.black)            // If set, will dim behind the view with 30% opacity of the given color
						.drawShadow(true)                   // Whether to draw a drop shadow or not
						.cancelable(true)                  // Whether tapping outside the outer circle dismisses the view
						.tintTarget(true)                   // Whether to tint the target view's color
						.transparentTarget(false)           // Specify whether the target is transparent (displays the content underneath)
						.targetRadius(50),                  // Specify the target radius (in dp)
					new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
						@Override
						public void onTargetClick(TapTargetView view) {
							super.onTargetClick(view);

						}
					});
				logger.info("First time tool tip shown");
				preferenceService.setIsImageLabelingTooltipShown(true);
			} catch (Exception e) {
				logger.warn("Could not show first time labeling tooltip", e);
			}
		}
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
		if (Build.VERSION.SDK_INT >= 24) {
			return isInMultiWindowMode();
		} else {
			return false;
		}
	}
}
