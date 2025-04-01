/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.Objects;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import ch.threema.app.R;

public class ImageViewUtil {
    private ImageViewUtil() {
    }

    private static float cornerRadius = 0F;

    public static void showRoundedBitmapOrImagePlaceholder(
        @NonNull Context context,
        @Nullable View blockView,
        @Nullable ImageView imageView,
        @Nullable Bitmap bitmap,
        int width
    ) {
        if (blockView != null && imageView != null) {
            ViewGroup.LayoutParams params = blockView.getLayoutParams();
            params.width = width;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            blockView.setLayoutParams(params);

            Bitmap bitmapOrPlaceholder = bitmap == null
                ? getPlaceholderImage(context, width, R.drawable.ic_image_outline)
                : bitmap;

            RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory
                .create(context.getResources(), bitmapOrPlaceholder);
            roundedBitmapDrawable.setCornerRadius(getCornerRadius(context));

            if (bitmapOrPlaceholder != null) {
                showBitmap(imageView, bitmapOrPlaceholder, roundedBitmapDrawable, width);
            }
        }
    }

    public static void showBitmapOrImagePlaceholder(
        @NonNull Context context,
        @Nullable View blockView,
        @Nullable ImageView imageView,
        @Nullable Bitmap bitmap,
        int width
    ) {
        showBitmapOrPlaceholder(
            context,
            blockView,
            imageView,
            bitmap,
            R.drawable.ic_image_outline,
            width
        );
    }

    public static void showBitmapOrMoviePlaceholder(
        @NonNull Context context,
        @Nullable View blockView,
        @Nullable ImageView imageView,
        @Nullable Bitmap bitmap,
        int width
    ) {
        showBitmapOrPlaceholder(
            context,
            blockView,
            imageView,
            bitmap,
            R.drawable.ic_movie_outline,
            width
        );
    }

    public static void showBitmapOrPlaceholder(
        @NonNull Context context,
        @Nullable View blockView,
        @Nullable ImageView imageView,
        @Nullable Bitmap bitmap,
        @DrawableRes int drawableId,
        int width
    ) {
        if (blockView != null && imageView != null) {
            ViewGroup.LayoutParams params = blockView.getLayoutParams();
            params.width = width;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            blockView.setLayoutParams(params);

            Bitmap bitmapOrPlaceholder = bitmap == null
                ? getPlaceholderImage(context, width, drawableId)
                : bitmap;

            if (bitmapOrPlaceholder != null) {
                showBitmap(imageView, bitmapOrPlaceholder, null, width);
            }
        }
    }

    private static @Nullable Bitmap getPlaceholderImage(@NonNull Context context, int width, @DrawableRes int drawableId) {
        Drawable drawable = ResourcesCompat.getDrawable(context.getResources(), drawableId, context.getTheme());
        return drawable == null
            ? null
            : createBitmapFromDrawable(context, Objects.requireNonNull(drawable), width);
    }

    private static @NonNull Bitmap createBitmapFromDrawable(@NonNull Context context, @NonNull Drawable drawable, int width) {
        int height = getHeightForDrawableRatio(drawable, width);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int background = ContextCompat.getColor(context, R.color.bubble_thumbnail_placeholder_background);
        canvas.drawColor(background);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        int tint = ContextCompat.getColor(context, R.color.bubble_thumbnail_placeholder_tint);
        drawable.setTint(tint);
        drawable.draw(canvas);
        return bitmap;
    }

    private static int getHeightForDrawableRatio(@NonNull Drawable drawable, int width) {
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        return drawableWidth <= 0 || drawableHeight <= 0
            ? width
            : (int) ((float) width / drawableWidth * drawableHeight);
    }

    public static float getCornerRadius(Context context) {
        if (cornerRadius == 0F) {
            cornerRadius = context.getResources().getDimensionPixelSize(R.dimen.chat_bubble_border_radius_inside);
        }
        return cornerRadius;
    }

    private static void showBitmap(ImageView imageView, Bitmap bitmap, Drawable drawable, int width) {
        if (TestUtil.required(imageView, bitmap)) {
            ViewGroup.LayoutParams params = imageView.getLayoutParams();
            params.width = width;
            params.height = (int) ((float) width / bitmap.getWidth() * bitmap.getHeight());

            if (drawable != null) {
                imageView.setImageDrawable(drawable);
            } else {
                imageView.setImageBitmap(bitmap);
            }
            imageView.setLayoutParams(params);
            imageView.setVisibility(View.VISIBLE);
        }
    }
}
