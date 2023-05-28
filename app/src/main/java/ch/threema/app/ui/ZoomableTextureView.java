/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

/* Lifted and modified from https://github.com/Manuiq/ZoomableTextureView */

/*
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package ch.threema.app.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;

import ch.threema.app.R;

public class ZoomableTextureView extends TextureView {
	private static final String SUPERSTATE_KEY = "superState";
	private static final String MIN_SCALE_KEY = "minScale";
	private static final String MAX_SCALE_KEY = "maxScale";

	private Context context;

	private float minScale = 1f;
	private float maxScale = 5f;
	private float saveScale = 1f;

	public void setMinScale(float scale) {
		if (scale < 1.0f || scale > maxScale)
			throw new RuntimeException("minScale can't be lower than 1 or larger than maxScale(" + maxScale + ")");
		else minScale = scale;
	}

	public void setMaxScale(float scale) {
		if (scale < 1.0f || scale < minScale)
			throw new RuntimeException("maxScale can't be lower than 1 or minScale(" + minScale + ")");
		else minScale = scale;
	}

	private static final int NONE = 0;
	private static final int DRAG = 1;
	private static final int ZOOM = 2;
	private int mode = NONE;

	private Matrix matrix = new Matrix();
	private ScaleGestureDetector mScaleDetector;
	private float[] m;

	private PointF last = new PointF();
	private PointF start = new PointF();
	private float right, bottom;


	public ZoomableTextureView(Context context) {
		super(context);
		this.context = context;
		initView(null);
	}

	public ZoomableTextureView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		initView(attrs);
	}

	public ZoomableTextureView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
		initView(attrs);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Bundle bundle = new Bundle();
		bundle.putParcelable(SUPERSTATE_KEY, super.onSaveInstanceState());
		bundle.putFloat(MIN_SCALE_KEY, minScale);
		bundle.putFloat(MAX_SCALE_KEY, maxScale);
		return bundle;

	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (state instanceof Bundle) {
			Bundle bundle = (Bundle) state;
			this.minScale = bundle.getInt(MIN_SCALE_KEY);
			this.minScale = bundle.getInt(MAX_SCALE_KEY);
			state = bundle.getParcelable(SUPERSTATE_KEY);
		}
		super.onRestoreInstanceState(state);
	}

	private void initView(AttributeSet attrs) {
		TypedArray a = context.getTheme().obtainStyledAttributes(
			attrs,
			R.styleable.ZoomableTextureView,
			0, 0);
		try {
			minScale = a.getFloat(R.styleable.ZoomableTextureView_minScale, minScale);
			maxScale = a.getFloat(R.styleable.ZoomableTextureView_maxScale, maxScale);
		} finally {
			a.recycle();
		}

		setOnTouchListener(new ZoomOnTouchListeners());
	}

	private class ZoomOnTouchListeners implements View.OnTouchListener {
		ZoomOnTouchListeners() {
			super();
			m = new float[9];
			mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
		}

		private GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				performClick();
				return false;
			}
		});

		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			gestureDetector.onTouchEvent(motionEvent);
			mScaleDetector.onTouchEvent(motionEvent);

			matrix.getValues(m);
			float x = m[Matrix.MTRANS_X];
			float y = m[Matrix.MTRANS_Y];
			PointF curr = new PointF(motionEvent.getX(), motionEvent.getY());

			switch (motionEvent.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					last.set(motionEvent.getX(), motionEvent.getY());
					start.set(last);
					mode = DRAG;
					break;
				case MotionEvent.ACTION_UP:
					saveScale = 1.0f;
					matrix = new Matrix();
					mode = NONE;
					break;
				case MotionEvent.ACTION_POINTER_DOWN:
					last.set(motionEvent.getX(), motionEvent.getY());
					start.set(last);
					mode = ZOOM;
					break;
				case MotionEvent.ACTION_MOVE:
					if (mode == ZOOM || (mode == DRAG && saveScale > minScale)) {
						float deltaX = curr.x - last.x;// x difference
						float deltaY = curr.y - last.y;// y difference
						if (y + deltaY > 0)
							deltaY = -y;
						else if (y + deltaY < -bottom)
							deltaY = -(y + bottom);

						if (x + deltaX > 0)
							deltaX = -x;
						else if (x + deltaX < -right)
							deltaX = -(x + right);
						matrix.postTranslate(deltaX, deltaY);
						last.set(curr.x, curr.y);
					}
					break;
				case MotionEvent.ACTION_POINTER_UP:
					mode = NONE;
					break;
			}
			ZoomableTextureView.this.setTransform(matrix);
			ZoomableTextureView.this.invalidate();
			return true;
		}

		private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

			@Override
			public boolean onScaleBegin(ScaleGestureDetector detector) {
				mode = ZOOM;
				return true;
			}

			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				float mScaleFactor = detector.getScaleFactor();
				float origScale = saveScale;
				saveScale *= mScaleFactor;
				if (saveScale > maxScale) {
					saveScale = maxScale;
					mScaleFactor = maxScale / origScale;
				} else if (saveScale < minScale) {
					saveScale = minScale;
					mScaleFactor = minScale / origScale;
				}
				right = getWidth() * saveScale - getWidth();
				bottom = getHeight() * saveScale - getHeight();
				if (0 <= getWidth() || 0 <= getHeight()) {
					matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
					if (mScaleFactor < 1) {
						matrix.getValues(m);
						float x = m[Matrix.MTRANS_X];
						float y = m[Matrix.MTRANS_Y];
						if (mScaleFactor < 1) {
							if (0 < getWidth()) {
								if (y < -bottom)
									matrix.postTranslate(0, -(y + bottom));
								else if (y > 0)
									matrix.postTranslate(0, -y);
							} else {
								if (x < -right)
									matrix.postTranslate(-(x + right), 0);
								else if (x > 0)
									matrix.postTranslate(-x, 0);
							}
						}
					}
				} else {
					matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
					matrix.getValues(m);
					float x = m[Matrix.MTRANS_X];
					float y = m[Matrix.MTRANS_Y];
					if (mScaleFactor < 1) {
						if (x < -right)
							matrix.postTranslate(-(x + right), 0);
						else if (x > 0)
							matrix.postTranslate(-x, 0);
						if (y < -bottom)
							matrix.postTranslate(0, -(y + bottom));
						else if (y > 0)
							matrix.postTranslate(0, -y);
					}
				}
				return true;
			}
		}
	}
}
