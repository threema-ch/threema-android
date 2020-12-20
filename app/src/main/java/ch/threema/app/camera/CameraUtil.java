/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2020 Threema GmbH
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
import androidx.annotation.RequiresApi;
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
/*		// Pixel 4
		add("Pixel 4");
		add("Pixel 4 XL");

		// Huawei Mate 10
		add("ALP-L29");
		add("ALP-L09");
		add("ALP-AL00");

		// Huawei Mate 10 Pro
		add("BLA-L29");
		add("BLA-L09");
		add("BLA-AL00");
		add("BLA-A09");

		// Huawei Mate 20
		add("HMA-L29");
		add("HMA-L09");
		add("HMA-LX9");
		add("HMA-AL00");

		// Huawei Mate 20 Pro
		add("LYA-L09");
		add("LYA-L29");
		add("LYA-AL00");
		add("LYA-AL10");
		add("LYA-TL00");
		add("LYA-L0C");

		// Huawei P20
		add("EML-L29C");
		add("EML-L09C");
		add("EML-AL00");
		add("EML-TL00");
		add("EML-L29");
		add("EML-L09");

		// Huawei P20 Pro
		add("CLT-L29C");
		add("CLT-L29");
		add("CLT-L09C");
		add("CLT-L09");
		add("CLT-AL00");
		add("CLT-AL01");
		add("CLT-TL01");
		add("CLT-AL00L");
		add("CLT-L04");
		add("HW-01K");

		// Huawei P30
		add("ELE-L29");
		add("ELE-L09");
		add("ELE-AL00");
		add("ELE-TL00");
		add("ELE-L04");

		// Huawei P30 Pro
		add("VOG-L29");
		add("VOG-L09");
		add("VOG-AL00");
		add("VOG-TL00");
		add("VOG-L04");
		add("VOG-AL10");

		// Huawei Honor 10
		add("COL-AL10");
		add("COL-L29");
		add("COL-L19");

		// Huawei Honor 10 View
		add("BKL-AL20");
		add("BKL-L04");
		add("BKL-L09");
		add("BKL-AL00");

		// OnePlus 3T
		add("A3010");

		// Sony Xperia 5
		add("J9210");
*/	}};

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private static boolean shouldCropImage(@NonNull ImageProxy image) {
		Size sourceSize = new Size(image.getWidth(), image.getHeight());
		Size targetSize = new Size(image.getCropRect().width(), image.getCropRect().height());

		return !targetSize.equals(sourceSize);
	}

	public static @ImageCapture.CaptureMode int getCaptureMode() {
		return MAX_QUALITY_CAMERAS.contains(Build.MODEL) ? ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY : ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY;
	}

	public static boolean isBlacklistedCamera() {
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || BLACKLISTED_CAMERAS.contains(Build.MODEL);
	}
}
