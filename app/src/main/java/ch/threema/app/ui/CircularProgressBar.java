/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import ch.threema.app.R;

public class CircularProgressBar extends View {

	private static final float DEFAULT_MAX_VALUE = 100;
	private static final float DEFAULT_START_ANGLE = 270;

	private float progress = 0;
	private float progressMax = DEFAULT_MAX_VALUE;
	private float strokeWidth = getResources().getDimension(R.dimen.conversation_controller_progress_stroke_width);
	private float backgroundStrokeWidth = getResources().getDimension(R.dimen.conversation_controller_progress_stroke_width);
	private float borderWidth = getResources().getDimension(R.dimen.conversation_controller_progress_border_width);
	private int color = Color.BLACK;
	private int backgroundColor = Color.GRAY;

	// Bounding box for progress bar
	private RectF rectF;
	private Paint backgroundPaint;
	private Paint foregroundPaint;

	public CircularProgressBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs) {
		rectF = new RectF();
		TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CircularProgressBar, 0, 0);
		try {
			progress = typedArray.getFloat(R.styleable.CircularProgressBar_progressbar_progress, progress);
			progressMax = typedArray.getFloat(R.styleable.CircularProgressBar_progressbar_max, progressMax);
			strokeWidth = typedArray.getDimension(R.styleable.CircularProgressBar_progressbar_width, strokeWidth);
			backgroundStrokeWidth = typedArray.getDimension(R.styleable.CircularProgressBar_background_width, backgroundStrokeWidth);
			color = typedArray.getInt(R.styleable.CircularProgressBar_progressbar_color, color);
			backgroundColor = typedArray.getInt(R.styleable.CircularProgressBar_background_color, backgroundColor);
			borderWidth = typedArray.getDimension(R.styleable.CircularProgressBar_border_width, borderWidth);
		} finally {
			typedArray.recycle();
		}

		backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		backgroundPaint.setColor(backgroundColor);
		backgroundPaint.setStyle(Paint.Style.STROKE);
		backgroundPaint.setStrokeWidth(backgroundStrokeWidth);

		foregroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		foregroundPaint.setColor(color);
		foregroundPaint.setStyle(Paint.Style.STROKE);
		foregroundPaint.setStrokeWidth(strokeWidth);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		canvas.drawOval(rectF, backgroundPaint);
		float realProgress = progress * DEFAULT_MAX_VALUE / progressMax;
		float angle = 360 * realProgress / 100;
		canvas.drawArc(rectF, DEFAULT_START_ANGLE, angle, false, foregroundPaint);
	}

	private void reDraw() {
		requestLayout();
		invalidate();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
		final int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		final int min = Math.min(width, height);
		setMeasuredDimension(min, min);
		float highStroke = strokeWidth > backgroundStrokeWidth ? strokeWidth : backgroundStrokeWidth;
		rectF.set(borderWidth + highStroke / 2, borderWidth + highStroke / 2, min - borderWidth - highStroke / 2, min - borderWidth - highStroke / 2);
	}

	public float getProgress() {
		return progress;
	}

	public void setProgress(float progress) {
		this.progress = progress <= progressMax ? progress : progressMax;
		invalidate();
	}

	public float getMax() {
		return progressMax;
	}

	public void setMax(float progressMax) {
		this.progressMax = progressMax >= 0 ? progressMax : DEFAULT_MAX_VALUE;
		reDraw();
	}

	public int getColor() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
		foregroundPaint.setColor(color);
		reDraw();
	}

	public int getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(int backgroundColor) {
		this.backgroundColor = backgroundColor;
		backgroundPaint.setColor(backgroundColor);
		reDraw();
	}
}
