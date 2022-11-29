/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.storage.models.ReceiverModel;

import static ch.threema.app.glide.AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR;
import static ch.threema.app.glide.AvatarOptions.DefaultAvatarPolicy.DEFAULT_AVATAR;
import static ch.threema.app.glide.AvatarOptions.DefaultAvatarPolicy.DEFAULT_FALLBACK;

public interface AvatarService<M extends ReceiverModel> {

	/**
	 * Get the avatar with the given avatar options of the given model as bitmap.
	 *
	 * @param model   the model of which the avatar is returned
	 * @param options the options for loading the avatar
	 * @return the avatar of the given model
	 */
	@Nullable
	@AnyThread
	Bitmap getAvatar(@Nullable M model, @NonNull AvatarOptions options);


	/**
	 * Get the bitmap of the given model. This method can be called from any thread.
	 *
	 * @param model   the model of which the avatar is returned
	 * @param highRes if true, the high resolution avatar is returned, the low resolution avatar otherwise
	 * @return the avatar of the given model in high or low resolution
	 */
	@Nullable
	@AnyThread
	default Bitmap getAvatar(@Nullable M model, boolean highRes) {
		return getAvatar(model, highRes, true);
	}

	/**
	 * Get the avatar of the given model.
	 *
	 * @param model                     if the model is null, the default avatar is returned
	 * @param highResolution            if true, the high resolution avatar is loaded
	 * @param returnDefaultAvatarIfNone if true, the default avatar is returned if no custom avatar is set for the given model, otherwise null is returned
	 * @return the avatar of the given model
	 */
	@AnyThread
	@Nullable
	default Bitmap getAvatar(@Nullable M model, boolean highResolution, boolean returnDefaultAvatarIfNone) {
		return getAvatar(model, highResolution, returnDefaultAvatarIfNone, false);
	}

	/**
	 * Get the avatar of the given model.
	 *
	 * @param model                     if the model is null, the default avatar is returned
	 * @param highResolution            if true, the high resolution avatar is loaded
	 * @param returnDefaultAvatarIfNone if true, the default avatar is returned if no custom avatar is set for the given model, otherwise null is returned
	 * @param darkerBackground if true, the background will be darker than white, otherwise it will be white
	 * @return the avatar of the given model
	 */
	@AnyThread
	@Nullable
	default Bitmap getAvatar(@Nullable M model, boolean highResolution, boolean returnDefaultAvatarIfNone, boolean darkerBackground) {
		return getAvatar(model, new AvatarOptions.Builder()
			.setHighRes(highResolution)
			.setReturnPolicy(returnDefaultAvatarIfNone ? DEFAULT_FALLBACK : CUSTOM_AVATAR)
			.setDarkerBackground(darkerBackground)
			.toOptions()
		);
	}

	/**
	 * Load the avatar of the given model into the provided image view. The avatar bitmap is loaded
	 * asynchronously and the default avatar is shown as a placeholder.
	 *
	 * @param model       the conversation model
	 * @param imageView   the image view
	 * @param options     the options for loading the image
	 */
	@AnyThread
	void loadAvatarIntoImage(@NonNull M model, @NonNull ImageView imageView, @NonNull AvatarOptions options);

	/**
	 * Get the default avatar even if a custom avatar is set for the given model.
	 *
	 * @param model            if the model is null, the default color is used for the avatar
	 * @param highResolution   if true, the high resolution avatar is loaded
	 * @param darkerBackground if true, the background will be darker than white, otherwise it will be white
	 * @return the default avatar for the model type M
	 */
	@AnyThread
	@Nullable
	default Bitmap getDefaultAvatar(@Nullable M model, boolean highResolution, boolean darkerBackground) {
		return getAvatar(model, new AvatarOptions.Builder()
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
	 * Delete the cache of the given model
	 */
	void clearAvatarCache(@NonNull M model);

	/**
	 * Get the color of the avatar. This method considers the "isDefaultContactPictureColored" setting.
	 *
	 * @param model the model where the avatar color is determined. If null, the default color is returned
	 * @return the color of the given model, or the default color if the model is null
	 */
	@AnyThread
	@ColorInt
	int getAvatarColor(@Nullable M model);
}
