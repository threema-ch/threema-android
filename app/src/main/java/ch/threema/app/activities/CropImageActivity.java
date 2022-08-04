/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;

import com.canhub.cropper.CropImageView;

import androidx.appcompat.widget.Toolbar;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;

public class CropImageActivity extends ThreemaToolbarActivity {

	public static final String EXTRA_ASPECT_X = "ax";
	public static final String EXTRA_ASPECT_Y = "ay";
	public static final String EXTRA_MAX_X = "mx";
	public static final String EXTRA_MAX_Y = "my";
	public static final String EXTRA_OVAL = "oval";
	public static final String FORCE_DARK_THEME = "darkTheme";

	public static final int REQUEST_CROP = 7732;

	private int aspectX;
	private int aspectY;
	private int orientation, flip;

	// Output image size
	private int maxX;
	private int maxY;

	private boolean oval = false;

	private Uri sourceUri;
	private Uri saveUri;

	private boolean isSaving;

	private CropImageView imageView;

	@Override
	public void onCreate(Bundle icicle) {
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();

		if (extras != null && extras.getBoolean(FORCE_DARK_THEME, false)) {
			ConfigUtils.configureActivityTheme(this, ConfigUtils.THEME_DARK);
		} else {
			ConfigUtils.configureActivityTheme(this);
		}

		super.onCreate(icicle);

		Toolbar toolbar = findViewById(R.id.crop_toolbar);
		setSupportActionBar(toolbar);

		View cancelActionView = findViewById(R.id.action_cancel);
		cancelActionView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
		View doneActionView = findViewById(R.id.action_done);
		doneActionView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onSaveClicked();
			}
		});

		setupFromIntent();

		imageView = findViewById(R.id.crop_image);
		imageView.setOnSetImageUriCompleteListener(new CropImageView.OnSetImageUriCompleteListener() {
			@Override
			public void onSetImageUriComplete(CropImageView view, Uri uri, Exception error) {
				if (error == null && uri != null) {
					BitmapUtil.ExifOrientation exifOrientation = BitmapUtil.getExifOrientation(CropImageActivity.this, uri);
					int exifFlip = exifOrientation.getFlip();
					int exifRotation = 0;

					// Bug Workaround: CropImageView accounts for exif rotation but NOT if there's also a flip
					if ((exifFlip & BitmapUtil.FLIP_HORIZONTAL) == BitmapUtil.FLIP_HORIZONTAL) {
						view.flipImageHorizontally();
						exifRotation = (int) exifOrientation.getRotation();
					}
					if ((exifFlip & BitmapUtil.FLIP_VERTICAL) == BitmapUtil.FLIP_VERTICAL) {
						view.flipImageVertically();
						exifRotation = (int) exifOrientation.getRotation();
					}
					if (exifRotation != 0) {
						view.rotateImage(exifRotation);
					}

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

					if (aspectX != 0 && aspectY != 0) {
						view.setAspectRatio(aspectX, aspectY);
						view.setFixedAspectRatio(true);
					}
				}
			}
		});
		imageView.setImageUriAsync(sourceUri);
		imageView.setCropShape(oval ? CropImageView.CropShape.OVAL : CropImageView.CropShape.RECTANGLE);
		imageView.setOnCropImageCompleteListener(new CropImageView.OnCropImageCompleteListener() {
			@Override
			public void onCropImageComplete(CropImageView view, CropImageView.CropResult result) {
				cropCompleted();
			}
		});
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_crop;
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
			orientation = extras.getInt(ThreemaApplication.EXTRA_ORIENTATION, 0);
			flip = extras.getInt(ThreemaApplication.EXTRA_FLIP, BitmapUtil.FLIP_NONE);
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
}


