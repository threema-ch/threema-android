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

package ch.threema.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;

public class AnimatedEllipsisTextView extends androidx.appcompat.widget.AppCompatTextView {
    private static final int MAX_DOTS = 3;
    private static final String ellipsis = "...";
    private CharSequence baseText;

    public AnimatedEllipsisTextView(Context context) {
        super(context);
    }

    public AnimatedEllipsisTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AnimatedEllipsisTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Sets the text to be displayed and the {@link BufferType}.
     * <p/>
     * When required, TextView will use {@link Spannable.Factory} to create final or
     * intermediate {@link Spannable Spannables}. Likewise it will use
     * {@link Editable.Factory} to create final or intermediate
     * {@link Editable Editables}.
     * <p>
     * Subclasses overriding this method should ensure that the following post condition holds,
     * in order to guarantee the safety of the view's measurement and layout operations:
     * regardless of the input, after calling #setText both {@code mText} and {@code mTransformed}
     * will be different from {@code null}.
     *
     * @param text text to be displayed
     * @param type a {@link BufferType} which defines whether the text is
     *             stored as a static text, styleable/spannable text, or editable text
     * @attr ref android.R.styleable#TextView_text
     * @attr ref android.R.styleable#TextView_bufferType
     * @see #setText(CharSequence)
     * @see BufferType
     * @see #setSpannableFactory(Spannable.Factory)
     * @see #setEditableFactory(Editable.Factory)
     */
    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        if (text != null) {
            if (!(text instanceof SpannedString)) {
                this.baseText = text;
            }
        }
    }

    public void animateText(final int numDots) {
        SpannableString spannableString = new SpannableString(ellipsis);
        spannableString.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), numDots, spannableString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (this.baseText != null) {
            setText(TextUtils.concat(this.baseText, spannableString));
        }

        postDelayed(new Runnable() {
            @Override
            public void run() {
                animateText(numDots >= MAX_DOTS ? 0 : numDots + 1);
            }
        }, 300);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        animateText(0);
    }
}
