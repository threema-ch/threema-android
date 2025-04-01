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

package ch.threema.app.mediaattacher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
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
import com.google.android.material.shape.MaterialShapeDrawable;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.FitWindowsFrameLayout;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.PopupMenuWrapper;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
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
import ch.threema.app.ui.CheckableView;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.MediaGridItemDecoration;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RecyclerViewUtil;
import ch.threema.base.utils.LoggingUtil;
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
    private static final Logger logger = LoggingUtil.getThreemaLogger("MediaSelectionBaseActivity");

    // Threema services
    protected ServiceManager serviceManager;
    protected PreferenceService preferenceService;
    protected GroupService groupService;

    public static final String KEY_BOTTOM_SHEET_STATE = "bottom_sheet_state";
    public static final String KEY_PREVIEW_MODE = "preview_mode";
    private static final String KEY_PREVIEW_ITEM_POSITION = "preview_item";
    private static final String KEY_IS_EDITING_CONTACT = "contact_editing";

    protected static final int PERMISSION_REQUEST_ATTACH_FROM_GALLERY = 4;
    protected static final int PERMISSION_REQUEST_ATTACH_FILE = 5;
    protected static final int PERMISSION_REQUEST_ATTACH_STORAGE = 7;

    protected CoordinatorLayout rootView, gridContainer, pagerContainer;
    protected AppBarLayout appBarLayout;
    protected MaterialToolbar toolbar;
    protected EmptyRecyclerView mediaAttachRecyclerView;
    protected FastScroller fastScroller;
    protected GridLayoutManager gridLayoutManager;
    protected ConstraintLayout bottomSheetLayout, previewBottomSheetLayout;
    protected ImageView dragHandle;
    protected FrameLayout controlPanel;
    protected LinearLayout menuTitleFrame;
    protected TextView menuTitle, previewFilenameTextView, previewDateTextView;
    protected DisplayMetrics displayMetrics;
    protected MenuItem selectFromGalleryItem;
    protected PopupMenu bucketFilterMenu;
    protected ViewPager2 previewPager;
    private CheckableView checkBox;
    private ViewGroup selectMediaPermissionNoticeContainer;

    protected MediaAttachViewModel mediaAttachViewModel;

    protected MediaAttachAdapter mediaAttachAdapter;
    protected ImagePreviewPagerAdapter imagePreviewPagerAdapter;

    protected boolean isEditingContact = false;

    protected int peekHeightNumRows = 1;
    private @ColorInt int savedStatusBarColor = 0, expandedStatusBarColor;

    private boolean isDragging = false;
    private boolean bottomSheetScroll = false;
    private boolean isPreviewMode = false;
    private boolean expandedForFirstTime = true;

    BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior, previewBottomSheetBehavior;

    private final ActivityResultLauncher<Intent> fileAttachedResultLauncher = getFileAttachedResultLauncher();

    // Locks
    private final Object filterMenuLock = new Object();
    private final Object previewLock = new Object();

    /* start lifecycle methods */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigUtils.configureSystemBars(this);
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
    protected void handleSavedInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            onItemChecked(mediaAttachViewModel.getSelectedMediaItemsHashMap().size());
            int bottomSheetStyleState = savedInstanceState.getInt(KEY_BOTTOM_SHEET_STATE);
            isEditingContact = savedInstanceState.getBoolean(KEY_IS_EDITING_CONTACT);
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_BOTTOM_SHEET_STATE, bottomSheetBehavior.getState());
        outState.putBoolean(KEY_PREVIEW_MODE, isPreviewMode);
        outState.putInt(KEY_PREVIEW_ITEM_POSITION, previewPager.getCurrentItem());
        outState.putBoolean(KEY_IS_EDITING_CONTACT, isEditingContact);
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
                attachFilesFromGallery();
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
        this.gridContainer = findViewById(R.id.grid_container);
        this.previewPager = findViewById(R.id.pager);
        this.pagerContainer = findViewById(R.id.pager_container);
        this.checkBox = findViewById(R.id.check_box);
        this.previewFilenameTextView = findViewById(R.id.filename_view);
        this.previewDateTextView = findViewById(R.id.date_view);
        this.selectMediaPermissionNoticeContainer = findViewById(R.id.select_media_permission_notice_container);

        this.bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        this.previewBottomSheetBehavior = BottomSheetBehavior.from(previewBottomSheetLayout);

        MaterialToolbar previewToolbar = findViewById(R.id.preview_toolbar);
        previewToolbar.setNavigationOnClickListener(v -> onBackPressed());

        if (ConfigUtils.isFullVideoImagePermissionGranted(this)) {
            // full access
            findViewById(R.id.permission_container).setVisibility(View.GONE);
            findViewById(R.id.select_media_permission_notice_container).setVisibility(View.GONE);
        } else if (ConfigUtils.isPartialVideoImagePermissionGranted(this)) {
            // partial access
            findViewById(R.id.permission_container).setVisibility(View.GONE);
            selectMediaPermissionNoticeContainer.setVisibility(View.VISIBLE);
            findViewById(R.id.button_select_media_permission).setOnClickListener(v -> ConfigUtils.requestStoragePermissions(MediaSelectionBaseActivity.this, null, PERMISSION_REQUEST_ATTACH_STORAGE));
        } else {
            // no access
            findViewById(R.id.permission_container).setVisibility(View.VISIBLE);
            findViewById(R.id.permission_button).setOnClickListener(v -> ConfigUtils.requestStoragePermissions(MediaSelectionBaseActivity.this, null, PERMISSION_REQUEST_ATTACH_STORAGE));
        }

        this.checkBox.setOnClickListener(v -> {
            checkBox.toggle();
            MediaAttachItem mediaItem = imagePreviewPagerAdapter.getItem(previewPager.getCurrentItem());
            if (mediaItem != null) {
                if (checkBox.isChecked()) {
                    mediaAttachViewModel.addSelectedMediaItem(mediaItem.getId(), mediaItem);
                } else {
                    mediaAttachViewModel.removeSelectedMediaItem(mediaItem.getId());
                }
            }
        });

        this.previewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updatePreviewInfo(position);
            }
        });

        @SuppressLint("RestrictedApi") FitWindowsFrameLayout contentFrameLayout = (FitWindowsFrameLayout) ((ViewGroup) rootView.getParent()).getParent();
        contentFrameLayout.setOnClickListener(v -> finish());

        // set status bar color
        expandedStatusBarColor = getResources().getColor(R.color.attach_status_bar_color_expanded);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        this.bottomSheetLayout.post(new Runnable() {
            @Override
            public void run() {
                Drawable background = bottomSheetLayout.getBackground();
                if (background instanceof MaterialShapeDrawable) {
                    expandedStatusBarColor = ((MaterialShapeDrawable) background).getResolvedTintColor();
                }
            }
        });

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

            this.peekHeightNumRows = 1;
        } else {
            this.gridLayoutManager = new GridLayoutManager(this, 3);
            this.mediaAttachRecyclerView.setLayoutManager(gridLayoutManager);

            this.peekHeightNumRows = isInSplitScreenMode() ? 1 : 2;
        }

        // Set initial peek height
        this.updatePeekHeight();

        // Listen for layout changes
        this.mediaAttachAdapter = new MediaAttachAdapter(this, this, this.gridLayoutManager.getSpanCount());
        this.imagePreviewPagerAdapter = new ImagePreviewPagerAdapter(this);
        this.previewPager.setOffscreenPageLimit(1);
        this.mediaAttachRecyclerView.addItemDecoration(new MediaGridItemDecoration(getResources().getDimensionPixelSize(R.dimen.grid_spacing)));
        this.mediaAttachRecyclerView.setAdapter(mediaAttachAdapter);

        ConfigUtils.addIconsToOverflowMenu(this.toolbar.getMenu());

        this.rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // adjust height of bottom sheet container to snap smack below toolbar
                CoordinatorLayout bottomSheetContainer = findViewById(R.id.bottom_sheet_container);
                int topMargin = toolbar.getHeight() - getResources().getDimensionPixelSize(R.dimen.drag_handle_height);

                CoordinatorLayout.LayoutParams bottomSheetContainerLayoutParams = (CoordinatorLayout.LayoutParams) bottomSheetContainer.getLayoutParams();
                bottomSheetContainerLayoutParams.setMargins(0, topMargin, 0, 0);
                bottomSheetContainer.setLayoutParams(bottomSheetContainerLayoutParams);
            }
        });
    }

    /**
     * Get the result launcher to handle the selected files. Note that this file result launcher is
     * not used to send media explicitly as files. Therefore media should be sent with the correct
     * settings.
     */
    protected abstract ActivityResultLauncher<Intent> getFileAttachedResultLauncher();

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
        ConfigUtils.tintMenuIcon(this, topMenuItem, R.attr.colorOnSurface);

        // Fetch all media, add a unique menu item for each media storage bucket and media type group.
        registerOnAllDataFetchedListener(new Observer<>() {
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
                    final TreeMap<String, Integer> mediaAttachTypes = new TreeMap<>();

                    for (MediaAttachItem mediaAttachItem : mediaAttachItems) {
                        String bucket = mediaAttachItem.getBucketName();
                        if (!TextUtils.isEmpty(bucket) && !buckets.contains(bucket)) {
                            buckets.add(mediaAttachItem.getBucketName());
                        }

                        int type = mediaAttachItem.getType();
                        if (!mediaAttachTypes.containsValue(type)) {
                            String mediaTypeName = MediaSelectionBaseActivity.this.getMediaAttachTypeTitle(type);
                            mediaAttachTypes.put(mediaTypeName, type);
                        }
                    }

                    Collections.sort(buckets);

                    // Fill menu first media types sorted then folders/buckets sorted
                    for (Map.Entry<String, Integer> mediaType : mediaAttachTypes.entrySet()) {
                        MenuItem item = menu.add(mediaType.getKey()).setOnMenuItemClickListener(menuItem -> {
                            MediaSelectionBaseActivity.this.filterMediaByMediaAttachType(menuItem.toString());
                            return true;
                        });

                        switch (mediaType.getValue()) {
                            case MediaAttachItem.TYPE_IMAGE:
                                item.setIcon(R.drawable.ic_image_outline);
                                break;
                            case MediaAttachItem.TYPE_VIDEO:
                                item.setIcon(R.drawable.ic_movie_outline);
                                break;
                            case MediaAttachItem.TYPE_GIF:
                                item.setIcon(R.drawable.ic_gif_24dp);
                                break;
                            case MediaAttachItem.TYPE_WEBP:
                                item.setIcon(R.drawable.ic_webp);
                                break;
                        }
                        ConfigUtils.tintMenuIcon(MediaSelectionBaseActivity.this, item, R.attr.colorOnSurface);
                    }

                    for (String bucket : buckets) {
                        if (!TextUtils.isEmpty(bucket)) {
                            MenuItem item = menu.add(bucket).setOnMenuItemClickListener(menuItem -> {
                                MediaSelectionBaseActivity.this.filterMediaByBucket(menuItem.toString());
                                return true;
                            });
                            item.setIcon(R.drawable.ic_outline_folder_24);
                            ConfigUtils.tintMenuIcon(MediaSelectionBaseActivity.this, item, R.attr.colorOnSurface);
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
                            MediaSelectionBaseActivity.this.filterMediaByMediaAttachType(savedQuery);
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
        if (mediaItem == null) {
            return;
        }

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
                mediaAttachAdapter.setMediaAttachItems(currentlyShowingItems);
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
     *
     * @return true if option has been enabled by user and permissions are available
     */
    protected boolean shouldShowMediaGrid() {
        return preferenceService.isShowImageAttachPreviewsEnabled()
            && ConfigUtils.isVideoImagePermissionGranted(this);
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
                setFirstVisibleItemData();

                // make sure only bottom sheet or recyclerview is scrolling at a same time
                if (bottomSheetScroll && bottomSheetBehavior.getState() == STATE_EXPANDED) {
                    bottomSheetScroll = false;
                    bottomSheetBehavior.setDraggable(false);
                } else if (!bottomSheetScroll && !recyclerView.canScrollVertically(-1)) {
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
                            .setPopupStyle(RecyclerViewUtil.thumbScrollerPopupStyle)
                            .setPopupTextProvider((view, position) -> {
                                int firstVisible = gridLayoutManager.findFirstCompletelyVisibleItemPosition();
                                if (firstVisible >= 0) {
                                    MediaAttachItem item = mediaAttachAdapter.getMediaAttachItems().get(firstVisible);
                                    return LocaleUtil.formatDateRelative(item.getDateModified() * 1000);
                                }

                                return context.getString(R.string.unknown);
                            })
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
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
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
                        getWindow().setNavigationBarColor(expandedStatusBarColor);
                    }
                    if (!ConfigUtils.isTheDarkSide(this)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                        }
                    }
                    isPreviewMode = false;
                }
            } else {
                finish();
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

                toolbar.postDelayed(() -> {
                    savedStatusBarColor = getWindow().getStatusBarColor();
                    getWindow().setStatusBarColor(getResources().getColor(R.color.gallery_background));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        getWindow().setNavigationBarColor(getResources().getColor(R.color.gallery_background));
                    }
                    if (!ConfigUtils.isTheDarkSide(this)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                        }
                    }
                }, delay);

                previewPager.post(() -> {
                    previewPager.setCurrentItem(position, false);
                    updatePreviewInfo(position);
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

    public void filterMediaByMediaAttachType(@NonNull String mediaAttachTypeTitle) {
        int mediaAttachType = 0;

        if (mediaAttachTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_pictures))) {
            mediaAttachType = MediaAttachItem.TYPE_IMAGE;
        } else if (mediaAttachTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_videos))) {
            mediaAttachType = MediaAttachItem.TYPE_VIDEO;
        } else if (mediaAttachTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_gifs))) {
            mediaAttachType = MediaAttachItem.TYPE_GIF;
        } else if (mediaAttachTypeTitle.equals(ThreemaApplication.getAppContext().getResources().getString(R.string.media_gallery_animated_webps))) {
            mediaAttachType = MediaAttachItem.TYPE_WEBP;
        }

        if (mediaAttachType != 0) {
            mediaAttachViewModel.setMediaByType(mediaAttachType);
        }
        menuTitle.setText(mediaAttachTypeTitle);
        mediaAttachViewModel.setlastQuery(FILTER_MEDIA_TYPE, mediaAttachTypeTitle);
    }

    public void filterMediaBySelectedItems() {
        mediaAttachViewModel.setSelectedMedia();
        menuTitle.setText(R.string.selected_media);
        mediaAttachViewModel.setlastQuery(FILTER_MEDIA_SELECTED, null);
    }

    public String getMediaAttachTypeTitle(int mediaAttachType) {
        switch (mediaAttachType) {
            case (MediaAttachItem.TYPE_IMAGE):
                return getResources().getString(R.string.media_gallery_pictures);
            case (MediaAttachItem.TYPE_VIDEO):
                return getResources().getString(R.string.media_gallery_videos);
            case (MediaAttachItem.TYPE_GIF):
                return getResources().getString(R.string.media_gallery_gifs);
            case (MediaAttachItem.TYPE_WEBP):
                return getResources().getString(R.string.media_gallery_animated_webps);
            default:
                return null;
        }
    }

    public void updateUI(int state) {
        Animation animation;

        if (bottomSheetBehavior.getState() != state) {
            try {
                bottomSheetBehavior.setState(state);
            } catch (IllegalArgumentException e) {
                // some states such as DRAGGING cannot be set externally
            }
        }

        switch (state) {
            case STATE_HIDDEN:
                // If editing a contact, we don't want to finish this activity yet
                if (!isEditingContact) {
                    finish();
                }
                break;
            case STATE_EXPANDED:
                dragHandle.setVisibility(View.INVISIBLE);
                setFirstVisibleItemData();

                bucketFilterMenu.getMenu().setGroupVisible(Menu.NONE, true);
                menuTitleFrame.setClickable(true);

                animation = appBarLayout.getAnimation();
                if (animation != null) {
                    animation.cancel();
                }

                appBarLayout.setAlpha(0f);
                appBarLayout.setVisibility(View.VISIBLE);
                appBarLayout.animate()
                    .alpha(1f)
                    .setDuration(100)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            appBarLayout.setVisibility(View.VISIBLE);
                        }
                    });
                appBarLayout.postDelayed(
                    () -> getWindow().setStatusBarColor(expandedStatusBarColor),
                    50
                );
                // show/hide control panel in attach mode depending on whether we have selected items
                if (MediaSelectionBaseActivity.this instanceof MediaAttachActivity) {
                    if (mediaAttachViewModel.getSelectedMediaItemsHashMap().isEmpty()) {
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

                    dragHandle.setVisibility(View.VISIBLE);

                    animation = appBarLayout.getAnimation();
                    if (animation != null) {
                        animation.cancel();
                    }
                    appBarLayout.setAlpha(1f);
                    appBarLayout.animate()
                        .alpha(0f)
                        .setDuration(100)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                appBarLayout.setVisibility(View.GONE);
                            }
                        });
                    appBarLayout.postDelayed(() -> {
                        getWindow().setStatusBarColor(Color.TRANSPARENT);
                    }, 50);
                }
                break;
            case STATE_COLLAPSED:
                bottomSheetBehavior.setDraggable(true);
                bottomSheetScroll = true;
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
     * Adjust the peek height of the bottom sheet to fit in the {@link #peekHeightNumRows}
     * number of items vertically.
     */
    protected synchronized void updatePeekHeight() {
        logger.debug("*** updatePeekHeight");

        if (shouldShowMediaGrid()) {
            final int numRows = this.peekHeightNumRows;
            logger.debug("Update peek height ({} elements)", numRows);
            int numItems = mediaAttachRecyclerView.getLayoutManager().getItemCount();

            bottomSheetLayout.setVisibility(View.VISIBLE);

            // Fetch some pixel dimensions we need for calculations below
            final Resources resources = this.getResources();
            final int controlPanelHeight = resources.getDimensionPixelSize(R.dimen.control_panel_height);
            final int controlPanelShadowHeight = resources.getDimensionPixelSize(R.dimen.media_attach_control_panel_shadow_size);
            final int dragHandleHeight = resources.getDimensionPixelSize(R.dimen.drag_handle_height);

            int selectMediaPermissionNoticeHeight = selectMediaPermissionNoticeContainer.getHeight();
            // additional bottom padding under notice container
            if (selectMediaPermissionNoticeHeight > 0) {
                selectMediaPermissionNoticeHeight += dragHandleHeight;
            }

            // We have a bit of a chicken-and-egg problem: Before the peek height is not set,
            // the grid items are not loaded. But without the grid items being loaded, we cannot
            // calculate the proper height.
            // To avoid this, we initially set a peek height that's just enough to show the top
            // "grab handle", which will trigger the loading of the elements. Once the elements
            // are loaded, we can update the peek height.
            int peekHeight = controlPanelHeight
                - controlPanelShadowHeight
                + dragHandleHeight
                + selectMediaPermissionNoticeHeight;
            boolean peekHeightKnown;
            if (numItems > 0 && mediaAttachRecyclerView.getChildAt(0) != null) {
                // Child views are already here, we can calculate the total height
                int itemHeight = mediaAttachRecyclerView.getChildAt(0).getHeight();
                peekHeight += itemHeight * numRows;
                peekHeight += MediaSelectionBaseActivity.this.getResources().getDimensionPixelSize(R.dimen.grid_spacing) * numRows;
                if (numItems > (numRows * gridLayoutManager.getSpanCount())) {
                    peekHeight += itemHeight / 8; // teaser for further items below
                }
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

    protected void setFirstVisibleItemData() {
        int firstVisible = gridLayoutManager.findFirstVisibleItemPosition();
        if (firstVisible < 0) {
            mediaAttachRecyclerView.clearEmptyView();

            EmptyView emptyView = new EmptyView(this, 0);
            emptyView.setup(R.string.no_media_found_global);
            ((ViewGroup) mediaAttachRecyclerView.getParent()).addView(emptyView);
            mediaAttachRecyclerView.setEmptyView(emptyView);
        }
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        if (id == R.id.collapsing_toolbar) {
            finish();
        } else if (id == R.id.cancel) {
            // finish when clicking transparent area showing the chat behind the attacher
            if (mediaAttachAdapter != null) {
                mediaAttachAdapter.clearSelection();
                onItemChecked(0);
            }
        } else if (id == R.id.select_counter_button) {
            if (mediaAttachAdapter != null) {
                filterMediaBySelectedItems();
            }
        }
    }

    @Override
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

    /**
     * Attach any files from gallery.
     */
    protected void attachFilesFromGallery() {
        FileUtil.selectFile(this, fileAttachedResultLauncher, new String[]{MimeUtil.MIME_TYPE_ANY}, true, MAX_BLOB_SIZE, null);
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
        appBarLayout.setVisibility(View.GONE);
        appBarLayout.post(() -> {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        });

        updateUI(STATE_COLLAPSED);
    }
}
