package ch.threema.app.services.avatarcache;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.bumptech.glide.RequestManager;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.data.models.GroupIdentity;

/**
 * The methods of this interface must use a caching mechanism to speed up loading times.
 */
public interface AvatarCacheService {

    /**
     * Get the avatar for the provided identity in high resolution. If an error happens while
     * loading the avatar, the default avatar or null is returned. Note: Do not call this method
     * with the {@link AvatarOptions.DefaultAvatarPolicy#CUSTOM_AVATAR} for identities that do not
     * have a custom avatar. This may cause glide to misbehave :)
     *
     * @param identity if the identity is null, the neutral identity avatar is returned
     * @param options  the options for loading the avatar
     * @return the identity avatar depending on the given choices
     */
    @AnyThread
    @Nullable
    Bitmap getIdentityAvatar(@Nullable String identity, @NonNull AvatarOptions options);

    /**
     * Load the avatar directly into the given image view.
     *
     * @param identity     the identity to use the avatar for
     * @param imageView    the image view
     * @param options      the options for loading the image
     */
    @AnyThread
    void loadIdentityAvatarIntoImage(
        @NonNull String identity,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    );

    /**
     * Get the avatar of the provided local group id in high resolution. If an error happens while
     * loading the avatar, the default avatar or null is returned. Note: Do not call this method
     * with the {@link AvatarOptions.DefaultAvatarPolicy#CUSTOM_AVATAR} for groups that do not have
     * a custom avatar. This may cause glide to misbehave :)
     *
     * @param groupIdentity if null, the neutral group avatar is returned
     * @param options       the options for loading the avatar
     * @return the group avatar depending on the given choices
     */
    @AnyThread
    @Nullable
    Bitmap getGroupAvatar(@Nullable GroupIdentity groupIdentity, @NonNull AvatarOptions options);

    /**
     * Load the avatar directly into the given image view.
     *
     * @param options the options for loading the image
     */
    @AnyThread
    void loadGroupAvatarIntoImage(
        @Nullable GroupIdentity groupIdentity,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    );

    /**
     * Get the avatar of the provided model in low resolution. If an error happens while loading the
     * avatar, the default avatar or null is returned. Distribution list avatars are never cached,
     * as they are computationally cheap to load.
     *
     * @return the distribution list avatar depending on the given model; null if an error occurred
     */
    @AnyThread
    @Nullable
    Bitmap getDistributionListAvatarLow(@Nullable Long distributionListId);

    /**
     * Load the avatar directly into the given image view. Distribution list avatars are never cached,
     * as they are computationally cheap to load.
     */
    @AnyThread
    void loadDistributionListAvatarIntoImage(
        @NonNull Long distributionListId,
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
     * Clears the cache of the for the given identity.
     */
    @AnyThread
    void reset(@NonNull String identity);

    /**
     * Clears the cache of the given group.
     */
    @AnyThread
    void reset(@NonNull GroupIdentity groupIdentity);
}
