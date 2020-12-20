/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020 Threema GmbH
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

package ch.threema.app.video;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.LruCache;

import org.msgpack.core.annotations.Nullable;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

/**
 * A simple LRU cache for VideoEditView timeline thumbnails
 * This cache needs to be cleared on each configuration change as thumbnail count and size may vary
 */
public class VideoTimelineCache {
	private static final int CACHE_MAX_SIZE = 128;

	private final LruCache<Pair<Uri, Integer>, Bitmap> cache;

	private static VideoTimelineCache sInstance = null;

	public static synchronized VideoTimelineCache getInstance() {
		if (sInstance == null) {
			sInstance = new VideoTimelineCache();
		}
		return sInstance;
	}

	public VideoTimelineCache() {
		this.cache = new LruCache<>(CACHE_MAX_SIZE);
	}

	public @Nullable Bitmap get(@NonNull Uri uri, int thumbNo) {
		synchronized (this.cache) {
			return this.cache.get(new Pair<>(uri, thumbNo));
		}
	}

	public void set(@NonNull Uri uri, int thumbNo, Bitmap bitmap) {
		synchronized (this.cache) {
			this.cache.put(new Pair<>(uri, thumbNo), bitmap);
		}
	}

	public void flush() {
		synchronized (this.cache) {
			this.cache.evictAll();
		}
	}
}
