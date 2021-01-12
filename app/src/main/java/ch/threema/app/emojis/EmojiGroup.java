/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.emojis;

import android.util.SparseArray;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public class EmojiGroup {
	private final @Nullable String assetPathPrefix;
	private final @Nullable String assetPathSuffix;
	private final @DrawableRes int groupIcon;
	private final @StringRes int groupName;
	private final @NonNull SparseArray<EmojiSpritemapBitmap> spritemapBitmaps = new SparseArray<>();

	EmojiGroup(@Nullable String assetPathPrefix, @Nullable String assetPathSuffix,
	           @DrawableRes int groupIcon, @StringRes int groupName) {
		this.assetPathPrefix = assetPathPrefix;
		this.assetPathSuffix = assetPathSuffix;
		this.groupIcon = groupIcon;
		this.groupName = groupName;
	}

	public @DrawableRes int getGroupIcon() {
		return groupIcon;
	}

	public @StringRes int getGroupName() {
		return groupName;
	}

	@Nullable
	public String getAssetPath(int spritemapId) {
		if (this.assetPathPrefix == null || this.assetPathSuffix == null) {
			return null;
		}
		return this.assetPathPrefix + spritemapId + this.assetPathSuffix;
	}

	@Nullable
	public EmojiSpritemapBitmap getSpritemapBitmap(int spritemapId) {
		return this.spritemapBitmaps.get(spritemapId);
	}

	public boolean hasSpritemapBitmap(int spritemapId) {
		return this.spritemapBitmaps.indexOfKey(spritemapId) >= 0;
	}

	public void setSpritemapBitmap(int spritemapId, @NonNull EmojiSpritemapBitmap spritemapBitmap) {
		this.spritemapBitmaps.put(spritemapId, spritemapBitmap);
	}

}
