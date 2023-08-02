/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PaintView extends View {
	private float mX, mY;
	private int currentColor, currentStrokeWidth, currentWidth, currentHeight;
	private static final float TOUCH_TOLERANCE = 4;
	private boolean isActive = true, hasMoved;
	private TouchListener onTouchListener;
	private final List<Rect> drawingRect = Collections.singletonList(new Rect());

	private final ArrayList<Path> paths = new ArrayList<>();
	private final ArrayList<Paint> paints = new ArrayList<>();

	public PaintView(Context context) {
		super(context);
		init();
	}

	public PaintView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public PaintView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init() {
		createPath();
		createPaint();

		// defaults
		currentColor = 0xFFFF0000;
		currentStrokeWidth = 15;
	}

	private Path createPath() {
		Path path = new Path();
		paths.add(path);

		return path;
	}

	private Paint createPaint() {
		Paint paint = new Paint();
		paints.add(paint);

		paint.setAntiAlias(true);
		paint.setDither(true);
		paint.setColor(currentColor);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeWidth(currentStrokeWidth);

		return paint;
	}

	private Path getCurrentPath() {
		return paths.get(paths.size() - 1);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		currentWidth = canvas.getWidth();
		currentHeight = canvas.getHeight();

		for (int i = 0; i < paths.size(); i++) {
			canvas.drawPath(paths.get(i), paints.get(i));
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		getDrawingRect(drawingRect.get(0));
		ViewCompat.setSystemGestureExclusionRects(this, drawingRect);
	}

	private void touch_start(float x, float y) {
		// new path
		Path path = createPath();
		createPaint();

		path.moveTo(x, y);
		mX = x;
		mY = y;
		hasMoved = false;
	}

	private void touch_move(float x, float y) {
		if (isRealMovement(x, y)) {
			getCurrentPath().quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
			mX = x;
			mY = y;
			hasMoved = true;
		}
	}

	private void touch_up(float x, float y) {
		if (isRealMovement(x, y) || hasMoved) {
			getCurrentPath().lineTo(mX, mY);
			onTouchListener.onAdded();
		}
		else {
			int pathIndex = paths.size() - 1;
			paths.remove(pathIndex);
			paints.remove(pathIndex);
			invalidate();
		}
	}

	private boolean isRealMovement(float x, float y) {
		float dx = Math.abs(x - mX);
		float dy = Math.abs(y - mY);
		return (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isActive) {
			return false;
		}

		float x = event.getX();
		float y = event.getY();

		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				touch_start(x, y);
				this.onTouchListener.onTouchDown();
				break;
			case MotionEvent.ACTION_MOVE:
				touch_move(x, y);
				break;
			case MotionEvent.ACTION_CANCEL:
				// Handle a cancel action the same as action up (for system gestures)
			case MotionEvent.ACTION_UP:
				touch_up(x, y);
				this.onTouchListener.onTouchUp();
				break;
			default:
				return false;
		}

		invalidate();

		return true;
	}

	public void undo() {
		int pathIndex = paths.size() - 1;
		if (pathIndex > 0) {
			paths.remove(pathIndex);
			paints.remove(pathIndex);
			invalidate();
			onTouchListener.onDeleted();
		}
	}

	public void renderOverlay(Canvas combinedCanvas, int srcWidth, int srcHeight) {
		// render overlay to original canvas
		float factorX = (float) combinedCanvas.getWidth() / (float) srcWidth;
		float factorY = (float) combinedCanvas.getHeight() / (float) srcHeight;

		Matrix matrix = new Matrix();
		matrix.setScale(factorX, factorY);

		for (int i = 0; i < paths.size(); i++) {
			Path path = paths.get(i);
			Path scaledPath = new Path();
			path.transform(matrix, scaledPath);

			Paint paint = paints.get(i);
			Paint scaledPaint = new Paint(paint);
			scaledPaint.setStrokeWidth(scaledPaint.getStrokeWidth() * factorX);

			combinedCanvas.drawPath(scaledPath, scaledPaint);
		}
	}

	public void recalculate(int newWidth, int newHeight) {
		if (currentHeight != 0 && currentWidth != 0) {
			float factorX = (float) newWidth / (float) currentWidth;
			float factorY = (float) newHeight / (float) currentHeight;

			Matrix matrix = new Matrix();
			matrix.setScale(factorX, factorY);

			for (int i = 0; i < paths.size(); i++) {
				Path path = paths.get(i);
				Path scaledPath = new Path();
				path.transform(matrix, scaledPath);
				paths.get(i).set(scaledPath);

				Paint paint = paints.get(i);
				Paint scaledPaint = new Paint(paint);
				scaledPaint.setStrokeWidth(scaledPaint.getStrokeWidth() * factorX);
				paints.get(i).set(scaledPaint);
			}
			invalidate();
		}
	}

	public void flip() {
		Matrix flipMatrix = new Matrix();
		flipMatrix.setScale(-1, 1);
		flipMatrix.postTranslate(getWidth(), 0);

		for (Path path : paths) {
			path.transform(flipMatrix);
		}
	}

	public void setColor(int color) {
		currentColor = color;
	}

	public void setStrokeWidth(int width) {
		currentStrokeWidth = width;
	}

	public void setActive(boolean active) {
		isActive = active;
	}

	public boolean getActive() {
		return isActive;
	}

	public int getNumPaths() {
		return paths.size() - 1;
	}

	public void setTouchListener(TouchListener touchListener) {
		this.onTouchListener = touchListener;
	}

	public interface TouchListener {
		void onTouchUp();
		void onTouchDown();
		void onAdded();
		void onDeleted();
	}
}
