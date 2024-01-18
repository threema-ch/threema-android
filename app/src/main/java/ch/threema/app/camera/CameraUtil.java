/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;

public class CameraUtil {
	// list of MAX_QUALITY compatible cameras
	private static final HashSet<String> MAX_QUALITY_CAMERAS = new HashSet<String>() {{
		add("Pixel 2");
		add("Pixel 2 XL");
		add("Pixel 3");
		add("Pixel 3 XL");
		add("Pixel 3a");
		add("Pixel 3a XL");
	}};

	// list of cameras that are incompatible with camerax
	private static final HashSet<String> BLACKLISTED_CAMERAS = new HashSet<String>() {{
	}};

	private static byte[] transformByteArray(@NonNull byte[] data, Rect cropRect, int rotation, boolean flip) throws IOException {
		Bitmap in = null;

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, options);

		if (cropRect != null && cropRect.width() <= options.outWidth && cropRect.height() <= options.outHeight) {
			BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(data, 0, data.length, false);
			in = decoder.decodeRegion(cropRect, new BitmapFactory.Options());
			decoder.recycle();
		} else {
			in = BitmapFactory.decodeByteArray(data, 0, data.length);
		}

		if (in != null) {
			Bitmap out = in;

			if (rotation != 0 || flip) {
				Matrix matrix = new Matrix();
				matrix.postRotate(rotation);

				if (flip) {
					matrix.postScale(-1, 1);
					matrix.postTranslate(in.getWidth(), 0);
				}

				out = Bitmap.createBitmap(in, 0, 0, in.getWidth(), in.getHeight(), matrix, true);
			}

			byte[] transformedData = toJpegBytes(out);

			in.recycle();
			out.recycle();

			return transformedData;
		}
		return null;
	}

	static byte[] getJpegBytes(@NonNull ImageProxy image, int rotation, boolean flip) throws IOException {
		ImageProxy.PlaneProxy[] planes = image.getPlanes();
		ByteBuffer buffer = planes[0].getBuffer();
		Rect cropRect = shouldCropImage(image) ? image.getCropRect() : null;
		byte[] data = new byte[buffer.capacity()];

		buffer.get(data);

		if (cropRect != null || rotation != 0 || flip) {
			data = transformByteArray(data, cropRect, rotation, flip);
		}

		return data;
	}

	private static byte[] toJpegBytes(@NonNull Bitmap bitmap) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
			throw new IOException("Failed to compress bitmap.");
		}

		return out.toByteArray();
	}

	private static boolean shouldCropImage(@NonNull ImageProxy image) {
		Size sourceSize = new Size(image.getWidth(), image.getHeight());
		Size targetSize = new Size(image.getCropRect().width(), image.getCropRect().height());

		return !targetSize.equals(sourceSize);
	}

	public static @ImageCapture.CaptureMode int getCaptureMode() {
		return MAX_QUALITY_CAMERAS.contains(Build.MODEL) ? ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY : ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY;
	}

	/**
	 * Return true if the internal camera is compatible with the current hardware or operating system
	 * @return
	 */
	public static boolean isInternalCameraSupported() {
		return !BLACKLISTED_CAMERAS.contains(Build.MODEL);
	}
}
