package ch.threema.app.services;

import android.content.Intent;
import android.widget.ImageView;

import java.io.IOException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ch.threema.app.messagereceiver.MessageReceiver;
import kotlin.jvm.functions.Function0;

public interface WallpaperService {

    ActivityResultLauncher<Intent> getWallpaperActivityResultLauncher(@NonNull Fragment fragment, @Nullable Runnable onResultAction, @Nullable Function0<MessageReceiver> getMessageReceiver);

    void removeWallpaper(String uniqueIdString);

    boolean setupWallpaperBitmap(MessageReceiver messageReceiver, ImageView wallpaperView, boolean landscape, boolean isTheDarkside);

    boolean hasGalleryWallpaper(MessageReceiver messageReceiver);

    void selectWallpaper(@NonNull Fragment fragment, @NonNull ActivityResultLauncher<Intent> fileSelectionLauncher, @Nullable MessageReceiver messageReceiver, @Nullable Runnable onSuccess);

    void deleteAll() throws IOException;

    boolean hasGlobalGalleryWallpaper();

    boolean hasGlobalEmptyWallpaper();
}
