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
import android.graphics.Color;

public class ColorUtil {

    // Singleton stuff
    private static ColorUtil sInstance = null;

    public static synchronized ColorUtil getInstance() {
        if (sInstance == null) {
            sInstance = new ColorUtil();
        }
        return sInstance;
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
}
