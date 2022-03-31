/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.fragments.mediaviews;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;

import ch.threema.app.R;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public class ImageViewFragment extends MediaViewFragment {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ImageViewFragment");

	private WeakReference<SubsamplingScaleImageView> imageViewReference;
	private WeakReference<ImageView> previewViewReference;

	private boolean uiVisibilityStatus = false;

	public ImageViewFragment() { super(); }

	@Override
	protected int getFragmentResourceId() {
		return R.layout.fragment_media_viewer_image;
	}

	@Override
	public boolean inquireClose() {
		return true;
	}

	@Override
	protected void showThumbnail(Bitmap thumbnail, boolean isGeneric, String filename) {
		if (TestUtil.required(imageViewReference.get(), thumbnail)) {
			if (!thumbnail.isRecycled()) {
				previewViewReference.get().setVisibility(View.VISIBLE);
				previewViewReference.get().setImageBitmap(thumbnail);
			}
			logger.debug("invisible");
			imageViewReference.get().setVisibility(View.INVISIBLE);
		}
	}

	@Override
	protected void hideThumbnail() {
		previewViewReference.get().setVisibility(View.INVISIBLE);
	}

	@Override
	protected void created(Bundle savedInstanceState) {
		SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.ARGB_8888);

		if (rootViewReference != null && rootViewReference.get() != null) {
			imageViewReference = new WeakReference<>(rootViewReference.get().findViewById(R.id.subsampling_image));
			previewViewReference = new WeakReference<>(rootViewReference.get().findViewById(R.id.preview_image));

			imageViewReference.get().setMaxScale(8);
			imageViewReference.get().setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);
			imageViewReference.get().setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					showUi(uiVisibilityStatus);
					uiVisibilityStatus = !uiVisibilityStatus;
				}
			});
			imageViewReference.get().setOnImageEventListener(new SubsamplingScaleImageView.OnImageEventListener() {
				@Override
				public void onReady() { }

				@Override
				public void onImageLoaded() {
					hideThumbnail();
				}

				@Override
				public void onPreviewLoadError(Exception e) { }

				@Override
				public void onImageLoadError(Exception e) { }

				@Override
				public void onTileLoadError(Exception e) { }

				@Override
				public void onPreviewReleased() { }
			});
		}
	}

	@Override
	protected void handleDecryptingFile() {
		//on decoding, do nothing!
	}

	@Override
	protected void handleDecryptFailure() {
		//
	}

	@Override
	protected void handleDecryptedFile(File file) {
		if (this.isAdded()) {

			imageViewReference.get().setImage(ImageSource.uri(file.getPath()));

			try {
				BitmapUtil.ExifOrientation exifOrientation = BitmapUtil.getExifOrientation(getContext(), Uri.fromFile(file));
				logger.debug("Orientation = " + exifOrientation);
				int rotation = (int) exifOrientation.getRotation();

				if (exifOrientation.getFlip() != BitmapUtil.FLIP_NONE) {
					if ((exifOrientation.getFlip() & BitmapUtil.FLIP_VERTICAL) == BitmapUtil.FLIP_VERTICAL) {
						imageViewReference.get().setScaleY(-1f);
					}
					if ((exifOrientation.getFlip() & BitmapUtil.FLIP_HORIZONTAL) == BitmapUtil.FLIP_HORIZONTAL) {
						imageViewReference.get().setScaleX(-1f);
						// invert rotation to compensate for flip
						rotation = 360 - rotation;
					}
				}
				if (exifOrientation.getRotation() != 0F) {
					imageViewReference.get().setOrientation(rotation);
				}
			} catch (Exception e) {
				logger.error("Exception", e);
			}
			imageViewReference.get().setVisibility(View.VISIBLE);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		if (this.rootViewReference != null && this.rootViewReference.get() != null) {
			this.rootViewReference.get().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					if (rootViewReference != null && rootViewReference.get() != null) {
						rootViewReference.get().getViewTreeObserver().removeOnGlobalLayoutListener(this);
						if (imageViewReference != null && imageViewReference.get() != null) {
							imageViewReference.get().resetScaleAndCenter();
						}
					}
				}
			});
		}
	}
}
