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

package ch.threema.app.motionviews;

import android.graphics.Bitmap;
import android.media.FaceDetector;

public class FaceItem {
	private FaceDetector.Face face;
	private Bitmap bitmap;
	private float preScale;

	public FaceItem(FaceDetector.Face face, Bitmap bitmap, float preScale) {
		this.face = face;
		this.bitmap = bitmap;
		this.preScale = preScale;
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public FaceDetector.Face getFace() {
		return face;
	}

	public float getPreScale() {
		return preScale;
	}
}
