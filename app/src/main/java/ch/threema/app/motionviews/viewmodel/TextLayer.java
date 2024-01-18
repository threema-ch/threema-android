/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.motionviews.viewmodel;

public class TextLayer extends Layer {

	private String text;
	private Font font;

	public TextLayer() {
	}

	@Override
	protected void reset() {
		super.reset();
		this.text = "";
		this.font = new Font();
	}

	@Override
	protected float getMaxScale() {
		return Limits.MAX_SCALE;
	}

	@Override
	protected float getMinScale() {
		return Limits.MIN_SCALE;
	}

	@Override
	public float initialScale() {
		return Limits.INITIAL_SCALE;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public Font getFont() {
		return font;
	}

	public void setFont(Font font) {
		this.font = font;
	}

	public interface Limits {
		/**
		 * limit text size to view bounds so that users don't put small font size and scale it 100+
		 * times
		 */
		float MAX_SCALE = 4.0F;
		float MIN_SCALE = 0.4F;

		float MIN_BITMAP_HEIGHT = 0.13F;

		float INITIAL_SCALE = 1.0F; // set the same to avoid text scaling
	}
}
