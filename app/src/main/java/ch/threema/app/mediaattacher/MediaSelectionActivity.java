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
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import ch.threema.app.R;
import ch.threema.app.activities.SendMediaActivity;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LocaleUtil;

public class MediaSelectionActivity extends MediaSelectionBaseActivity {

	private ControlPanelButton selectButton, cancelButton;
	private Button selectCounterButton;

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
		dateView.setVisibility(View.GONE);
		controlPanel.setVisibility(View.GONE);
		controlPanel.animate().translationY(getResources().getDimensionPixelSize(R.dimen.control_panel_height));

		mediaAttachViewModel.getCurrentMedia().observe(this, new Observer<List<MediaAttachItem>>() {
			@Override
			public void onChanged(List<MediaAttachItem> mediaAttachItems) {
				if (mediaAttachItems.size() != 0) {
					dateView.setVisibility(View.VISIBLE);
					controlPanel.setVisibility(View.VISIBLE);
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
			controlPanel.animate().translationY(0);
			controlPanel.postDelayed(() -> bottomSheetLayout.setPadding(
				0,
				0,
				0,
				0), 300);
		} else {
			selectCounterButton.setVisibility(View.GONE);
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
		controlPanel.setTranslationY(controlPanel.getHeight());
		ConstraintLayout selectPanel = findViewById(R.id.select_panel);
		this.cancelButton = selectPanel.findViewById(R.id.cancel);
		this.selectButton = selectPanel.findViewById(R.id.select);
		this.selectCounterButton = selectPanel.findViewById(R.id.select_counter_button);
	}

	public void setupControlPanelListeners(){
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

	private void selectItemsAndClose(ArrayList<Uri> uris) {
		ArrayList<MediaItem> mediaItems = new ArrayList<>();
		for (Uri uri : uris) {
			MediaItem mediaItem = new MediaItem(uri, FileUtil.getMimeTypeFromUri(MediaSelectionActivity.this, uri), null);
			mediaItem.setFilename(FileUtil.getFilenameFromUri(getContentResolver(), mediaItem));
			mediaItems.add(mediaItem);
		}

		Intent resultIntent = new Intent();
		resultIntent.putExtra(SendMediaActivity.EXTRA_MEDIA_ITEMS, mediaItems);
		if (mediaAttachViewModel.getLastQuery() != null) {
			resultIntent = IntentDataUtil.addLastMediaFilterToIntent(resultIntent,
				mediaAttachViewModel.getLastQuery(), mediaAttachViewModel.getLastQueryType());
		}
		setResult(RESULT_OK, resultIntent);
		finish();
	}

	/**
	 * Check if the media attacher's selectable media grid can be shown
	 * @return true if option has been enabled by user and permissions are available
	 */
	@Override
	protected boolean shouldShowMediaGrid() {
		return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
	public void onActivityResult(int requestCode, int resultCode, final Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if (resultCode == Activity.RESULT_OK) {
			switch (requestCode) {
				case REQUEST_CODE_ATTACH_FROM_GALLERY:
					selectItemsAndClose(FileUtil.getUrisFromResult(intent, getContentResolver()));
					break;
				default:
					break;
			}
		}
	}
}
