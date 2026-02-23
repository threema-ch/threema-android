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
