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
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import ch.threema.app.R;

public class ColorUtil {

    /* This is the default gray for the light theme. Note that it is darker than COLOR_GRAY_DARK to increase the contrasts. */
    public final static int COLOR_GRAY_LIGHT = 0xFF777777;

    /* This is the default gray for the dark theme. Note that it is lighter than COLOR_GRAY_LIGHT to increase the contrasts. */
    public final static int COLOR_GRAY_DARK = 0xFFAAAAAA;

    /* The light id colors */
    private final static String[] ID_COLOR_LIGHT = new String[]{"#d84315", "#ef6c00", "#ff8f00", "#eb9c00", "#9e9d24", "#7cb342", "#2e7d32", "#00796b", "#0097a7", "#0288d1", "#1565c0", "#283593", "#7b3ab7", "#ac24aa", "#ad1457", "#c62828"};

    /* The dark id colors */
    private final static String[] ID_COLOR_DARK = new String[]{"#ff7043", "#ffa726", "#ffca28", "#fff176", "#a6a626", "#8bc34a", "#66bb6a", "#2ab7a9", "#26c6da", "#4fc3f7", "#42a5f5", "#8a93ff", "#a88ce3", "#c680d1", "#f16f9a", "#f2706e"};

    // Singleton stuff
    private static ColorUtil sInstance = null;

    public static synchronized ColorUtil getInstance() {
        if (sInstance == null) {
            sInstance = new ColorUtil();
        }
        return sInstance;
    }

    /**
     * Get the light ID color at the given index. If the index is out of range, the default gray color is returned.
     *
     * @param index the index based on the hash
     * @return the ID color at the given index
     */
    public int getIDColorLight(int index) {
        if (index < 0 || index >= ID_COLOR_LIGHT.length) {
            return COLOR_GRAY_LIGHT;
        }
        return Color.parseColor(ID_COLOR_LIGHT[index]);
    }

    /**
     * Get the dark ID color at the given index. If the index is out of range, the default gray color is returned.
     *
     * @param index the index based on the hash
     * @return the ID color at the given index
     */
    public int getIDColorDark(int index) {
        if (index < 0 || index >= ID_COLOR_DARK.length) {
            return COLOR_GRAY_DARK;
        }
        return Color.parseColor(ID_COLOR_DARK[index]);
    }

    /**
     * Get the color index for the given first byte of the ID color hash.
     *
     * @param firstByte the first byte of the hash
     * @return the color for the first byte
     */
    public int getIDColorIndex(byte firstByte) {
        return (((int) firstByte) & 0xff) / ID_COLOR_LIGHT.length;
    }

    /**
     * Get the default gray based on the current theme. In light theme a darker gray is returned to
     * increase the contrasts.
     *
     * @param context the context is needed to determine the current app theme
     * @return the gray based on the theme
     */
    public int getCurrentThemeGray(Context context) {
        if (ConfigUtils.isTheDarkSide(context)) {
            return COLOR_GRAY_DARK;
        } else {
            return COLOR_GRAY_LIGHT;
        }
    }

    /*
        Calculates the estimated brightness of an Android Bitmap.
        pixelSpacing tells how many pixels to skip each pixel. Higher values result in better performance, but a more rough estimate.
        When pixelSpacing = 1, the method actually calculates the real average brightness, not an estimate.
        This is what the calculateBrightness() shorthand is for.
        Do not use values for pixelSpacing that are smaller than 1.
    */
    public int calculateBrightness(Bitmap bitmap, int pixelSpacing) {
        int R = 0;
        int G = 0;
        int B = 0;
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();
        int n = 0;
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i += pixelSpacing) {
            int color = pixels[i];
            R += Color.red(color);
            G += Color.green(color);
            B += Color.blue(color);
            n++;
        }
        if (n != 0) {
            return (R + B + G) / (n * 3);
        }
        return 0;
    }

    public static boolean shouldUseDarkVariant(final @NonNull Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean areDynamicColorsCurrentlyApplied(final @NonNull Context context) {
        final @ColorInt int staticColorPrimary = context.getResources().getColor(
            shouldUseDarkVariant(context)
                ? R.color.md_theme_dark_primary
                : R.color.md_theme_light_primary
        );
        final @ColorInt int dynamicColorPrimary = ConfigUtils.getColorFromAttribute(context, R.attr.colorPrimary);
        return staticColorPrimary != dynamicColorPrimary;
    }
}
