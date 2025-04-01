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

package ch.threema.app.emojis;

import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import android.text.style.ImageSpan;
import android.widget.TextView;

public class EmojiImageSpan extends ImageSpan {
    private final int size, scale;
    private final FontMetricsInt fm;

    public EmojiImageSpan(@NonNull Drawable drawable, @NonNull TextView tv, int scale) {
        super(drawable);
        drawable.setCallback(tv);
        this.scale = scale;

        fm = tv.getPaint().getFontMetricsInt();
        size = fm != null ? (Math.abs(fm.descent) + Math.abs(fm.ascent)) * scale : 64 * scale;
        getDrawable().setBounds(0, 0, size, size);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                       FontMetricsInt fm) {
        if (fm != null && this.fm != null) {
            fm.ascent = this.fm.ascent * scale;
            fm.descent = this.fm.descent * scale;
            fm.top = this.fm.top * scale;
            fm.bottom = this.fm.bottom * scale;
            return size;
        } else {
            return super.getSize(paint, text, start, end, fm);
        }
    }
}
