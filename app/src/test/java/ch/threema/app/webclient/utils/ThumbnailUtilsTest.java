/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.app.webclient.utils;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class ThumbnailUtilsTest {

	/**
	 * Only downscaling should happen, no upscaling.
	 */
	@Test
	public void resizeProportionallyNoChange1() {
		int w = 300;
		int h = 200;
		int maxSidePx = 400;
		final ThumbnailUtils.Size size = ThumbnailUtils.resizeProportionally(w, h, maxSidePx);
		assertEquals(w, size.width);
		assertEquals(h, size.height);
	}

	/**
	 * Only downscaling should happen, no upscaling.
	 */
	@Test
	public void resizeProportionallyNoChange2() {
		int w = 300;
		int h = 200;
		int maxSidePx = 300;
		final ThumbnailUtils.Size size = ThumbnailUtils.resizeProportionally(w, h, maxSidePx);
		assertEquals(w, size.width);
		assertEquals(h, size.height);
	}

	/**
	 * Evenly divisible scale factors should not have any rounding errors.
	 */
	@Test
	public void resizeProportionallyEven1() {
		int w = 300;
		int h = 200;
		int maxSidePx = 150;
		final ThumbnailUtils.Size size = ThumbnailUtils.resizeProportionally(w, h, maxSidePx);
		assertEquals(150, size.width);
		assertEquals(100, size.height);
	}

	/**
	 * Evenly divisible scale factors should not have any rounding errors.
	 * The order of width and height should not matter.
	 */
	@Test
	public void resizeProportionallyEven2() {
		int w = 200;
		int h = 300;
		int maxSidePx = 150;
		final ThumbnailUtils.Size size = ThumbnailUtils.resizeProportionally(w, h, maxSidePx);
		assertEquals(100, size.width);
		assertEquals(150, size.height);
	}

	/**
	 * Not evenly divisible scale factors should properly round.
	 */
	@Test
	public void resizeProportionallyRound() {
		int w = 300;
		int h = 200;
		int maxSidePx = 100;
		final ThumbnailUtils.Size size = ThumbnailUtils.resizeProportionally(w, h, maxSidePx);
		assertEquals(100, size.width);
		assertEquals(67, size.height);
	}
}
