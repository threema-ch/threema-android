/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.glide;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.executor.GlideExecutor;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

import ch.threema.app.services.AvatarCacheServiceImpl;
import ch.threema.storage.models.AbstractMessageModel;

@GlideModule
public class AvatarGlideModule extends AppGlideModule {

	@Override
	public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
		builder.setDefaultRequestOptions(new RequestOptions().format(DecodeFormat.PREFER_RGB_565));
	}

	@Override
	public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
		registry.prepend(AvatarCacheServiceImpl.ContactAvatarConfig.class, Bitmap.class, new ContactAvatarModelLoaderFactory(context));
		registry.prepend(AvatarCacheServiceImpl.GroupAvatarConfig.class, Bitmap.class, new GroupAvatarModelLoaderFactory(context));
		registry.prepend(AvatarCacheServiceImpl.DistributionListAvatarConfig.class, Bitmap.class, new DistributionListAvatarModelLoaderFactory(context));
		registry.prepend(AbstractMessageModel.class, Bitmap.class, new ThumbnailLoaderFactory());
	}
}
