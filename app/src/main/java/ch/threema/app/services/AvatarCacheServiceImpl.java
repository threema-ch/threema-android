/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.util.LruCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import ch.threema.app.R;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ContactUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

final public class AvatarCacheServiceImpl implements AvatarCacheService {
	private static final Logger logger = LoggerFactory.getLogger(AvatarCacheServiceImpl.class);

	private static final String KEY_GROUP = "g";
	private static final String KEY_DISTRIBUTION_LIST = "d";
	private final LruCache<String, Bitmap> cache;

	private final Context context;
	private final IdentityStore identityStore;
	private final PreferenceService preferenceService;
	private final FileService fileService;

	private final VectorDrawableCompat contactDefaultAvatar;
	private final VectorDrawableCompat groupDefaultAvatar;
	private final VectorDrawableCompat distributionListDefaultAvatar;
	private final VectorDrawableCompat businessDefaultAvatar;

	private final int avatarSizeSmall, avatarSizeHires;

	private Boolean isDefaultAvatarColored = null;

	private interface GenerateBitmap {
		Bitmap gen();
	}

	public AvatarCacheServiceImpl(Context context,
								  IdentityStore identityStore,
								  PreferenceService preferenceService,
								  FileService fileService) {
		this.context = context;
		this.identityStore = identityStore;
		this.preferenceService = preferenceService;
		this.fileService = fileService;

		this.contactDefaultAvatar = VectorDrawableCompat.create(context.getResources(), R.drawable.ic_contact, null);
		this.groupDefaultAvatar = VectorDrawableCompat.create(context.getResources(), R.drawable.ic_group, null);
		this.distributionListDefaultAvatar = VectorDrawableCompat.create(context.getResources(), R.drawable.ic_distribution_list, null);
		this.businessDefaultAvatar = VectorDrawableCompat.create(context.getResources(), R.drawable.ic_business, null);

		this.avatarSizeSmall = context.getResources().getDimensionPixelSize(R.dimen.avatar_size_small);
		this.avatarSizeHires = context.getResources().getDimensionPixelSize(R.dimen.avatar_size_hires);

		// Get max available VM memory, exceeding this amount will throw an
		// OutOfMemory exception. Stored in kilobytes as LruCache takes an
		// int in its constructor.
		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

		// Use 1/32th of the available memory for this memory cache.
		final int cacheSize = Math.min(maxMemory / 32, 1024 * 16); // 16 MB max

		cache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				// The cache size will be measured in kilobytes rather than
				// number of items.
				return bitmap.getByteCount() / 1024;
			}
		};
		logger.debug("cache created, size (kB): " + cacheSize);
	}

	private String getCacheKey(ContactModel contactModel) {
		if(contactModel == null) {
			return null;
		}
		return contactModel.getIdentity();
	}

	private String getCacheKey(GroupModel groupModel) {
		if(groupModel == null) {
			return null;
		}
		return KEY_GROUP + groupModel.getId();
	}
	private String getCacheKey(DistributionListModel distributionListModel) {
		if(distributionListModel == null) {
			return null;
		}
		return KEY_DISTRIBUTION_LIST + distributionListModel.getId();
	}


	private Bitmap getCached(ContactModel contactModel, GenerateBitmap generateBitmap) {
		return this.getCached(
				contactModel.getIdentity(),
				generateBitmap);
	}


	private Bitmap getCached(GroupModel groupModel, GenerateBitmap generateBitmap) {
		return this.getCached(
				this.getCacheKey(groupModel),
				generateBitmap);
	}


	private Bitmap getCached(DistributionListModel distributionListModel, GenerateBitmap generateBitmap) {
		return this.getCached(
				this.getCacheKey(distributionListModel),
				generateBitmap);
	}


	private Bitmap getCached(String key, GenerateBitmap generateBitmap) {
		Bitmap res;

		if(key == null) {
			return null;
		}

		synchronized (this.cache) {
			res = this.cache.get(key);

			if(generateBitmap != null && (res == null || res.isRecycled())) {
				logger.debug("generateBitmap " + key + ", " + (res == null ? "null" : "object ok"));
				res = generateBitmap.gen();
				if(res != null) {
					this.cache.put(key, res);
				}
			}
			return res;
		}
	}

	@Override
	public Bitmap getContactAvatarHigh(ContactModel contactModel) {
		if(contactModel == null) {
			return null;
		}
		return this.getAvatar(contactModel, true);
	}

	@Override
	public Bitmap getContactAvatarLow(final ContactModel contactModel) {
		return this.getCached(contactModel, new GenerateBitmap() {
			@Override
			public Bitmap gen() {
				return getAvatar(contactModel, false);
			}
		});
	}

	@Override
	public Bitmap getContactAvatarLowFromCache(final ContactModel contactModel) {
		return this.getCached(contactModel, null);
	}

	@Override
	public Bitmap getGroupAvatarHigh(@NonNull GroupModel groupModel, Collection<Integer> contactColors, boolean defaultOnly) {
		return this.getAvatar(groupModel, contactColors, true, defaultOnly);
	}

	@Override
	public Bitmap getGroupAvatarLow(@NonNull final GroupModel groupModel, final Collection<Integer> contactColors, boolean defaultOnly) {
		if (defaultOnly) {
			return getAvatar(groupModel, contactColors, false, true);
		} else {
			return this.getCached(groupModel, new GenerateBitmap() {
				@Override
				public Bitmap gen() {
					return getAvatar(groupModel, contactColors, false, false);
				}
			});
		}
	}

	@Override
	public Bitmap getGroupAvatarLowFromCache(final GroupModel groupModel) {
		return this.getCached(groupModel, null);
	}

	@Override
	public Bitmap getDistributionListAvatarLow(final DistributionListModel distributionListModel, final int[] contactColors) {
		return this.getCached(distributionListModel, new GenerateBitmap() {
			@Override
			public Bitmap gen() {
				return getAvatar(distributionListModel, contactColors);
			}
		});
	}

	@Override
	public Bitmap getDistributionListAvatarLowFromCache(DistributionListModel distributionListModel) {
		return this.getCached(distributionListModel, null);
	}

	@Override
	public void reset(GroupModel groupModel) {
		synchronized (this.cache) {
			this.cache.remove(this.getCacheKey(groupModel));
		}
	}

	@Override
	public void reset(ContactModel contactModel) {
		synchronized (this.cache) {
			this.cache.remove(this.getCacheKey(contactModel));
		}
	}

	@Override
	public void clear() {
		synchronized (this.cache) {
			cache.evictAll();
		}
		this.isDefaultAvatarColored = null;
	}

	private Bitmap getAvatar(ContactModel contactModel, final boolean highResolution) {
		Bitmap result = null;
		@ColorInt int color = ColorUtil.getInstance().getCurrentThemeGray(this.context);

		if (contactModel != null) {
			if (this.getDefaultAvatarColored() && (contactModel.getIdentity() != null && !contactModel.getIdentity().equals(identityStore.getIdentity()))) {
				color = contactModel.getColor();
			}

			// try profile picture
			try {
				result = fileService.getContactPhoto(contactModel);
				if (result != null && !highResolution) {
					result = AvatarConverterUtil.convert(this.context.getResources(), result);
				}
			} catch (Exception e) {
				// whatever...
			}

			if (result == null) {
				// try local saved avatar
				try {
					result = fileService.getContactAvatar(contactModel);
					if (result != null && !highResolution) {
						result = AvatarConverterUtil.convert(this.context.getResources(), result);
					}
				} catch (Exception e) {
					// whatever...
				}
			}

			if (result == null) {
				if (!ContactUtil.isChannelContact(contactModel)) {
					// regular contacts

					Uri contactUri = ContactUtil.getAndroidContactUri(this.context, contactModel);
					if (contactUri != null) {
						// address book contact
						try {
							result = fileService.getAndroidContactAvatar(contactModel);
							if (result != null && !highResolution) {
								result = AvatarConverterUtil.convert(this.context.getResources(), result);
							}
						} catch (Exception e) {
							// whatever...
						}
					}

					if (result == null) {
						//return default avatar
						if (!highResolution) {
							result = AvatarConverterUtil.getAvatarBitmap(contactDefaultAvatar, color, this.avatarSizeSmall);
						}
					}
				} else {
					// business (gateway) contacts
					if (highResolution) {
						result = buildHiresDefaultAvatar(color, AVATAR_BUSINESS);
					} else {
						result = AvatarConverterUtil.getAvatarBitmap(businessDefaultAvatar, color, this.avatarSizeSmall);
					}
				}
			}
		}

		return result;
	}

	@Nullable
	private Bitmap getAvatar(DistributionListModel distributionListModel, int[] contactColors) {
		synchronized (this.distributionListDefaultAvatar) {
			if(distributionListModel == null) {
				return null;
			}

			int color = ColorUtil.getInstance().getCurrentThemeGray(this.context);

			if(this.getDefaultAvatarColored()
					&& contactColors != null
					&& contactColors.length > 0) {
				//default color
				color = contactColors[0];
			}
			return AvatarConverterUtil.getAvatarBitmap(distributionListDefaultAvatar, color, this.avatarSizeSmall);
		}
	}

	@Override
	public Bitmap buildHiresDefaultAvatar(int color, int avatarType) {
		VectorDrawableCompat drawable = contactDefaultAvatar;

		switch (avatarType) {
			case AVATAR_GROUP:
				drawable = groupDefaultAvatar;
				break;
			case AVATAR_BUSINESS:
				drawable = businessDefaultAvatar;
				break;
		}

		int borderWidth = this.avatarSizeHires * 3 / 2;
		Bitmap def = AvatarConverterUtil.getAvatarBitmap(drawable, Color.WHITE, avatarSizeHires);
		def.setDensity(Bitmap.DENSITY_NONE);

		Bitmap.Config conf = Bitmap.Config.ARGB_8888;
		Bitmap newBitmap = Bitmap.createBitmap(def.getWidth() + borderWidth, def.getHeight() + borderWidth, conf);
		Canvas canvas = new Canvas(newBitmap);
		Paint p = new Paint();
		p.setColor(color);
		canvas.drawRect(0, 0, newBitmap.getWidth(), newBitmap.getHeight(), p);
		canvas.drawBitmap(def, borderWidth / 2f, borderWidth / 2f, null);
		BitmapUtil.recycle(def);

		return newBitmap;
	}

	@Override
	public Bitmap getGroupAvatarNeutral(boolean highResolution) {
		return getAvatar(null, null, highResolution, true);
	}

	private @Nullable Bitmap getAvatar(GroupModel groupModel, Collection<Integer> contactColors, boolean highResolution, boolean defaultOnly) {
		try {
			Bitmap groupImage = null;
			if (!defaultOnly) {
				groupImage = this.fileService.getGroupAvatar(groupModel);
			}

			if (groupImage == null) {
				int color = ColorUtil.getInstance().getCurrentThemeGray(this.context);
				if (this.getDefaultAvatarColored()
					&& contactColors != null
					&& contactColors.size() > 0) {
					//default color
					color = contactColors.iterator().next();
				}

				if (highResolution) {
					groupImage = buildHiresDefaultAvatar(color, AVATAR_GROUP);
				} else {
					synchronized (this.groupDefaultAvatar) {
						groupImage = AvatarConverterUtil.getAvatarBitmap(groupDefaultAvatar, color, this.avatarSizeSmall);
					}
				}
			} else {
				//resize image!
				Bitmap converted = AvatarConverterUtil.convert(this.context.getResources(), groupImage);
				if (groupImage != converted) {
					BitmapUtil.recycle(groupImage);
				}
				return converted;
			}

			return groupImage;
		} catch (Exception e) {
			logger.error("Exception", e);
			//DO NOTHING
			return null;
		}
	}

	@Override
	public boolean getDefaultAvatarColored() {
		if(this.isDefaultAvatarColored == null) {
			this.isDefaultAvatarColored = (this.preferenceService == null || this.preferenceService.isDefaultContactPictureColored());
		}

		return this.isDefaultAvatarColored;
	}
}
