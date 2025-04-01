/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;


/* ScrollView that behaves correctly in full screen activities upon resize (i.e. when opening the soft keyboard) */
/* Fixes https://code.google.com/p/android/issues/detail?id=5497 */

public class ResizingScrollView extends ScrollView {

    public ResizingScrollView(Context context) {
        super(context);
    }

    public ResizingScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResizingScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // called when size of view changes (e.g. soft keyboard appears or screen is rotated)
        super.onSizeChanged(w, h, oldw, oldh);

        if (h < oldh) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }
}
