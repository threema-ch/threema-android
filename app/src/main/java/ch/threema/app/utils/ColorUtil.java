/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

import java.util.Random;

public class ColorUtil {

	@Deprecated
	private static int[] paletteMixes = new int[] {
		Color.GREEN,
		Color.BLUE,
		Color.RED,
		Color.CYAN
	};

	private static int[] googlePalette = new int[] {
		//red #607d8b
		-10453621,
		//Pink #e91e63
		//-1499549,
		//Purple #9c27b0
		-6543440,
		//Deep Purple #673ab7
		-10011977,
		//Indigo #3f51b5
		-12627531,
		//Blue #2196f3
		//-14575885,
		//Light Blue #03a9f4
		-16537100,
		//Cyan #00bcd4
		-16728876,
		//Teal #009688
		-16738680,
		//Green #4caf50
		-11751600,
		//Light Green #8bc34a
		-7617718,
		//Amber #ffc107
		-16121,
		//Orange #ff9800
		-26624,
		//Deep Orange #ff5722
		-43230,
		//Brown #795548
		-8825528,
		//Grey #9e9e9e
		//-6381922,
		//Blue Grey #607d8b
		-10453621
	};

	private Random random;

	// Singleton stuff
	private static ColorUtil sInstance = null;

	public static synchronized ColorUtil getInstance() {
		if (sInstance == null) {
			sInstance = new ColorUtil();
		}
		return sInstance;
	}

	public ColorUtil() {
		this.random = new Random();
	}

	public int getGoogleColor(int index) {
		if(index >= 0 && index < googlePalette.length) {
			return googlePalette[index];
		}
		else {
			//return first google color
			return googlePalette[0];
		}
	}

	/**
	 *
	 * @param context
	 * @return
	 */
	public int getCurrentThemeGray(Context context) {
		switch (ConfigUtils.getAppTheme(context)) {
			case ConfigUtils.THEME_DARK:
				return 0xFFAAAAAA;
			default:
				return 0xFF777777;
		}
	}

	@Deprecated
	public int generateRandomColor(int colorMix) {
		int red = random.nextInt(256);
		int green = random.nextInt(256);
		int blue = random.nextInt(256);

		int r = (colorMix >> 16) & 0xFF;
		int g = (colorMix >> 8) & 0xFF;
		int b = (colorMix) & 0xFF;

		// mix the color
		red = (red + r) / 2;
		green = (green + g) / 2;
		blue = (blue + b) / 2;


		return Color.rgb(red, green, blue);
	}

	@Deprecated
	public int[] generateColorPalette(int size) {
		int palette = 0;
		int[] res = new int[size];
		for(int n = 0; n < size; n++) {
			res[n] = generateRandomColor(paletteMixes[palette]);
			palette = palette+1 >= paletteMixes.length ? 0 : palette + 1;
		}
		return res;
	}

	public int[] generateGoogleColorPalette(int size) {
		int gPos = 0;
		int[] res = new int[size];
		for(int n = 0; n < size; n++) {
			if(googlePalette.length <= gPos) {
				gPos = 0;
			}
			res[n] = googlePalette[gPos++];
		}
		return res;
	}

	public int getRecordColor(int recordPosition) {
		return getGoogleColor((recordPosition-1) % googlePalette.length);
	}

	/*
		Calculates the estimated brightness of an Android Bitmap.
		pixelSpacing tells how many pixels to skip each pixel. Higher values result in better performance, but a more rough estimate.
		When pixelSpacing = 1, the method actually calculates the real average brightness, not an estimate.
		This is what the calculateBrightness() shorthand is for.
		Do not use values for pixelSpacing that are smaller than 1.
	*/
	public int calculateBrightness(Bitmap bitmap, int pixelSpacing) {
		int R = 0; int G = 0; int B = 0;
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
