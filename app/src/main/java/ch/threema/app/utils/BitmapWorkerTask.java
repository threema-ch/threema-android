/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

package ch.threema.app.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.widget.ImageView;

import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

import androidx.appcompat.content.res.AppCompatResources;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.BitmapUtil.FLIP_NONE;

public class BitmapWorkerTask extends AsyncTask<BitmapWorkerTaskParams, Void, Bitmap> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("BitmapWorkerTask");

	private final WeakReference<ImageView> imageViewReference;

	public BitmapWorkerTask(ImageView imageView) {
		this.imageViewReference = new WeakReference<ImageView>(imageView);
	}

	// Decode image in background.
	@Override
	protected Bitmap doInBackground(BitmapWorkerTaskParams... params) {
		BitmapWorkerTaskParams bitmapParams = params[0];
		Bitmap bitmap = null;

		try {
			bitmap = decodeSampledBitmapFromUri(bitmapParams);
		} catch (FileNotFoundException | IllegalStateException | SecurityException e) {
			logger.error("Exception", e);
		}

		if (bitmap == null) {
			bitmap = BitmapUtil.getBitmapFromVectorDrawable(AppCompatResources.getDrawable(ThreemaApplication.getAppContext(), R.drawable.ic_baseline_broken_image_24), Color.WHITE);
		}

		return bitmap;
	}

	// Once complete, see if ImageView is still around and set bitmap.
	@Override
	protected void onPostExecute(Bitmap bitmap) {
		if (imageViewReference != null) {
			final ImageView imageView = imageViewReference.get();
			if (imageView != null) {
				imageView.setImageBitmap(bitmap);
			}
		}
	}

	private Bitmap decodeSampledBitmapFromUri(BitmapWorkerTaskParams bitmapParams) throws FileNotFoundException, IllegalStateException, SecurityException {
		InputStream is = null;
		final BitmapFactory.Options options = new BitmapFactory.Options();

		if (bitmapParams.width != 0 && bitmapParams.height != 0) {
			// First decode with inJustDecodeBounds=true to check dimensions
			options.inJustDecodeBounds = true;

			try {
				is = bitmapParams.contentResolver.openInputStream(bitmapParams.imageUri);
				BitmapFactory.decodeStream(is, null, options);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						//
					}
				}
			}

			boolean isRotated = (bitmapParams.exifOrientation + bitmapParams.orientation) % 180 != 0;

			// width of container in which the resulting bitmap should be fitted
			int containerWidth = bitmapParams.width;
			int containerHeight = bitmapParams.height;

			int bitmapWidth = isRotated ? options.outHeight : options.outWidth;
			int bitmapHeight = isRotated ? options.outWidth : options.outHeight;

			if (bitmapWidth < containerWidth && bitmapHeight < containerHeight) {
				// blow up
				float ratioX = (float) containerWidth / bitmapWidth;
				float ratioY = (float) containerHeight / bitmapHeight;

				if (ratioX < ratioY) {
					bitmapWidth = containerWidth;
					bitmapHeight = (int) ((float) bitmapHeight * ratioX);
				} else {
					bitmapHeight = containerHeight;
					bitmapWidth = (int) ((float) bitmapWidth * ratioY);
				}
			}
			else {
				if (bitmapWidth > containerWidth) {
					// scale down
					bitmapHeight = (bitmapHeight * containerWidth) / bitmapWidth;
					bitmapWidth = containerWidth;
				}
				if (bitmapHeight > containerHeight) {
					bitmapWidth = (bitmapWidth * containerHeight) / bitmapHeight;
					bitmapHeight = containerHeight;
				}
			}

			options.inSampleSize = calculateInSampleSize(options, bitmapWidth, bitmapHeight);

			// Decode bitmap with inSampleSize set
			options.inJustDecodeBounds = false;
			options.inMutable = bitmapParams.mutable;

			is = null;
			Bitmap roughBitmap;
			try {
				is = bitmapParams.contentResolver.openInputStream(bitmapParams.imageUri);
				roughBitmap = BitmapFactory.decodeStream(is, null, options);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						//
					}
				}
			}

			int outWidth = bitmapWidth;
			int outHeight = bitmapHeight;

			// resize bitmap to exact size
			if (roughBitmap != null) {
				if (bitmapParams.exifOrientation != 0 || bitmapParams.exifFlip != FLIP_NONE) {
					roughBitmap = BitmapUtil.rotateBitmap(roughBitmap, bitmapParams.exifOrientation, bitmapParams.exifFlip);
				}

				if (bitmapParams.orientation != 0 || bitmapParams.flip != FLIP_NONE) {
					roughBitmap = BitmapUtil.rotateBitmap(roughBitmap, bitmapParams.orientation, bitmapParams.flip);
				}

				try {
					return Bitmap.createScaledBitmap(roughBitmap, outWidth, outHeight, true);
				} catch (Exception e) {
					return null;
				}
			}
			return null;
		}
		options.inMutable = bitmapParams.mutable;

		is = null;
		try {
			is = bitmapParams.contentResolver.openInputStream(bitmapParams.imageUri);
			Bitmap roughBitmap = BitmapFactory.decodeStream(is, null, options);

			if (roughBitmap != null) {
				if (bitmapParams.exifOrientation != 0 || bitmapParams.exifFlip != FLIP_NONE) {
					roughBitmap = BitmapUtil.rotateBitmap(roughBitmap, bitmapParams.exifOrientation, bitmapParams.exifFlip);
				}

				if (bitmapParams.orientation != 0 || bitmapParams.flip != FLIP_NONE) {
					roughBitmap = BitmapUtil.rotateBitmap(roughBitmap, bitmapParams.orientation, bitmapParams.flip);
				}
			}
			return roughBitmap;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					//
				}
			}
		}
	}

	private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			// Calculate ratios of height and width to requested height and width
			final int heightRatio = Math.round((float) height / (float) reqHeight);
			final int widthRatio = Math.round((float) width / (float) reqWidth);

			// Choose the smallest ratio as inSampleSize value, this will guarantee
			// a final image with both dimensions larger than or equal to the
			// requested height and width.
			inSampleSize = Math.min(heightRatio, widthRatio);
		}

		return inSampleSize;
	}
}
