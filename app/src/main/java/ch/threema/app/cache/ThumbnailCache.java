/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.cache;

import android.graphics.Bitmap;
import android.util.LruCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.Nullable;

public class ThumbnailCache<T> {
	private static final Logger logger = LoggerFactory.getLogger(ThumbnailCache.class);
	private final Object lock = new Object();
	private final LruCache<T, Bitmap> thumbnails;

	/**
	 * @param maxCacheSizeLimitKb Set this to limit the cache size (in kilobytes).
	 */
	public ThumbnailCache(@Nullable Integer maxCacheSizeLimitKb) {
		// Get max available VM memory, exceeding this amount will throw an
		// OutOfMemory exception. Stored in kilobytes as LruCache takes an
		// int in its constructor.
		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

		final int cacheSizeDefault = Math.min(maxMemory / 16, 1024 * 16); // 16 MB max
		final int cacheSize;
		if (maxCacheSizeLimitKb == null) {
			cacheSize = cacheSizeDefault;
		} else {
			cacheSize = Math.min(cacheSizeDefault, maxCacheSizeLimitKb);
		}
		logger.debug("init size = " + cacheSize);


		this.thumbnails = new LruCache<T, Bitmap>(cacheSize) {

			@Override
			protected int sizeOf(T key, Bitmap bitmap) {
				// The cache size will be measured in kilobytes rather than
				// number of items.
				return bitmap.getByteCount() / 1024;
			}

			@Override
			protected void entryRemoved(boolean evicted, T key, Bitmap oldValue, Bitmap newValue) {
				super.entryRemoved(evicted, key, oldValue, newValue);

				/*
				* We should not recycle bitmaps here. they might still be referenced by an image view
				* but the cache is too small to hold it. instead, we rely on the garbage collector.
				*/

/*				if (evicted) {
					BitmapUtil.recycle(oldValue);
				}
*/			}
		};
	}

	public Bitmap get(T index) {
		synchronized (this.lock) {
			return this.thumbnails.get(index);
		}
	}

	public void set(T index, Bitmap bitmap) {
		synchronized (this.lock) {
			if (index != null && bitmap != null) {
				this.thumbnails.put(index, bitmap);
			}
		}
	}

	public void flush() {
		synchronized (this.lock) {
			logger.debug("evictAll");

			this.thumbnails.evictAll();
		}
	}
}
