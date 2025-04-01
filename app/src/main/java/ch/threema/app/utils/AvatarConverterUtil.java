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

import android.Manifest;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import ch.threema.app.R;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.dialogs.ContactEditDialog.CONTACT_AVATAR_HEIGHT_PX;
import static ch.threema.app.dialogs.ContactEditDialog.CONTACT_AVATAR_WIDTH_PX;

public class AvatarConverterUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AvatarConverterUtil");

    private static int avatarSize = -1, iconSize = -1, iconOffset = -1;

    public static int getAvatarSize(Resources r) {
        if (avatarSize == -1) {
            avatarSize = r.getDimensionPixelSize(R.dimen.avatar_size_small);
        }
        return avatarSize;
    }

    private static int getContentIconSize(Resources r) {
        if (iconSize == -1) {
            iconSize = r.getDimensionPixelSize(R.dimen.conversation_controller_icon_size);
        }
        return iconSize;
    }

    private static int getContentIconOffset(Resources r) {
        if (iconOffset == -1) {
            iconOffset = (getAvatarSize(r) - getContentIconSize(r)) / 2;
        }
        return iconOffset;
    }

    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    public static Bitmap convert(Context context, Uri contactUri) {
        Bitmap source = null;
        SampleResult sampleResult;
        int x = 0, y = 0, size = 0;

        try (InputStream is = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), contactUri, true)) {
            if (is == null || is.available() == 0) {
                return null;
            }

            // decode image size (decode metadata only, not the whole image)
            BitmapFactory.Options options = BitmapUtil.getImageDimensions(is);

            // save width and height
            int inWidth = options.outWidth;
            int inHeight = options.outHeight;
            size = Math.min(inWidth, inHeight);
            x = inWidth > inHeight ? (inWidth - size) / 2 : 0;
            y = inWidth < inHeight ? (inHeight - size) / 2 : 0;

            sampleResult = BitmapUtil.getSampleSize(inWidth, inHeight, CONTACT_AVATAR_WIDTH_PX, CONTACT_AVATAR_HEIGHT_PX);
        } catch (IOException e) {
            logger.error("Exception", e);
            return null;
        }

        try (InputStream is = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), contactUri, true)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleResult.inSampleSize;
            options.inJustDecodeBounds = false;

            if (x != y) {
                // this is a non-square image. use a region
                BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
                source = decoder.decodeRegion(new Rect(x, y, size + x, size + y), options);
            } else {
                source = BitmapFactory.decodeStream(is, null, options);
            }
            return source;

        } catch (IOException e) {
            logger.error("Exception", e);
            return null;
        }
    }

    public static Bitmap convert(Resources r, Bitmap sourceBitmap) {
        return convertToRound(r, sourceBitmap, Color.WHITE, null, getAvatarSize(r));
    }

    public static Bitmap convert(Resources r, Bitmap sourceBitmap, int bgcolor, int fgcolor) {
        return convertToRound(r, sourceBitmap, bgcolor, fgcolor, getAvatarSize(r));
    }

    public static Drawable convertToRound(Resources r, Bitmap sourceBitmap) {
        RoundedBitmapDrawable bitmapDrawable = RoundedBitmapDrawableFactory.create(r, sourceBitmap);
        bitmapDrawable.setAntiAlias(true);
        bitmapDrawable.setCircular(true);
        return bitmapDrawable;
    }

    @Nullable
    public static Bitmap convertToRound(Resources r, @Nullable Bitmap sourceBitmap, @ColorInt final int bgcolor, @Nullable final Integer fgcolor, int size) {
        if (sourceBitmap == null) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(
            size,
            size,
            Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint bgPaint = new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setFilterBitmap(true);
        canvas.drawARGB(0, 0, 0, 0);
        bgPaint.setColor(bgcolor);
        canvas.drawCircle(
            (float) output.getWidth() / 2,
            (float) output.getHeight() / 2,
            (float) output.getWidth() / 2,
            bgPaint);
        bgPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        int sourceLeft = 0, sourceTop = 0, sourceRight = sourceBitmap.getWidth(), sourceBottom = sourceBitmap.getHeight();

        // make sure source bitmap is square by cropping longer edge
        if (sourceBitmap.getWidth() != sourceBitmap.getHeight()) {
            sourceBitmap.setDensity(Bitmap.DENSITY_NONE);
            if (sourceBitmap.getWidth() > sourceBitmap.getHeight()) {
                sourceLeft = (sourceRight - sourceBottom) / 2;
                sourceRight = sourceLeft + sourceBottom;
            } else {
                sourceTop = (sourceBottom - sourceRight) / 2;
                sourceBottom = sourceTop + sourceRight;
            }
        }

        if (fgcolor == null) {
            canvas.drawBitmap(
                sourceBitmap,
                new Rect(sourceLeft, sourceTop, sourceRight, sourceBottom),
                new Rect(0, 0, size, size),
                bgPaint);
        } else {

            final Paint fgPaint = new Paint();
            fgPaint.setAntiAlias(true);
            fgPaint.setColorFilter(new LightingColorFilter(0xFFFFFFFF, fgcolor));
            fgPaint.setFilterBitmap(true);

            Rect destRect = new Rect(0, 0, getContentIconSize(r), getContentIconSize(r));
            destRect.offset(getContentIconOffset(r), getContentIconOffset(r));

            canvas.drawBitmap(
                sourceBitmap,
                new Rect(0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight()),
                destRect,
                fgPaint);
        }
        return output;
    }

    @NonNull
    public static Bitmap getAvatarBitmap(Drawable drawable, @ColorInt int color, int size) {
        drawable.mutate();

        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        drawable.setBounds(0, 0, size, size);
        drawable.setTint(color);
        drawable.draw(canvas);

        return result;
    }

    /**
     * Build the default avatar bitmap in high resolution.
     *
     * @param drawable        the icon drawable
     * @param avatarSize      the size of the avatar
     * @param color           the color of the icon
     * @param backgroundColor the color of the background
     * @return the high resolution bitmap
     */
    public static Bitmap buildDefaultAvatarHighRes(Drawable drawable, int avatarSize, int color, int backgroundColor) {
        int borderWidth = avatarSize * 3 / 2;
        Bitmap defaultBitmap = AvatarConverterUtil.getAvatarBitmap(drawable, backgroundColor, avatarSize);
        defaultBitmap.setDensity(Bitmap.DENSITY_NONE);
        Bitmap.Config config = Bitmap.Config.ARGB_8888;
        Bitmap newBitmap = Bitmap.createBitmap(defaultBitmap.getWidth() + borderWidth, defaultBitmap.getHeight() + borderWidth, config);
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        canvas.drawRect(0f, 0f, newBitmap.getWidth(), newBitmap.getHeight(), paint);
        canvas.drawBitmap(defaultBitmap, borderWidth / 2f, borderWidth / 2f, null);
        BitmapUtil.recycle(defaultBitmap);
        return newBitmap;
    }

}
