/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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
     * Get the avatar of the provided contact model in high resolution. If an error happens while
     * loading the avatar, the default avatar or null is returned. Note: Do not call this method
     * with the {@link AvatarOptions.DefaultAvatarPolicy#CUSTOM_AVATAR} for contacts that do not
     * have a custom avatar. This may cause glide to misbehave :)
     *
     * @param contactModel if the contact model is null, the neutral contact avatar is returned
     * @param options      the options for loading the avatar
     * @return the contact avatar depending on the given choices
     */
    @AnyThread
    @Nullable
    Bitmap getContactAvatar(@Nullable ContactModel contactModel, @NonNull AvatarOptions options);

    /**
     * Load the avatar directly into the given image view.
     *
     * @param contactModel the contact model
     * @param imageView    the image view
     * @param options      the options for loading the image
     */
    @AnyThread
    void loadContactAvatarIntoImage(
        @NonNull ContactModel contactModel,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    );

    /**
     * Get the avatar of the provided group model in high resolution. If an error happens while
     * loading the avatar, the default avatar or null is returned. Note: Do not call this method
     * with the {@link AvatarOptions.DefaultAvatarPolicy#CUSTOM_AVATAR} for groups that do not have
     * a custom avatar. This may cause glide to misbehave :)
     *
     * @param groupModel if the group model is null, the neutral group avatar is returned
     * @param options    the options for loading the avatar
     * @return the contact group depending on the given choices
     */
    @AnyThread
    @Nullable
    Bitmap getGroupAvatar(@Nullable GroupModel groupModel, @NonNull AvatarOptions options);

    /**
     * Load the avatar directly into the given image view.
     *
     * @param groupModel the group model
     * @param imageView  the image view
     * @param options    the options for loading the image
     */
    @AnyThread
    void loadGroupAvatarIntoImage(
        @Nullable GroupModel groupModel,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    );

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
    void loadDistributionListAvatarIntoImage(
        @NonNull DistributionListModel distributionListModel,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    );

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
     * Clears the cache of the contact with the given identity.
     */
    @AnyThread
    void reset(@NonNull String identity);

    /**
     * Clears the cache of the given group model.
     */
    @AnyThread
    void reset(@NonNull GroupModel groupModel);
}
