/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

package ch.threema.app.webclient.utils;

import android.graphics.Bitmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

/**
 * Functions related to the webclient.
 */
@AnyThread
public class ThumbnailUtils {
	private static final Logger logger = LoggerFactory.getLogger(ThumbnailUtils.class);

	public static class Size {
		public int width;
		public int height;
		public Size(int width, int height) {
			this.width = width;
			this.height = height;
		}
	}

	/**
	 * Calculate new dimensions, resize down proportionally.
	 */
	@NonNull
	public static Size resizeProportionally(int width, int height, int maxSidePx) {
		if (width > maxSidePx || height > maxSidePx) {
			int largerSide = Math.max(width, height);
			double scaleFactor = (double) maxSidePx / largerSide;
			int newWidth = (int)Math.round((double) width * scaleFactor);
			int newHeight = (int)Math.round((double) height * scaleFactor);
			return new Size(newWidth, newHeight);
		}
		return new Size(width, height);
	}

	/**
	 * Make sure that no side is larger than maxSize,
	 * resizing if necessary.
	 */
	public static Bitmap resize(@NonNull final Bitmap thumbnail, int maxSidePx) {
		int w = thumbnail.getWidth();
		int h = thumbnail.getHeight();

		if (w > maxSidePx || h > maxSidePx) {
			Size newSize = ThumbnailUtils.resizeProportionally(w, h, maxSidePx);

			try {
				return Bitmap.createScaledBitmap(thumbnail, newSize.width, newSize.height, true);
			} catch (Exception x) {
				logger.error("Exception", x);
			}
		}

		return thumbnail;
	}
}
