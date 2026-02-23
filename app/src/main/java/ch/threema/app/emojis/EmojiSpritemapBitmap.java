package ch.threema.app.emojis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.slf4j.Logger;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Objects;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class EmojiSpritemapBitmap {
    private static final Logger logger = getThreemaLogger("EmojiSpritemapBitmap");

    private final Context context;
    private final EmojiGroup emojiGroup;
    private final int spritemapId;
    private final int spritemapInSampleSize;

    private SoftReference<Bitmap> bitmapReference;

    public EmojiSpritemapBitmap(@NonNull Context context, EmojiGroup emojiGroup, int spritemapId, int spritemapInSampleSize) {
        this.context = context.getApplicationContext();
        this.emojiGroup = emojiGroup;
        this.spritemapId = spritemapId;
        this.spritemapInSampleSize = spritemapInSampleSize;
    }

    @Nullable
    @AnyThread
    public Bitmap getSpritemapBitmap() {
        return isSpritemapLoaded() ? bitmapReference.get() : null;
    }

    @AnyThread
    public boolean isSpritemapLoaded() {
        return bitmapReference != null && bitmapReference.get() != null;
    }

    @WorkerThread
    public Bitmap loadSpritemapAsset() {
        try {
            if (emojiGroup.getAssetPath(this.spritemapId) != null) {
                logger.debug("*** Loading Emoji spritemap for group " + this.emojiGroup.getAssetPath(this.spritemapId));

                Bitmap spritemapBitmap = null;
                try (InputStream is = context.getAssets().open(emojiGroup.getAssetPath(this.spritemapId))) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = spritemapInSampleSize;
                    spritemapBitmap = BitmapFactory.decodeStream(is, null, opts);
                } catch (Exception e) {
                    logger.error("Could not load emoji bitmap");
                }

                if (spritemapBitmap != null) {
                    bitmapReference = new SoftReference<>(spritemapBitmap);
                    return spritemapBitmap;
                }
            }
        } catch (Exception e) {
            logger.error("Could not find spritemap", e);
        }
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return Objects.requireNonNull(this.emojiGroup.getAssetPath(this.spritemapId));
    }
}
