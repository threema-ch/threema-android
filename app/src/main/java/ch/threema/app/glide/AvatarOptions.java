/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.glide;

import androidx.annotation.Nullable;

/**
 * The options defined in this class are used to specify which avatar and in which resolution should be loaded for a certain model.
 */
public class AvatarOptions {

	/**
	 * Load the avatar in low resolution. If no avatar is found, load the default avatar.
	 */
	public static final AvatarOptions DEFAULT = new Builder().toOptions();

	/**
	 * Load the avatar with default options but do not cache it.
	 */
	public static final AvatarOptions DEFAULT_NO_CACHE = new Builder().disableCache().toOptions();

	/**
	 * Defines whether the avatar should be loaded in high or low resolution
	 */
	public final boolean highRes;
	/**
	 * Defines whether the default avatar should be loaded (even if a custom avatar is available)
	 */
	public final boolean defaultOnly;
	/**
	 * Defines whether the the default avatar or null should be returned if no custom avatar is found
	 */
	public final boolean returnDefaultAvatarIfNone;
	/**
	 * Disable the cache for this query
	 */
	public final boolean disableCache;

	private AvatarOptions(boolean highRes, boolean defaultOnly, boolean returnDefaultAvatarIfNone, boolean disableCache) {
		this.highRes = highRes;
		this.defaultOnly = defaultOnly;
		this.returnDefaultAvatarIfNone = returnDefaultAvatarIfNone;
		this.disableCache = disableCache;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj instanceof AvatarOptions) {
			AvatarOptions other = (AvatarOptions) obj;
			return this.highRes == other.highRes && this.defaultOnly == other.defaultOnly && this.returnDefaultAvatarIfNone == other.returnDefaultAvatarIfNone && this.disableCache == other.disableCache;
		}

		return false;
	}

	/**
	 * Note that the hashcode is used to cache the elements and therefore only the options that have
	 * an effect of the actual avatar drawable are part of the hash.
	 *
	 * @return the hash code of the options object.
	 */
	@Override
	public int hashCode() {
		int hashCode = highRes ? 2 : 3;
		hashCode = 31 * hashCode + (defaultOnly ? 1 : 2);
		hashCode = 31 * hashCode + (returnDefaultAvatarIfNone ? 1 : 2);
		return hashCode;
	}

	/**
	 * Helper class to build an avatar options object more easily.
	 */
	public static class Builder {
		private boolean highRes = false;
		private boolean defaultOnly = false;
		private boolean returnDefaultAvatarIfNone = true;
		private boolean disableCache = false;

		public Builder setHighRes(boolean highRes) {
			this.highRes = highRes;
			return this;
		}

		public Builder setDefaultOnly(boolean defaultOnly) {
			this.defaultOnly = defaultOnly;
			return this;
		}

		public Builder setReturnDefaultAvatarIfNone(boolean returnDefaultAvatarIfNone) {
			this.returnDefaultAvatarIfNone = returnDefaultAvatarIfNone;
			return this;
		}

		public Builder disableCache() {
			this.disableCache = true;
			return this;
		}

		public AvatarOptions toOptions() {
			return new AvatarOptions(highRes, defaultOnly, returnDefaultAvatarIfNone, disableCache);
		}
	}

}
