/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.motionviews.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import ch.threema.app.motionviews.viewmodel.TextLayer;

public class TextEntity extends MotionEntity {

	private final TextPaint textPaint;
	public static final float TEXT_SHADOW_OFFSET = 0.5f;
	public static final float TEXT_SHADOW_RADIUS = 0.5f;

	@Nullable
	private Bitmap bitmap;

	public TextEntity(@NonNull TextLayer textLayer,
	                  @IntRange(from = 1) int canvasWidth,
	                  @IntRange(from = 1) int canvasHeight) {
		super(textLayer, canvasWidth, canvasHeight);

		this.textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

		updateEntity(false);
	}

	private void updateEntity(boolean moveToPreviousCenter) {

		// save previous center
		PointF oldCenter = absoluteCenter();

		Bitmap newBmp = createBitmap(getLayer(), bitmap);

		// recycle previous bitmap (if not reused) as soon as possible
		if (bitmap != null && bitmap != newBmp && !bitmap.isRecycled()) {
			bitmap.recycle();
		}

		this.bitmap = newBmp;

		float width = bitmap.getWidth();
		float height = bitmap.getHeight();

		@SuppressWarnings("UnnecessaryLocalVariable")
		float widthAspect = 1.0F * canvasWidth / width;

		// for text we always match text width with parent width
		this.holyScale = widthAspect;

		// initial position of the entity
		srcPoints[0] = 0;
		srcPoints[1] = 0;
		srcPoints[2] = width;
		srcPoints[3] = 0;
		srcPoints[4] = width;
		srcPoints[5] = height;
		srcPoints[6] = 0;
		srcPoints[7] = height;
		srcPoints[8] = 0;
		srcPoints[8] = 0;

		if (moveToPreviousCenter) {
			// move to previous center
			moveCenterTo(oldCenter);
		}
	}

	/**
	 * If reuseBmp is not null, and size of the new bitmap matches the size of the reuseBmp, new
	 * bitmap won't be created, reuseBmp it will be reused instead
	 *
	 * @param textLayer text to draw
	 * @param reuseBmp the bitmap that will be reused
	 * @return bitmap with the text
	 */
	@NonNull
	private Bitmap createBitmap(@NonNull TextLayer textLayer, @Nullable Bitmap reuseBmp) {

		int boundsWidth = canvasWidth;

		// init params - size, color, typeface
		textPaint.setStyle(Paint.Style.FILL);
		textPaint.setTextSize(textLayer.getFont().getSize());
		textPaint.setColor(textLayer.getFont().getColor());
		textPaint.setTypeface(Typeface.DEFAULT_BOLD);
		textPaint.setShadowLayer(TEXT_SHADOW_RADIUS, TEXT_SHADOW_OFFSET, TEXT_SHADOW_OFFSET, Color.BLACK);

		// drawing text guide : http://ivankocijan.xyz/android-drawing-multiline-text-on-canvas/
		// Static layout which will be drawn on canvas
		StaticLayout sl = new StaticLayout(
				textLayer.getText(), // - text which will be drawn
				textPaint,
				boundsWidth, // - width of the layout
				Layout.Alignment.ALIGN_CENTER, // - layout alignment
				1, // 1 - text spacing multiply
				1, // 1 - text spacing add
				true); // true - include padding

		// calculate height for the entity, min - Limits.MIN_BITMAP_HEIGHT
		int boundsHeight = sl.getHeight();

		// create bitmap not smaller than TextLayer.Limits.MIN_BITMAP_HEIGHT
		int bmpHeight = (int) (canvasHeight * Math.max(TextLayer.Limits.MIN_BITMAP_HEIGHT,
				1.0F * boundsHeight / canvasHeight));

		// create bitmap where text will be drawn
		Bitmap bmp;
		if (reuseBmp != null && reuseBmp.getWidth() == boundsWidth
				&& reuseBmp.getHeight() == bmpHeight) {
			// if previous bitmap exists, and it's width/height is the same - reuse it
			bmp = reuseBmp;
			bmp.eraseColor(Color.TRANSPARENT); // erase color when reusing
		} else {
			bmp = Bitmap.createBitmap(boundsWidth, bmpHeight, Bitmap.Config.ARGB_8888);
		}

		Canvas canvas = new Canvas(bmp);
		canvas.save();

		// move text to center if bitmap is bigger that text
		if (boundsHeight < bmpHeight) {
			//calculate Y coordinate - In this case we want to draw the text in the
			//center of the canvas so we move Y coordinate to center.
			float textYCoordinate = (bmpHeight - boundsHeight) / 2;
			canvas.translate(0, textYCoordinate);
		}

		//draws static layout on canvas
		sl.draw(canvas);
		canvas.restore();

		return bmp;
	}

	@Override
	@NonNull
	public TextLayer getLayer() {
		return (TextLayer) layer;
	}

	@Override
	protected void drawContent(@NonNull Canvas canvas, @Nullable Paint drawingPaint) {
		if (bitmap != null) {
			canvas.drawBitmap(bitmap, matrix, drawingPaint);
		}
	}

	@Override
	public boolean hasFixedPositionAndSize() {
		return false;
	}

	@Override
	public int getWidth() {
		return bitmap != null ? bitmap.getWidth() : 0;
	}

	@Override
	public int getHeight() {
		return bitmap != null ? bitmap.getHeight() : 0;
	}

	public void updateEntity() {
		updateEntity(true);
	}
}
