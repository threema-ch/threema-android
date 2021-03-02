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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import ch.threema.app.R;
import ch.threema.app.motionviews.gestures.MoveGestureDetector;
import ch.threema.app.motionviews.gestures.RotateGestureDetector;

/**
 * Created on 9/29/16.
 */

public class MotionView extends FrameLayout {
	private TouchListener touchListener;

	public interface Constants {
		float SELECTED_LAYER_ALPHA = 0.15F;
	}

	public interface MotionViewCallback {
		void onEntitySelected(@Nullable MotionEntity entity);

		void onEntityDoubleTap(@NonNull MotionEntity entity);
	}

	// layers
	private final List<MotionEntity> entities = new ArrayList<>();
	@Nullable
	private MotionEntity selectedEntity;

	private Paint selectedLayerPaint;

	// callback
	@Nullable
	private MotionViewCallback motionViewCallback;

	// gesture detection
	private ScaleGestureDetector scaleGestureDetector;
	private RotateGestureDetector rotateGestureDetector;
	private MoveGestureDetector moveGestureDetector;
	private GestureDetectorCompat gestureDetectorCompat;

	// constructors
	public MotionView(Context context) {
		super(context);
		init(context);
	}

	public MotionView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public MotionView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	@SuppressWarnings("unused")
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public MotionView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init(context);
	}

	private void init(@NonNull Context context) {
		/* Typically, if you override {@link #onDraw(android.graphics.Canvas)}
         * you should clear this flag.
		 */
		setWillNotDraw(false);

		selectedLayerPaint = new Paint();
		selectedLayerPaint.setAlpha((int) (255 * Constants.SELECTED_LAYER_ALPHA));
		selectedLayerPaint.setAntiAlias(true);

		// init listeners
		this.scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
		this.rotateGestureDetector = new RotateGestureDetector(context, new RotateListener());
		this.moveGestureDetector = new MoveGestureDetector(context, new MoveListener());
		this.gestureDetectorCompat = new GestureDetectorCompat(context, new TapsListener());

		setOnTouchListener(onTouchListener);

		updateUI();
	}

	public MotionEntity getSelectedEntity() {
		return selectedEntity;
	}

	public List<MotionEntity> getEntities() {
		return entities;
	}

	public void setMotionViewCallback(@Nullable MotionViewCallback callback) {
		this.motionViewCallback = callback;
	}

	public void addEntity(@Nullable MotionEntity entity) {
		if (entity != null) {
			initEntityBorder(entity);
			entities.add(entity);
			selectEntity(entity, false);
			touchListener.onAdded(entity);
			unselectEntity();
		}
	}

	public void addEntityAndPosition(@Nullable MotionEntity entity) {
		if (entity != null) {
			initEntityBorder(entity);
			initialTranslateAndScale(entity);
			entities.add(entity);
			selectEntity(entity, true);
			touchListener.onAdded(entity);
		}
	}

	private void initEntityBorder(@NonNull MotionEntity entity) {
		// init stroke
		int strokeSize = getResources().getDimensionPixelSize(R.dimen.imagepaint_overlay_select_stroke_width);
		int dashSize = getResources().getDimensionPixelSize(R.dimen.imagepaint_overlay_select_dash_size);
		Paint borderPaint = new Paint();
		borderPaint.setStyle(Paint.Style.STROKE);
		borderPaint.setStrokeWidth(strokeSize);
		borderPaint.setAntiAlias(true);
		borderPaint.setColor(ContextCompat.getColor(getContext(), R.color.imagepaint_overlay_select_color));
		borderPaint.setPathEffect(new DashPathEffect(new float[] {dashSize, dashSize}, 0));

		entity.setBorderPaint(borderPaint);
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		// dispatch draw is called after child views is drawn.
		// the idea that is we draw background stickers, than child views (if any), and than selected item
		// to draw on top of child views - do it in dispatchDraw(Canvas)
		// to draw below that - do it in onDraw(Canvas)
		if (selectedEntity != null) {
			selectedEntity.draw(canvas, selectedLayerPaint);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		drawAllEntities(canvas);
		super.onDraw(canvas);
	}

	/**
	 * draws all entities on the canvas
	 *
	 * @param canvas Canvas where to draw all entities
	 */
	private void drawAllEntities(Canvas canvas) {
		for (int i = 0; i < entities.size(); i++) {
			entities.get(i).draw(canvas, null);
		}
	}

	/**
	 * as a side effect - the method deselects Entity (if any selected)
	 *
	 * @return bitmap with all the Entities at their current positions
	 */
	public Bitmap getThumbnailImage() {
		selectEntity(null, false);

		Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
		// IMPORTANT: always create white background, cos if the image is saved in JPEG format,
		// which doesn't have transparent pixels, the background will be black
		bmp.eraseColor(Color.WHITE);
		Canvas canvas = new Canvas(bmp);
		drawAllEntities(canvas);

		return bmp;
	}

	private void updateUI() {
		invalidate();
	}

	private void handleTranslate(PointF delta) {
		if (selectedEntity != null && !selectedEntity.hasFixedPositionAndSize()) {
			float newCenterX = selectedEntity.absoluteCenterX() + delta.x;
			float newCenterY = selectedEntity.absoluteCenterY() + delta.y;
			// limit entity center to screen bounds
			boolean needUpdateUI = false;
			if (newCenterX >= 0 && newCenterX <= getWidth()) {
				selectedEntity.getLayer().postTranslate(delta.x / getWidth(), 0.0F);
				needUpdateUI = true;
			}
			if (newCenterY >= 0 && newCenterY <= getHeight()) {
				selectedEntity.getLayer().postTranslate(0.0F, delta.y / getHeight());
				needUpdateUI = true;
			}
			if (needUpdateUI) {
				updateUI();
			}
		}
	}

	private void initialTranslateAndScale(@NonNull MotionEntity entity) {
		entity.moveToCanvasCenter();
		entity.getLayer().setScale(entity.getLayer().initialScale());
	}

	private void selectEntity(@Nullable MotionEntity entity, boolean updateCallback) {
		if (selectedEntity != null) {
			selectedEntity.setIsSelected(false);
		}
		if (entity != null) {
			entity.setIsSelected(true);
		}
		selectedEntity = entity;
		invalidate();
		if (updateCallback && motionViewCallback != null) {
			motionViewCallback.onEntitySelected(entity);
		}
		touchListener.onSelected(selectedEntity != null);
	}

	public void unselectEntity() {
		if (selectedEntity != null) {
			selectEntity(null, true);
		}
	}

	@Nullable
	private MotionEntity findEntityAtPoint(float x, float y) {
		MotionEntity selected = null;
		PointF p = new PointF(x, y);
		for (int i = entities.size() - 1; i >= 0; i--) {
			if (entities.get(i).pointInLayerRect(p)) {
				selected = entities.get(i);
				break;
			}
		}
		return selected;
	}

	private void updateSelectionOnTap(MotionEvent e) {
		MotionEntity entity = findEntityAtPoint(e.getX(), e.getY());
		selectEntity(entity, true);
	}

	private void updateOnLongPress(MotionEvent e) {
		// if layer is currently selected and point inside layer - move it to front
		updateSelectionOnTap(e);
		if (selectedEntity != null) {
			PointF p = new PointF(e.getX(), e.getY());
			if (selectedEntity.pointInLayerRect(p)) {
				touchListener.onLongClick(selectedEntity, (int) e.getX(), (int) e.getY());
			}
		}
	}

	private void bringLayerToFront(@NonNull MotionEntity entity) {
		// removing and adding brings layer to front
		if (entities.remove(entity)) {
			entities.add(entity);
			invalidate();
		}
	}

	private void moveEntityToBack(@Nullable MotionEntity entity) {
		if (entity == null) {
			return;
		}
		if (entities.remove(entity)) {
			entities.add(0, entity);
			invalidate();
		}
	}

	public void flipSelectedEntity() {
		if (selectedEntity == null) {
			return;
		}
		selectedEntity.getLayer().flip();
		invalidate();
	}

	public void moveSelectedEntityToFront() {
		if (selectedEntity == null) {
			return;
		}
		bringLayerToFront(selectedEntity);
	}

	public void moveSelectedBack() {
		moveEntityToBack(selectedEntity);
	}

	public void deletedSelectedEntity() {
		if (selectedEntity == null) {
			return;
		}

		touchListener.onDeleted(selectedEntity);

		if (entities.remove(selectedEntity)) {
			selectedEntity.release();
			selectedEntity = null;
			invalidate();
		}
	}

	public void deleteEntity(MotionEntity entity) {
		if (entities.contains(entity)) {
			selectedEntity = entity;

			deletedSelectedEntity();
		}
	}

	// memory
	public void release() {
		for (MotionEntity entity : entities) {
			entity.release();
		}
	}

	// gesture detectors

	private final View.OnTouchListener onTouchListener = new View.OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					touchListener.onTouchDown();
					break;
				case MotionEvent.ACTION_UP:
					touchListener.onTouchUp();
					break;
				default:
					break;
			}

			if (scaleGestureDetector != null) {
				scaleGestureDetector.onTouchEvent(event);
				rotateGestureDetector.onTouchEvent(event);
				moveGestureDetector.onTouchEvent(event);
				gestureDetectorCompat.onTouchEvent(event);
			}
			return true;
		}
	};

	private class TapsListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			if (motionViewCallback != null && selectedEntity != null) {
				motionViewCallback.onEntityDoubleTap(selectedEntity);
			}
			return true;
		}

		@Override
		public void onLongPress(MotionEvent e) {
			updateOnLongPress(e);
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			updateSelectionOnTap(e);
			return true;
		}
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			if (selectedEntity != null && !selectedEntity.hasFixedPositionAndSize()) {
				float scaleFactorDiff = detector.getScaleFactor();
				selectedEntity.getLayer().postScale(scaleFactorDiff - 1.0F);
				updateUI();
			}
			return true;
		}
	}

	private class RotateListener extends RotateGestureDetector.SimpleOnRotateGestureListener {
		@Override
		public boolean onRotate(RotateGestureDetector detector) {
			if (selectedEntity != null && !selectedEntity.hasFixedPositionAndSize()) {
				selectedEntity.getLayer().postRotate(-detector.getRotationDegreesDelta());
				updateUI();
			}
			return true;
		}
	}

	private class MoveListener extends MoveGestureDetector.SimpleOnMoveGestureListener {
		@Override
		public boolean onMove(MoveGestureDetector detector) {
			handleTranslate(detector.getFocusDelta());
			return true;
		}
	}

	/* custom stuff */

	public void renderOverlay(Canvas canvas) {
		unselectEntity();
		draw(canvas);
	}

	public int getEntitiesCount() {
		return entities.size();
	}

	public void setTouchListener(TouchListener touchListener) {
		this.touchListener = touchListener;
	}

	public interface TouchListener {
		void onSelected(boolean isSelected);
		void onLongClick(MotionEntity entity, int x, int y);
		void onAdded(MotionEntity entity);
		void onDeleted(MotionEntity entity);
		void onTouchUp();
		void onTouchDown();
	}
}
