package ch.threema.app.services.avatarcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;
import com.bumptech.glide.signature.ObjectKey;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import ch.threema.app.R;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.RuntimeUtil;

import static ch.threema.android.ThreadUtilKt.isMainThread;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.models.GroupIdentity;
import ch.threema.data.datatypes.IdColor;

import java.util.function.Supplier;

/**
 * The AvatarCacheService manages the cached loading of avatars. If an avatar is modified or deleted, it must be reset in this class.
 */
final public class AvatarCacheServiceImpl implements AvatarCacheService {

    private static final Logger logger = getThreemaLogger("AvatarCacheServiceImpl");

    /**
     * The mapping of identities to "states". If the number is changed, the next time the avatar of this identity is accessed, it will be loaded
     * from the database.To invalidate all identity caches clear this map.
     */
    private final Map<String, Long> identityAvatarStates = new HashMap<>();

    /**
     * The mapping of group identities to "states". If the number is changed, the next time the group avatar is accessed, it will be loaded
     * from the database. To invalidate all group caches clear this map.
     */
    private final Map<GroupIdentity, Long> groupAvatarStates = new HashMap<>();

    @NonNull
    private final Context appContext;

    private final VectorDrawableCompat identityPlaceholder;
    private final VectorDrawableCompat groupPlaceholder;
    private final VectorDrawableCompat distributionListPlaceholder;

    private final DrawableCrossFadeFactory factory = new DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build();

    private final int avatarSizeSmall;

    public AvatarCacheServiceImpl(@NonNull Context appContext) {
        this.appContext = appContext;

        this.identityPlaceholder = VectorDrawableCompat.create(appContext.getResources(), R.drawable.ic_contact, null);
        this.groupPlaceholder = VectorDrawableCompat.create(appContext.getResources(), R.drawable.ic_group, null);
        this.distributionListPlaceholder = VectorDrawableCompat.create(appContext.getResources(), R.drawable.ic_distribution_list, null);
        this.avatarSizeSmall = appContext.getResources().getDimensionPixelSize(R.dimen.avatar_size_small);

        // Use dark theme default gray for placeholder
        int color = IdColor.invalid().getColorDark();
        if (identityPlaceholder != null) {
            AvatarConverterUtil.getAvatarBitmap(identityPlaceholder, color, avatarSizeSmall);
        }
        if (groupPlaceholder != null) {
            AvatarConverterUtil.getAvatarBitmap(groupPlaceholder, color, avatarSizeSmall);
        }
        if (distributionListPlaceholder != null) {
            AvatarConverterUtil.getAvatarBitmap(distributionListPlaceholder, color, avatarSizeSmall);
        }
    }

    @AnyThread
    @Override
    public @Nullable Bitmap getIdentityAvatar(@Nullable final String identity, @NonNull AvatarOptions options) {
        return getBitmapInWorkerThread(
            () -> this.getAvatar(identity, options)
        );
    }

    @AnyThread
    @Override
    public void loadIdentityAvatarIntoImage(
        @NonNull String identity,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    ) {
        loadBitmap(new IdentityAvatarConfig(identity, options), identityPlaceholder, imageView, requestManager);
    }

    @AnyThread
    @Override
    public @Nullable Bitmap getGroupAvatar(
        @Nullable GroupIdentity groupIdentity,
        @NonNull AvatarOptions options
    ) {
        return getBitmapInWorkerThread(
            () -> getAvatar(groupIdentity, options)
        );
    }

    @AnyThread
    @Override
    public void loadGroupAvatarIntoImage(
        @Nullable GroupIdentity groupIdentity,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    ) {
        loadBitmap(new GroupAvatarConfig(groupIdentity, options), groupPlaceholder, imageView, requestManager);
    }

    @AnyThread
    @Override
    public @Nullable Bitmap getDistributionListAvatarLow(@Nullable Long distributionListId) {
        return getBitmapInWorkerThread(
            () -> getAvatar(distributionListId)
        );
    }

    @AnyThread
    @Override
    public void loadDistributionListAvatarIntoImage(
        @NonNull Long distributionListId,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    ) {
        loadBitmap(new DistributionListAvatarConfig(distributionListId, options), distributionListPlaceholder, imageView, requestManager);
    }

    @Override
    public void reset(@NonNull String identity) {
        synchronized (this.identityAvatarStates) {
            this.identityAvatarStates.put(identity, System.currentTimeMillis());
        }
    }

    @AnyThread
    @Override
    public void reset(@NonNull GroupIdentity groupIdentity) {
        synchronized (this.groupAvatarStates) {
            this.groupAvatarStates.put(groupIdentity, System.currentTimeMillis());
        }
    }

    @AnyThread
    @Override
    public void clear() {
        synchronized (this.identityAvatarStates) {
            identityAvatarStates.clear();
        }
        synchronized (this.groupAvatarStates) {
            groupAvatarStates.clear();
        }
        RuntimeUtil.runOnUiThread(() -> Glide.get(appContext).clearMemory());
    }

    @WorkerThread
    private @Nullable Bitmap getAvatar(@Nullable String identity, AvatarOptions options) {
        return getBitmap(new IdentityAvatarConfig(identity, options));
    }

    @WorkerThread
    private @Nullable Bitmap getAvatar(@Nullable GroupIdentity groupIdentity, AvatarOptions options) {
        return getBitmap(new GroupAvatarConfig(groupIdentity, options));
    }

    @WorkerThread
    private @Nullable Bitmap getAvatar(@Nullable Long distributionListId) {
        return getBitmap(new DistributionListAvatarConfig(distributionListId, AvatarOptions.PRESET_DEFAULT_AVATAR_NO_CACHE));
    }

    @WorkerThread
    private @Nullable <S> Bitmap getBitmap(@NonNull AvatarConfig<S> config) {
        logger.debug("Getting avatar for config {} with state {}", config.options, config.state);
        try {
            RequestBuilder<Bitmap> requestBuilder = Glide.with(appContext)
                .asBitmap()
                .load(config)
                .diskCacheStrategy(DiskCacheStrategy.NONE);
            if (config.subject == null || config.options.disableCache || config.options.highRes) {
                requestBuilder = requestBuilder.skipMemoryCache(true);
            }
            if (!config.options.highRes) {
                requestBuilder = requestBuilder
                    .signature(new ObjectKey(config.state))
                    .override(avatarSizeSmall);
            }
            return requestBuilder.submit().get();
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error while getting avatar bitmap for configuration {}", config, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    @AnyThread
    private <S> void loadBitmap(
        @NonNull AvatarConfig<S> config,
        @Nullable Drawable placeholder,
        @NonNull ImageView view,
        @NonNull RequestManager requestManager
    ) {
        logger.debug("Loading avatar for config {} with state = {}", config.options, config.state);
        try {
            RequestBuilder<Bitmap> requestBuilder = requestManager
                .asBitmap()
                .load(config)
                .placeholder(placeholder)
                .transition(BitmapTransitionOptions.withCrossFade(factory))
                .diskCacheStrategy(DiskCacheStrategy.NONE);
            if (config.subject == null || config.options.disableCache || config.options.highRes) {
                requestBuilder = requestBuilder.skipMemoryCache(true);
            }
            if (!config.options.highRes) {
                requestBuilder = requestBuilder
                    .signature(new ObjectKey(config.state))
                    .override(avatarSizeSmall);
            }
            requestBuilder.into(view);
        } catch (Exception e) {
            logger.debug("Glide failure", e);
        }
    }

    /**
     * This class stores information to access an avatar belonging to a threema identity. Every object of this class
     * with the same hashcode references the same image and can therefore be cached by glide.
     */
    public class IdentityAvatarConfig extends AvatarConfig<String> {

        private IdentityAvatarConfig(
            @Nullable String identity,
            @NonNull AvatarOptions options
        ) {
            super(identity, options);
        }

        @Override
        protected int getSubjectHashCode() {
            return (subject != null) ? subject.hashCode() : -1;
        }

        @Override
        public long getAvatarState() {
            if (subject == null) {
                // we don't use the cache for default avatars
                return System.currentTimeMillis();
            }
            synchronized (identityAvatarStates) {
                final @Nullable Long state = identityAvatarStates.get(subject);
                if (state != null) {
                    return state;
                }
                long newState = System.currentTimeMillis();
                identityAvatarStates.put(subject, newState);
                return newState;
            }
        }

        @NonNull
        @Override
        protected String getSubjectDebugString() {
            return subject != null ? subject : "null";
        }
    }

    /**
     * This class stores information about the access of a group avatar. Every object of this class
     * with the same hashcode references the same image and can therefore be cached by glide.
     */
    public class GroupAvatarConfig extends AvatarConfig<GroupIdentity> {
        private GroupAvatarConfig(
            @Nullable GroupIdentity groupIdentity,
            @NonNull AvatarOptions options
        ) {
            super(groupIdentity, options);
        }

        @Override
        protected int getSubjectHashCode() {
            return (subject != null) ? subject.hashCode() : -2;
        }

        @Override
        protected long getAvatarState() {
            if (subject == null) {
                // If the groupIdentity is null, then a default avatar is wanted and therefore it does not
                // make a big difference if it is loaded from cache or not
                return 0;
            }
            synchronized (groupAvatarStates) {
                final @Nullable Long state = groupAvatarStates.get(subject);
                if (state != null) {
                    return state;
                }
                final long newState = System.currentTimeMillis();
                groupAvatarStates.put(subject, newState);
                return newState;
            }
        }

        @NonNull
        @Override
        protected String getSubjectDebugString() {
            return subject != null ? subject.toString() : "null";
        }
    }

    /**
     * This class stores information about the access of a distribution list avatar. Every object of
     * this class with the same hashcode references the same image and can therefore be cached by glide.
     */
    public static class DistributionListAvatarConfig extends AvatarConfig<Long> {

        private DistributionListAvatarConfig(
            @Nullable Long distributionListId,
            @NonNull AvatarOptions options
        ) {
            super(distributionListId, options);
        }

        @Override
        protected int getSubjectHashCode() {
            return (subject != null) ? subject.hashCode() : -3;
        }

        @Override
        protected long getAvatarState() {
            return 1;
        }

        @NonNull
        @Override
        protected String getSubjectDebugString() {
            return subject != null ? String.valueOf(subject) : "null";
        }
    }

    /**
     * Run the given code in a separate thread if this is called from the UI thread.
     *
     * @param function the function that is executed
     * @return the bitmap that the function returns and null if an error occurs
     */
    @Nullable
    @AnyThread
    private static Bitmap getBitmapInWorkerThread(@NonNull Supplier<Bitmap> function) {
        if (isMainThread()) {
            BitmapLoadThread bitmapLoadThread = new BitmapLoadThread(function);
            bitmapLoadThread.start();
            try {
                bitmapLoadThread.join();
                return bitmapLoadThread.bitmap;
            } catch (InterruptedException e) {
                logger.error("Error while loading bitmap", e);
                Thread.currentThread().interrupt();
                return null;
            }
        } else {
            return function.get();
        }
    }

    private static class BitmapLoadThread extends Thread {
        private final @NonNull Supplier<Bitmap> function;
        private Bitmap bitmap;

        private BitmapLoadThread(@NonNull Supplier<Bitmap> function) {
            this.function = function;
        }

        @Override
        public void run() {
            bitmap = function.get();
        }
    }
}
