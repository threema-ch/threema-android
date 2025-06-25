/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.appcompat.app.ActionBar;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceManager;

import com.canhub.cropper.CropImageView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.slf4j.Logger;

import java.util.Collections;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class CropImageActivity extends ThreemaToolbarActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("CropImageActivity");

    public static final String EXTRA_ASPECT_X = "ax";
    public static final String EXTRA_ASPECT_Y = "ay";
    public static final String EXTRA_MAX_X = "mx";
    public static final String EXTRA_MAX_Y = "my";
    public static final String EXTRA_OVAL = "oval";
    public static final String FORCE_DARK_THEME = "darkTheme";
    public static final String EXTRA_ADDITIONAL_ORIENTATION = "additional_rotation";
    public static final String EXTRA_ADDITIONAL_FLIP = "additional_flip";
    public static final int REQUEST_CROP = 7732;

    private int aspectX, aspectY, orientation, flip, additionalOrientation, additionalFlip, maxX, maxY;
    private boolean oval = false, isSaving;
    private Uri sourceUri, saveUri;
    private CropImageView imageView;
    private View contentView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        logScreenVisibility(this, logger);
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (extras != null && extras.getBoolean(FORCE_DARK_THEME, false)) {
            setTheme(R.style.Theme_Threema_MediaViewer);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (sharedPreferences != null && sharedPreferences.getBoolean("pref_dynamic_color", false)) {
                DynamicColors.applyToActivityIfAvailable(this);
            }
        }

        super.onCreate(savedInstanceState);

        MaterialToolbar toolbar = findViewById(R.id.crop_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        ExtendedFloatingActionButton doneActionView = findViewById(R.id.floating);
        doneActionView.setOnClickListener(v -> onSaveClicked());

        setupFromIntent();

        imageView = findViewById(R.id.crop_image);
        imageView.setOnSetImageUriCompleteListener((view, uri, error) -> {
            if (error == null) {
                // non-exif
                if ((flip & BitmapUtil.FLIP_HORIZONTAL) == BitmapUtil.FLIP_HORIZONTAL) {
                    view.flipImageHorizontally();
                }
                if ((flip & BitmapUtil.FLIP_VERTICAL) == BitmapUtil.FLIP_VERTICAL) {
                    view.flipImageVertically();
                }
                if (orientation != 0) {
                    view.rotateImage(orientation);
                }

                // Additional flip and rotation
                if ((additionalFlip & BitmapUtil.FLIP_HORIZONTAL) == BitmapUtil.FLIP_HORIZONTAL) {
                    view.flipImageHorizontally();
                }
                if ((additionalFlip & BitmapUtil.FLIP_VERTICAL) == BitmapUtil.FLIP_VERTICAL) {
                    view.flipImageVertically();
                }
                if (additionalOrientation != 0) {
                    view.rotateImage(additionalOrientation);
                }

                if (aspectX != 0 && aspectY != 0) {
                    view.setAspectRatio(aspectX, aspectY);
                    view.setFixedAspectRatio(true);
                }
            }
        });
        if (savedInstanceState == null) {
            imageView.setCropShape(oval ? CropImageView.CropShape.OVAL : CropImageView.CropShape.RECTANGLE);
            imageView.setImageUriAsync(sourceUri);
        }
        imageView.setOnCropImageCompleteListener((view, result) -> cropCompleted());

        contentView = findViewById(android.R.id.content);
        ViewTreeObserver treeObserver = contentView.getViewTreeObserver();
        treeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                contentView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                excludeGestures();
            }
        });
    }

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.crop_parent),
            InsetSides.lbr()
        );
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_crop;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void cropCompleted() {
        setResult(RESULT_OK, new Intent().putExtra(MediaStore.EXTRA_OUTPUT, saveUri));
        finish();
    }

    private void setupFromIntent() {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (extras != null) {
            aspectX = extras.getInt(EXTRA_ASPECT_X);
            aspectY = extras.getInt(EXTRA_ASPECT_Y);
            maxX = extras.getInt(EXTRA_MAX_X);
            maxY = extras.getInt(EXTRA_MAX_Y);
            oval = extras.getBoolean(EXTRA_OVAL, false);
            saveUri = extras.getParcelable(MediaStore.EXTRA_OUTPUT);
            orientation = extras.getInt(AppConstants.EXTRA_ORIENTATION, 0);
            flip = extras.getInt(AppConstants.EXTRA_FLIP, BitmapUtil.FLIP_NONE);
            additionalOrientation = extras.getInt(EXTRA_ADDITIONAL_ORIENTATION, 0);
            additionalFlip = extras.getInt(EXTRA_ADDITIONAL_FLIP, BitmapUtil.FLIP_NONE);
        }

        sourceUri = intent.getData();
    }

    private void onSaveClicked() {
        if (imageView == null || isSaving) {
            return;
        }
        isSaving = true;

        if (maxX != 0 && maxY != 0) {
            imageView.croppedImageAsync(Bitmap.CompressFormat.PNG, 100, maxX, maxY, CropImageView.RequestSizeOptions.RESIZE_INSIDE, saveUri);
        } else {
            imageView.croppedImageAsync(Bitmap.CompressFormat.PNG, 100, 0, 0, CropImageView.RequestSizeOptions.NONE, saveUri);
        }
    }

    private void excludeGestures() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }

        int maxHeight = getResources().getDimensionPixelSize(R.dimen.gesture_exclusion_max_height);
        Rect drawingRect = new Rect();
        imageView.getDrawingRect(drawingRect);

        int y = 0;
        int realHeight = drawingRect.height();
        if (realHeight > maxHeight) {
            y = (realHeight - maxHeight) / 2;
            realHeight = maxHeight;
        }

        Rect exclusionRect = new Rect(0, y, getResources().getDimensionPixelSize(R.dimen.gesture_exclusion_border_width), y + realHeight);
        ViewCompat.setSystemGestureExclusionRects(imageView, Collections.singletonList(exclusionRect));
    }
}


