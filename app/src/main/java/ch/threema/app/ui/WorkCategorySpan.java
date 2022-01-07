/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

package ch.threema.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.TestUtil;

public class WorkCategorySpan extends ReplacementSpan {

	private int contentsWidth = 0;
	private Paint backgroundPaint, closeButtonPaint;
	@ColorInt private int textColor;
	private static final int contentsPaddingHorizontal = ThreemaApplication.getAppContext().getResources().getDimensionPixelSize(R.dimen.work_category_padding);
	private static final int paddingVertical = ThreemaApplication.getAppContext().getResources().getDimensionPixelSize(R.dimen.work_category_padding_vertical);
	private static final int radius = ThreemaApplication.getAppContext().getResources().getDimensionPixelSize(R.dimen.work_category_radius);
	private static final int closeButtonDimension = ThreemaApplication.getAppContext().getResources().getDimensionPixelSize(R.dimen.work_category_close_button_dimension);
	private static final int closeButtonStrokeWidth = ThreemaApplication.getAppContext().getResources().getDimensionPixelSize(R.dimen.work_category_close_button_stroke_width);

	public WorkCategorySpan(@ColorInt int backgroundColor, @ColorInt int textColor) {
		super();

		backgroundPaint = new Paint();
		backgroundPaint.setStyle(Paint.Style.FILL);
		backgroundPaint.setColor(backgroundColor);
		backgroundPaint.setAlpha(0xff);

		closeButtonPaint = new Paint();
		closeButtonPaint.setStyle(Paint.Style.STROKE);
		closeButtonPaint.setColor(textColor);
		closeButtonPaint.setStrokeWidth(closeButtonStrokeWidth);
		closeButtonPaint.setAlpha(0xff);

		this.textColor = textColor;
	}

	@Override
	public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
		if (!TestUtil.empty(text)) {
			contentsWidth = (int) paint.measureText(text.subSequence(start, end).toString()) + (contentsPaddingHorizontal * 3) + closeButtonDimension;
			return contentsWidth;
		}
		return 0;
	}

	@Override
	public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
		if (!TestUtil.empty(text)) {
			int alpha = paint.getAlpha();
			int color = paint.getColor();

			Paint.FontMetricsInt fm = paint.getFontMetricsInt();
			float rectTop = y + fm.top - paddingVertical;
			float rectBottom = rectTop + (fm.bottom - fm.top) + (paddingVertical * 2);
			float rectLeft = x;
			float rectRight = x + contentsWidth;

			canvas.drawRoundRect(new RectF(rectLeft, rectTop, rectRight, rectBottom), radius, radius, backgroundPaint);

			paint.setColor(this.textColor);
			paint.setAlpha(0xFF);
			canvas.drawText(text.subSequence(start, end).toString(), x + contentsPaddingHorizontal, y, paint);

			float closeLeft = rectRight - contentsPaddingHorizontal - closeButtonDimension;
			float closeRight = rectRight - contentsPaddingHorizontal;
			float closeTop = rectTop + ((rectBottom - rectTop - closeButtonDimension) / 2f);
			float closeBottom = closeTop + closeButtonDimension;

			canvas.drawLine(closeLeft, closeTop, closeRight, closeBottom, closeButtonPaint);
			canvas.drawLine(closeRight, closeTop, closeLeft, closeBottom, closeButtonPaint);

			paint.setAlpha(alpha);
			paint.setColor(color);
		}
	}
}
