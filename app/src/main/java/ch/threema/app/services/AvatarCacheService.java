/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

/**
 * The methods of this interface must use a caching mechanism to speed up loading times.
 */
public interface AvatarCacheService {

	/**
	 * Get the avatar of the provided contact model in high resolution. If an error happens while loading
	 * the avatar, the default avatar or null is returned.
	 *
	 * @param contactModel              if the contact model is null, the default contact avatar is returned
	 * @param defaultOnly               it true, the default contact avatar of this contact is returned (colored - depending on the preference)
	 * @param returnDefaultAvatarIfNone if true, the default avatar is returned if no custom avatar is set for the given contact, otherwise null is returned
	 * @return the contact avatar depending on the given choices
	 */
	@AnyThread
	@Nullable
	Bitmap getContactAvatarHigh(@Nullable ContactModel contactModel, boolean defaultOnly, boolean returnDefaultAvatarIfNone);

	/**
	 * Get the avatar of the provided contact model in low resolution. If an error happens while loading
	 * the avatar, the default avatar or null is returned.
	 *
	 * @param contactModel              if the contact model is null, the default contact avatar is returned
	 * @param defaultOnly               it true, the default contact avatar of this contact is returned (colored - depending on the preference)
	 * @param returnDefaultAvatarIfNone if true, the default avatar is returned if no custom avatar is set for the given contact, otherwise null is returned
	 * @return the contact avatar depending on the given choices
	 */
	@AnyThread
	@Nullable
	Bitmap getContactAvatarLow(@Nullable ContactModel contactModel, boolean defaultOnly, boolean returnDefaultAvatarIfNone);

	/**
	 * Load the avatar directly into the given image view.
	 *
	 * @param contactModel the contact model
	 * @param imageView    the image view
	 * @param options      the options for loading the image
	 */
	@AnyThread
	void loadContactAvatarIntoImage(@NonNull ContactModel contactModel, @NonNull ImageView imageView, AvatarOptions options);

	/**
	 * Get the avatar of the provided group model in high resolution. If an error happens while loading
	 * the avatar, the default avatar or null is returned.
	 *
	 * @param groupModel                if the group model is null, the default group avatar is returned
	 * @param defaultOnly               it true, the default group avatar of this group is returned (colored - depending on the preference)
	 * @param returnDefaultAvatarIfNone if true, the default avatar is returned if no custom avatar is set for the given group, otherwise null is returned
	 * @return the contact group depending on the given choices
	 */
	@AnyThread
	@Nullable
	Bitmap getGroupAvatarHigh(@Nullable GroupModel groupModel, boolean defaultOnly, boolean returnDefaultAvatarIfNone);

	/**
	 * Get the avatar of the provided group model in low resolution. If an error happens while loading
	 * the avatar, the default avatar or null is returned.
	 *
	 * @param groupModel                if the group model is null, the default group avatar is returned
	 * @param defaultOnly               it true, the default group avatar of this group is returned (colored - depending on the preference)
	 * @param returnDefaultAvatarIfNone if true, the default avatar is returned if no custom avatar is set for the given group, otherwise null is returned
	 * @return the contact group depending on the given choices; might be null
	 */
	@AnyThread
	@Nullable
	Bitmap getGroupAvatarLow(@Nullable GroupModel groupModel, boolean defaultOnly, boolean returnDefaultAvatarIfNone);

	/**
	 * Get the default neutral group avatar (default color).
	 *
	 * @param highResolution if true, the high resolution neutral avatar is returned
	 * @return the neutral group avatar; null if an error occurred
	 */
	@AnyThread
	@Nullable
	Bitmap getGroupAvatarNeutral(boolean highResolution);

	/**
	 * Load the avatar directly into the given image view.
	 *
	 * @param groupModel  the group model
	 * @param imageView   the image view
	 * @param options     the options for loading the image
	 */
	@AnyThread
	void loadGroupAvatarIntoImage(@Nullable GroupModel groupModel, @NonNull ImageView imageView, AvatarOptions options);

	/**
	 * Get the avatar of the provided model in low resolution. If an error happens while loading the
	 * avatar, the default avatar or null is returned. Distribution list avatars are never cached,
	 * as they are computationally cheap to load.
	 *
	 * @param distributionListModel if the model is null, the distribution list avatar is returned in default color
	 * @return the distribution list avatar depending on the given model; null if an error occurred
	 */
	@AnyThread
	@Nullable
	Bitmap getDistributionListAvatarLow(@Nullable DistributionListModel distributionListModel);

	/**
	 * Load the avatar directly into the given image view. Distribution list avatars are never cached,
	 * as they are computationally cheap to load.
	 *
	 * @param distributionListModel the distribution list model
	 * @param imageView             the image view
	 * @param options               the options for loading the image
	 */
	@AnyThread
	void loadDistributionListAvatarIntoImage(@NonNull DistributionListModel distributionListModel, @NonNull ImageView imageView, AvatarOptions options);

	/**
	 * Clears the cache. This should be called if many (or all) avatars change, e.g., when changing the default avatar color preference.
	 */
	@AnyThread
	void clear();

	/**
	 * Clears the cache of the given contact model.
	 */
	@AnyThread
	void reset(@NonNull ContactModel contactModel);

	/**
	 * Clears the cache of the given group model.
	 */
	@AnyThread
	void reset(@NonNull GroupModel groupModel);
}
