/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.voip;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.slf4j.Logger;

import ch.threema.app.R;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class CallAnswerIndicatorLayout extends RelativeLayout {
    private static final Logger logger = getThreemaLogger("CallAnswerIndicatorLayout");

    // Constants for Drawable.setAlpha()
    private static final int DARK = 100;
    private static final int LIGHT = 255;

    private ImageView answer0, answer1, answer2, decline0, decline1, decline2;

    public CallAnswerIndicatorLayout(Context context) {
        super(context);
        init();
    }

    public CallAnswerIndicatorLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CallAnswerIndicatorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        logger.debug("newInstance");

        inflate(getContext(), R.layout.call_answer_indicator, this);

        answer0 = findViewById(R.id.answer_arrow0);
        answer1 = findViewById(R.id.answer_arrow1);
        answer2 = findViewById(R.id.answer_arrow2);
        decline0 = findViewById(R.id.decline_arrow0);
        decline1 = findViewById(R.id.decline_arrow1);
        decline2 = findViewById(R.id.decline_arrow2);
    }

    private void updateIndicator(final int selectedLayer) {

        answer0.setImageAlpha(selectedLayer == 0 ? LIGHT : DARK);
        decline0.setImageAlpha(selectedLayer == 0 ? LIGHT : DARK);
        answer1.setImageAlpha(selectedLayer == 1 ? LIGHT : DARK);
        decline1.setImageAlpha(selectedLayer == 1 ? LIGHT : DARK);
        answer2.setImageAlpha(selectedLayer == 2 ? LIGHT : DARK);
        decline2.setImageAlpha(selectedLayer == 2 ? LIGHT : DARK);

        postDelayed(new Runnable() {
            @Override
            public void run() {
                updateIndicator(selectedLayer >= 6 ? 0 : selectedLayer + 1);
            }
        }, 150);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        logger.debug("onAttached");

        updateIndicator(0);
    }

    @Override
    protected void onDetachedFromWindow() {
        logger.debug("onDetached");

        super.onDetachedFromWindow();
    }
}
