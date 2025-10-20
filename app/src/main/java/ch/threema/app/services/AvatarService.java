/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.app.services;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.bumptech.glide.RequestManager;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.glide.AvatarOptions;

import static ch.threema.app.glide.AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR;
import static ch.threema.app.glide.AvatarOptions.DefaultAvatarPolicy.DEFAULT_AVATAR;
import static ch.threema.app.glide.AvatarOptions.DefaultAvatarPolicy.DEFAULT_FALLBACK;

public interface AvatarService<S> {

    /**
     * Get the avatar with the given avatar options of the given subject as bitmap.
     *
     * @param subject the subject for which the avatar should be returned
     * @param options the options for loading the avatar
     * @return the avatar of the given subject
     */
    @Nullable
    @AnyThread
    Bitmap getAvatar(@Nullable S subject, @NonNull AvatarOptions options);


    /**
     * Get the avatar of the given subject as bitmap.
     *
     * @param subject the subject for which the avatar should be returned
     * @param highRes if true, the high resolution avatar is returned, the low resolution avatar otherwise
     * @return the avatar of the given subject in high or low resolution
     */
    @Nullable
    @AnyThread
    default Bitmap getAvatar(@Nullable S subject, boolean highRes) {
        return getAvatar(subject, highRes, true);
    }

    /**
     * Get the avatar of the given subject.
     *
     * @param subject                   the subject for which the avatar should be returned
     * @param highResolution            if true, the high resolution avatar is loaded
     * @param returnDefaultAvatarIfNone if true, the default avatar is returned if no custom avatar is set for the given subject, otherwise null is returned
     * @return the avatar of the given subject
     */
    @AnyThread
    @Nullable
    default Bitmap getAvatar(@Nullable S subject, boolean highResolution, boolean returnDefaultAvatarIfNone) {
        return getAvatar(subject, highResolution, returnDefaultAvatarIfNone, false);
    }

    /**
     * Get the avatar of the given subject.
     *
     * @param subject                   if the subject is null, the default avatar is returned
     * @param highResolution            if true, the high resolution avatar is loaded
     * @param returnDefaultAvatarIfNone if true, the default avatar is returned if no custom avatar is set for the given subject, otherwise null is returned
     * @param darkerBackground          if true, the background will be darker than white, otherwise it will be white
     * @return the avatar of the given subject
     */
    @AnyThread
    @Nullable
    default Bitmap getAvatar(@Nullable S subject, boolean highResolution, boolean returnDefaultAvatarIfNone, boolean darkerBackground) {
        return getAvatar(subject, new AvatarOptions.Builder()
            .setHighRes(highResolution)
            .setReturnPolicy(returnDefaultAvatarIfNone ? DEFAULT_FALLBACK : CUSTOM_AVATAR)
            .setDarkerBackground(darkerBackground)
            .toOptions()
        );
    }

    /**
     * Load the avatar of the given subject into the provided image view. The avatar bitmap is loaded
     * asynchronously and the default avatar is shown as a placeholder.
     *
     * @param subject     the conversation subject
     * @param imageView the image view
     * @param options   the options for loading the image
     */
    @AnyThread
    void loadAvatarIntoImage(
        @NonNull S subject,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    );

    /**
     * Get the default avatar even if a custom avatar is set for the given subject.
     *
     * @param subject          if the subject is null, the default color is used for the avatar
     * @param highResolution   if true, the high resolution avatar is loaded
     * @param darkerBackground if true, the background will be darker than white, otherwise it will be white
     * @return the default avatar for the subject
     */
    @AnyThread
    @Nullable
    default Bitmap getDefaultAvatar(@Nullable S subject, boolean highResolution, boolean darkerBackground) {
        return getAvatar(subject, new AvatarOptions.Builder()
            .setHighRes(highResolution)
            .setReturnPolicy(DEFAULT_AVATAR)
            .setDarkerBackground(darkerBackground)
            .toOptions()
        );
    }

    /**
     * Get the neutral avatar with the default color.
     *
     * @param options the options for loading the avatar
     * @return the neutral avatar
     */
    @AnyThread
    @Nullable
    default Bitmap getNeutralAvatar(@Nullable AvatarOptions options) {
        return getAvatar(null, options != null ? options : AvatarOptions.PRESET_DEFAULT_AVATAR_NO_CACHE);
    }

    /**
     * Get the color of the default avatar.
     *
     * @param subject the subject for which the avatar color is to be determined.
     *                If null, the default color is returned
     * @return the avatar color of the given subject, or the default color if the subject is null
     */
    @AnyThread
    @ColorInt
    int getAvatarColor(@Nullable S subject);
}
