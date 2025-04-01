/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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
import android.content.Intent;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ch.threema.app.messagereceiver.MessageReceiver;
import kotlin.jvm.functions.Function0;

public interface WallpaperService {

    ActivityResultLauncher<Intent> getWallpaperActivityResultLauncher(@NonNull Fragment fragment, @Nullable Runnable onResultAction, @Nullable Function0<MessageReceiver> getMessageReceiver);

    boolean removeWallpaper(MessageReceiver messageReceiver);

    void removeWallpaper(String uniqueIdString);

    boolean setupWallpaperBitmap(MessageReceiver messageReceiver, ImageView wallpaperView, boolean landscape, boolean isTheDarkside);

    boolean hasGalleryWallpaper(MessageReceiver messageReceiver);

    void selectWallpaper(@NonNull Fragment fragment, @NonNull ActivityResultLauncher<Intent> fileSelectionLauncher, @Nullable MessageReceiver messageReceiver, @Nullable Runnable onSuccess);

    void removeAll(Context context, boolean silent);

    boolean hasGlobalGalleryWallpaper();

    boolean hasGlobalEmptyWallpaper();
}
