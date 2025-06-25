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

import static ch.threema.app.mediaattacher.MediaFilterQuery.FILTER_MEDIA_BUCKET;
import static ch.threema.app.mediaattacher.MediaFilterQuery.FILTER_MEDIA_TYPE;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.activities.SendMediaActivity;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.base.utils.LoggingUtil;

public class MediaSelectionActivity extends MediaSelectionBaseActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("MediaSelectionActivity");

    private ControlPanelButton selectButton, cancelButton;
    private Button selectCounterButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    protected void initActivity(Bundle savedInstanceState) {
        super.initActivity(null);
        setControlPanelLayout();
        setupControlPanelListeners();

        // always open bottom sheet in expanded state right away
        expandBottomSheet();
        setInitialMediaGrid();

        handleSavedInstanceState(savedInstanceState);
    }

    @Override
    protected void setInitialMediaGrid() {
        super.setInitialMediaGrid();
        // hide media items dependent views until we have data loaded and set to grid

        // check for previous filter selection to be reset
        Intent intent = getIntent();
        int queryType = 0;
        String query = null;
        if (intent.hasExtra(ComposeMessageFragment.EXTRA_LAST_MEDIA_SEARCH_QUERY)) {
            MediaFilterQuery lastFilter = IntentDataUtil.getLastMediaFilterFromIntent(intent);
            queryType = lastFilter.getType();
            query = lastFilter.getQuery();
        }

        int finalPreviousQueryType = queryType;
        String finalPreviousQuery = query;

        // because the MediaSelectionActivity subclass is initialized with expanded bottom sheet,
        // we start fetching all data right away and listen for it be be available
        mediaAttachViewModel.fetchAllMediaFromRepository(false);
        registerOnAllDataFetchedListener(new Observer<List<MediaAttachItem>>() {
            @Override
            public void onChanged(List<MediaAttachItem> mediaAttachItems) {
                // ignore the first onChanged trigger, when there are not items yet
                if (!mediaAttachItems.isEmpty()) {
                    // if we previously searched media, we reset the filter, otherwise we post all media to grid view
                    if (finalPreviousQuery != null) {
                        switch (finalPreviousQueryType) {
                            case FILTER_MEDIA_TYPE:
                                MediaSelectionActivity.this.filterMediaByMediaAttachType(finalPreviousQuery);
                                break;
                            case FILTER_MEDIA_BUCKET:
                                MediaSelectionActivity.this.filterMediaByBucket(finalPreviousQuery);
                                break;
                            default:
                                break;
                        }
                    } else {
                        mediaAttachViewModel.setAllMedia();
                    }
                    // remove listener after receiving full list as we listen to current selected media afterwards to update the grid view
                    mediaAttachViewModel.getAllMedia().removeObserver(this);
                }
            }
        });

        mediaAttachViewModel.getCurrentMedia().observe(this, new Observer<List<MediaAttachItem>>() {
            @Override
            public void onChanged(List<MediaAttachItem> mediaAttachItems) {
                if (!mediaAttachItems.isEmpty()) {
                    mediaAttachViewModel.getCurrentMedia().removeObserver(this);
                }
            }
        });
    }

    @Override
    public void onItemChecked(int count) {
        if (count > 0) {
            selectCounterButton.setText(String.format(LocaleUtil.getCurrentLocale(this), "%d", count));
            selectCounterButton.setVisibility(View.VISIBLE);
            controlPanel.setVisibility(View.VISIBLE);
            controlPanel.animate().translationY(0);
            // align last grid element on top of control panel
            controlPanel.postDelayed(() -> bottomSheetLayout.setPadding(
                0,
                0,
                0,
                0), 300);
        } else {
            controlPanel.animate().translationY(controlPanel.getHeight());
            ValueAnimator animator = ValueAnimator.ofInt(bottomSheetLayout.getPaddingBottom(), 0);
            animator.addUpdateListener(valueAnimator -> bottomSheetLayout.setPadding(
                0,
                0,
                0,
                (Integer) valueAnimator.getAnimatedValue()));
            animator.setDuration(300);
            animator.start();
        }
    }

    public void setControlPanelLayout() {
        ViewStub stub = findViewById(R.id.stub);
        stub.setLayoutResource(R.layout.media_selection_control_panel);
        stub.inflate();

        this.controlPanel = findViewById(R.id.control_panel);
        controlPanel.setVisibility(View.GONE);

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            controlPanel,
            InsetSides.bottom()
        );

        ConstraintLayout selectPanel = findViewById(R.id.select_panel);
        this.cancelButton = selectPanel.findViewById(R.id.cancel);
        this.selectButton = selectPanel.findViewById(R.id.select);
        this.selectCounterButton = selectPanel.findViewById(R.id.select_counter_button);
        this.selectCounterButton.setContentDescription(ConfigUtils.getSafeQuantityString(this, R.plurals.selection_counter_label, 0, 0));
    }

    public void setupControlPanelListeners() {
        this.selectCounterButton.setOnClickListener(this);
        this.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaSelectionActivity.super.onClick(v);
            }
        });
        this.selectButton.setOnClickListener(new DebouncedOnClickListener(1000) {
            @Override
            public void onDebouncedClick(View v) {
                v.setAlpha(0.3f);
                selectItemsAndClose(mediaAttachViewModel.getSelectedMediaUris());
            }
        });
    }

    private void selectItemsAndClose(List<Uri> uris) {
        ArrayList<MediaItem> mediaItems = MediaItem.getFromUris(uris, this);

        Intent resultIntent = new Intent();
        resultIntent.putExtra(SendMediaActivity.EXTRA_MEDIA_ITEMS, mediaItems);
        if (mediaAttachViewModel.getLastQuery() != null) {
            IntentDataUtil.addLastMediaFilterToIntent(resultIntent,
                mediaAttachViewModel.getLastQuery(), mediaAttachViewModel.getLastQueryType());
        }
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    /**
     * Check if the media attacher's selectable media grid can be shown
     *
     * @return true if option has been enabled by user and permissions are available
     */
    @Override
    protected boolean shouldShowMediaGrid() {
        return ConfigUtils.isVideoImagePermissionGranted(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case PERMISSION_REQUEST_ATTACH_FILE:
                    updateUI(BottomSheetBehavior.STATE_COLLAPSED);
                    toolbar.setVisibility(View.GONE);
                    selectButton.setAlpha(0.3f);
                    selectButton.setOnClickListener(v -> {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(MediaSelectionActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                            showPermissionRationale(R.string.permission_storage_required);
                        }

                    });
                    cancelButton.setOnClickListener(v -> finish());
            }
        }
    }

    @Override
    protected ActivityResultLauncher<Intent> getFileAttachedResultLauncher() {
        return registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectItemsAndClose(FileUtil.getUrisFromResult(result.getData(), getContentResolver()));
                }
            });
    }
}
