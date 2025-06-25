/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.emojis;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class EmojiDrawable extends Drawable {
    private final SpriteCoordinates coordinates;
    private final int spritemapInSampleSize;
    private Bitmap bitmap;

    private static final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    EmojiDrawable(SpriteCoordinates coordinates, int spritemapInSampleSize) {
        this.coordinates = coordinates;
        this.spritemapInSampleSize = spritemapInSampleSize;
    }

    @Override
    public int getIntrinsicWidth() {
        return spritemapInSampleSize * EmojiManager.EMOJI_WIDTH;
    }

    @Override
    public int getIntrinsicHeight() {
        return spritemapInSampleSize * EmojiManager.EMOJI_HEIGHT;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (bitmap != null) {
            canvas.drawBitmap(bitmap,
                new Rect((int) ((float) (coordinates.x) / spritemapInSampleSize),
                    (int) ((float) coordinates.y / spritemapInSampleSize),
                    (int) ((float) (coordinates.x + EmojiManager.EMOJI_WIDTH) / spritemapInSampleSize),
                    (int) ((float) (coordinates.y + EmojiManager.EMOJI_HEIGHT) / spritemapInSampleSize)),
                getBounds(),
                paint);
        }
    }

    public void setBitmap(@Nullable Bitmap bitmap) {
        if (this.bitmap == null || !this.bitmap.sameAs(bitmap)) {
            this.bitmap = bitmap;
            invalidateSelf();
        }
    }

    @Override
    public void setAlpha(@IntRange(from = 0, to = 255) int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
