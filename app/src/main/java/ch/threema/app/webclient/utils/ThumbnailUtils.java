package ch.threema.app.webclient.utils;

import android.graphics.Bitmap;

import org.slf4j.Logger;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * Functions related to the webclient.
 */
@AnyThread
public class ThumbnailUtils {
    private static final Logger logger = getThreemaLogger("ThumbnailUtils");

    public static class Size {
        public int width;
        public int height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Calculate new dimensions, resize down proportionally.
     */
    @NonNull
    public static Size resizeProportionally(int width, int height, int maxSidePx) {
        if (width > maxSidePx || height > maxSidePx) {
            int largerSide = Math.max(width, height);
            double scaleFactor = (double) maxSidePx / largerSide;
            int newWidth = (int) Math.round((double) width * scaleFactor);
            int newHeight = (int) Math.round((double) height * scaleFactor);
            return new Size(newWidth, newHeight);
        }
        return new Size(width, height);
    }

    /**
     * Make sure that no side is larger than maxSize,
     * resizing if necessary.
     */
    public static Bitmap resize(@NonNull final Bitmap thumbnail, int maxSidePx) {
        int w = thumbnail.getWidth();
        int h = thumbnail.getHeight();

        if (w > maxSidePx || h > maxSidePx) {
            Size newSize = ThumbnailUtils.resizeProportionally(w, h, maxSidePx);

            try {
                return Bitmap.createScaledBitmap(thumbnail, newSize.width, newSize.height, true);
            } catch (Exception x) {
                logger.error("Exception", x);
            }
        }

        return thumbnail;
    }
}
