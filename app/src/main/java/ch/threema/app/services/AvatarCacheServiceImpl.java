/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
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
import ch.threema.app.utils.ColorUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ReceiverModel;
import java8.util.function.Supplier;

/**
 * The AvatarCacheService manages the cached loading of avatars. If an avatar is modified or deleted, it must be reset in this class.
 */
final public class AvatarCacheServiceImpl implements AvatarCacheService {

	private static final Logger logger = LoggingUtil.getThreemaLogger("AvatarCacheServiceImpl");

	/**
	 * The mapping of identities to "states". If the number is changed, the next time the avatar of this identity is accessed, it will be loaded
	 * from the database.To invalidate all contact caches clear this map.
	 */
	private final Map<String, Long> contactAvatarStates = new HashMap<>();

	/**
	 * The mapping of group IDs to "states". If the number is changed, the next time the group avatar is accessed, it will be loaded
	 * from the database. To invalidate all group caches clear this map.
	 */
	private final Map<Integer, Long> groupAvatarStates = new HashMap<>();

	private final Context context;

	private final VectorDrawableCompat contactPlaceholder;
	private final VectorDrawableCompat groupPlaceholder;
	private final VectorDrawableCompat distributionListPlaceholder;

	private final DrawableCrossFadeFactory factory = new DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build();

	public AvatarCacheServiceImpl(Context context) {
		this.context = context;

		this.contactPlaceholder = VectorDrawableCompat.create(context.getResources(), R.drawable.ic_contact, null);
		this.groupPlaceholder = VectorDrawableCompat.create(context.getResources(), R.drawable.ic_group, null);
		this.distributionListPlaceholder = VectorDrawableCompat.create(context.getResources(), R.drawable.ic_distribution_list, null);

		// Use dark theme default gray for placeholder
		int color = ColorUtil.COLOR_GRAY_DARK;
		int avatarSizeSmall = context.getResources().getDimensionPixelSize(R.dimen.avatar_size_small);
		if (contactPlaceholder != null) {
			AvatarConverterUtil.getAvatarBitmap(contactPlaceholder, color, avatarSizeSmall);
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
	public @Nullable Bitmap getContactAvatar(@Nullable final ContactModel contactModel, @NonNull AvatarOptions options) {
		return getBitmapInWorkerThread(
			() -> this.getAvatar(contactModel, options)
		);
	}

	@AnyThread
	@Override
	public void loadContactAvatarIntoImage(@NonNull ContactModel model, @NonNull ImageView imageView, @NonNull AvatarOptions options) {
		loadBitmap(new ContactAvatarConfig(model, options), contactPlaceholder, imageView);
	}

	@AnyThread
	@Override
	public @Nullable Bitmap getGroupAvatar(@Nullable GroupModel groupModel, @NonNull AvatarOptions options) {
		return getBitmapInWorkerThread(
			() -> getAvatar(groupModel, options)
		);
	}

	@AnyThread
	@Override
	public void loadGroupAvatarIntoImage(@Nullable GroupModel groupModel, @NonNull ImageView imageView, @NonNull AvatarOptions options) {
		loadBitmap(new GroupAvatarConfig(groupModel, options), groupPlaceholder, imageView);
	}

	@AnyThread
	@Override
	public @Nullable Bitmap getDistributionListAvatarLow(@Nullable final DistributionListModel distributionListModel) {
		return getBitmapInWorkerThread(
			() -> getAvatar(distributionListModel)
		);
	}

	@AnyThread
	@Override
	public void loadDistributionListAvatarIntoImage(@NonNull DistributionListModel model, @NonNull ImageView imageView, @NonNull AvatarOptions options) {
		loadBitmap(new DistributionListAvatarConfig(model, options), distributionListPlaceholder, imageView);
	}

	@AnyThread
	@Override
	public void reset(@NonNull ContactModel contactModel) {
		synchronized (this.contactAvatarStates) {
			this.contactAvatarStates.put(contactModel.getIdentity(), System.currentTimeMillis());
		}
	}

	@AnyThread
	@Override
	public void reset(@NonNull GroupModel groupModel) {
		synchronized (this.groupAvatarStates) {
			this.groupAvatarStates.put(groupModel.getApiGroupId().hashCode(), System.currentTimeMillis());
		}
	}

	@AnyThread
	@Override
	public void clear() {
		synchronized (this.contactAvatarStates) {
			contactAvatarStates.clear();
		}
		synchronized (this.groupAvatarStates) {
			groupAvatarStates.clear();
		}
	}

	@WorkerThread
	private @Nullable Bitmap getAvatar(@Nullable ContactModel contactModel, AvatarOptions options) {
		return getBitmap(new ContactAvatarConfig(contactModel, options));
	}

	@WorkerThread
	private @Nullable Bitmap getAvatar(@Nullable GroupModel groupModel, AvatarOptions options) {
		return getBitmap(new GroupAvatarConfig(groupModel, options));
	}

	@WorkerThread
	private @Nullable Bitmap getAvatar(@Nullable DistributionListModel distributionListModel) {
		return getBitmap(new DistributionListAvatarConfig(distributionListModel, AvatarOptions.PRESET_DEFAULT_AVATAR_NO_CACHE));
	}

	@WorkerThread
	private @Nullable <M extends ReceiverModel> Bitmap getBitmap(@NonNull AvatarConfig<M> config) {
		try {
			RequestBuilder<Bitmap> requestBuilder = Glide.with(context).asBitmap().load(config).diskCacheStrategy(DiskCacheStrategy.NONE).signature(new ObjectKey(config.state));
			if (config.model == null || config.options.disableCache) {
				requestBuilder = requestBuilder.skipMemoryCache(true);
			}
			return requestBuilder.submit().get();
		} catch (ExecutionException | InterruptedException e) {
			logger.error("Error while getting avatar bitmap for configuration " + config, e);
			Thread.currentThread().interrupt();
			return null;
		}
	}

	@AnyThread
	private <M extends ReceiverModel> void loadBitmap(@NonNull AvatarConfig<M> config, @Nullable Drawable placeholder, @NonNull ImageView view) {
		RequestBuilder<Bitmap> requestBuilder = Glide.with(context).asBitmap().load(config).placeholder(placeholder).transition(BitmapTransitionOptions.withCrossFade(factory)).diskCacheStrategy(DiskCacheStrategy.NONE).signature(new ObjectKey(config.state));
		if (config.options.disableCache) {
			requestBuilder = requestBuilder.skipMemoryCache(true);
		}
		try {
			requestBuilder.into(view);
		} catch (Exception e) {
			logger.debug("Glide failure", e);
		}
	}

	/**
	 * This class is used as a identifier for glide. Based on its hashcode, the objects can be cached in different resolutions.
	 */
	public static abstract class AvatarConfig<M extends ReceiverModel> {

		@Nullable
		final M model;
		@NonNull
		private final AvatarOptions options;
		private final long state;

		private AvatarConfig(@Nullable M model, @NonNull AvatarOptions options) {
			this.model = model;
			this.options = options;
			this.state = getAvatarState();
		}

		/**
		 * Get the model of this identifier.
		 *
		 * @return the contact model
		 */
		public @Nullable M getModel() {
			return model;
		}

		/**
		 * Get the avatar loading options.
		 *
		 * @return the options
		 */
		public @NonNull AvatarOptions getOptions() {
			return this.options;
		}

		abstract int getHashCode();

		abstract long getAvatarState();

		/**
		 * The hash code of this class is based only on the parameters that change the actual result, e.g., the resolution,
		 * the default options and of course the contact, group, or distribution list. The state does not affect the hashcode
		 * and must be used as signature.
		 *
		 * @return the hash code of the object
		 */
		@Override
		public int hashCode() {
			int hash = getHashCode();
			return hash * 31 + options.hashCode();
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj instanceof AvatarConfig) {
				@SuppressWarnings("rawtypes") AvatarConfig other = (AvatarConfig) obj;

				boolean equalModels = this.model == other.model;
				if (!equalModels && this.model != null) {
					equalModels = this.model.equals(other.model);
				}

				return equalModels && this.options.equals(other.options);
			}
			return false;
		}

		@NonNull
		@Override
		public String toString() {
			return (model != null ? model.toString() : "null") + " " + options;
		}
	}

	/**
	 * This class stores information about the access of a contact avatar. Every object of this class
	 * with the same hashcode references the same image and can therefore be cached by glide.
	 */
	public class ContactAvatarConfig extends AvatarConfig<ContactModel> {

		private ContactAvatarConfig(@Nullable ContactModel model, @NonNull AvatarOptions options) {
			super(model, options);
		}

		@Override
		int getHashCode() {
			if (model != null && model.getIdentity() != null) {
				return model.getIdentity().hashCode();
			}
			return -1;
		}

		@Override
		long getAvatarState() {
			if (this.model == null) {
				// we don't use the cache for default avatars
				return System.currentTimeMillis();
			}

			Long state = contactAvatarStates.get(this.model.getIdentity());
			if (state != null) {
				return state;
			}
			long newState = System.currentTimeMillis();
			synchronized (contactAvatarStates) {
				contactAvatarStates.put(this.model.getIdentity(), newState);
			}
			return newState;
		}
	}

	/**
	 * This class stores information about the access of a group avatar. Every object of this class
	 * with the same hashcode references the same image and can therefore be cached by glide.
	 */
	public class GroupAvatarConfig extends AvatarConfig<GroupModel> {
		private GroupAvatarConfig(@Nullable GroupModel model, @NonNull AvatarOptions options) {
			super(model, options);
		}

		@Override
		int getHashCode() {
			if (model != null) {
				return model.getApiGroupId().hashCode();
			}
			return -2;
		}

		@Override
		long getAvatarState() {
			if (this.model == null) {
				// if the group model is null, then a default avatar is wanted and therefore it does not make a big difference if it is loaded from cache or not
				return 0;
			}

			Long state = groupAvatarStates.get(this.model.getApiGroupId().hashCode());
			if (state != null) {
				return state;
			}
			long newState = System.currentTimeMillis();
			synchronized (groupAvatarStates) {
				groupAvatarStates.put(this.model.getApiGroupId().hashCode(), newState);
			}
			return newState;
		}
	}

	/**
	 * This class stores information about the access of a distribution list avatar. Every object of
	 * this class with the same hashcode references the same image and can therefore be cached by glide.
	 */
	public static class DistributionListAvatarConfig extends AvatarConfig<DistributionListModel> {
		private DistributionListAvatarConfig(@Nullable DistributionListModel model, @NonNull AvatarOptions options) {
			super(model, options);
		}

		@Override
		int getHashCode() {
			if (model != null) {
				return (int) model.getId();
			}
			return -3;
		}

		@Override
		long getAvatarState() {
			return 1;
		}
	}

	/**
	 * Run the given code in a separate thread if this is called from the UI thread.
	 *
	 * @param function the function that is executed
	 * @return the bitmap that the function returns and null if an error occurs
	 */
	@AnyThread
	private static Bitmap getBitmapInWorkerThread(Supplier<Bitmap> function) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			BitmapLoadThread t = new BitmapLoadThread(function);
			t.start();

			try {
				t.join();
				return t.bitmap;
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
		private final Supplier<Bitmap> function;
		private Bitmap bitmap;

		private BitmapLoadThread(Supplier<Bitmap> function) {
			this.function = function;
		}

		@Override
		public void run() {
			bitmap = function.get();
		}
	}
}
