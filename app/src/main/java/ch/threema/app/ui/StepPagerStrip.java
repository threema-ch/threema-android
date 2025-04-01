/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

/*
 * Copyright 2012 Roman Nurik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.threema.app.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import ch.threema.app.R;

public class StepPagerStrip extends View {
    private static final int[] ATTRS = new int[]{
        android.R.attr.gravity
    };
    private int mPageCount;
    private int mCurrentPage;

    private int mGravity = Gravity.LEFT | Gravity.TOP;
    private float mTabWidth;
    private float mTabHeight;
    private float mTabSpacing;

    private Paint mPrevTabPaint;
    private Paint mSelectedTabPaint;
    private Paint mSelectedLastTabPaint;
    private Paint mNextTabPaint;

    private RectF mTempRectF = new RectF();

    public StepPagerStrip(Context context) {
        this(context, null, 0);
    }

    public StepPagerStrip(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StepPagerStrip(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS);
        mGravity = a.getInteger(0, mGravity);
        a.recycle();

        final Resources res = getResources();
        mTabWidth = res.getDimensionPixelSize(R.dimen.step_pager_tab_width);
        mTabHeight = res.getDimensionPixelSize(R.dimen.step_pager_tab_height);
        mTabSpacing = res.getDimensionPixelSize(R.dimen.step_pager_tab_spacing);

        mPrevTabPaint = new Paint();
        mPrevTabPaint.setColor(res.getColor(R.color.step_pager_previous_tab_color));

        mSelectedTabPaint = new Paint();
        mSelectedTabPaint.setColor(res.getColor(R.color.step_pager_selected_tab_color));

        mNextTabPaint = new Paint();
        mNextTabPaint.setColor(res.getColor(R.color.step_pager_next_tab_color));

        a = context.obtainStyledAttributes(attrs, R.styleable.StepPagerStrip);
        final int N = a.getIndexCount();
        for (int i = 0; i < N; ++i) {
            int attr = a.getIndex(i);
            switch (attr) {
                case R.styleable.StepPagerStrip_previousColor:
                    mPrevTabPaint.setColor(a.getColor(attr, getResources().getColor(R.color.step_pager_previous_tab_color)));
                    break;
                case R.styleable.StepPagerStrip_selectedColor:
                    mSelectedTabPaint.setColor(a.getColor(attr, getResources().getColor(R.color.step_pager_selected_tab_color)));
                    break;
                case R.styleable.StepPagerStrip_nextColor:
                    mNextTabPaint.setColor(a.getColor(attr, getResources().getColor(R.color.step_pager_next_tab_color)));
                    break;
                default:
                    break;
            }
        }
        a.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mPageCount == 0) {
            return;
        }

        float totalWidth = mPageCount * (mTabWidth + mTabSpacing) - mTabSpacing;
        float totalLeft;
        boolean fillHorizontal = false;

        switch (mGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL:
                totalLeft = (getWidth() - totalWidth) / 2;
                break;
            case Gravity.RIGHT:
                totalLeft = getWidth() - getPaddingRight() - totalWidth;
                break;
            case Gravity.FILL_HORIZONTAL:
                totalLeft = getPaddingLeft();
                fillHorizontal = true;
                break;
            default:
                totalLeft = getPaddingLeft();
        }

        switch (mGravity & Gravity.VERTICAL_GRAVITY_MASK) {
            case Gravity.CENTER_VERTICAL:
                mTempRectF.top = (int) (getHeight() - mTabHeight) / 2;
                break;
            case Gravity.BOTTOM:
                mTempRectF.top = getHeight() - getPaddingBottom() - mTabHeight;
                break;
            default:
                mTempRectF.top = getPaddingTop();
        }

        mTempRectF.bottom = mTempRectF.top + mTabHeight;

        float tabWidth = mTabWidth;
        if (fillHorizontal) {
            tabWidth = (getWidth() - getPaddingRight() - getPaddingLeft()
                - (mPageCount - 1) * mTabSpacing) / mPageCount;
        }

        for (int i = 0; i < mPageCount; i++) {
            mTempRectF.left = totalLeft + (i * (tabWidth + mTabSpacing));
            mTempRectF.right = mTempRectF.left + tabWidth;
            canvas.drawCircle(mTempRectF.left, mTempRectF.top + (mTabHeight / 2), mTabWidth,
                i < mCurrentPage
                    ? mPrevTabPaint
                    : (i > mCurrentPage
                    ? mNextTabPaint
                    : mSelectedTabPaint));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
            View.resolveSize(
                (int) (mPageCount * (mTabWidth + mTabSpacing) - mTabSpacing)
                    + getPaddingLeft() + getPaddingRight(),
                widthMeasureSpec),
            View.resolveSize(
                (int) mTabHeight
                    + getPaddingTop() + getPaddingBottom(),
                heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        scrollCurrentPageIntoView();
        super.onSizeChanged(w, h, oldw, oldh);
    }


    public void setCurrentPage(int currentPage) {
        mCurrentPage = currentPage;
        invalidate();
        scrollCurrentPageIntoView();
    }

    private void scrollCurrentPageIntoView() {
    }

    public void setPageCount(int count) {
        mPageCount = count;
        invalidate();
    }

}
