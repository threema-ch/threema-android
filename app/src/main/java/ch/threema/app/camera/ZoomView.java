/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2023 Threema GmbH
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

package ch.threema.app.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;

public class ZoomView extends FrameLayout {

	private Paint linePaint, circlePaint, semiPaint, labelPaint;
	private int strokeWidth, barPadding, labelStrokeWidth;
	private float zoomFactor;

	public ZoomView(@NonNull Context context) {
		this(context, null);
	}

	public ZoomView(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ZoomView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		setVisibility(GONE);

		this.strokeWidth = context.getResources().getDimensionPixelSize(R.dimen.zoom_view_stroke_width);
		this.labelStrokeWidth = context.getResources().getDimensionPixelSize(R.dimen.zoom_view_label_stroke_width);
		this.barPadding = context.getResources().getDimensionPixelSize(R.dimen.zoom_view_bar_padding);

		this.zoomFactor = 0;

		this.linePaint = new Paint();
		this.linePaint.setStyle(Paint.Style.STROKE);
		this.linePaint.setColor(Color.WHITE);
		this.linePaint.setAntiAlias(true);
		this.linePaint.setStrokeWidth(this.strokeWidth);

		this.semiPaint = new Paint();
		this.semiPaint.setStyle(Paint.Style.STROKE);
		this.semiPaint.setColor(getResources().getColor(R.color.background_dim));
		this.semiPaint.setAntiAlias(true);
		this.semiPaint.setStrokeWidth(this.strokeWidth);

		this.circlePaint = new Paint();
		this.circlePaint.setStyle(Paint.Style.FILL);
		this.circlePaint.setColor(Color.WHITE);
		this.circlePaint.setAntiAlias(true);
		this.circlePaint.setStrokeWidth(0);

		this.labelPaint = new Paint();
		this.labelPaint.setStyle(Paint.Style.STROKE);
		this.labelPaint.setColor(Color.WHITE);
		this.labelPaint.setAntiAlias(true);
		this.labelPaint.setStrokeWidth(this.labelStrokeWidth);
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		if (getWidth() > getHeight()) {
			int left = getLeft() + this.barPadding;
			int right = getRight() - this.barPadding;
			int width = right - left;
			int top = 0;
			int bottom = getBottom() - getTop(); // height
			int center = (bottom + top) / 2;
			int circlePosition = left + (int) (this.zoomFactor * (float) width);

			// draw lines
			canvas.drawLine(left, center, circlePosition, center, this.linePaint);
			canvas.drawLine(circlePosition, center, right, center, this.semiPaint);

			// draw circle
			canvas.drawArc(circlePosition - (bottom / 2), top, circlePosition + (bottom / 2), bottom, 0, 360, false, this.circlePaint);

			// draw plus/minus
			canvas.drawLine(left - (bottom * 2), center, left - bottom, center, this.labelPaint);
			canvas.drawLine(right + (bottom * 2), center, right + bottom, center, this.labelPaint);
			canvas.drawLine(right + bottom + center, top, right + bottom + center, bottom, this.labelPaint);
		} else {
			int top = getTop() + this.barPadding;
			int bottom = getBottom() - this.barPadding;
			int height = bottom - top;
			int left = 0;
			int right = getRight() - getLeft(); // width
			int center = (right + left) / 2;
			int circlePosition = bottom - (int) (this.zoomFactor * (float) height);

			// draw lines
			canvas.drawLine(center, top, center, circlePosition, this.semiPaint);
			canvas.drawLine(center, circlePosition, center, bottom, this.linePaint);

			// draw circle
			canvas.drawArc(left, circlePosition - (right / 2), right, circlePosition + (right / 2), 0, 360, false, this.circlePaint);

			// draw plus/minus
			int plusLineY = top - (right * 2);
			canvas.drawLine(left, bottom + (right * 2), right, bottom + (right * 2), this.labelPaint);
			canvas.drawLine(left, plusLineY, right, plusLineY, this.labelPaint);
			canvas.drawLine(center, plusLineY - (right / 2), center, plusLineY + (right / 2), this.labelPaint);
		}
	}

	public void setZoomFactor(float zoomFactor) {
		this.zoomFactor = zoomFactor;

		if (this.zoomFactor > 0) {
			setVisibility(VISIBLE);
		} else {
			setVisibility(GONE);
		}

		invalidate();
	}
}
