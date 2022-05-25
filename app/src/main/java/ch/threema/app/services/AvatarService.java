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

public interface AvatarService<M extends ReceiverModel> {

	/**
	 * Get the bitmap of the given model. This method can be called from any thread.
	 *
	 * @param model   the model of which the avatar is returned
	 * @param highRes if true, the high resolution avatar is returned, the low resolution avatar otherwise
	 * @return the avatar of the given model in high or low resolution
	 */
	@Nullable
	@AnyThread
	Bitmap getAvatar(@Nullable M model, boolean highRes);

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
	Bitmap getAvatar(@Nullable M model, boolean highResolution, boolean returnDefaultAvatarIfNone);

	/**
	 * Load the avatar of the given model into the provided image view. The avatar bitmap is loaded
	 * asynchronously and the default avatar is shown as a placeholder.
	 *
	 * @param model       the conversation model
	 * @param imageView   the image view
	 * @param options     the options for loading the image
	 */
	@AnyThread
	void loadAvatarIntoImage(@NonNull M model, @NonNull ImageView imageView, AvatarOptions options);

	/**
	 * Get the default avatar even if a custom avatar is set for the given model.
	 *
	 * @param model          if the model is null, the default color is used for the avatar
	 * @param highResolution if true, the high resolution avatar is loaded
	 * @return the default avatar for the model type M
	 */
	@AnyThread
	@Nullable
	Bitmap getDefaultAvatar(@Nullable M model, boolean highResolution);

	/**
	 * Get the default avatar with the default color.
	 *
	 * @param highResolution if true, the high resolution avatar is loaded
	 * @return the default avatar
	 */
	@AnyThread
	@Nullable
	Bitmap getNeutralAvatar(boolean highResolution);

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
