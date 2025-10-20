/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The options defined in this class are used to specify which avatar and in which resolution should be loaded for a certain model.
 */
public class AvatarOptions {

    /**
     * Define whether the custom or the default avatar should be loaded.
     */
    public enum DefaultAvatarPolicy {
        /**
         * Try to load the custom avatar. If no custom avatar available, then return the default
         * avatar instead of null.
         */
        DEFAULT_FALLBACK,
        /**
         * Load the custom avatar. If no custom avatar is set, then return null. Note that a custom
         * avatar can either be a contact or user defined profile picture.
         */
        CUSTOM_AVATAR,
        /**
         * Load the default avatar even if a custom avatar would be available.
         */
        DEFAULT_AVATAR,
    }

    /**
     * Load the avatar in low resolution. If no avatar is found, load the default avatar. This
     * respects the setting where the user defined profile picture should not be shown.
     */
    public static final AvatarOptions PRESET_DEFAULT_FALLBACK = new Builder()
        .setReturnPolicy(DefaultAvatarPolicy.DEFAULT_FALLBACK)
        .toOptions();

    /**
     * Load the avatar with default fallback and do not cache it.
     */
    public static final AvatarOptions PRESET_DEFAULT_AVATAR_NO_CACHE = new Builder()
        .setReturnPolicy(DefaultAvatarPolicy.DEFAULT_AVATAR)
        .disableCache()
        .toOptions();

    /**
     * Defines whether the avatar should be loaded in high or low resolution
     */
    public final boolean highRes;

    /**
     * Defines whether to load the custom avatar with the default avatar as fallback, the custom
     * avatar without fallback, the default avatar, or the avatar based on the settings
     */
    @NonNull
    public final DefaultAvatarPolicy defaultAvatarPolicy;

    /**
     * Disable the cache for this query
     */
    public final boolean disableCache;

    /**
     * Use a darkened background
     */
    public final boolean darkerBackground;

    private AvatarOptions(boolean highRes, @NonNull DefaultAvatarPolicy defaultAvatarPolicy, boolean disableCache, boolean darkerBackground) {
        this.highRes = highRes;
        this.defaultAvatarPolicy = defaultAvatarPolicy;
        this.disableCache = disableCache;
        this.darkerBackground = darkerBackground;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof AvatarOptions) {
            AvatarOptions other = (AvatarOptions) obj;
            return this.highRes == other.highRes
                && this.defaultAvatarPolicy == other.defaultAvatarPolicy
                && this.disableCache == other.disableCache
                && this.darkerBackground == other.darkerBackground;
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
        hashCode = 31 * hashCode + defaultAvatarPolicy.ordinal();
        return hashCode;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(
            "'highRes=%s defaultAvatarPolicy=%s disableCache=%s darkerBackground=%s'",
            this.highRes,
            this.defaultAvatarPolicy,
            this.disableCache,
            this.darkerBackground
        );
    }

    /**
     * Helper class to build an avatar options object more easily.
     */
    public static class Builder {
        private boolean highRes = false;
        private @NonNull DefaultAvatarPolicy defaultAvatarPolicy = DefaultAvatarPolicy.DEFAULT_AVATAR;
        private boolean disableCache = false;
        private boolean darkerBackground = false;

        public Builder setHighRes(boolean highRes) {
            this.highRes = highRes;
            return this;
        }

        public Builder setReturnPolicy(@NonNull DefaultAvatarPolicy defaultAvatarPolicy) {
            this.defaultAvatarPolicy = defaultAvatarPolicy;
            return this;
        }

        public Builder disableCache() {
            this.disableCache = true;
            return this;
        }

        public Builder setDarkerBackground(boolean darkerBackground) {
            this.darkerBackground = darkerBackground;
            return this;
        }

        public AvatarOptions toOptions() {
            return new AvatarOptions(highRes, defaultAvatarPolicy, disableCache, darkerBackground);
        }
    }

}
