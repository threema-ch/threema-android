/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2020 Threema GmbH
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
import androidx.viewpager.widget.ViewPager;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

import java.util.ArrayList;
import java.util.List;

/**
 * Subclass of {@link ViewPager} to create a parallax effect between this View
 * and some Views below it.
 * Based on https://github.com/garrapeta/ParallaxViewPager
 */
public class ParallaxViewPager extends LockableViewPager {

	private List<HorizontalScrollView> mLayers;

	public ParallaxViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public ParallaxViewPager(Context context) {
		super(context);
		init();
	}

	private void init() {
		mLayers = new ArrayList<HorizontalScrollView>();

	}

	public void addLayer(HorizontalScrollView layer) {
		mLayers.add(layer);
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
		super.onPageScrolled(position, positionOffset, positionOffsetPixels);

		final int pageWidth = getWidth();
		final int viewpagerSwipeLength = pageWidth * (getAdapter().getCount() - 1);
		final int viewpagerOffset = (position * pageWidth) + positionOffsetPixels;

		final double viewpagerSwipeLengthRatio = (double) viewpagerOffset / viewpagerSwipeLength;

		for (HorizontalScrollView layer : mLayers) {
			setOffset(layer, viewpagerSwipeLengthRatio);
		}
	}

	private void setOffset(HorizontalScrollView layer, double viewpagerSwipeLengthRatio) {
		int layerWidth = layer.getWidth();
		int layerContentWidth = layer.getChildAt(0)
				.getWidth();
		int layerSwipeLength = layerContentWidth - layerWidth;

		double pageOffset = layerSwipeLength * viewpagerSwipeLengthRatio;

		layer.scrollTo((int) pageOffset, 0);
	}

}
