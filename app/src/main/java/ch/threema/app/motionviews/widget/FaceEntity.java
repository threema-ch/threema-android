/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021 Threema GmbH
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
import android.graphics.PointF;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import ch.threema.app.motionviews.FaceItem;
import ch.threema.app.motionviews.viewmodel.Layer;

public abstract class FaceEntity extends MotionEntity {
	public static final float BLUR_RADIUS = 1.5f;

	@NonNull protected final FaceItem faceItem;
	@NonNull protected final Bitmap bitmap;

	public FaceEntity(@NonNull Layer layer,
	                  @NonNull FaceItem faceItem,
	                  @IntRange(from = 1) int originalImageWidth,
	                  @IntRange(from = 1) int originalImageHeight,
	                  @IntRange(from = 1) int canvasWidth,
	                  @IntRange(from = 1) int canvasHeight) {
		super(layer, canvasWidth, canvasHeight);

		this.faceItem = faceItem;
		this.bitmap = faceItem.getBitmap();

		float width = bitmap.getWidth() * faceItem.getPreScale();
		float height = bitmap.getHeight() * faceItem.getPreScale();

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
		srcPoints[9] = 0;

		float widthAspect = 1.0F * canvasWidth / width;
		float heightAspect = 1.0F * canvasHeight / height;
		// fit the smallest size
		holyScale = Math.min(widthAspect, heightAspect);
		float canvasScaleX = (float) canvasWidth / originalImageWidth;
		float canvasScaleY = (float) canvasHeight / originalImageHeight;

		PointF midPoint = new PointF();
		faceItem.getFace().getMidPoint(midPoint);
		midPoint.x = midPoint.x * canvasScaleX;
		midPoint.y = midPoint.y * canvasScaleY;

		float diameter = faceItem.getFace().eyesDistance() * canvasScaleX * (2f * BLUR_RADIUS);

		moveCenterTo(midPoint);
		getLayer().setScale(diameter / (originalImageWidth > originalImageHeight ? canvasHeight : canvasWidth));
	}

	@Override
	public boolean hasFixedPositionAndSize() {
		return true;
	}

	@Override
	public int getWidth() {
		return bitmap.getWidth();
	}

	@Override
	public int getHeight() {
		return bitmap.getHeight();
	}
}
