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

package ch.threema.app.glide

/**
 * The options defined in this class are used to specify which avatar and in which resolution should be loaded for a certain model.
 */
class AvatarOptions private constructor(
    /**
     * Defines whether the avatar should be loaded in high or low resolution
     */
    @JvmField val highRes: Boolean,
    /**
     * Defines whether to load the custom avatar with the default avatar as fallback, the custom
     * avatar without fallback, the default avatar, or the avatar based on the settings
     */
    @JvmField val defaultAvatarPolicy: DefaultAvatarPolicy,
    /**
     * Disable the cache for this query
     */
    @JvmField val disableCache: Boolean,
    /**
     * Use a darkened background
     */
    val darkerBackground: Boolean,
) {
    /**
     * Define whether the custom or the default avatar should be loaded.
     */
    enum class DefaultAvatarPolicy {
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

    override fun equals(other: Any?): Boolean {
        if (other is AvatarOptions) {
            return highRes == other.highRes &&
                defaultAvatarPolicy == other.defaultAvatarPolicy &&
                disableCache == other.disableCache &&
                darkerBackground == other.darkerBackground
        }
        return false
    }

    /**
     * Note that the hashcode is used to cache the elements and therefore only the options that have
     * an effect on the actual avatar drawable are part of the hash.
     *
     * @return the hash code of the options object.
     */
    override fun hashCode(): Int {
        var hashCode = if (highRes) 2 else 3
        hashCode = 31 * hashCode + defaultAvatarPolicy.ordinal
        return hashCode
    }

    override fun toString() =
        "'highRes=$highRes defaultAvatarPolicy=$defaultAvatarPolicy disableCache=$disableCache darkerBackground=$darkerBackground'"

    /**
     * Helper class to build an avatar options object more easily.
     */
    class Builder {
        private var highRes = false
        private var defaultAvatarPolicy = DefaultAvatarPolicy.DEFAULT_AVATAR
        private var disableCache = false
        private var darkerBackground = false

        fun setHighRes(highRes: Boolean) = also {
            this.highRes = highRes
        }

        fun setReturnPolicy(defaultAvatarPolicy: DefaultAvatarPolicy) = also {
            this.defaultAvatarPolicy = defaultAvatarPolicy
        }

        fun disableCache() = also {
            this.disableCache = true
        }

        fun setDarkerBackground(darkerBackground: Boolean) = also {
            this.darkerBackground = darkerBackground
        }

        fun toOptions(): AvatarOptions =
            AvatarOptions(highRes, defaultAvatarPolicy, disableCache, darkerBackground)
    }

    companion object {
        /**
         * Load the avatar in low resolution. If no avatar is found, load the default avatar. This
         * respects the setting where the user defined profile picture should not be shown.
         */
        @JvmField
        val PRESET_DEFAULT_FALLBACK: AvatarOptions = Builder()
            .setReturnPolicy(DefaultAvatarPolicy.DEFAULT_FALLBACK)
            .toOptions()

        /**
         * Load the avatar with default fallback and do not cache it.
         */
        @JvmField
        val PRESET_DEFAULT_AVATAR_NO_CACHE: AvatarOptions = Builder()
            .setReturnPolicy(DefaultAvatarPolicy.DEFAULT_AVATAR)
            .disableCache()
            .toOptions()
    }
}
