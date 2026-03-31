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
        @NonNull View blockView,
        @NonNull ImageView imageView,
        @Nullable Bitmap bitmap,
        int width
    ) {
        Context context = blockView.getContext();
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

    public static void showBitmapOrImagePlaceholder(
        @NonNull View blockView,
        @NonNull ImageView imageView,
        @Nullable Bitmap bitmap,
        int width
    ) {
        showBitmapOrPlaceholder(
            blockView,
            imageView,
            bitmap,
            R.drawable.ic_image_outline,
            width
        );
    }

    public static void showBitmapOrMoviePlaceholder(
        @NonNull View blockView,
        @NonNull ImageView imageView,
        @Nullable Bitmap bitmap,
        int width
    ) {
        showBitmapOrPlaceholder(
            blockView,
            imageView,
            bitmap,
            R.drawable.ic_movie_outline,
            width
        );
    }

    public static void showBitmapOrPlaceholder(
        @NonNull View blockView,
        @NonNull ImageView imageView,
        @Nullable Bitmap bitmap,
        @DrawableRes int drawableId,
        int width
    ) {
        Context context = blockView.getContext();
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
        if (imageView != null && bitmap != null) {
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
