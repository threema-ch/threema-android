/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import ch.threema.app.R;

public class ImageViewUtil {
	private ImageViewUtil() {
	}

	private static float cornerRadius = 0F;

	public static void showBitmap(ImageView imageView, Bitmap bitmap, Drawable drawable, int fixWidth) {
		if(TestUtil.required(imageView, bitmap)) {
			ViewGroup.LayoutParams params = imageView.getLayoutParams();
			params.width = fixWidth;
			//calculate height
			params.height = (int)((float)fixWidth/bitmap.getWidth()*bitmap.getHeight());

			if (drawable != null) {
				imageView.setImageDrawable(drawable);
			} else {
				imageView.setImageBitmap(bitmap);
			}
			imageView.setLayoutParams(params);
			imageView.setVisibility(View.VISIBLE);
		}
	}

	public static void showRoundedBitmap(Context context, View blockView, ImageView imageView, Bitmap bitmap, int fixWidth) {
		if(blockView != null) {
			ViewGroup.LayoutParams params = blockView.getLayoutParams();
			params.width = fixWidth;
			params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
			blockView.setLayoutParams(params);

			RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
			drawable.setCornerRadius(getCornerRadius(context));

			showBitmap(imageView, bitmap, drawable, fixWidth);
		}
	}

	public static void showPlaceholderBitmap(View blockView, ImageView imageView, int fixWidth) {
		if(blockView != null) {
			// TODO: The placeholder is currently only an empty bubble
			imageView.setImageBitmap(null);
			imageView.setVisibility(View.INVISIBLE);

			ViewGroup.LayoutParams params = blockView.getLayoutParams();
			params.width = fixWidth;
			params.height = fixWidth / 2;
			blockView.setLayoutParams(params);
		}
	}

	public static float getCornerRadius(Context context) {
		if (cornerRadius == 0F) {
			cornerRadius = context.getResources().getDimensionPixelSize(R.dimen.chat_bubble_border_radius_inside);
		}
		return cornerRadius;
	}

}
